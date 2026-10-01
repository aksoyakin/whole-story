package world.wholestory.api.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import world.wholestory.api.TestcontainersConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole sign-up and sign-in path against a real PostgreSQL and a real Redis, driven the way the Next server
 * drives it: a cookie comes back from one call and goes out with the next.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;

    @Test
    void registeringCreatesAnOwnedOrganizationAndSignsThePersonIn() throws Exception {
        String email = uniqueEmail();

        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "Ada Lovelace", "correct horse battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.organizationId").isNotEmpty())
                .andReturn();

        Cookie session = sessionCookie(registered);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void theSessionCookieIsNotReadableOrSendableFromAnotherSite() throws Exception {
        MvcResult registered = register(uniqueEmail());

        String setCookie = registered.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotNull();
        assertThat(setCookie.toLowerCase())
                .contains("httponly")
                .contains("secure")
                .contains("samesite=lax")
                // No Domain attribute: the cookie stays host-only and never reaches the marketing site.
                .doesNotContain("domain=");
        // It outlives the browser window, matching the session the server keeps.
        assertThat(registered.getResponse().getCookie("SESSION").getMaxAge()).isPositive();
    }

    @Test
    void theStoredCredentialIsAPrefixedHashAndNotThePassword() throws Exception {
        String email = uniqueEmail();
        register(email);

        String hash = jdbc.sql("select password_hash from identity.users where email = ?")
                .param(email).query(String.class).single();

        assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain("correct horse battery");
    }

    @Test
    void anAddressCanOnlyBeRegisteredOnceWhateverTheCapitalisation() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email.toUpperCase(), "Someone Else", "another good password")))
                .andExpect(status().isConflict());
    }

    @Test
    void signingInNeedsTheRightPassword() throws Exception {
        String email = uniqueEmail();
        register(email);

        MvcResult signedIn = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "correct horse battery"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        mvc.perform(get("/api/auth/me").cookie(sessionCookie(signedIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong password here"}""".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownAccountAndAMalformedAddressAnswerTheSameAsAWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "some password"}"""))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-address", "password": "some password"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutACookieThereIsNoProfile() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutEndsTheSession() throws Exception {
        Cookie session = sessionCookie(register(uniqueEmail()));
        // Proven to work first, so that the assertion after the logout cannot pass for the wrong reason.
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTooShortPasswordIsRejectedBeforeAnAccountExists() throws Exception {
        String email = uniqueEmail();

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "Ada", "short")))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.sql("select count(*) from identity.users where email = ?")
                .param(email).query(Long.class).single()).isZero();
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "Ada Lovelace", "correct horse battery")))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String body(String email, String name, String password) {
        return """
                {"email": "%s", "name": "%s", "password": "%s"}""".formatted(email, name, password);
    }

    private static Cookie sessionCookie(MvcResult result) {
        return result.getResponse().getCookie("SESSION");
    }

    /** Each test registers its own account: the database is shared by every test in the class. */
    private static String uniqueEmail() {
        return "ada-" + UUID.randomUUID() + "@example.com";
    }
}

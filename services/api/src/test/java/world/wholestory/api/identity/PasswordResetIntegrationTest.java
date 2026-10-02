package world.wholestory.api.identity;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import world.wholestory.api.TestcontainersConfiguration;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole forgotten-password path, against a real PostgreSQL, a real Redis and a real SMTP server.
 * <p>
 * GreenMail is started with TLS on its SMTPS port, so the configuration this service actually ships — implicit
 * TLS on 465 rather than STARTTLS — is the one being exercised. The mail is sent on another thread, through the
 * outbox, which is why the assertions wait for it.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3465",
        "spring.mail.username=no-reply@wholestory.test",
        "spring.mail.password=mailbox-secret",
        // Implicit TLS, exactly as configured for port 465 in production.
        "spring.mail.properties.mail.smtp.ssl.enable=true",
        // The only two concessions to a server running in this JVM: its certificate is self-signed and names no
        // host at all, so neither the chain nor the identity can be checked here. Both stay on in production,
        // and this is the one place they are off.
        "spring.mail.properties.mail.smtp.ssl.trust=*",
        "spring.mail.properties.mail.smtp.ssl.checkserveridentity=false",
        "wholestory.mail.from=no-reply@wholestory.test",
        "wholestory.mail.base-url=https://app.wholestory.test",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final String NEW_PASSWORD = "an entirely different one";
    private static final Pattern LINK = Pattern.compile("https://app\\.wholestory\\.test/reset-password\\?token=(\\S+)");

    @RegisterExtension
    static final GreenMailExtension MAIL = new GreenMailExtension(ServerSetupTest.SMTPS)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("no-reply@wholestory.test", "mailbox-secret"));

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;

    @Test
    void aResetLinkArrivesAndSetsANewPassword() throws Exception {
        String email = register();

        requestReset(email);

        MimeMessage mail = awaitOneMail();
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(mail.getFrom()[0].toString()).isEqualTo("no-reply@wholestory.test");
        assertThat(mail.getSubject()).isEqualTo("Reset your Whole Story password");

        reset(tokenFrom(mail)).andExpect(status().isNoContent());

        signIn(email, NEW_PASSWORD).andExpect(status().isOk());
        signIn(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    /** Only the hash is written down, so a database dump cannot be turned into a working link. */
    @Test
    void theLinkInTheMailIsNotWhatIsStored() throws Exception {
        String email = register();

        requestReset(email);
        String token = tokenFrom(awaitOneMail());

        String storedHash = jdbc.sql("""
                        select t.token_hash from identity.password_reset_tokens t
                        join identity.users u on u.id = t.user_id where u.email = ?""")
                .param(email).query(String.class).single();
        assertThat(storedHash).isNotEqualTo(token).hasSize(64);

        // Nor is the secret left behind in the outbox row that made the mail retryable.
        String publications = String.join("\n", jdbc.sql("""
                        select serialized_event from platform.event_publication
                        where event_type like '%PasswordResetRequested'""")
                .query(String.class).list());
        assertThat(publications).doesNotContain(token).doesNotContain(email);
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String email = register();
        requestReset(email);
        String token = tokenFrom(awaitOneMail());
        reset(token).andExpect(status().isNoContent());

        reset(token).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownAddressIsAnsweredTheSameWayAndSendsNothing() throws Exception {
        requestReset("nobody-" + UUID.randomUUID() + "@example.com").andExpect(status().isNoContent());
        requestReset("not-an-address").andExpect(status().isNoContent());

        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
        assertThat(MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    void somethingThatIsNotALinkIsRefused() throws Exception {
        reset("not-a-real-token").andExpect(status().isBadRequest());
    }

    /** The password rules still apply, and a refused password must not have spent the link on its way out. */
    @Test
    void aTooShortPasswordIsRejectedWithoutSpendingTheLink() throws Exception {
        String email = register();
        requestReset(email);
        String token = tokenFrom(awaitOneMail());

        mvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "password": "short"}""".formatted(token)))
                .andExpect(status().isBadRequest());

        reset(token).andExpect(status().isNoContent());
    }

    /**
     * The reason to reset a password is usually that somebody else knows it, so the sessions they already have
     * open have to end with it.
     */
    @Test
    void resettingThePasswordEndsEverySessionThatWasOpen() throws Exception {
        String email = register();
        Cookie session = signInFor(email, PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

        requestReset(email);
        reset(tokenFrom(awaitOneMail())).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    /** Asking again invalidates the earlier link: one reset, one link. */
    @Test
    void anOlderLinkStopsWorkingOnceANewerOneIsUsed() throws Exception {
        String email = register();
        requestReset(email);
        String first = tokenFrom(awaitOneMail());
        MAIL.purgeEmailFromAllMailboxes();
        requestReset(email);
        String second = tokenFrom(awaitOneMail());

        reset(second).andExpect(status().isNoContent());

        reset(first).andExpect(status().isBadRequest());
    }

    /** Without a cap the endpoint is a way to post mail into somebody else's inbox as often as you like. */
    @Test
    void anAccountIsSentOnlySoManyLinksInAnHour() throws Exception {
        String email = register();

        for (int i = 0; i < 5; i++) {
            requestReset(email).andExpect(status().isNoContent());
        }

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(MAIL.getReceivedMessages()).hasSize(3));
        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
        assertThat(MAIL.getReceivedMessages()).hasSize(3);
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private MimeMessage awaitOneMail() {
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(MAIL.getReceivedMessages()).hasSize(1));
        return MAIL.getReceivedMessages()[0];
    }

    private static String tokenFrom(MimeMessage mail) throws Exception {
        Matcher matcher = LINK.matcher(mail.getContent().toString());
        assertThat(matcher.find()).as("the mail carries a reset link").isTrue();
        return matcher.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token) throws Exception {
        return mvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "password": "%s"}""".formatted(token, NEW_PASSWORD)));
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private Cookie signInFor(String email, String password) throws Exception {
        return signIn(email, password).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    /** Each test registers its own account: the database and the mailbox are shared across the class. */
    private String register() throws Exception {
        String email = "ada-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "name": "Ada Lovelace", "password": "%s"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getResponse().getCookie("SESSION")).isNotNull();
        return email;
    }
}

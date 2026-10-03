package world.wholestory.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;
import java.util.Map;

/** Authentication and session rules. Rationale, including the disabled CSRF filter, is in ADR 0018. */
@Configuration
class SecurityConfig {

    /** Cost 12: measured so that a login stays well under a second while a guessing attempt stays expensive. */
    private static final int BCRYPT_STRENGTH = 12;

    private static final String SESSION_COOKIE = "SESSION";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        return http
                // api is not routed from the internet and no browser talks to it, so there is no cross-site
                // request to defend against here. The browser-facing boundary is the Next app (ADR 0018).
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        // Whoever needs these cannot sign in by definition (ADR 0021).
                        .requestMatchers("/api/auth/password-reset", "/api/auth/password-reset/request").permitAll()
                        // A dashboard its owner chose to share. These read nothing from the session, and what
                        // they will answer for is decided by the site's own sharing flag, not by this matcher.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        // The generated contract feeds the web client's types; api is internal either way.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // An unauthenticated call gets a bare 401 for the Next server to turn into a redirect; a login
                // form served from api would never be seen by anyone.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Logging out is an endpoint in AuthController instead, so that it is part of the published
                // contract; the filter would intercept the path before the controller ever saw it.
                .logout(logout -> logout.disable())
                .build();
    }

    /**
     * Built explicitly rather than through {@code PasswordEncoderFactories}, which encodes with bcrypt at its
     * default cost of 10. Hashes are stored with their algorithm prefix ({@code {bcrypt}$2a$12$…}), so adding
     * Argon2id later means adding it to this map and changing the id used for encoding: matching keeps working
     * for every hash already stored, and no data migration is needed (ADR 0018).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        String idForEncode = "bcrypt";
        return new DelegatingPasswordEncoder(idForEncode,
                Map.of(idForEncode, new BCryptPasswordEncoder(BCRYPT_STRENGTH)));
    }

    /**
     * The cookie's attributes are declared here instead of in configuration. Boot only derives them from
     * {@code server.servlet.session.cookie.*} when an embedded web server is present, so anywhere else they
     * would be quietly absent — and these three attributes are what protects the cookie (ADR 0018).
     * <p>
     * No domain attribute is set, which keeps the cookie host-only: the marketing site on the apex domain never
     * receives it.
     *
     * Its lifetime matches the session's, so closing the browser does not sign the person out of a session the
     * server still considers valid.
     *
     * @param secureCookie set to false only if a browser refuses a Secure cookie over plain-HTTP localhost
     */
    @Bean
    CookieSerializer cookieSerializer(@Value("${wholestory.session.secure-cookie:true}") boolean secureCookie,
                                      @Value("${spring.session.timeout}") Duration sessionTimeout) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge((int) sessionTimeout.toSeconds());
        return serializer;
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /** Shared by the filter chain and the login endpoint, so both read and write the same place. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new HttpSessionSecurityContextRepository(), new RequestAttributeSecurityContextRepository());
    }
}

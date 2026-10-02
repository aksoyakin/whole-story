package world.wholestory.api.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from    the sender; a mailbox that exists at the mail host, so that SPF and DKIM hold
 * @param baseUrl where the dashboard is served from, which is what a reset link has to point at
 */
@ConfigurationProperties("wholestory.mail")
record PasswordResetMailProperties(String from, String baseUrl) {
}

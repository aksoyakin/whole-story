package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import world.wholestory.api.identity.application.PasswordResetMail;
import world.wholestory.api.identity.application.PasswordResetMailer;
import world.wholestory.api.identity.domain.ResetToken;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends the reset link over SMTP (ADR 0021). Plain text, so there is no template engine in the way and the mail
 * reads the same in every client; an analytics product has nothing to gain from a designed e-mail here.
 * <p>
 * Any failure is left to propagate. The caller runs inside the outbox, which is what turns a refused connection
 * into a retry instead of a mail the person never gets.
 */
@Component
@RequiredArgsConstructor
class JavaMailPasswordResetMailer implements PasswordResetMailer {

    private static final String SUBJECT = "Reset your Whole Story password";

    private final JavaMailSender mailSender;
    private final PasswordResetMailProperties properties;

    @Override
    public void send(PasswordResetMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(mail.to().value());
        message.setSubject(SUBJECT);
        message.setText(body(mail));
        mailSender.send(message);
    }

    /**
     * The address is named in the body so that someone who did not ask for this can see which account it was
     * about, and the mail says plainly that nothing has happened yet.
     */
    private String body(PasswordResetMail mail) {
        return """
                Hello %s,

                Somebody asked to reset the password of the Whole Story account for %s.
                Choose a new one within the hour:

                %s

                If that was not you, nothing has changed and you can ignore this message.
                Nobody can sign in with this link without reading it here.

                — Whole Story
                """.formatted(mail.name().value(), mail.to().value(), link(mail.token()));
    }

    private String link(ResetToken token) {
        return "%s/reset-password?token=%s".formatted(
                properties.baseUrl(), URLEncoder.encode(token.value(), StandardCharsets.UTF_8));
    }
}

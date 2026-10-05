package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import world.wholestory.api.identity.application.UserSessions;
import world.wholestory.api.identity.domain.EmailAddress;

/**
 * Ends every session of one person, through Spring Session's index on the principal name.
 * <p>
 * That index only exists with {@code spring.session.data.redis.repository-type: indexed}; without it this bean is not
 * even constructible, so a deployment that forgot the setting fails at startup rather than quietly leaving old
 * sessions alive after a password reset.
 */
@Component
@RequiredArgsConstructor
class SpringSessionUserSessions implements UserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    @Override
    public void revokeAll(EmailAddress email) {
        sessions.findByPrincipalName(email.value()).keySet().forEach(sessions::deleteById);
    }
}

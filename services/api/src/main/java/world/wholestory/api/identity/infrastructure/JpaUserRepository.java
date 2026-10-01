package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import world.wholestory.api.identity.application.UserRepository;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.EmailAlreadyRegisteredException;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.identity.domain.UserId;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JpaUserRepository implements UserRepository {

    /** PostgreSQL's name for the unique constraint on identity.users(email). */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    private final UserEntityRepository users;

    /**
     * Flushed immediately so that a duplicate e-mail fails here, where it can be named, instead of at commit
     * time where the cause is no longer in view.
     */
    @Override
    public void save(User user) {
        try {
            users.saveAndFlush(UserJpaMapper.toEntity(user));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEmail(e)) {
                throw new EmailAlreadyRegisteredException(user.getEmail());
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        return users.findById(id.value()).map(UserJpaMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return users.findByEmail(email.value()).map(UserJpaMapper::toDomain);
    }

    /** Only this one constraint becomes a domain error; any other violation is a bug and must stay visible. */
    private static boolean isDuplicateEmail(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }
}

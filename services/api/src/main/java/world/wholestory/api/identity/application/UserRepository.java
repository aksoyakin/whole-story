package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.identity.domain.UserId;

import java.util.Optional;

/**
 * Port to stored users. The adapter is responsible for turning a violated uniqueness index into
 * {@code EmailAlreadyRegisteredException}: no aggregate can enforce a rule that spans all of them.
 */
public interface UserRepository {

    void save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(EmailAddress email);
}

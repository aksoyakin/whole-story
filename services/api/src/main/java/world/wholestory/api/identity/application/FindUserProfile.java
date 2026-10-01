package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.identity.domain.Role;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/** Reads the signed-in user's profile, including the organization they act in. */
@Service
@RequiredArgsConstructor
public class FindUserProfile {

    private final UserRepository users;
    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Optional<UserProfile> byId(UserId userId) {
        return users.findById(userId).map(this::toProfile);
    }

    private UserProfile toProfile(User user) {
        List<Organization> memberships = organizations.findByMember(user.getId());
        if (memberships.isEmpty()) {
            // Registration creates both in one transaction, so this cannot happen without the data being broken.
            throw new IllegalStateException("user " + user.getId() + " belongs to no organization");
        }
        // v1 gives every user exactly one organization; an organization switcher is a later feature.
        Organization organization = memberships.getFirst();
        Role role = organization.roleOf(user.getId()).orElseThrow();
        return new UserProfile(user.getId(), user.getEmail(), user.getName(), organization.getId(), role);
    }
}

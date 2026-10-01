package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import world.wholestory.api.identity.domain.MembershipException;
import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.identity.domain.OrganizationId;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.Role;
import world.wholestory.api.identity.domain.UserId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void theCreatorBecomesTheOwner() {
        UserId creator = newUserId();

        Organization organization = organizationFor(creator);

        assertThat(organization.roleOf(creator)).contains(Role.OWNER);
        assertThat(organization.memberships()).hasSize(1);
        assertThat(organization.getSiteLimit()).isEqualTo(Organization.DEFAULT_SITE_LIMIT);
        assertThat(organization.getMonthlyEventLimit()).isEqualTo(Organization.DEFAULT_MONTHLY_EVENT_LIMIT);
    }

    @Test
    void theLastOwnerCannotBeRemoved() {
        UserId owner = newUserId();
        Organization organization = organizationFor(owner);
        organization.addMember(newUserId(), Role.ADMIN, NOW);

        assertThatThrownBy(() -> organization.removeMember(owner, NOW))
                .isInstanceOf(MembershipException.class)
                .hasMessageContaining("last owner");
        assertThat(organization.hasMember(owner)).isTrue();
    }

    @Test
    void theLastOwnerCannotBeDemotedEither() {
        UserId owner = newUserId();
        Organization organization = organizationFor(owner);

        assertThatThrownBy(() -> organization.changeRole(owner, Role.VIEWER, NOW))
                .isInstanceOf(MembershipException.class);
        assertThat(organization.roleOf(owner)).contains(Role.OWNER);
    }

    @Test
    void anOwnerCanLeaveOnceSomeoneElseOwnsIt() {
        UserId first = newUserId();
        UserId second = newUserId();
        Organization organization = organizationFor(first);
        organization.addMember(second, Role.OWNER, NOW);

        organization.removeMember(first, NOW);

        assertThat(organization.hasMember(first)).isFalse();
        assertThat(organization.roleOf(second)).contains(Role.OWNER);
    }

    @Test
    void aUserCannotJoinTwice() {
        UserId owner = newUserId();
        UserId member = newUserId();
        Organization organization = organizationFor(owner);
        organization.addMember(member, Role.VIEWER, NOW);

        assertThatThrownBy(() -> organization.addMember(member, Role.ADMIN, NOW))
                .isInstanceOf(MembershipException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void aStrangerCannotBeRemovedOrPromoted() {
        Organization organization = organizationFor(newUserId());
        UserId stranger = newUserId();

        assertThatThrownBy(() -> organization.removeMember(stranger, NOW))
                .isInstanceOf(MembershipException.class)
                .hasMessageContaining("not a member");
        assertThatThrownBy(() -> organization.changeRole(stranger, Role.ADMIN, NOW))
                .isInstanceOf(MembershipException.class);
    }

    @Test
    void theMembershipListCannotBeChangedFromOutside() {
        Organization organization = organizationFor(newUserId());

        assertThatThrownBy(() -> organization.memberships().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aSiteFitsUntilTheLimitIsReached() {
        Organization organization = organizationFor(newUserId());
        organization.applyLimits(2, 500L, NOW);

        assertThat(organization.allowsAnotherSite(1)).isTrue();
        assertThat(organization.allowsAnotherSite(2)).isFalse();
    }

    private static Organization organizationFor(UserId creator) {
        return Organization.createFor(OrganizationId.of(UUID.randomUUID()), creator, PersonName.of("Ada"), NOW);
    }

    private static UserId newUserId() {
        return UserId.of(UUID.randomUUID());
    }
}

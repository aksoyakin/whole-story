package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainException;

/** Membership rules of an organization: who may join, leave or change role. */
public class MembershipException extends DomainException {

    private MembershipException(String message) {
        super(message);
    }

    static MembershipException alreadyAMember(UserId userId) {
        return new MembershipException("user " + userId + " is already a member");
    }

    static MembershipException notAMember(UserId userId) {
        return new MembershipException("user " + userId + " is not a member");
    }

    /** An organization without an owner can never be administered again, so the last one is protected. */
    static MembershipException lastOwner(UserId userId) {
        return new MembershipException("user " + userId + " is the last owner of this organization");
    }
}

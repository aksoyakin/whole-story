package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.NotPermittedException;

/** The caller named an organization they are not a member of. */
public class NotAnOrganizationMemberException extends NotPermittedException {

    public NotAnOrganizationMemberException() {
        super("not a member of this organization");
    }
}

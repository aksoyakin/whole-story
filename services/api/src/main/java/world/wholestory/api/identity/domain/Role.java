package world.wholestory.api.identity.domain;

/**
 * What a member may do inside an organization. v1 only ever creates {@link #OWNER}; the other two exist because
 * the data model is built for team management from the start (D-004) and the check constraint already allows them.
 */
public enum Role {

    /** Full control, including billing and deletion. An organization always has at least one. */
    OWNER,
    /** Manages sites and settings, but cannot remove owners or delete the organization. */
    ADMIN,
    /** Reads dashboards. */
    VIEWER
}

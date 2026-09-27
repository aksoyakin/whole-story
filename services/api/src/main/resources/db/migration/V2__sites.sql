create table sites.sites (
    id               uuid        primary key,
    organization_id  uuid        not null, -- no FK into identity: bounded context boundary (D-038)
    domain           text        not null,
    timezone         text        not null default 'UTC',
    public_dashboard boolean     not null default false,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    deleted_at       timestamptz
);
create unique index sites_domain_active_uq on sites.sites (domain) where deleted_at is null;
create index sites_organization_idx on sites.sites (organization_id) where deleted_at is null;

create table sites.goals (
    id         uuid        primary key,
    site_id    uuid        not null references sites.sites (id) on delete cascade,
    type       text        not null check (type in ('EVENT', 'PAGEVIEW')),
    event_name text,
    page_path  text,
    created_at timestamptz not null,
    check ((type = 'EVENT' and event_name is not null and page_path is null)
        or (type = 'PAGEVIEW' and page_path is not null and event_name is null))
);
create unique index goals_site_target_uq on sites.goals (site_id, type, coalesce(event_name, page_path));

create table sites.ip_exclusions (
    site_id uuid not null references sites.sites (id) on delete cascade,
    cidr    cidr not null,
    primary key (site_id, cidr)
);

create table sites.page_exclusions (
    site_id uuid not null references sites.sites (id) on delete cascade,
    pattern text not null,
    primary key (site_id, pattern)
);

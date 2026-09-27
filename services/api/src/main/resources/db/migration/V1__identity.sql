create table identity.users (
    id                uuid        primary key,
    email             citext      not null unique,
    password_hash     text        not null,
    name              text        not null,
    email_verified_at timestamptz,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);

create table identity.organizations (
    id                  uuid        primary key,
    name                text        not null,
    site_limit          int         not null default 10,
    monthly_event_limit bigint      not null default 1000000,
    created_at          timestamptz not null,
    updated_at          timestamptz not null
);

create table identity.memberships (
    organization_id uuid        not null references identity.organizations (id) on delete cascade,
    user_id         uuid        not null references identity.users (id) on delete cascade,
    role            text        not null check (role in ('OWNER', 'ADMIN', 'VIEWER')),
    created_at      timestamptz not null,
    primary key (organization_id, user_id)
);
create index memberships_user_idx on identity.memberships (user_id);

create table identity.password_reset_tokens (
    token_hash text        primary key,
    user_id    uuid        not null references identity.users (id) on delete cascade,
    expires_at timestamptz not null,
    used_at    timestamptz
);

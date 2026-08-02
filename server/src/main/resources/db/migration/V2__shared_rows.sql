-- Rebuild onto the shared-rows model. V1's per-account schema (every row owned
-- by one account) cannot express shared groups, and merging per-account
-- duplicates into shared rows has no general answer — so this migration is
-- explicitly destructive: pre-release user content is dropped and re-imported
-- or re-synced from clients. Accounts and exchange rates carry over unchanged.
--
-- Access model: rows are shared, not owned. A group and its expenses are one row
-- visible to every member, so there is no per-row account_id. Visibility derives
-- from group_member (group content) and expense_share (non-group expenses).
--
-- Every replicated table has a single-column text primary key, which PowerSync
-- requires on the client. Join tables get a deterministic composite id so two
-- devices creating the same row converge instead of duplicating.

drop publication if exists powersync;
drop table if exists comment cascade;
drop table if exists expense_share cascade;
drop table if exists settlement cascade;
drop table if exists expense cascade;
drop table if exists group_member cascade;
drop table if exists group_ cascade;
drop table if exists person cascade;
drop table if exists category cascade;
drop table if exists user_preferences cascade;

-- One row per login; tokens carry the row id as jti, so deleting the row
-- (logout) revokes every token minted for it.
create table session (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index session_account_idx on session (account_id);

-- account_id names the account this person *is*, not an owner; null until they register.
-- is_me is deliberately absent: it is per-install and stays in the client's Room copy.
create table person (
    id text primary key,
    account_id text references account(id) on delete set null,
    first_name text not null,
    last_name text,
    email text,
    phone text,
    avatar_url text,
    default_currency_code text not null default 'USD',
    country_code text,
    is_registered boolean not null default false,
    external_source text,
    external_id text,
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    -- One person per account.
    unique (account_id),
    -- Splitwise ids are globally unique, so two accounts importing the same
    -- shared expense converge on one row instead of duplicating it.
    unique (external_source, external_id)
);
create index person_email_idx on person (lower(email));

-- Bearer capability to become an unclaimed person: issued when someone invites
-- an email that has no account, redeemed at sign-in. Never replicated — the
-- token must not leak to group members via sync.
create table person_claim (
    token text primary key,
    person_id text not null unique references person(id) on delete cascade,
    created_at timestamptz not null default now()
);

create table group_ (
    id text primary key,
    name text not null,
    type text not null default 'OTHER',
    avatar_url text,
    cover_url text,
    default_currency_code text not null default 'USD',
    simplify_by_default boolean not null default false,
    default_split_json text,
    whiteboard text,
    external_source text,
    external_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    unique (external_source, external_id)
);

-- Membership is the access-control table: you sync a group's content iff your
-- person is a member of it.
create table group_member (
    id text primary key,
    group_id text not null references group_(id) on delete cascade,
    person_id text not null references person(id) on delete cascade,
    role text not null default 'MEMBER',
    joined_at timestamptz not null default now(),
    unique (group_id, person_id)
);
create index group_member_group_idx on group_member (group_id);
create index group_member_person_idx on group_member (person_id);

create table expense (
    id text primary key,
    group_id text references group_(id) on delete set null,
    description text not null,
    cost_minor_units bigint not null,
    currency_code text not null,
    date date not null,
    category_id text not null default 'uncategorized',
    notes text,
    created_by text not null references person(id),
    -- text, not jsonb: jsonb normalizes key order and whitespace, which would
    -- diff against the exact string the client stores and round-trips.
    split_strategy_json text not null,
    is_payment boolean not null default false,
    is_refund boolean not null default false,
    receipt_url text,
    repeat_interval text not null default 'NEVER',
    next_repeat_at timestamptz,
    bundle_id text,
    external_source text,
    external_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (external_source, external_id)
);
create index expense_group_idx on expense (group_id);
create index expense_date_idx on expense (date desc);
create index expense_updated_idx on expense (updated_at desc);

create table expense_share (
    id text primary key,
    expense_id text not null references expense(id) on delete cascade,
    person_id text not null references person(id) on delete cascade,
    paid_minor_units bigint not null,
    owed_minor_units bigint not null,
    unique (expense_id, person_id)
);
create index expense_share_expense_idx on expense_share (expense_id);
create index expense_share_person_idx on expense_share (person_id);

create table comment (
    id text primary key,
    expense_id text not null references expense(id) on delete cascade,
    author_id text not null references person(id),
    content text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index comment_expense_idx on comment (expense_id);

-- Logical replication publication for PowerSync (it streams these tables to clients).
-- user_preferences is deliberately absent: theme, dynamic colour, biometric lock
-- and decimal separator are per-device settings, so preferences stay client-local.
create publication powersync for table
    person, group_, group_member, expense, expense_share, comment;

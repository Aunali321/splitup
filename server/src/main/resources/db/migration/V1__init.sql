-- SplitUp! initial schema. Mirrors the local Room schema; PowerSync replicates
-- selected tables down to clients via logical replication.
--
-- Naming conventions:
--   - snake_case columns
--   - UUID primary keys (text) for distributed id generation; clients generate
--     their own ids and we trust them. No serial keys.
--   - All user-content tables carry (created_at, updated_at, deleted_at) for
--     LWW conflict resolution.
--   - All shareable tables have a user_id (the owning account) so PowerSync rules
--     can scope rows per-user.

create extension if not exists "pgcrypto";

create table account (
    id text primary key,
    email text unique not null,
    password_hash text not null,
    display_name text not null,
    locale text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table person (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    first_name text not null,
    last_name text,
    email text,
    phone text,
    avatar_url text,
    default_currency_code text not null default 'USD',
    country_code text,
    is_me boolean not null default false,
    is_registered boolean not null default false,
    external_source text,
    external_id text,
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (account_id, external_source, external_id)
);
create index person_account_idx on person (account_id);

create table group_ (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    name text not null,
    type text not null default 'OTHER',
    avatar_url text,
    cover_url text,
    default_currency_code text not null default 'USD',
    simplify_by_default boolean not null default true,
    whiteboard text,
    external_source text,
    external_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    unique (account_id, external_source, external_id)
);
create index group_account_idx on group_ (account_id);

create table group_member (
    group_id text not null references group_(id) on delete cascade,
    person_id text not null references person(id) on delete cascade,
    role text not null default 'MEMBER',
    joined_at timestamptz not null default now(),
    primary key (group_id, person_id)
);

create table expense (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    group_id text references group_(id) on delete set null,
    description text not null,
    cost_minor_units bigint not null,
    currency_code text not null,
    date date not null,
    category_id text not null default 'uncategorized',
    notes text,
    created_by text not null references person(id),
    split_strategy_json jsonb not null,
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
    unique (account_id, external_source, external_id)
);
create index expense_account_idx on expense (account_id);
create index expense_group_idx on expense (group_id);
create index expense_date_idx on expense (date desc);
create index expense_updated_idx on expense (updated_at desc);

create table expense_share (
    expense_id text not null references expense(id) on delete cascade,
    person_id text not null references person(id) on delete cascade,
    paid_minor_units bigint not null,
    owed_minor_units bigint not null,
    primary key (expense_id, person_id)
);
create index expense_share_person_idx on expense_share (person_id);

create table settlement (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    group_id text references group_(id) on delete set null,
    from_person_id text not null references person(id),
    to_person_id text not null references person(id),
    amount_minor_units bigint not null,
    currency_code text not null,
    date date not null,
    method text not null default 'UNSPECIFIED',
    notes text,
    external_source text,
    external_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (account_id, external_source, external_id)
);
create index settlement_account_idx on settlement (account_id);

create table comment (
    id text primary key,
    account_id text not null references account(id) on delete cascade,
    expense_id text not null references expense(id) on delete cascade,
    author_id text not null references person(id),
    content text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index comment_expense_idx on comment (expense_id);

create table category (
    id text primary key,
    parent_id text references category(id),
    name text not null,
    icon text not null,
    sort_order int not null default 0
);

create table exchange_rate (
    from_code text not null,
    to_code text not null,
    rate8 bigint not null,         -- rate * 10^8
    date date not null,
    source text not null,
    fetched_at timestamptz not null default now(),
    primary key (from_code, to_code, date)
);

create table user_preferences (
    account_id text primary key references account(id) on delete cascade,
    home_currency_code text not null default 'USD',
    convert_to_home_in_ui boolean not null default true,
    fx_source text not null default 'ECB',
    locale text,
    first_day_of_week int not null default 1,
    theme text not null default 'SYSTEM',
    use_dynamic_color boolean not null default true,
    decimal_separator text,
    biometric_lock boolean not null default false,
    push_enabled boolean not null default true,
    onboarding_completed_at timestamptz
);

-- Logical replication publication for PowerSync (it streams these tables to clients)
create publication powersync for table
    person, group_, group_member, expense, expense_share, settlement,
    comment, category, exchange_rate, user_preferences;

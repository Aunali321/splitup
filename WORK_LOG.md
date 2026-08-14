# Work log

## 2026-08-15 — Whole-codebase review, fixes for the confirmed findings

Six parallel reviews (domain, data/sync, server+deploy, Splitwise import, Compose
UI, build/CI). Everything below was fixed and verified: `:shared:jvmTest` runs 46
tests green (10 new), `:server:compileKotlin`, `:composeApp:compileKotlinDesktop`
and `:composeApp:compileDebugKotlinAndroid` all build.

**Recurring expenses were the worst of it.** Three compounding bugs, none covered
by a test. Occurrences were advanced by stepping off the *previous* occurrence,
so a monthly series from Jan 31 clamped to Feb 29 and then stayed on the 29th
forever; `RepeatInterval.next` is replaced by `occurrence(anchor, n)`, which
measures every occurrence from the template's own date. The due date was stored
as an `Instant` at *local* midnight and read back through the device's zone, so
two devices in different zones derived different dates, and since the occurrence
id is `"<template>@<date>"` they minted two rows for one occurrence and both
synced — the exact duplication the id scheme exists to prevent. The series is now
anchored in UTC, while due-ness is still judged against the user's local calendar.
Finally, `EditExpenseUseCase` recomputed `nextRepeatAt` from the template's date on
*every* edit, rewinding the series and re-upserting occurrences the user had
edited or deleted; it now only resets when the date or interval actually changes,
and materialization skips ids that already exist, so it is strictly additive.

**Split engine.** `equal()` built its map with `mapIndexed{}.toMap()`, so duplicate
participants collapsed while the divisor still counted them and money vanished —
duplicates are now rejected in `SplitStrategy.Equal`/`Adjustment` where the other
preconditions live. The rounding remainder was handed out in id order across *all*
keys, so a 0%-share participant could be charged a cent; it now goes only to
participants with non-zero weight. `adjustment()` could produce negative owed
shares (`exact()` already refused them) and carried a dead `require`. `calculate`
closes with a post-condition that owed sums to the total, so any future strategy
fails at the source rather than two layers down in `Expense.init`.

**Money.** `parse` did `major * scale + minor` unchecked, so 18 digits wrapped
silently into a plausible amount; out-of-range and malformed input (`.50`, `1e5`,
`--5`) now fail as `IllegalArgumentException` like every other validation here.
Dead `sumOrZero` removed.

**CSV export** quoted per RFC 4180 but never neutralised formulas, and quoting does
not stop Excel or Sheets evaluating a leading `=`. Member-supplied fields
(description, category, display names) are prefixed with an apostrophe; the amount
columns are deliberately untouched, since they are ours and legitimately start
with a minus.

**Sync/data.** Saving an expense cleared *all* its shares and re-inserted them,
which queues a DELETE per share — and on a non-group expense a share is what
grants write access, so the deletes applied, the re-inserts were refused, and both
users lost the split. Only departed participants are deleted now. `Person.toEntity`
silently dropped `deleted_at`, so any later save resurrected a deleted person and
uploaded the resurrection; `Person` carries `deletedAt` and both mappers preserve
it. Soft-delete on person/comment and group archive did not move `updated_at`, so
the server's last-write-wins check discarded the tombstone in favour of an older
edit. "Erase all data" left PowerSync's bucket checkpoints intact, so signing back
in resumed from "everything already delivered" and the data never returned;
`SyncService.resetLocalSyncState()` now calls `disconnectAndClear()`.

**Server.** `group_member` and `expense_share` were authorized on the row's parent
but written keyed by a client-supplied `id`, so a write you *are* allowed to make
could land on a row you are not — both ids are derived on the client, so the
server verifies them instead of trusting them. Not changed: `created_by` on new
non-group expenses is still unvalidated, deliberately — the 2026-08-02 entry
removed that rule because it breaks imports, and Splitwise sets the real creator.

**Deploy.** `PS_MANAGEMENT_TOKEN` silently defaulted to `please-change-me-in-env`
while every sibling secret used the fail-fast `:?` form, and that port is published
on all interfaces. Now required.

**Importer.** `expectSuccess` was off, so a 401 or 429 body was fed to the success
deserializer and surfaced as "Field 'expenses' is required"; 429 is also now
retried, since `retryOnServerErrors` does not cover it and a throttle killed the
whole import. Dates were converted in UTC, shifting every expense a day for anyone
east of UTC; they use the device zone.

**UI.** `cleanDecimal` kept only ASCII digits and `.`, so on a German or French
device the decimal keyboard's comma was *deleted* and "12,50" saved as €1250 —
both separators are now accepted, with the last one treated as the decimal point
and earlier ones as grouping. The app lock computed `locked` once in `init` and
never re-armed, so it was bypassed on every return to the foreground; it re-locks
on `ON_STOP`. `AndroidAppLock`/`AndroidImagePicker` were detached unconditionally
in `onDestroy`, and since the incoming activity attaches *before* the outgoing one
is destroyed, any recreation left biometrics and the picker permanently dead —
detach now only clears if the caller still owns the slot. Changing currency left
a stale unparseable amount and a permanently disabled Save. Sign-in enforced the
8-character minimum meant for registration, locking out older accounts. Delete
hard-removed the receipt file behind a *soft*-deleted expense. Repeat selection
applied on Cancel. Detail screens returned early before rendering any chrome,
leaving a blank page with no way back; they render a `LoadingPane`. All twelve
`combine` + `stateIn` view-models ran their debt math on `Dispatchers.Main` —
added `flowOn(Dispatchers.Default)`.

**CI.** The release job had no `contents: write`, so attaching the APK would 403
on repos with the default read-only token. `versionCode` was pinned to 1 for every
tag; both it and `versionName` now come from the tag. Keystore patterns added to
`.gitignore` (nothing sensitive is in history — checked).

**Handoff.** Known-but-unfixed, in rough priority order: an emptied group can still
be taken over by anyone who knows its id (the bootstrap self-check does not prevent
this, despite the comment); rejected sync writes are still discarded with
`batch.complete(null)`, so local and server diverge silently; writes made while
signed out never upload, because triggers are absent and the catch-up seed is
once-per-account; `repoint*` mutates primary keys with UPDATE, which the trigger
scheme cannot express (needs delete + insert); `SplitwiseCategories` maps ids that
appear invented rather than taken from a live `get_categories` — worth one API call
to confirm before trusting any imported category; the importer has no per-record
isolation, so one bad row still costs the whole run; and PKCE is sent alongside a
`client_secret` with no positive confirmation that Splitwise enforces
`code_verifier`. None of the sync work has been exercised against a real
multi-device deployment.

## 2026-08-02 — Full review fix pass (sync correctness, security, tests)

A review of the whole uncommitted change set surfaced ~17 issues; all fixed.
Everything below compiles (`:server:compileKotlin`, `:composeApp:compileKotlinDesktop`,
`:composeApp:compileDebugKotlinAndroid`) and `:shared:jvmTest` runs 34 real tests again.

**Server (`SyncWrite.kt` rewrite).**
- Each `/sync/write` op now runs in its own savepoint (`useNestedTransactions`
  in `Database.kt`). Before, the first SQL error (FK violation from a rejected
  parent, `unique(external_source, external_id)` collisions across accounts)
  aborted the Postgres transaction, poisoned every later op, and 500'd — the
  exact queue-wedge the per-op rejection design was meant to prevent.
- Person writes are scoped: PUT requires the row be yours, or unclaimed *and*
  visible (shared group/expense); hard DELETE of an existing person is always
  refused (it would cascade through everyone's shares). Expense PUT validates
  membership of the *target* `group_id`, not just the current row. Group-member
  bootstrap only accepts your own membership row, so emptied groups can't be
  taken over. The `created_by == me` rule on new non-group expenses is gone —
  it broke imports (Splitwise sets the real creator) and friend-paid expenses;
  visibility comes from shares, which the creator writes anyway.
- **Email is no longer identity.** `claimPersonByEmail` at registration let
  anyone who registered with a victim's address inherit their person and every
  group behind it. Replaced with invite claim tokens: `person_claim` table
  (never replicated), issued by `/groups/{id}/invite` only when the invitee is
  unclaimed and the inviter is a member of every group the invitee is in (the
  token can never grant more than the inviter can already see), redeemed via
  `POST /account/claim` which links the account or merges the ghost into its
  existing person.
- Rate limit: `SPLITUP_BEHIND_PROXY=true` installs XForwardedHeaders so the
  limiter keys on the real client behind a reverse proxy; off by default
  because trusting XFF on a bare deployment lets clients spoof the key.

**Migrations.** V1 restored to its committed content; the shared-rows schema is
now `V2__shared_rows.sql` (explicitly destructive — the per-account model can't
be merged into shared rows mechanically; accounts and exchange rates survive).
Existing deployments no longer hit a Flyway checksum failure. Upgrade note in
deploy/README covers the Postgres password change on existing volumes.

**Client sync lifecycle.**
- CRUD triggers now exist only while signed in: installed on connect, dropped on
  sign-out (`SyncTriggers`, via Room's writer connection). "Erase all data"
  signs out first and `RoomLocalDataReset` drops triggers + purges `ps_crud`
  before clearing tables — a local wipe can no longer replay as a server-side
  wipe of shared groups.
- First connect per account seeds the upload queue with every pre-sign-in row
  (`crudSeeds` generated from the same `Col` model as triggers/downloads;
  `sync_seed` table remembers seeded accounts, DB v6). Previously nothing
  created before sign-in ever uploaded.
- Second-device sign-in no longer loses "me": `repointPerson` first copies the
  local person under the canonical id (`INSERT OR IGNORE`, external ids nulled
  to dodge the unique index, `is_me` kept), dedupes colliding shares and
  memberships, then repoints and deletes the old row. The later download upsert
  only touches synced columns, so `is_me` survives.
- `receipt_url` is a `Col.DeviceUrl`: `file://` paths upload as null and a null
  download no longer clobbers a local file path — receipts stay device-local
  until a real upload story exists; imported https receipts still sync.
- Group deletion order fixed (group row before memberships) so the server-side
  group DELETE is still authorized by the deleter's membership and cascades the
  members itself; before, the trailing group op was refused and left an orphan.
- `SyncApi` maps error bodies to messages ("Wrong email or password" instead of
  a serialization stack trace); sign-in failures clear the stored secrets.

**Domain/UI.**
- `createdBy` now means "who recorded it": AddExpense uses me (was: first
  payer, which the new server rules would have rejected for friend-paid
  non-group expenses and which mislabeled the activity feed);
  `SettleUpUseCase.record` takes `recordedBy`.
- Friend settle-up now plans over the whole relationship via
  `DebtSimplifier.balancesToward` — identical math to the friend-detail header,
  so the two can no longer disagree; it also stops surfacing third-party debts
  from `observeWithFriend`.
- Recurring occurrences get deterministic ids (`template@date`) so two devices
  materializing the same due date converge instead of duplicating.
- Lakh/crore grouping applies to NPR/PKR/BDT/LKR, not just INR.
- Receipts: picker copy moved off the main thread; picked-but-unsaved files,
  replaced receipts, and deleted expenses' receipts are deleted
  (`ImagePicker.discard`); Android picker/app-lock detach in `onDestroy`.
- `BalanceCell` labels per currency (mixed-sign multi-currency rows were
  mislabeled). Tab search includes stale-settled rows. Activity timestamps pad
  the hour. Add-expense pops instead of blanking when its group/friends vanished.
- Sync screen gains an invite-code field (sign-in and signed-in redeem); the
  group-settings invite dialog shows the claim code to share.

**Tests restored.** Money/SplitCalculator/DebtSimplifier tests rewritten on
plain kotlin-test (no kotest dep) including the new behaviors (grouping,
trailing-zero parse, `balancesToward`/`netOf`, payments settling debts).
`domain-tests` in CI gates releases on something real again.

**Verify on device (next session):**
- `powersync_crud` view must exist whenever triggers fire — triggers now only
  exist while signed in and `PowerSyncDatabase.opened` runs before install, but
  confirm the view survives an app restart *while signed in* before the first
  `connect()` completes (a write in that window would otherwise throw).
- `ps_crud` is assumed to be the internal queue table name for `purgeQueue`;
  the existence check makes a rename a silent no-op — confirm once against a
  real PowerSync database.
- Exposed `useNestedTransactions` savepoint behavior under the batch: exercise
  `/sync/write` with a deliberately failing op followed by valid ones.

## 2026-08-02 — Phases 5 & 6: sync UI, invites, and the Exposed fallout

**Phase 5 — sign-in and sync UI.** New `ui/screens/sync/SyncScreen.kt` with its
view model: signed out it collects server address, PowerSync address, email and
password (and a name when registering); signed in it shows the account, any
refused writes, and sign-out. Reached from Account > Sync. `RootViewModel` now
calls `SyncService.start()` at launch, which is a no-op unless a previous
session was signed in. The addresses are collected rather than defaulted because
the app is self-hosted and there is no sensible address to bake in.

**Phase 6 — invites and identity resolution.**
- `POST /groups/{id}/invite {email}` resolves an address to a canonical person:
  an existing registered person wins, otherwise an unclaimed ghost with that
  address, otherwise a new ghost is created. Then it adds the `group_member` row
  and returns the id. Callers who are not members get 403.
- Registration now claims any unclaimed person already created for that email,
  so someone invited *before* they signed up lands straight into their groups.
- Client: `SyncApi.invite`, `SyncService.invite` returning a typed `InviteResult`,
  and an "Invite by email" row in group settings. Nothing is written locally —
  the resulting rows arrive over sync.

Sharing is deliberately online-only. Ghost friends stay creatable offline, but
making one visible to another account needs the server to decide which row
everyone converges on; otherwise two devices invent two people for the same
human and the balances never reconcile.

### The Exposed upgrade was forced, and only a runtime test caught it
Compilation succeeded but `/auth/register` returned 500 with
`NoClassDefFoundError: kotlinx/datetime/Instant`. kotlinx-datetime 0.8.0 turned
that class into a typealias for `kotlin.time.Instant`, so source still compiles,
but **Exposed 0.59.0's bytecode still references the deleted class**. Upgraded to
Exposed 1.3.1, which targets `kotlin.time` — a package move to
`org.jetbrains.exposed.v1.{core,jdbc,datetime,exceptions}` plus a switch from
`SqlExpressionBuilder.eq/less/lessEq` to the top-level operators.

Worth remembering: a green compile said nothing here. Only running the server
found it.

### R8 diagnosis, corrected
The release build failed with a wall of "incompatible Kotlin metadata 2.4.0,
expected 2.2.0" lines, which looked like R8 being too old. It wasn't — those are
noise. The real cause was `Missing class com.google.errorprone.annotations.*`
from Tink, pulled in by the `security-crypto` dependency added for `SecretStore`.
One `-dontwarn` in `proguard-rules.pro` fixes it, and stock AGP R8 works fine.
An R8 override was added while chasing this and has been reverted.

### Verified
23/23 authorization + invite tests pass against real Postgres, covering
cross-account refusal with data intact, person impersonation, comment-authorship
forgery, LWW, non-group expense authorization, invite idempotency, non-members
refused, ghost claiming on registration, and an invited member writing to the
shared group. shared (jvm + android), server, Android debug **and release (R8)**,
and desktop all build.

Still outstanding: two-client device verification (deferred, no device), and the
iOS `SecretStore` is an `NSUserDefaults` placeholder that needs Keychain.

## 2026-08-02 — Phase 4: PowerSync wired into the client

New `shared/.../data/sync/` package. All targets compile; **none of it is reachable
yet** — nothing calls `SyncService.start()` and there is no sign-in UI (phase 5).

- **`SyncSchema.kt`** — the raw-table schema and CRUD triggers are both generated
  from one `Col` list per table, so the download statement, the upload trigger and
  the synced-column set cannot drift apart. Timestamps convert at the boundary
  (epoch millis ↔ ISO) rather than changing Room's storage.
  Downloads use `INSERT … ON CONFLICT(id) DO UPDATE SET` so `person.is_me`
  (declared `localOnly`) survives; `INSERT OR REPLACE` would reset it every sync.
  Triggers emit **whole rows as PUT** for insert and update — PowerSync's default
  partial PATCH cannot distinguish an absent column from an explicit null.
- **`SyncApi.kt`** — typed client for the server. `CrudEntry.opData.jsonValues`
  gives a `JsonObject` with types intact, so numbers stay numbers on the wire.
- **`SplitUpConnector.kt`** — `fetchCredentials` via `/sync/token`, `uploadData`
  via `/sync/write`. Rejections are surfaced, not retried: they arrive inside a 200
  and a retry would not fix an unauthorized write.
- **`SecretStore`** expect/actual — Android `EncryptedSharedPreferences`, desktop
  a `0600` file under the app data dir, iOS a `NSUserDefaults` placeholder (needs
  Keychain before iOS ships). Nothing else in the app stored a long-lived secret.
- **`SyncConfig`** — server and PowerSync URLs are persisted, not constants: the
  app is self-hosted and there is no sensible default to bake in. The sign-in
  screen must collect them.
- **`MaintenanceDao.repointPerson`** — signing in on a second device finds the
  account already has a person, so local shares/memberships/expenses/comments are
  re-pointed at the canonical id and the local duplicate is dropped.
- `BundledSQLiteDriver().loadPowerSyncExtension()` in `DatabaseBuilder` — PowerSync
  reads and writes through Room's own connection, so the extension loads there.

### Next
Phase 5 (sign-in / sync settings UI, calling `start()` at launch) and phase 6
(invites). Then two-client verification, still deferred for lack of a device.

## 2026-08-02 — Toolchain upgrade (forced by PowerSync)

PowerSync's own jars carry **Kotlin 2.3.0 metadata at every version that has raw
tables** (checked 1.11.0, 1.11.2, 1.12.0, 1.13.0, 1.14.1), and Kotlin 2.1.20
cannot read forward. There was no PowerSync build compatible with the old
toolchain, so the upgrade was unavoidable rather than optional.

| | was | now |
|---|---|---|
| Kotlin | 2.1.20 | 2.4.10 |
| KSP | 2.1.20-1.0.32 | 2.3.10 (independent versioning now) |
| Gradle | 8.13 | 9.5.1 |
| AGP | 8.8.0 | 8.13.2 |
| Compose MP | 1.8.0 | 1.11.1 |
| Room | 2.7.0 `androidx.room` | 3.0.1 **`androidx.room3`** |
| androidx.sqlite | 2.5.0 | 2.7.0 |
| Ktor | 3.1.2 | 3.5.2 (plugin 3.5.1 — versions separately) |
| kotlinx-datetime | 0.6.2 | 0.8.0 |
| serialization / coroutines | 1.8.0 / 1.10.1 | 1.11.0 / 1.11.0 |
| compileSdk / targetSdk | 35 | 36 |
| PowerSync | (declared, unused) | 1.14.1 |

Non-obvious things this forced:

- **Room 3 is a different Maven group and package**: `androidx.room3:room3-*`,
  package `androidx.room3`, Gradle plugin id `androidx.room3`, build extension
  `room3 { }`. It also renamed `@TypeConverters`/`@TypeConverter` to
  `@ColumnTypeConverters`/`@ColumnTypeConverter`, and `@Relation` now takes
  plural `parentColumns`/`entityColumns` arrays.
- **kotlinx-datetime 0.8.0 deletes `kotlinx.datetime.Instant` and `Clock`**
  (they are typealiases to `kotlin.time.*` now — no `.class` files). Migrated 38
  files to `kotlin.time.Instant` / `kotlin.time.Clock`. `LocalDate.toEpochDays()`
  returns `Long` now, so the Room converter changed `Int?` → `Long?`.
  This mattered because Android resolved datetime 0.8.0 (via PowerSync) while
  JVM stayed on 0.6.2 — a split that only showed up on the Android target.
- **`iosX64` dropped** — Room 3, androidx.sqlite 2.7 and PowerSync no longer
  publish it (Intel Mac simulators). iosArm64 + iosSimulatorArm64 remain.
- Removed the unsupported `kotlin.mpp.androidSourceSetLayoutVersion` property.

### Held back deliberately
- **AGP stays on 8.x.** AGP 9 refuses `com.android.application` together with
  the Kotlin Multiplatform plugin and wants a module-structure migration to
  `com.android.kotlin.multiplatform.library`. That is unrelated to sync and
  cannot be validated without a device, so it is not bundled in here.
- **compileSdk stays 36.** androidx.core 1.19 / lifecycle 2.11 / activity 1.13
  need compileSdk 37, which needs AGP 9. Pinned core 1.18.0 and lifecycle 2.10.0.
- **Exposed stays 0.59.0.** 1.x is a breaking rewrite of server DAO code with no
  bearing on sync. It compiles fine against `kotlin.time`.
- **Gradle stays 9.5.1**, not 9.6.x: 9.6 removed an internal API AGP 8.x uses.

Verified: shared (jvm + android), server, composeApp android debug APK and
desktop all compile.

## 2026-08-02 — Multi-user sync, phases 1–3 (schema, sync rules, write API)

The server module was fully built and completely unused: no client referenced
any endpoint, PowerSync was in the version catalog but no module depended on
it, and the client had no `account_id` anywhere. Building real Splitwise-style
sharing (invite by email, both sides add expenses) invalidated the data model,
because per-row `account_id` means "one row belongs to one account" and a
shared group is one row seen by many. Ownership was replaced by membership.

Decisions taken with the user: multi-user shared groups (not just multi-device),
sign-in stays OPTIONAL so the app remains local-first, destructive wipe rather
than a migration, verification against local docker-compose.

**Schema (V1__init.sql rewritten in place, Room v5, destructive):**
- `group_`, `expense`, `comment` lost `account_id`. Visibility now derives from
  `group_member` (group content) and `expense_share` (non-group expenses).
- `person.account_id` inverted meaning: it names the account a person *is*,
  null until they register. `unique (account_id)` — one person per account.
- `group_member` and `expense_share` gained single-column TEXT ids
  (`memberId()` / `shareId()`, deterministic `a:b`) because PowerSync requires
  a single `id` primary key client-side. Natural keys kept as UNIQUE.
- `external_source/external_id` uniques went global — Splitwise ids are
  globally unique, so two accounts importing the same shared expense converge.
- `split_strategy_json` jsonb → text: jsonb normalizes key order and whitespace,
  which would diff against the exact string the client round-trips.
- `person.is_me` stays CLIENT-ONLY and is excluded from sync both ways; it means
  "the user of this install", which is meaningless on a shared row.
- `user_preferences` dropped server-side and removed from the publication —
  theme, dynamic colour, biometric lock and decimal separator are per-device.
- Client `exchange_rate` entity deleted (nothing read or wrote it). The server
  table stays, backing /fx/latest.

**Sync rules** rewritten for membership using edition-3 streams. Streams support
JOINs, subqueries and `with:` CTEs, so no denormalizing `group_id` onto
`expense_share`/`comment` was needed (legacy bucket_definitions would require it).

**Server write API** — the largest gap; there were no write endpoints at all.
`POST /sync/write` applies a batch in one transaction, authorizing every op
against group membership / expense participation / comment authorship, with
last-write-wins on `updated_at`. It returns 200 even for rejections, because a
4xx wedges the client's upload queue permanently.
`POST /account/identity` links an account to its person. This exists because
/sync/write authorizes against the caller's person but the person itself
arrives over sync — a deadlock. Taking the whole row breaks it idempotently.

### Verified (not just compiled)
Local stack up (`docker compose --profile sync`), Flyway applied the schema,
replication live over the six published tables, sync rules accepted and
resolving correctly (buckets `groups|0["g1"]`, `groups|2["p1"]`,
`groups|3["e1"]`, `self|0["acc1"]` for one account). 14/14 authorization tests
pass against real Postgres: cross-account writes refused with data intact,
person impersonation blocked, comment-authorship forgery blocked, LWW keeps the
newer write, non-group expenses authorized by author then participation.

### Wire format, measured (do not guess these)
`timestamptz` → ISO-8601 with FIXED 6-digit microseconds (`.5Z` came back as
`.500000Z`); `date` → `YYYY-MM-DD`; `boolean` → integer 0/1, not true/false;
text holding JSON is passed through verbatim. Room keeps epoch millis / epoch
days, so raw-table statements convert at the boundary:
`CAST(ROUND(unixepoch(?,'subsec')*1000) AS INTEGER)` and
`CAST(julianday(?)-2440587.5 AS INTEGER)`. Downloads must use
`INSERT … ON CONFLICT(id) DO UPDATE SET <synced cols>`, never
`INSERT OR REPLACE`, or `is_me` gets wiped.

### Handoff
- Phases 4–6 remain: client PowerSync wiring (pin **1.13.0** — 1.14.0 requires
  Room 3.0 and we are on 2.7.0), auth/sync UI, invites.
- Client triggers must emit FULL rows as PUT, not PowerSync's partial PATCH:
  partial updates cannot distinguish an absent column from an explicit null,
  and full rows are what LWW wants. The server op set is {PUT, DELETE} only.
- `deploy/.env` was missing `POSTGRES_PASSWORD`; appended. The existing postgres
  volume predated it, so the role password needed `ALTER USER` once.
- Device verification (two clients) deferred at user request — no phone or
  emulator available this session.

## 2026-08-01 — Honesty-audit fix
"Settle up" from the Non-group screen planned debts over ALL expenses
(observeAll) instead of only non-group ones; its no-scope branch now uses
observeNonGroup(). Installed on device.

## 2026-08-01 (late night, 4) — Dead-code purge + reuse pass

Post-parity cleanup, no behavior changes intended:

Deleted dead code:
- Client FX chain: `ExchangeRate`/`CurrencyConverter`/`NoOpCurrencyConverter`
  domain file, its mappers, `ExchangeRateDao` + DB accessor + DI single.
  The `exchange_rate` TABLE stays (no Room bump — the device has real imported
  data; the server FX pipeline still syncs into it when client FX lands).
- Room `Converters` cut to Instant + LocalDate — entities store ids/enums as
  strings, so the other ten converters were never invoked.
- `AddExpenseDraft.splitSummary()` (orphaned by the split-picker rewrite).

New shared components (each replacing 2–5 copies):
- `components/SearchField` (was private in FriendDetail, used by five screens).
- `components/SettingRow` — unified SettingsScreen's SettingRow +
  GroupSettings' ActionRow + the hand-rolled simplify-debts switch row
  (gains `iconTint`; delete rows tint icon + title error).
- `components/Lists.kt`: `ListDivider()` (one hairline alpha app-wide,
  replaced ~15 inline `HorizontalDivider(outline.copy(alpha=…))` calls),
  `MonthHeader`, `SectionLabel` (was three private copies).
- `ui/util/Dates.staleSettledCutoff()` shared by the two tab view-models.
- Unused-import sweep across all UI files (delegate `getValue`/`setValue`
  imports restored where `by` is used — they never appear literally).

All targets compile (desktop/jvm/server/androidDebug), APK installed on the
device, launch clean (no FATALs). Final visual pass blocked mid-check: the
phone went to its secure lock screen, which adb can't dismiss.

## 2026-08-01 (late night, 3) — Simplify debts defaults OFF

Per user preference (turn it on only after all expenses are in):
`Group.simplifyByDefault` now defaults to false (model + server V1 default),
and the no-group fallbacks flipped from "simplify unless explicitly off" to
"simplify only when explicitly on" (GroupBalances, SettleUp planning —
friend/non-group scopes are pairwise by nature). Imported groups keep the
setting Splitwise sent. Note: groups created before this change (e.g. "Flat")
still carry true in the DB; toggle in group settings if wanted.

## 2026-08-01 (late night, 2) — Group Balances screen

Captured the real app's Group > Balances (Kashmir, read-only) and replaced our
inline balances toggle with a dedicated `GroupBalancesScreen`
(`Route.GroupBalances`): toolbar "Balances"; per-member rows (44dp avatar +
"You get back ₹250.00" green / "Rafi owes ₹250.00" red / "X is settled up")
with expand chevrons; expanded rows list the suggested repayments involving
that member (24dp avatar + "Rafi owes you ₹250.00" + Settle up pill →
SettleUp); pinned bottom banner "✓ Simplify debts is turned on in this group."
honoring the group toggle (repayments pairwise when off). The inline
`BalancesBreakdown` + `debts` plumbing in GroupDetail was deleted; the
Balances chip navigates. Verified on device against the reference; zero
crashes.

## 2026-08-01 (late night) — Detail-screen parity, commas, quick-split removal

User feedback round against the real app (now installed on the device with
their real imported data — Kashmir Trip is read-only, "Test" group is for
experiments):

- **Two-person quick-split sheet removed entirely.** The real app's split
  chip opens Adjust split directly; the sheet was a flow the user never sees.
  Split chip → SplitPicker always; sheet + VM choice enum deleted.
- **Money.format() grew thousands separators** — Indian lakh/crore grouping
  for INR (₹2,09,670.00), western 3-digit grouping otherwise.
  `toPlainString()` stays plain for inputs/CSV.
- **Expense detail rebuilt to Splitwise's layout** (captured their screen on
  device first): category-tinted band with faded glyph watermark + back /
  attach-photo / delete / edit actions; big description + amount;
  "Added by X on May 25, 2026" / "Updated on …" lines; payer avatar with the
  indented owes-tree and drawn └ connectors; "Spending trends for <group> ::
  <category>" card (same group + same parent category, expense month ±1,
  native bars); receipt, notes, comments below. Attach-photo on detail saves
  `receiptUrl` straight onto the expense.
- **Importer fix found via the real data:** Splitwise's id-0 pseudo-group
  ("Non-group expenses") was being imported as a REAL group; it now maps to
  groupId = null only. The stray group from the earlier import was deleted
  on-device via group settings.

Verified on device with the imported Kashmir data: group list, Kashmir header,
payment rows ("You paid Ahemad Wagh ₹35,000.00"), Urbania Bill detail
(₹89,500.00, owes-tree, trends card with lakh grouping), delete flow. Zero
crashes.

## 2026-08-01 (night) — Flow parity pass (rebuilt five surfaces against real-app screenshots)

User sent 10 screenshots of the real Splitwise (in ~/Downloads/Localsend) and
called out that the flows differed. Rebuilt each surface to match; verified on
the SM-G990E side by side (the real Splitwise is installed there too — read
Kashmir Trip only, never modify it; use a new group for experiments).

- **Add-expense screen**: date/receipt/notes moved from mid-screen chips to
  Splitwise's bottom detail bar (context label left, calendar/camera/note icon
  buttons right); repeat now lives inside the date dialog (calendar + repeat
  chips); form body is just With-row, category+description, ₹+amount,
  "Paid by/split" chips. The "With you and:" step is real now:
  `Route.ExpenseWith` picker (groups one-tap, friends multi-select) opened by
  the tab-level Add-expense FABs; `AddExpenseFlow.friendId` became
  `friendIds: List<String>` (multi-friend non-group expenses).
- **Adjust split**: per-tab title + explainer (Splitwise's exact copy),
  underlined right-aligned inline fields (₹/%/shares/+ affixes), computed
  per-person owed under names on derived tabs, mode-specific tally footers
  ("₹A of ₹B / ₹C left", "N% of 100%", "N total shares",
  "₹X/person (N people)" + All checkbox). Non-equal tabs list everyone
  (checkboxes only on Equally), matching Splitwise; draft gained
  setParticipants/enteredTotal/enteredBasisPoints/enteredShares/owedPreview.
- **Tabs**: icon-only toolbars (search filters the list inline + create-group
  / add-friend actions); the tab FAB is now the global "Add expense" (receipt
  icon) opening the With-picker; Groups lists the "Non-group expenses"
  pseudo-group (new `observeNonGroup` DAO/repo + `NonGroupScreen`); inline
  "Start a new group" / "Add more friends" outlined CTAs; settled-up expander
  now collapses/expands with Splitwise's copy ("Previously settled groups.
  Re-hide").
- **Group detail**: full-bleed image header (cover photo or tint gradient +
  scrim) with circular back/search/settings buttons over it, big overlaid
  name + type-icon "N people" pill; 🎉 settled line; chip order Settle up ·
  Charts (→ Totals screen) · Balances · Whiteboard; payment rows restyled to
  date-badge + banknote tile + "A paid B ₹x" (was italic handshake row).
- **Activity**: real event feed ("Recent activity"): sentences with bold
  names ("You added "Dinner" in "Flat"."), category/payment tile with actor
  avatar badge, colored impact line (You get back / You owe / You paid /
  You received), "1 Aug, 16:14" timestamps. Events derived locally from
  expense created/updated/deleted times (new `observeFeed` includes
  soft-deleted; softDelete bumps updated_at so ordering is by last event).
  Search stays: snapshot expense-row results.
- **Tests deleted on user instruction**: commonTest/jvmTest/server test dirs,
  test deps, catalog entries, useJUnitPlatform blocks — all gone.

Verified on device (screenshots v2_* in session scratchpad): Groups tab,
With-picker, form + bottom bar, Equally/Unequally tabs, group header, Activity
feed. Zero crashes. assembleDebug/assembleRelease/server/desktop all green.

## 2026-08-01 (evening) — Splitwise parity, phase 3 (Pro features free, on-device verified)

User demand: full parity, Pro features included free, tests deprioritized.
Ground truth: a third APK subagent pass (two-person split cells, adjustment
tab, default split, charts internals). All verified live on the SM-G990E over
wireless adb — screenshots in the session scratchpad.

### Shipped this pass
- **Tab filters + collapsing**: `BalanceFilter` + `BalanceFilterHeader`
  (overall line + tune popup: All / Outstanding / You owe / Owes you),
  "Showing only…" caption, "No one to see here." + clear, and
  settled-up-over-one-month rows behind "Show N settled-up friends/groups".
  Both tabs share the components; filtering lives in the view-models.
- **Two-person quick split**: sheet on 2-person expenses — Splitwise's exact
  four options + computed "Rafi owes you ₹250.00" details + More options.
  Full-amount options map to Percent 100/0 so they survive amount edits.
- **By adjustment editor**: fifth split tab ("+ ₹" per person); engine already
  had Splitwise's semantics (remainder equal). Faithful edit hydration too.
- **Group default split**: `Group.defaultSplit` (json column, Room v4,
  server V1 + `default_split_json`), editor dialog in group settings
  (Equal/Percent/Shares, percent-sum validation), applied on new expenses in
  that group, skipped when membership drifted. Free (Pro in Splitwise).
- **Receipt attach**: `ImagePicker` platform abstraction — Android photo
  picker + TakePicture into `filesDir/receipts` via FileProvider (manifest
  gained `files-path`; dead CAMERA permission removed — it would have forced a
  runtime prompt), desktop file dialog into the shared `appDataDir()` (helper
  extracted from DatabaseFactory), iOS honestly unavailable until the Xcode
  shell exists. Receipt chip on the form (hidden when unavailable).
- **Group images**: `GroupAvatar` (imported avatar or tinted type glyph;
  deduped the two per-screen icon mappers) in rows/hero/settings; cover photo
  banner on group detail.
- **Charts (native, free)**: Totals gained the two visuals Splitwise renders
  locally — all-time share donut (ring + center total + share legend) and
  monthly per-parent-category bar list. Single-hue theme marks, labels in text
  color (dataviz method; dynamic-color theme so no fixed hexes).
- **Comments import**: importer now fetches `get_comments` per commented
  expense (Semaphore(8)) and upserts `splitwise-<id>` comment rows in the same
  transaction; system comments and unknown authors skipped.
- **Scoped search**: toolbar search on group and friend detail filtering the
  in-memory list (`List<Expense>.matching`), shared `SearchField`.
- **Friend removal**: on friend detail; deletes the person only when they
  appear in no expense, otherwise explains why history must stay.
- **App lock**: `AppLock` abstraction; Android BiometricPrompt
  (BIOMETRIC_WEAK|DEVICE_CREDENTIAL, androidx.biometric 1.1.0, MainActivity
  is now FragmentActivity), gate in App() with auto-prompt LockScreen,
  Settings toggle that requires successful auth to enable; iOS LAContext impl
  (uncompiled); desktop unavailable.
- Copy/layout fixes found on device: create-group type chips scroll (Other no
  longer wraps vertically), "1 person"/"1 member" singulars.

### On-device verification (SM-G990E, wireless adb, transport 43)
Walked live: onboarding (INR) → create Home group → add friend → add member →
add expense (category picker → Dining out, two-person sheet "You paid, split
equally") → balances (₹250 everywhere consistent) → Totals donut 50% +
monthly category bar → comment add/delete → CSV export share sheet
(flat-export.csv via FileProvider) → filter header on Groups tab. Zero
crashes in logcat. Not verifiable remotely: the biometric prompt itself
(needs a finger), receipt camera flow, repeat materialization over days.

### Verification (builds)
`:shared:compileKotlinJvm`, `:composeApp:compileKotlinDesktop`,
`:composeApp:assembleDebug`, `:server:compileKotlin` green. jvmTest not run
this pass per user instruction (tests deprioritized); test sources still
compile except where untouched.

### Handoff cues
- Room now v4 (default_split_json), server V1 edited again — dev DBs wipe.
- Remaining honest gaps in PARITY.md: home-screen widget, local group image
  upload, in-UI currency conversion (needs client FX), expense-saved overlay.
- iOS: three new platform impls (FileSharer/ImagePicker/AppLock) written but
  uncompiled — first Mac build will tell.
- The split-picker/adjustment/default-split UX specs came from the third APK
  agent; its report is in this session's scratchpad `tasks/` output if wording
  needs re-checking.

## 2026-08-01 (later) — Splitwise parity, phase 2

Continuation of the parity push after the previous session died mid-task
(login expiry) while wiring categories. Basis: the two decompile reports of
Splitwise 26.5.3 (feature inventory + UX teardown); copies live in the session
scratchpad under `apk-reports/` (tmp — re-decompile the APK at
`OtherProjects/reseam/reseam/test-apks/` if they're gone). Earlier parity work
that session (balances on tabs, member management + typed group creation,
record-payment screen) was already done; this session finished the rest.

### Categories (finished the interrupted task)
- Fixed the broken `AddExpenseViewModel` call site the dead session left
  (categoryId/notes params) and hydrated category/notes/repeat on edit.
- `DefaultCategories` (shared, in code): Splitwise's 48-category taxonomy in 7
  parent groups; `SplitwiseCategories` maps their numeric ids 1–48 on import.
- UI: `CategoryIcon` (tinted tile + vector per slug) used on the add-expense
  form (tappable tile → searchable `CategoryPicker` sheet), expense rows, and
  expense detail hero.
- Deleted the dead category DB stack everywhere: Room `CategoryEntity`/
  `CategoryDao`/mappers/converters (DB version → 3, destructive migration),
  server `category` table + PowerSync rule + publication entry (V1 edited in
  place — pre-release), unused Splitwise `get_categories` client/DTOs.
  The taxonomy is fixed in code; a DB table for it was scaffolding.

### Comments, notes, receipts
- Expense detail: comments section (list, add, delete-own) via the existing
  `CommentRepository`; receipt image via Coil `AsyncImage` when `receiptUrl`
  is set; notes editor dialog on the add-expense form ("Add notes" chip).

### Activity search + totals + whiteboard + export
- Activity tab: toolbar search (DAO LIKE search over description+notes;
  snapshot per query like Splitwise's), proper empty states.
- `GroupTotalsScreen`: This month / Last month / All time segmented periods;
  per-currency sections; total spent, you paid for, your share, payments
  made/received (Splitwise Pro stats, free here).
- `GroupWhiteboardScreen`: shared free-text pad saved on `Group.whiteboard`.
- Group detail hero now has the Splitwise action chip row: Settle up,
  Balances, Totals, Whiteboard.
- CSV export: shared `GroupCsv` (Splitwise column format: date, description,
  category, cost, currency, one net column per member, total-balance footer
  when single-currency; RFC-4180 escaping; tests). Delivered via new
  `FileSharer` platform abstraction — Android share sheet (FileProvider added
  to the manifest + `res/xml/file_paths.xml`), desktop AWT save dialog, iOS
  `UIActivityViewController` (uncompiled, needs a Mac). Entry:
  Group settings → "Export as spreadsheet (CSV)".

### Recurring expenses + simplify debts
- `RepeatInterval.next()` + `nextRepeatAt` computed on add/edit;
  `MaterializeRecurringExpensesUseCase` runs on app start (RootViewModel):
  each due date spawns a plain copy, the template advances past today.
  Catch-up over multiple missed periods is tested.
- Add-expense form: Repeat chip (Off/Weekly/Fortnightly/Monthly/Yearly —
  Splitwise's set; DAILY stays import-only). Expense detail shows
  "Repeats monthly · next on …".
- Importer now reads `next_repeat` so imported recurring expenses continue
  locally after migrating off Splitwise.
- Simplify debts: `SettleUpUseCase.plan(simplify=)` + group toggle in
  GroupSettings; honored by group balances breakdown and settle-up planning.

### PARITY.md
Honest gap audit vs the APK inventory: per-area status tables plus an explicit
out-of-scope list (server-social + US-banking features). Notable ❌ still open:
tab filter chips, receipt capture/attach, per-group default split, two-person
quick split options, charts, group cover images, comments import, biometric
lock, in-UI currency conversion.

### Verification
`:shared:jvmTest` 44/44 green; `:server:compileKotlin`,
`:composeApp:compileKotlinDesktop`, `:composeApp:assembleDebug`,
`:composeApp:assembleRelease` (R8) all green. No adb device attached this
session, so nothing was on-device tested; desktop `:composeApp:run` still
SIGSEGVs on this machine (pre-existing, see below).

### Handoff cues
- On-device smoke test when a device is back: category picker + icons,
  comments add/delete, notes, Activity search, Totals numbers, whiteboard
  save, CSV export share sheet (first exercise of the FileProvider), repeat
  materialization (set a repeat, backdate, relaunch), simplify-debts toggle.
- Everything remains uncommitted on `main` (two sessions' worth, ~90 files).
  Suggested split: shared/data, server+deploy, UI, iOS/CI.
- Room DB now v3 and server V1 edited again — existing dev installs/DBs need
  a wipe.
- Receipt loading uses Coil's ServiceLoader network fetcher (ktor); verify a
  real receipt URL renders on device.

## 2026-08-01 — Full fix pass over the code-review findings

Fixed every issue from the review session (settle-up no-op, import crashes, OAuth,
server auth, UI defects). Breaking changes were allowed (pre-release): schemas
edited in place, no compat shims.

### Critical: settle-up recorded payments nothing ever read
`SettleUpUseCase.record` wrote a `settlement` row that no code path read back —
balances everywhere are computed from expenses, so "Mark as paid" had zero
visible effect. Rewrote it to store a payment-type `Expense` (payer share =
cost, recipient owes cost, `isPayment = true`) — the exact shape imported
Splitwise payments already use, and (verified by decompiling the real Splitwise
26.5.3 APK) the exact design Splitwise itself uses: they have NO settlements
table, a settle-up is `create_expense` with `payment=true`. Deleted the whole
dead settlement stack (model, SettlementId/Method, entity, DAO, repository,
mappers, converters, DI, server table + sync rule). Room DB version bumped to 2
with destructive migration (pre-release policy). Tests in
`SettleUpUseCaseTest` prove a recorded payment zeroes the planned debt.

### Money/currency
`Money.parse` now trims trailing zeros before checking precision, so
Splitwise's `"1000.0"` JPY parses instead of aborting the import; only real
precision loss is rejected. Added every ISO 4217 currency with non-2 minor
units (0-dec CLP/ISK/PYG/UGX/…, 3-dec TND/OMR/JOD/IQD/LYD) so
`ofCodeOrDefault` no longer fabricates wrong scales for them.

### Splitwise import
Restructured to fetch everything over the network first, then persist in ONE
Room transaction (`TransactionRunner` → `useWriterConnection` +
`immediateTransaction`) — a failed import leaves no partial state. Re-import
preserves local edits: existing group currency/avatar/cover and person avatar
win over remote.

### Data layer
Expense reads now go through `ExpenseWithShares` (`@Embedded` + `@Relation`),
so Room invalidation tracks `expense_share` too — a share-only write re-emits.
Search LIKE patterns escape `%`/`_`/`\` (`ESCAPE '\'`). Group delete cascades
in one transaction: shares → comments → expenses → members → group (dialog copy
was already promising this). Removed dead `restore`, `saveAll`, `upsertAll`.
`UserPreferencesRepository.save` replaced by mutex-serialized `update(transform)`
so two quick toggles can't revert each other.

### Server
- JWTs carry `jti` = server-side session row; `POST /auth/logout` deletes the
  row and revokes everything minted for it. `parse` validates issuer+audience.
- Audience split: API tokens `splitup`, sync tokens `powersync`; PowerSync's
  `service.yaml` now only accepts `powersync`, so a leaked 60-day session token
  is not a sync credential.
- Registration: insert + catch SQLSTATE 23505 → 409 (no TOCTOU 500).
- Ktor RateLimit on /auth/* (10/min per remote host). CORS no longer combines
  anyHost with allowCredentials (bearer auth, credentials dropped).
- Passwords: argon2 `wipeArray` in `finally`. `DATABASE_PASSWORD` is required
  (no baked-in default anywhere, Dockerfile included).
- FX pipeline implemented: `Jobs.kt` refreshes OpenExchangeRates USD-base rates
  every 12 h (when `OPEN_EXCHANGE_RATES_API_KEY` set); `/fx/latest` serves
  direct rows or derives cross pairs via USD in fixed-point BigDecimal.
- `session` table added to V1 (edited in place — pre-release; existing dev DBs
  need a wipe or `flyway repair`).

### OAuth
PKCE (S256) added to the Splitwise flow (harmless if their Doorkeeper ignores
it, protective if enabled); `state`+PKCE are single-shot — a replayed redirect
finds nothing. The embedded consumer secret is documented as public-by-design
(shared migration app). `BrowserLauncher.handlesOAuthRedirect` gates the flow:
desktop/iOS can't receive `splitup://`, so those platforms show the
paste-an-API-key path instead of hanging at AwaitingBrowser; Cancel added.

### UI
- AddExpense flow rebuilt: `Route.AddExpenseFlow` nested nav graph; the form +
  PaidBy/Split pickers share ONE ViewModel scoped to the graph entry
  (`koinViewModel(viewModelStoreOwner = parentEntry)`). Popping the flow clears
  the draft — the stale-draft-resurrection bug is structurally impossible now.
  The app-lifetime Koin `single` is gone.
- Amount input sanitized via `cleanDecimal` (can't type more decimals than the
  currency has); percent inputs use 2 decimals regardless of currency (JPY can
  split 33.33/33.33/33.34); inline error line on the form; 0-dec placeholder "0".
- First-open currency comes from `prefs.get()` (no more Eagerly-initial USD race).
- Bottom bar renders only on tab roots and never mis-highlights Groups.
- Cold start: `RootViewModel.preferences` is null until Room emits → blank
  themed frame instead of onboarding flash.
- FriendDetail passes a real `nameById`; multi-payer rows show "N people paid";
  SettleUp list keys include currency (no duplicate-key crash multi-currency).
- Onboarding profile save upserts the existing `isMe` row + disables the button
  while saving (no duplicate "me").
- Delete-expense dialog copy matches behavior ("Cannot be undone").
- SplitPicker equal preview shows the real range ("₹33.33–₹33.34/person").
- Inert About/Source rows: About is informational (no chevron), Source opens
  GitHub via BrowserLauncher.
- Dedupe: `groupTint` → theme/Color.kt, month/date formatting → ui/util/Dates.kt.
  Deleted dead AmountInput.kt, `owers`, `Scope.leadingIcon`.

### iOS + CI
`MainViewController.kt` + `initKoin` + `IosBrowserLauncher` wired in iosMain.
NOT compiled — no macOS here; first Mac build should verify (plus the missing
Xcode shell still needs creating). CI: releases now build `assembleRelease`
signed from `SPLITUP_KEYSTORE_*` secrets (debug APK stays a CI artifact only);
`proguard-rules.pro` created (was referenced but missing — release R8 verified
green locally).

### Verification
`:shared:jvmTest` (34 tests) green; `:server:compileKotlin`,
`:composeApp:compileKotlinDesktop`, `:composeApp:assembleDebug`,
`:composeApp:assembleRelease` (R8) all green.

**Known issue (pre-existing, NOT from this pass):** `:composeApp:run` on this
Fedora machine SIGSEGVs inside the bundled androidx.sqlite JNI lib
(`nativeStep` during invalidation-tracker sync, SEGV_ACCERR). Reproduced
identically on baseline commit c474a77 in a clean worktree — environment/native
lib issue, not a regression. Desktop runtime is therefore unverified here; the
prior session's on-device Android verification workflow (wireless adb,
transport-id taps) is the way to smoke-test.

### Handoff cues
- On-device Android re-test recommended for: settle-up now clearing balances,
  edit-expense via the new AddExpenseFlow route, bottom-bar behavior.
- Server needs a DB wipe or `flyway repair` on existing dev deployments
  (V1__init.sql edited in place: +session table, −settlement table).
- `deploy/.env` now also needs `POSTGRES_PASSWORD`.
- PowerSync client integration remains unbuilt (docs now say so explicitly).
- iOS: needs a Mac to compile `iosMain` and an Xcode shell (`iosApp/`) that
  calls `MainViewController()` and registers the `splitup://` scheme.

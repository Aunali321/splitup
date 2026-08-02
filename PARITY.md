# Splitwise parity status

Reconciliation of SplitUp against a decompile-verified feature inventory of
Splitwise Android 26.5.3. SplitUp is offline-first and single-account; parity
targets what a local-first replacement can honestly deliver. Legend:
✅ implemented · 🟡 partial · ❌ missing · ⛔ out of scope.

## Navigation and shell

| Feature | Status | Notes |
|---|---|---|
| 4 bottom tabs: Groups, Friends, Activity, Account | ✅ | Same order as Splitwise |
| Add-expense FAB | ✅ | On group/friend/tab contexts |
| Light/dark/system theme, dynamic color | ✅ | Settings |
| Tab-level list filters (you owe / owe you / outstanding) | ✅ | Filter popup + settled-up-over-a-month collapsing on both tabs |
| Home-screen widget | ❌ | |

## Expenses

| Feature | Status | Notes |
|---|---|---|
| Add expense: description, currency, amount | ✅ | Amount input respects currency decimals |
| Category picker (48 categories, 8 parent groups) | ✅ | Splitwise taxonomy 1:1, searchable, sectioned |
| Category icons on rows and detail | ✅ | |
| Date picker | ✅ | |
| Notes | ✅ | Editor on the form, shown on detail |
| Repeat: off/weekly/fortnightly/monthly/yearly | ✅ | Materializes due copies on app start |
| Receipt image display | ✅ | Rendered on detail when a receipt URL exists (imported expenses) |
| Receipt attach from camera/gallery | ✅ | Android camera/photo picker, desktop file dialog; iOS needs the Xcode shell |
| Receipt OCR scanning + itemization | ⛔ | Splitwise Pro, server-side ML |
| Edit expense (faithful pre-fill) | ✅ | Same form, updates in place |
| Delete (soft) with honest dialog copy | ✅ | |
| Expense detail: payer/owe breakdown, added-by/edited metadata | ✅ | |
| Comments on expenses | ✅ | Local-only: add, list, delete own |
| "Expense saved" overlay / speed bumps | ❌ | Form validates inline instead |
| Max amount cap (99,999,999.99) | ✅ | Money uses Long minor units |

## Splits

| Feature | Status | Notes |
|---|---|---|
| Equally (with per-person toggle) | ✅ | |
| By exact amounts | ✅ | Live "left/over" validation |
| By percentages | ✅ | Basis points; works for 0-decimal currencies |
| By shares | ✅ | |
| By adjustment | ✅ | Dedicated tab; remainder splits equally, Splitwise's exact semantics |
| Multiple payers | ✅ | Per-payer amounts validated against total |
| Two-person quick options ("you paid, split equally", …) | ✅ | Sheet on two-person expenses, wording matches Splitwise |
| Per-group default split | ✅ | Equal/percent/shares from group settings; free (Pro in Splitwise) |

## Payments / settle up

| Feature | Status | Notes |
|---|---|---|
| Settle-up flow with suggested repayments | ✅ | Payment stored as payment-type expense — Splitwise's own design |
| Record payment: editable amount, date, notes, payer swap | ✅ | |
| Simplify debts (per-group toggle) | ✅ | Honored in group balances and settle-up planning |
| Payment detail with edit/delete | ✅ | Payment rows open expense detail |
| Third-party payment rails (PayPal/Venmo/Paytm links) | ⛔ | |
| Payment requests / reminders via email | ⛔ | Server-dependent |

## Groups

| Feature | Status | Notes |
|---|---|---|
| Group list with per-group net balance | ✅ | |
| Group detail: month-grouped expenses, balance header | ✅ | |
| Action chips: Settle up, Balances, Totals, Whiteboard | ✅ | Splitwise's carousel, minus Pro chips |
| Balances breakdown (who owes whom) | ✅ | |
| Totals: this month / last month / all time; total spent, your share, paid-for, payments | ✅ | Per-currency sections; Splitwise gates most stats behind Pro |
| Whiteboard | ✅ | Shared free-text pad |
| Export as CSV | ✅ | Splitwise column format, share sheet / save dialog |
| Charts | ✅ | Native: all-time share donut + monthly category bars in Totals, free (Splitwise's are server WebViews behind Pro) |
| Create group with type (Trip/Home/Couple/Other) | ✅ | |
| Trip dates, settle-up reminders, balance alerts per type | ⛔ | Pro + server email features |
| Rename, add/remove members (blocked while owing) | ✅ | |
| Delete group (cascades) | ✅ | |
| Group cover images / image gallery | 🟡 | Imported avatars/covers render everywhere; no local image upload yet |
| Invite links / QR codes | ⛔ | Server-dependent |
| Currency conversion ("Convert to X") | 🟡 | Server FX pipeline exists; UI conversion not wired |

## Friends

| Feature | Status | Notes |
|---|---|---|
| Friend list with balances and overall header | ✅ | |
| Friend detail: shared expenses (incl. group), balance header | ✅ | |
| Add friend (name/email/phone) | ✅ | Local person; no invite |
| Remove friend | ✅ | Blocked while shared expenses exist (history must stay) |
| Block/report friend | ⛔ | Social features need a server |

## Activity and search

| Feature | Status | Notes |
|---|---|---|
| Activity feed (recent expenses/payments) | ✅ | |
| Search expenses (description + notes) | ✅ | From the Activity tab |
| Per-group / per-friend expense search | ✅ | Toolbar search on both detail screens |
| Push notifications | ⛔ | Server-dependent |

## Splitwise import

| Feature | Status | Notes |
|---|---|---|
| OAuth (PKCE) or API-key import | ✅ | Key path on desktop/iOS, OAuth on Android |
| People, groups, expenses, payments, shares | ✅ | Atomic single-transaction persist; idempotent re-import |
| Categories mapped onto local taxonomy | ✅ | Splitwise numeric ids 1–48 |
| Recurring expenses continue locally | ✅ | `next_repeat` imported; local materializer takes over |
| Receipts, notes, deleted expenses | ✅ | |
| Comments import | ✅ | Per-expense fetch (bounded concurrency), idempotent ids |

## Account and settings

| Feature | Status | Notes |
|---|---|---|
| Profile (name, email, avatar colour) | ✅ | |
| Home currency, theme, dynamic colour | ✅ | |
| Erase all data | ✅ | |
| Passcode / biometric lock | ✅ | BiometricPrompt gate on Android (auth-to-enable); iOS LAContext impl uncompiled |
| Email settings, Pro plans, payment cards | ⛔ | |

## Out of scope (require Splitwise's server or US banking stack)

Invites and account merging, group invite links, friend QR codes, push
notifications, email reminders and balance alerts, Live Splits, blocking and
abuse reporting, server-driven promos and tours, Splitwise Pay / Card /
Pay-by-Bank / imported transactions (Plaid, KYC), Google sign-in, Pro billing.

Sharing and multi-device sync are built against the self-hosted stack in
`server/` and `deploy/`: sign-in is optional, groups are shared by membership
rather than owned, and you invite people by email. Invitees without an account
get a one-time invite code — an email address alone never claims an identity.
Writes are authorized server-side per row (each op in its own savepoint) and
reconciled last-write-wins. **Not yet verified on two devices** — see
`WORK_LOG.md` for the remaining device-verification checklist.

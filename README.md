# SplitUp!

An offline-first, open-source shared-expense app. A spiritual successor to Splitwise:
same proven UX, Material 3 Expressive, no upsells, no ads, no Pro-locked features.

## Status

In development. Domain model, split engine, Compose UI (Android/Desktop), Splitwise import, and the self-hostable server work today. Multi-device sync is server-side only — the PowerSync client integration has not shipped, so devices do not sync yet. iOS is a compile target without an app shell.

## Stack

| Layer | Choice | Why |
|---|---|---|
| Client UI | Compose Multiplatform 1.8 | Android + iOS + Desktop from one codebase |
| Design system | Material 3 Expressive | Latest M3 variant; dynamic color on Android |
| Local DB | Room 2.7 KMP + SQLite (bundled) | Reactive `Flow<T>` queries, KMP-stable since 2024 |
| Sync | PowerSync + Postgres (server side ready, client pending) | OSS, offline-first, Postgres-backed, no lock-in |
| Backend | Ktor 3 + Postgres 17 + Flyway | Kotlin all the way, single Docker stack |
| Auth | HS256 JWT + server-side session revocation + Argon2id | Separate audiences for API and sync tokens |
| Push | UnifiedPush (ntfy) | Google-free; self-hostable |
| OCR | Veryfi/Mindee (BYO) or olmOCR (self-hosted) | User chooses paid or self-host |
| Bank sync | Plaid (optional, BYO keys) | Opt-in only |

## Modules

```
splitup/
├── shared/              KMP library — domain, data, repository (Android/iOS/Desktop/JVM)
├── composeApp/          Compose Multiplatform UI (Android, Desktop, iOS framework)
├── server/              Ktor 3 backend + Postgres migrations + sync rules
└── deploy/              docker-compose for self-hosting
```

## Splitwise importer

One-shot migration: user pastes their Splitwise API key, SplitUp! pulls all
groups, friends, expenses, comments via the documented public API
(<https://dev.splitwise.com/>), maps categories/currencies, and dedupes via
`external_id`. After import, SplitUp! is fully independent — no ongoing
Splitwise dependency.

## Build

Requires JDK 17+, Android SDK (for Android target), Xcode (for iOS).

```bash
./gradlew :composeApp:assembleDebug         # Android APK
./gradlew :composeApp:run                   # Desktop
./gradlew :server:run                       # Backend on :8080
./gradlew :shared:jvmTest                   # Domain logic tests
```

## License

AGPL-3.0 (server) + Apache-2.0 (client). Forks must remain open if hosted publicly.

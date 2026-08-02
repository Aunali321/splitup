# Self-hosting SplitUp!

Two profiles:

| Profile | What you get | When to use |
|---|---|---|
| **default** (`docker compose up -d`) | Postgres + SplitUp! server | Single user, single device, or testing without sync |
| **sync** (`docker compose --profile sync up -d`) | Adds pg-storage + PowerSync | Server side of multi-device sync |

> **Sync status:** the sync profile provisions the server side only (Postgres
> CDC + PowerSync service + scoped tokens). The client-side PowerSync
> integration has not shipped yet, so apps do not sync between devices today.

## 1. Generate secrets

```bash
cd deploy
cp .env.example .env
$EDITOR .env

# Fill in:
#   SPLITUP_SESSION_SECRET=$(openssl rand -base64 32 | tr +/ -_ | tr -d =)
#   POSTGRES_PASSWORD=$(openssl rand -base64 24)
#   POWERSYNC_MANAGEMENT_TOKEN=$(openssl rand -base64 24)
```

`SPLITUP_SESSION_SECRET` is base64url-encoded (URL-safe alphabet, no padding) because PowerSync consumes it as a JWK `k` value, which RFC 7518 §6.4.1 requires in that form.

## 2. Default stack

```bash
docker compose up -d
```

| Service | Port | Notes |
|---|---|---|
| postgres | 127.0.0.1:5432 (override `POSTGRES_PORT`) | Postgres 17, `wal_level=logical` for CDC; loopback only, never published to the network |
| splitup-server | 8080 (override `SPLITUP_PORT`) | Ktor 3, Flyway migrations on boot |

```bash
curl -sS http://localhost:${SPLITUP_PORT:-8080}/health
# {"status":"ok"}
```

## 3. Add sync (PowerSync)

```bash
docker compose --profile sync up -d
```

Adds:

| Service | Port | Notes |
|---|---|---|
| pg-storage | (internal) | PowerSync's own state — separate Postgres instance, never touches the app schema |
| powersync | 8081 (override `POWERSYNC_PORT`) | Sync service; verify with `curl localhost:8081/probes/liveness` |

The server's `/sync/token` endpoint mints 1-hour JWTs with audience `powersync` from the long-lived session token (audience `splitup`). PowerSync only accepts the former, so a leaked session token is not a sync credential and vice versa. `POST /auth/logout` deletes the server-side session row, revoking every token minted for it.

```bash
# Verify end-to-end:
TOKEN=$(curl -sS -X POST localhost:${SPLITUP_PORT:-8080}/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"…"}' | jq -r .token)

SYNC=$(curl -sS -H "Authorization: Bearer $TOKEN" \
  localhost:${SPLITUP_PORT:-8080}/sync/token | jq -r .token)

curl -sS -X POST localhost:${POWERSYNC_PORT:-8081}/sync/stream \
  -H "Authorization: Bearer $SYNC" \
  -H 'Content-Type: application/json' \
  -d '{"include_checksum":true,"raw_data":true}'
# {"checkpoint":{"buckets":[…by_account…,…reference…],"streams":[…]}}
```

## 4. Backup

```bash
docker exec deploy-postgres-1 pg_dumpall -U splitup > splitup-$(date +%F).sql
```

PowerSync's pg-storage holds derived state and can be wiped/recreated from the app Postgres at any time. The app Postgres dump is the source of truth.

## 5. Upgrade

```bash
docker compose pull
docker compose up -d
```

Flyway runs new app migrations on splitup-server boot; PowerSync runs its own migrations on the storage DB.

> **Upgrading a pre-V2 deployment:** migration `V2__shared_rows` moves from
> per-account rows to shared rows. The models are incompatible, so V2 drops all
> user content (accounts and exchange rates survive); clients re-import or
> re-sync. Existing volumes also keep the old default Postgres password —
> either recreate the volume, or before upgrading run:
>
> ```bash
> docker exec deploy-postgres-1 psql -U splitup -c "ALTER USER splitup PASSWORD '<your POSTGRES_PASSWORD>'"
> ```

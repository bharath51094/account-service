# Account Service (account-service)

The internal service of the **Event Ledger** system. It owns account state — **balances** and
**transaction history** — and applies the transactions forwarded by the Event Gateway. It is not exposed
to external clients; only the Gateway calls it.

---

## 1. Architecture

```
Event Gateway (:8080) ──HTTP (sync, X-Internal-Api-Key)──▶ Account Service (:8081)
                                                            H2: accountdb
```

- Part of a two-service system. The **event-service** (separate repo) is the public Gateway; this service
  is internal and applies balance changes.
- Only the Gateway calls it — every request to `/accounts/**` must carry the shared `X-Internal-Api-Key`.
- It has its **own in-memory H2 database** (`accountdb`); no database is shared with the Gateway.
- It reuses the `X-Trace-Id` sent by the Gateway, so its logs share the **same trace id** as the
  originating request.

## 2. Key behaviours

- **Idempotency** — a replayed `transactionId` is not applied twice; the balance is unaffected.
- **Balance** — a running balance is kept on the account row (`previous ± amount`). Net balance is
  CREDITs − DEBITs.
- **One currency per account** — a transaction in a different currency is rejected with `409 Conflict`.
- **Concurrency — pessimistic write lock.** The account row is read with a `PESSIMISTIC_WRITE` lock
  (`SELECT … FOR UPDATE`, 3s timeout), so two transactions on the **same** account run one at a time and
  cannot lose-update the balance. It is a **per-row** lock, so different accounts still process in
  **parallel** — correctness on a hot account without losing throughput. Chosen over optimistic locking
  (`@Version` + retry), which would add retry logic and thrash under contention.

## 3. Prerequisites

- **JDK 21+** (`java -version` should report 21 or later).
- Maven is **not** required — the Maven Wrapper (`mvnw` / `mvnw.cmd`) is bundled.

## 4. Build & run

Start this service **before** the Gateway. It listens on port `8081`, where the Gateway expects it
(`account-service.base-url=http://localhost:8081`).

```bash
git clone https://github.com/bharath51094/account-service.git
cd account-service
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run  → http://localhost:8081
```

## 5. Run the tests

```bash
./mvnw test                   # Windows: mvnw.cmd test
```

## 6. Endpoints

Transaction payload (`POST /accounts/{accountId}/transactions`):
```json
{
  "transactionId": "evt-001",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z"
}
```

| Method | Endpoint | Description | Success | Notes |
|---|---|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction | `201` new / `200` duplicate | Idempotent on `transactionId`; `409` currency mismatch; `401` without a valid key |
| `GET` | `/accounts/{accountId}/balance` | Current balance | `200` | `404` unknown account; `401` without a valid key |
| `GET` | `/accounts/{accountId}` | Account details + recent transactions | `200` | last 10, newest first; `401` without a valid key |
| `GET` | `/health` | Health check | `200` | Actuator; includes DB status; no key required |

The `/accounts/**` calls need the internal key — normally the Gateway supplies it:
```bash
curl -i -X POST http://localhost:8081/accounts/acct-123/transactions \
  -H "Content-Type: application/json" -H "X-Internal-Api-Key: local-internal-api-key" \
  -d '{"transactionId":"evt-001","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

curl -H "X-Internal-Api-Key: local-internal-api-key" http://localhost:8081/accounts/acct-123/balance
curl http://localhost:8081/health      # no key required
```

## 7. Observability

| What | How to view |
|---|---|
| **Health** (+ DB connectivity) | `curl http://localhost:8081/health` |
| **Structured logs** | ECS JSON on the console — `@timestamp`, `log.level`, `service.name`, `traceId`, `message` |
| **Tracing** | logs carry the same `traceId` as the originating Gateway request |

This service exposes **only `/health`** — there is no `/metrics` or `/prometheus` here by design. All
application metrics (including resilience) live on the Gateway, which is the one that calls out to
dependencies.

## 8. Internal authentication & public access

- **Restricted:** every `/accounts/**` call must send `X-Internal-Api-Key` (it must equal
  `internal.api-key` in `application.yaml`). Missing or wrong → `401`. Only the Gateway has the key.
- **Open (no key):** `/health` and `/h2-console`. **Why:** health/liveness/readiness probes come from
  infrastructure (orchestrators, load balancers, monitors) that don't carry app credentials, and `/health`
  exposes only status + DB connectivity — no account data — so gating it would break standard probing for
  no security gain. The key protects the data-bearing endpoints specifically. In a real deployment this
  service would also sit on a private network and the H2 console would be disabled.

## 9. H2 console (inspect accounts & transactions)

While the app runs, open `http://localhost:8081/h2-console`:
- **JDBC URL:** `jdbc:h2:mem:accountdb`  •  **User:** `sa`  •  **Password:** *(blank)*

```sql
SELECT * FROM ACCOUNTS;
SELECT * FROM ACCOUNT_TRANSACTIONS;
```
Data is in-memory and is wiped on restart.

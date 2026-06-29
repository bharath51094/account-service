# Account Service (account-service)

Internal service of the **Event Ledger** system. It owns account state — **balances** and
**transaction history** — and applies transactions forwarded by the Event Gateway. It is **not**
exposed to external clients; only the Gateway calls it.

> Companion service: **event-service** (separate repo) — the public-facing Gateway. It normally
> starts together with this service.
>
> The system architecture and the rationale behind every design choice live in the Gateway repo's
> `DESIGN_DECISIONS.md`.

---

## 1. Prerequisites

- **JDK 21 or later** (the project targets Java 21).
- **Git**.
- Maven is **not** required — the Maven Wrapper (`mvnw` / `mvnw.cmd`) is bundled.

Check Java:
```bash
java -version   # should report 21+
```

## 2. Build & run the app

```bash
# 1. Clone and navigate INTO the project folder
git clone https://github.com/bharath51094/account-service.git
cd account-service

# 2. Build
./mvnw clean package          # Windows: mvnw.cmd clean package

# 3. Run (starts on http://localhost:8081)
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

This service listens on port **8081** — which is where the Event Gateway expects it
(`account-service.base-url=http://localhost:8081`). Start this service **before** the Gateway.

## 3. Running the tests

```bash
./mvnw test                   # Windows: mvnw.cmd test
```

## 4. H2 (in-memory database)

This service uses an **in-memory H2 database** — data exists only while the app is running and is
**wiped on restart**. To inspect accounts and transactions, open the H2 console **while the app is
running**:

1. Browse to `http://localhost:8081/h2-console`.
2. Log in with:
   - **JDBC URL:** `jdbc:h2:mem:accountdb`
   - **User Name:** `sa`
   - **Password:** *(leave blank)*
   - **Driver Class:** `org.h2.Driver`
3. Run queries, e.g.:

```sql
SELECT * FROM ACCOUNTS;
SELECT * FROM ACCOUNT_TRANSACTIONS;

-- account with its transactions
SELECT a.ACCOUNT_ID, a.BALANCE, a.CURRENCY, t.TRANSACTION_ID, t.TYPE, t.AMOUNT
FROM ACCOUNTS a
JOIN ACCOUNT_TRANSACTIONS t ON t.ACCOUNT_ID = a.ID
ORDER BY t.EVENT_TIMESTAMP;
```

## 5. Configuration, key behaviours & observability

| Concern | Detail |
|---|---|
| HTTP port | `8081` (`server.port`) |
| Database | in-memory H2 `jdbc:h2:mem:accountdb` |
| Logs | **structured JSON** (ECS) on the console — `@timestamp`, `log.level`, `service.name`, `traceId`, `message` |
| Tracing | reuses the `X-Trace-Id` propagated by the Gateway (or generates one), so logs here share the **same trace id** as the originating Gateway request |
| Health | `GET /health` (Actuator) — reports `UP`/`DOWN` plus DB connectivity |

Key behaviours:
- **Idempotency** — a replayed `transactionId` is not applied twice (balance unaffected).
- **Balance** — kept as an **incremental running balance** on the account row (`previous ± amount`),
  computed under a **pessimistic write lock** so concurrent transactions on the same account can't
  lost-update it.
- **One currency per account** — a transaction whose currency differs from the account's existing
  currency is rejected with **`409 Conflict`**.

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
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction | `201` new / `200` duplicate | Idempotent on `transactionId`; `409` on currency mismatch |
| `GET` | `/accounts/{accountId}/balance` | Current balance | `200` | `404` if account unknown |
| `GET` | `/accounts/{accountId}` | Account details + recent transactions | `200` | last 10 transactions, newest first |
| `GET` | `/health` | Health check | `200` | Actuator, includes DB status |

Examples:
```bash
# Apply a transaction
curl -i -X POST http://localhost:8081/accounts/acct-123/transactions \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"evt-001","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

# Balance
curl http://localhost:8081/accounts/acct-123/balance

# Account details + recent transactions
curl http://localhost:8081/accounts/acct-123

# Health
curl http://localhost:8081/health
```

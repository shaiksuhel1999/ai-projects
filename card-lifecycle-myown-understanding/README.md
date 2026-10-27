# Credit Card Lifecycle — POS to Issuer, Billing, Payments & Loans

An end-to-end **Spring Boot 3 / Java 21** simulation of a credit card's full life cycle,
modeling the real four-party payment flow and everything around it:

> **Onboard customer → Issue & activate card → Authorize at POS
> (Acquirer → Card Network → Issuer) → Capture → Settle → Cut statement →
> Make monthly payment → Apply for a loan / convert a purchase to EMI → Repay.**

Everything runs in-memory (H2), documents itself via Swagger UI, and a scripted
walk-through narrates the whole journey to the console on startup.

**Design docs:** [High-Level Design](docs/HLD.md) · [Low-Level Design](docs/LLD.md)
(architecture, ER model, state machines, sequence & flow diagrams).

---

## Tech stack

| Concern            | Choice                                   |
|--------------------|------------------------------------------|
| Language / runtime | Java 21                                  |
| Framework          | Spring Boot 3.3.5 (Web, Data JPA, Validation, Security) |
| Database           | H2 (in-memory, auto-created)             |
| API docs           | springdoc-openapi / Swagger UI           |
| Money math         | `BigDecimal` throughout (2 dp, HALF_EVEN)|
| Build              | Maven                                    |

### Authentication

Endpoints under `/api/**` require **HTTP Basic** auth. The startup seeder creates two logins
(and prints them to the console):

| User | Password | Role | Can do |
|------|----------|------|--------|
| `admin` | `admin123` | ADMIN | everything (ops: capture/settle/refund, statements, disputes, adjustments…) |
| `aarav` | `password` | CUSTOMER | only the seeded customer's own cards/accounts (view, pay, rewards, autopay, apply for loans) |

Swagger UI, the OpenAPI docs and the H2 console are open. Authorize a customer only for
accounts they own; ops actions are ADMIN-only (`403` otherwise, `401` when unauthenticated).

## Run it

```bash
mvn spring-boot:run
```

Or build the executable JAR and run it:

```bash
mvn clean package
java -jar target/card-life-cycle-1.0.0.jar
```

Or with Docker (Compose):

```bash
docker compose up --build
```

Or on Kubernetes (manifests in [`k8s/`](k8s) — Namespace, ConfigMap, Secret, Deployment with
health probes, Service, Ingress, HPA):

```bash
docker build -t card-life-cycle:1.0.0 .
kubectl apply -f k8s/
```

Health probes: `/actuator/health/liveness` and `/actuator/health/readiness`.

Then open:

- **Swagger UI** — http://localhost:8080/swagger-ui.html (interactive API, grouped 1→8 in flow order)
- **H2 console** — http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:cardlifecycle`, user `sa`, empty password)

On startup the app **seeds a ready-to-use ACTIVE card** and prints its ids/token, then
runs a full scripted lifecycle (`DemoRunner`) so you can see every step in the logs.
Disable the scripted demo with `--demo.run=false`.

Run the tests:

```bash
mvn test
```

---

## Architecture

Layered, with the **payment rail modeled as distinct hops** so the message flow is explicit:

```
web (REST controllers)
  └── service (business logic)
        ├── AcquirerService   POS entry point; validates merchant, resolves card token
        ├── NetworkService    routes by card brand (BIN) to the issuer
        ├── IssuerService      ← the brain: risk checks, holds, approve/decline
        ├── TransactionService capture / reverse / settle / refund
        ├── BillingService     statement cut, interest, late fees
        ├── PaymentService     minimum / total / custom payments
        ├── LoanService        underwriting, disbursement, EMI schedules, repayment
        └── CardService        issuance, activation, block/unblock, close, cash advance
  └── repository (Spring Data JPA)
  └── model (JPA entities + enums)
```

### The authorization flow (POS → Issuer)

```
POS terminal ──authorize──▶ Acquirer ──▶ Card Network ──▶ Issuer
                                                            │ risk & credit checks
                                                            │ place hold on available credit
POS receipt  ◀──approved / declined + auth code ───────────┘
```

The `authorizeResponse.flowTrace` field shows this path for every request, e.g.
`POS[POS-0001] -> Acquirer[HDFC Acquiring] -> Network[MASTERCARD] -> Issuer => APPROVED`.

### Money model

A **`CreditAccount`** holds the revolving line; a **`Card`** is the instrument on top of it.
The key invariant:

```
availableCredit = creditLimit − currentBalance − holdAmount
```

| Event      | Effect                                                             |
|------------|-------------------------------------------------------------------|
| Authorize  | `holdAmount += amount` (temporary block, funds not moved)         |
| Capture    | release hold, `currentBalance += capturedAmount`                  |
| Settle     | mark settled (interbank money movement)                           |
| Payment    | `currentBalance −= amount`                                         |
| Refund     | `currentBalance −= amount` (credit back)                          |
| Interest/Fee | `currentBalance += amount` at statement time                    |

---

## Realistic & real-time behaviour

This is not just a CRUD toy — it behaves like a real card processor.

### ISO 8583-style authorization

Every authorization carries real network message metadata and returns an ISO 8583
**response code** (field 39), not just a boolean:

```json
{
  "decision": "APPROVED",
  "responseCode": "00",
  "responseText": "Approved",
  "transactionReference": "TXN1750...",
  "authCode": "058283",
  "stan": "783831",
  "rrn": "271884930514",
  "mti": "0110",
  "amount": 5000.00,
  "availableCredit": 195000.00,
  "flowTrace": "POS[POS-0001] -> Acquirer[HDFC] -> Network[VISA] -> Issuer => APPROVED(00)"
}
```

| Code | Meaning | Triggered by |
|---|---|---|
| 00 | Approved | all checks pass |
| 43 | Stolen / blocked card | card is BLOCKED |
| 51 | Insufficient funds / over limit | amount > available credit |
| 54 | Expired card | past expiry |
| 57 | Transaction not permitted | card not ACTIVE |
| 59 / 61 / 62 / 65 | Suspected fraud / amount limit / restricted / velocity | the risk engine |
| 78 | Account frozen or delinquent | account not ACTIVE |
| 14 | Invalid card number | unknown card token |

### Real-time fraud & velocity engine

The issuer consults `FraudService` **inline on every authorization**, before the funds
check — just like a real processor. Configurable under `bank.fraud`:

- **Per-transaction ceiling** → code 61
- **Blocked MCC / country lists** → code 62
- **Spend velocity** (max approved auths in a rolling window) → code 65

Declines from the risk engine also raise a `FRAUD_ALERT` on the live event stream.

### Live event stream (Server-Sent Events)

Every state change is published as a real-time event (`AFTER_COMMIT`, so only committed
facts are broadcast) and fanned out to subscribers:

```bash
curl -N http://localhost:8080/api/stream/events      # live SSE feed (streams forever)
curl    http://localhost:8080/api/events/recent       # last 200 events as JSON
```

Event types: `CARD_ISSUED/ACTIVATED/BLOCKED`, `AUTH_APPROVED/DECLINED`, `FRAUD_ALERT`,
`CAPTURE`, `SETTLEMENT`, `REFUND`, `PAYMENT`, `STATEMENT_GENERATED`,
`LOAN_APPROVED/DISBURSED`, `EMI_CONVERTED/PAID`, and more.

### Scheduled settlement batch

A background job (`bank.settlement.auto-enabled`, default on) settles captured
transactions every `interval-ms` — the real-time-authorization + batch-settlement hybrid
real networks run. You can still trigger settlement manually via `POST /api/transactions/settle`.

### Memo-post vs posted (the real two-phase ledger)

The account tracks two balances, exactly like a real card ledger:

- **Posted (ledger) balance** — hard-posted, official, drives statements.
- **Memo balance** — pending entries not yet hard-posted (a memo *debit* is positive and
  reduces available; a memo *credit*, e.g. a dispute provisional credit, is negative and
  frees available).

```
availableCredit = creditLimit − postedBalance − holdAmount − memoBalance
```

A **memo post** hits available credit instantly; the **posting cycle**
(`POST /api/posting/run`, or the scheduled job) hard-posts it onto the ledger. Auxiliary
adjustments (goodwill credit, fee reversal, correction) can be booked as `MEMO` (posts later)
or `POSTED` (straight to the ledger).

### Disputes & chargebacks

Full case lifecycle against a posted purchase:

```
OPEN ──▶ PROVISIONAL_CREDIT_ISSUED ──▶ REPRESENTMENT ──▶ WON / LOST
          (memo credit → available up)                  │
                                                         ├─ WON  → provisional credit hard-posted (kept)
                                                         └─ LOST → provisional credit reversed (cardholder owes)
```

- Network-style **reason codes** (fraud 10.4, goods-not-received 13.1, duplicate 12.6, …).
- **Provisional credit is a real memo post** — resolved by hard-posting (won) or reversing (lost).
- **Fraud tag**: `POST /api/transactions/{id}/report-fraud` flags the transaction, blocks the
  card, and auto-opens a FRAUD dispute so the chargeback starts immediately.

---

## Use cases covered

**Card lifecycle** — issue · activate · block · unblock · close · cash advance
**Authorization** — POS → Acquirer → Network → Issuer, holds, ISO 8583 response codes, approve/decline (insufficient funds, inactive/expired/blocked card, frozen account)
**Real-time risk** — inline fraud screening: per-transaction ceiling, blocked MCC/country, spend velocity
**Fraud tagging** — report a transaction as fraud → auto-block card + auto-open a FRAUD dispute
**Disputes & chargebacks** — open → provisional credit → representment → resolve (won/lost)
**Memo-post / posted ledger** — memo posts hit available now; a posting cycle hard-posts them
**Auxiliary adjustments** — manual credit/debit postings (goodwill, fee reversal, correction), memo or posted
**Rewards / cashback** — earn points on spend (per-category multipliers), redeem for statement credit
**Autopay** — standing instruction that auto-debits the minimum or total due on the due date
**Real-time events** — live SSE stream of every lifecycle event + recent-events feed
**Automation** — scheduled settlement · posting cycle · autopay · auth-hold expiry (each per-item, resilient)
**Security** — HTTP Basic auth, ADMIN vs CUSTOMER roles, per-customer ownership checks
**Idempotency** — `Idempotency-Key` header makes mutating POSTs execute at most once
**Clearing** — capture (full & partial) · auth reversal · refund (full & partial)
**Settlement** — batch settlement of captured transactions
**Billing** — statement generation · opening/closing balance · minimum due · due date · revolving interest · late-payment fee · delinquency
**Payments** — minimum due · total due · custom · statement paid-status tracking · delinquency clearing
**Loans** — personal-loan application · risk-based underwriting (approve/decline + pricing) · disbursement · reducing-balance EMI schedule · instalment repayment
**EMI on card** — convert a captured purchase into a fixed EMI plan

---

## API reference

Base URL `http://localhost:8080`. All bodies are JSON.

### 1. Customers
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/customers` | Onboard a customer (KYC auto-completed) |
| GET  | `/api/customers/{id}` | Fetch a customer |

### 2. Merchants
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/merchants` | Register a merchant / POS terminal |
| GET  | `/api/merchants` | List merchants |

### 3. Card lifecycle
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/cards` | Issue a card + backing credit account |
| GET  | `/api/cards/{cardId}` | Fetch a card (incl. network token) |
| POST | `/api/cards/{cardId}/activate` | Activate an issued card |
| POST | `/api/cards/{cardId}/block` | Block (lost/stolen/fraud) |
| POST | `/api/cards/{cardId}/unblock` | Unblock |
| POST | `/api/cards/{cardId}/close` | Close permanently |
| POST | `/api/cards/{cardId}/cash-advance` | Draw a cash advance |
| GET  | `/api/cards/accounts/{accountId}` | Account snapshot (limit, balance, dues) |

### 4. POS authorization
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/pos/authorize` | Authorize a purchase (returns decision, auth code, flow trace) |

### 5. Clearing & settlement
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/transactions/{id}/capture` | Capture an authorized txn (optional partial `amount`) |
| POST | `/api/transactions/{id}/reverse` | Reverse an authorization (release hold) |
| POST | `/api/transactions/settle` | Settle all captured transactions |
| POST | `/api/transactions/{id}/refund` | Refund a captured/settled purchase |
| POST | `/api/transactions/{id}/report-fraud` | Tag as fraud → blocks the card + auto-opens a FRAUD dispute |
| GET  | `/api/transactions/{id}` | Fetch a transaction |
| GET  | `/api/transactions/accounts/{accountId}` | List account transactions |

### 6. Billing & statements
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/accounts/{accountId}/statements/generate` | Cut a statement (charges interest/late fee if due) |
| GET  | `/api/accounts/{accountId}/statements` | List statements |
| GET  | `/api/accounts/{accountId}/statements/latest` | Latest statement |

### 7. Payments
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/accounts/{accountId}/payments` | Pay `MINIMUM_DUE` / `TOTAL_DUE` / `CUSTOM` |
| GET  | `/api/accounts/{accountId}/payments` | List payments |

### 8. Loans & EMI
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/loans/apply` | Apply for a personal loan (underwritten instantly) |
| POST | `/api/loans/{loanId}/disburse` | Disburse + generate EMI schedule |
| POST | `/api/loans/convert-emi` | Convert a purchase into an EMI plan |
| POST | `/api/loans/{loanId}/pay-installment` | Pay the next EMI |
| GET  | `/api/loans/{loanId}` | Loan + full amortization schedule |
| GET  | `/api/loans/accounts/{accountId}` | List account loans |

### 9. Real-time events
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/stream/events` | Subscribe to the live SSE event stream |
| GET | `/api/events/recent` | Last 200 lifecycle events (newest first) |

### 10. Disputes & chargebacks
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/disputes/open` | Open a dispute / raise a chargeback on a posted purchase |
| POST | `/api/disputes/{id}/provisional-credit` | Issue provisional credit (a memo post) |
| POST | `/api/disputes/{id}/represent` | Record a merchant representment |
| POST | `/api/disputes/{id}/resolve` | Resolve won (hard-post credit) / lost (reverse) |
| GET  | `/api/disputes/{id}` | Fetch a dispute |
| GET  | `/api/disputes/accounts/{accountId}` | List account disputes |

### 11. Ledger & adjustments
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/accounts/{accountId}/adjustments` | Auxiliary manual adjustment (memo or posted) |
| POST | `/api/posting/run` | Run the posting cycle: hard-post pending memo entries |

### 12. Rewards
| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/accounts/{accountId}/rewards` | View points balance + redeemable cash |
| POST | `/api/accounts/{accountId}/rewards/redeem` | Redeem points for a statement credit |

### 13. Autopay
| Method | Path | Purpose |
|---|---|---|
| POST   | `/api/accounts/{accountId}/autopay` | Enroll in autopay (MINIMUM_DUE / TOTAL_DUE) |
| DELETE | `/api/accounts/{accountId}/autopay` | Disable autopay |
| POST   | `/api/autopay/run` | Run the autopay batch now |

---

## End-to-end walkthrough (curl)

Uses the seeded demo card. Grab its token first (needs `jq`, or copy it from the startup log):

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s $BASE/api/cards/1 | jq -r .token)     # seeded ACTIVE card #1, account #1
```

**1) Authorize a ₹5,000 purchase at merchant #1 (POS → Acquirer → Network → Issuer):**
```bash
curl -s -X POST $BASE/api/pos/authorize -H 'Content-Type: application/json' \
  -d "{\"cardToken\":\"$TOKEN\",\"amount\":5000,\"merchantId\":1,\"currency\":\"INR\"}"
```

**2) Capture it (use the `transactionReference`’s numeric id from GET, or the id below), then settle:**
```bash
curl -s -X POST $BASE/api/transactions/1/capture
curl -s -X POST $BASE/api/transactions/settle
```

**3) Cut a statement, then pay the minimum due:**
```bash
curl -s -X POST $BASE/api/accounts/1/statements/generate
curl -s -X POST $BASE/api/accounts/1/payments -H 'Content-Type: application/json' \
  -d '{"type":"MINIMUM_DUE"}'
```

**4) Convert a large purchase to EMI (after capturing it), and repay one instalment:**
```bash
curl -s -X POST $BASE/api/loans/convert-emi -H 'Content-Type: application/json' \
  -d '{"transactionId":2,"tenureMonths":6}'
curl -s -X POST $BASE/api/loans/1/pay-installment
```

**5) Apply for a personal loan, disburse it, view the schedule:**
```bash
curl -s -X POST $BASE/api/loans/apply -H 'Content-Type: application/json' \
  -d '{"accountId":1,"principal":300000,"tenureMonths":12,"purpose":"Home renovation"}'
curl -s -X POST $BASE/api/loans/2/disburse        # -> APPROVED loan id from previous call
```

See `requests.http` for the same flow as clickable requests (IntelliJ / VS Code REST Client).

---

## Business rules (configurable in `application.yml` under `bank:`)

| Key | Default | Meaning |
|---|---|---|
| `default-apr` | 36% | Revolving purchase APR |
| `cash-advance-apr` | 42% | Cash advance APR |
| `min-payment-percent` | 5% | Minimum due = this % of statement balance… |
| `min-payment-floor` | ₹200 | …but never below this floor |
| `grace-period-days` | 20 | Statement date → due date |
| `late-payment-fee` | ₹500 | Charged when minimum is missed |
| `loan.min-credit-score` | 650 | Underwriting cut-off |
| `loan.max-income-multiple` | 5× | Max personal-loan principal vs income |
| `loan.base-rate` | 14% | Base loan rate; better scores get a smaller premium |

---

## Architecture notes

**Correctness & production hardening built in**

- **AuthN / AuthZ** — HTTP Basic with `ADMIN` and `CUSTOMER` roles; `@PreAuthorize` enforces ops-only endpoints and per-customer ownership (`@access.ownsAccount/ownsCard`). Unauthenticated → `401`, forbidden → `403`.
- **Idempotency keys** — a filter records the first response for an `Idempotency-Key` and replays it on retries (`Idempotency-Replay: true`); concurrent duplicates are serialized by the unique key. Double-submitted auth/payment runs once.
- **Optimistic locking** — `CreditAccount` carries a JPA `@Version`; a concurrent lost update (double-spend) becomes a `409 Conflict` instead of silently overwriting a fresher balance.
- **Cluster-safe ids** — transaction references draw from a **DB sequence**, unique across nodes, not a per-JVM counter.
- **Resilient batch jobs** — settlement, the posting cycle, autopay and hold-expiry each process **per-item in their own transaction** with optimistic-lock retry, so one failure/conflict never rolls back the whole batch.
- **Average-daily-balance interest** — interest is charged on the ADB reconstructed from the cycle's ledger history (with full-payment grace), not a flat rate.
- **Auth-hold expiry** — a scheduled job reverses authorizations never captured within `bank.auth.hold-expiry-days`, releasing the held credit.
- **OSIV disabled** — the persistence context does not stay open during view rendering; mapper-visible associations are fetched inside the service transaction.
- **Refund integrity + reward clawback** — refunds are capped at the remaining refundable amount and claw back points proportionally.
- **Conflict mapping** — optimistic-lock / data-integrity → `409`; validation → `400`; business-rule → `422`; missing entity → `404`.

**Remaining scope boundaries (this is a single-node simulation)**

- In-memory H2 and `create-drop` schema — data resets on restart; a real deployment uses a persistent DB with migrations (Flyway/Liquibase).
- HTTP Basic over the wire — production would front this with TLS and likely OAuth2/JWT; a few endpoints (e.g. EMI conversion, dispute open) are role-gated but not ownership-gated on their target entity.
- Single node — the reference sequence and idempotency store are DB-backed (cluster-ready), but the SSE fan-out and in-JVM scheduling assume one instance.

## Notes

- Full PANs are never stored — only a masked PAN, last-4, and a network **token** (what the acquirer/network actually carry).
- Declined authorizations are persisted too, for audit.
- EMI schedules use the reducing-balance formula; the final instalment absorbs rounding so the balance clears exactly to zero.

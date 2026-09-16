# My Finance Manager - Backend

REST API backend for **My Finance Manager**, a personal finance tracking Android app. The
service consolidates income, expenses and investments, imports bank/credit-card statements,
accepts on-device SMS/email auto-capture, and generates AI-driven financial insights.

The Android client is maintained separately. This repository contains only the backend.

See `My_Finance_Management_PRD.md` for the full product requirements.

---

## 1. Tech stack

| Concern | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 3.3 |
| API style | REST, JSON, versioned under `/api/v1` |
| Database | PostgreSQL (Render Postgres, Neon-compatible) |
| Migrations | Flyway |
| Persistence | Spring Data JPA / Hibernate |
| Auth | Neon Auth (Managed Better Auth) owns credentials; this service verifies its JWT (EdDSA, via JWKS) |
| Statement parsing | Rapid Bank Statement Parsing API (RapidAPI) primary, OpenRouter AI fallback |
| File extraction | Apache PDFBox, Apache POI, Commons CSV |
| AI insights | OpenRouter (model configurable via `OPENROUTER_MODEL`) |
| Docs | springdoc-openapi (Swagger UI) |
| Deployment | Docker + Render Blueprint (`render.yaml`) |

---

## 2. Project layout

```
src/main/java/com/myfinancemanager
├── common/          API error envelope, pagination, global exception handling
├── config/          Security, CORS, OpenAPI, async, typed properties, DATABASE_URL normalizer
├── controller/      REST controllers (users, income, expenses, investments, ...)
├── domain/          JPA entities and enums
├── dto/             Request/response records grouped by feature
├── integration/     External clients (RapidAPI, OpenRouter, Google, text extraction)
├── repository/      Spring Data repositories + projections
├── security/        Neon Auth JWT decoder and filter, principal
└── service/         Business logic
src/main/resources
├── application.yml
├── application-prod.yml
└── db/migration/    Flyway migrations (V1 init, V2 budgets, V3 Neon Auth)
```

---

## 3. Running locally

### Prerequisites
- Java 21
- PostgreSQL 14+ (or use Docker)

### Start a local database

```bash
docker run --name myfinance-db -e POSTGRES_DB=myfinance \
  -e POSTGRES_USER=myfinance -e POSTGRES_PASSWORD=myfinance \
  -p 5432:5432 -d postgres:16
```

### Configure and run

```bash
cp .env.example .env

# Export the variables from .env into your shell, then run:
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. Swagger UI is at
`http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`.

Convenience shortcut for a local database:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/myfinance \
SPRING_DATASOURCE_USERNAME=myfinance \
SPRING_DATASOURCE_PASSWORD=myfinance \
NEON_AUTH_URL=https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth \
./mvnw spring-boot:run
```

### Build and test

```bash
./mvnw clean verify
```

Tests run against an in-memory H2 database (PostgreSQL compatibility mode), so no local
PostgreSQL is required for the test suite.

### Build the jar

```bash
./mvnw clean package -DskipTests

java -jar target/my-finance-manager-backend-1.0.0.jar
```

---

## 4. Configuration

All configuration is supplied through environment variables; secrets are never hardcoded.
See `.env.example` for the full list.

| Variable | Required | Purpose |
|---|---|---|
| `PORT` | no | HTTP port (default `8080`); set automatically by Render |
| `SPRING_PROFILES_ACTIVE` | no | `prod` enables production logging/validation |
| `DATABASE_URL` | yes* | Platform connection string (`postgresql://user:pass@host/db`) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | yes* | Explicit JDBC credentials (used when `DATABASE_URL` is absent) |
| `DATABASE_SSL_MODE` | no | Appends `sslmode` to the JDBC URL (e.g. `require` for Neon) |
| `DB_POOL_SIZE` | no | Hikari maximum pool size (default `10`) |
| `NEON_AUTH_URL` | yes | Neon Auth base URL: `https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth`. The issuer, audience and JWKS URL are derived from it |
| `NEON_AUTH_ISSUER` / `NEON_AUTH_JWKS_URI` | no | Override the derived values (only when the service is reached through a different hostname) |
| `OPENROUTER_API_KEY` | no | Enables AI insights and the statement-parsing fallback |
| `OPENROUTER_BASE_URL` | no | Defaults to `https://openrouter.ai/api/v1` |
| `OPENROUTER_MODEL` | no | LLM model used by OpenRouter (default `openai/gpt-4o-mini`) |
| `RAPIDAPI_KEY` / `RAPIDAPI_HOST` / `RAPIDAPI_URL` | no | Primary statement parsing API |
| `RAPIDAPI_FILE_FIELD_NAME` | no | Multipart field name expected by the API (default `file`) |
| `STORAGE_LOCATION` | no | Directory for uploaded statements (default `./data/uploads`) |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated origins, or `*` (default) |

\* Provide either `DATABASE_URL` or the three `SPRING_DATASOURCE_*` variables.

`DATABASE_URL` accepts `postgres://`, `postgresql://` and `jdbc:postgresql://` forms and is
normalized into Spring datasource properties at startup.

---

## 5. API overview

Base path: `/api/v1`. Every endpoint except the health probes and the API docs requires
`Authorization: Bearer <jwt>`, where the JWT is minted by Neon Auth.

There is no sign-up, login or refresh endpoint here. The app authenticates against Neon Auth
directly and presents the resulting JWT; the first authenticated request links that identity to
a local profile row, matching on email so an account that predates the migration is adopted
rather than duplicated. Every financial record hangs off that row.

### Account - `/users/me`
| Method | Path | Description |
|---|---|---|
| GET | `/` | Profile and preferences |
| PUT | `/` | Update profile / currency / notification prefs |
| GET | `/export` | Full JSON data export |
| DELETE | `/` | Delete the profile and all associated data |

### Income / Expenses / Investments
Standard CRUD plus filtering:

| Resource | Path | Notes |
|---|---|---|
| Income | `/incomes` | Filters: `from`, `to`, `source`, `category`, `origin`, `recurring`, `minAmount`, `maxAmount`, `q` |
| Expenses | `/expenses` | Filters add `merchant`, `paymentMode` |
| Investments | `/investments` | Filters: `from`, `to`, `type`, `broker`, `origin`, `q` |

`GET /investments/summary` returns portfolio totals, gain/loss and allocation by instrument type.

### Dashboard - `/dashboard`
| Method | Path | Description |
|---|---|---|
| GET | `/summary` | Income, expense, investment totals and net savings for a period |
| GET | `/expenses-by-category` | Expense breakdown with percentages |
| GET | `/income-by-category` | Income breakdown with percentages |
| GET | `/trend` | Monthly income vs expense series (`months` param) |
| GET | `/recent-transactions` | Merged recent activity feed (`limit` param) |

Periods: `this_month`, `last_month`, `this_quarter`, `this_year`, `last_year`,
`last_30_days`, `last_90_days`, `current_week`, `all`, or `custom` with `from`/`to`.

### Smart Import - `/imports`
| Method | Path | Description |
|---|---|---|
| POST | `/` | Upload a PDF/CSV/XLSX/XLS statement (multipart `file`) |
| GET | `/` | List import batches |
| GET | `/{id}` | Batch status |
| GET | `/{id}/detail` | Batch with staged transactions for review |
| POST | `/{id}/commit` | Commit reviewed rows (with optional per-row overrides) |
| DELETE | `/{id}` | Delete a batch |

Parsing runs asynchronously. The batch moves through `QUEUED` -> `PROCESSING` ->
`READY_FOR_REVIEW` -> `COMMITTED` (or `FAILED`). Extraction attempts the RapidAPI parser
first and falls back to PDFBox/POI/CSV text extraction plus an OpenRouter categorization
call. Duplicate rows already present in the ledger are flagged before commit.

### Auto-Capture - `/auto-capture`
| Method | Path | Description |
|---|---|---|
| POST | `/` | Submit a device-parsed SMS/email item to the review queue |
| GET | `/` | List queue items (optional `status`) |
| GET/PUT | `/{id}` | Read / edit a pending item |
| POST | `/{id}/confirm` | Confirm and create the ledger record |
| POST | `/{id}/reject` | Reject an item |
| DELETE | `/{id}` | Delete an item |
| GET/PUT | `/settings` | Sender allow/block lists and enablement flags |

### Budgets - `/budgets`
| Method | Path | Description |
|---|---|---|
| GET | `/` | List the user's category budgets |
| PUT | `/{category}` | Create or update the monthly limit for a category |
| DELETE | `/{category}` | Remove a category's budget |

The category is the natural key: a user has at most one budget per category, enforced by a
unique `(user_id, category)` constraint. That is what makes `PUT` idempotent, so a client
replaying the same change edits the limit instead of adding a second budget. Categories are
normalised to upper case; `DELETE` on a category without a budget answers `204` rather than
`404` for the same reason. `monthlyLimit` must be greater than zero.

### AI Insights - `/insights`
| Method | Path | Description |
|---|---|---|
| GET | `/` | List generated insights (optional `status`) |
| POST | `/generate` | Generate insights from the user's aggregated data |
| GET | `/{id}` | Read an insight |
| PATCH | `/{id}/status` | Save or dismiss |
| DELETE | `/{id}` | Delete |

---

## 6. Security notes

- Credentials never reach this service. Neon Auth stores and verifies them; here a JWT is
  verified against Neon Auth's published JWK set, restricted to EdDSA (which rules out
  signature-confusion attacks), with issuer, audience and expiry all enforced.
- Tokens are short-lived (15 minutes) and stateless, so there is nothing to revoke server-side.
- Statement files are written under a per-user/per-batch directory and deleted after parsing.
- `NEON_AUTH_URL` is a public URL, not a secret. The remaining secrets
  (`OPENROUTER_API_KEY`, `RAPIDAPI_KEY`, ...) are read only from the environment and are never
  committed to the repository.
- AI output is explicitly prompted to avoid regulated financial advice.

---

## 7. Deploying to Render

The repository ships a Render Blueprint (`render.yaml`) that deploys the API as a Docker web
service. The database is not provisioned by the blueprint: bring your own PostgreSQL (this
deployment uses Neon) and paste its connection string in from the dashboard.

1. Push this repository to GitHub/GitLab.
2. In Render, choose **New > Blueprint** and select the repository.
3. Render reads `render.yaml`, creates the database, and prompts for the `sync: false`
   secrets: `DATABASE_URL`, `DATABASE_SSL_MODE`, `OPENROUTER_API_KEY`, `RAPIDAPI_KEY`,
   `RAPIDAPI_HOST`, `RAPIDAPI_URL`. Provide the ones you use and leave the rest blank.
4. Apply. The blueprint sets `NEON_AUTH_URL` inline; confirm it names the branch that holds the
   `neon_auth` schema. A missing or wrong value fails fast at startup rather than at the first
   sign-in.
5. The service health check uses `/actuator/health`.

The blueprint defaults to the `free` plan for both the database and the web service. Free web
instances spin down when idle and wake on the next request; change `plan` to a paid tier for
an always-on production deployment.

To build the image locally:

```bash
docker build -t my-finance-manager-backend .

docker run -p 8080:8080 \
  -e DATABASE_URL=postgresql://myfinance:myfinance@host.docker.internal:5432/myfinance \
  -e NEON_AUTH_URL=https://<endpoint>.neonauth.<region>.aws.neon.tech/<database>/auth \
  my-finance-manager-backend
```

---

## 8. Database migrations

Flyway runs automatically on startup using `src/main/resources/db/migration`. The initial
migration (`V1__init.sql`) creates all tables and indexes, `V2__budgets.sql` adds category
budgets, and `V3__neon_auth.sql` removes local credential storage (password hashes, refresh
tokens) and adds `users.auth_subject`, the link to the Neon Auth user. Add new migrations as
`V<n>__<description>.sql`; never edit an already-applied migration.

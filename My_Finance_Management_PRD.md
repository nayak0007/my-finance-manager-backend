# Product Requirements Document (PRD)
## My Finance Manager — Personal Finance Tracker (Android)

**Document Version:** 1.0
**Status:** Draft
**Owner:** Product Team
**Last Updated:** September 12, 2026

---

## 1. Overview

### 1.1 Purpose
"My Finance Manager" is a personal finance tracking Android application that helps users consolidate and manage their income, expenses, and investments in one place. The app reduces manual data-entry effort through smart statement imports and automated SMS/email parsing, and provides AI-driven financial guidance to help users make better money decisions.

### 1.2 Problem Statement
Most individuals track finances across multiple disconnected sources — bank SMS alerts, email statements, broker apps, and manual notes. This fragmentation makes it hard to get a holistic view of net worth, spending patterns, and investment performance, and it discourages consistent tracking due to the manual effort involved.

### 1.3 Goals
- Provide a single, unified view of income, expenses, and investments.
- Minimize manual entry through automated data capture (SMS/email parsing, statement import).
- Deliver actionable, personalized financial insights using AI.
- Ensure user financial data is stored and transmitted securely.

### 1.4 Non-Goals (v1)
- No direct bank account linking/aggregation via Account Aggregator or Plaid-like APIs (may be considered in a future version).
- No multi-user/family shared accounts in v1.
- No bill payment or money movement functionality — the app is read-only/tracking-only.
- No iOS app in this phase.

---

## 2. Target Users & Use Cases

### 2.1 Target Users
- Individuals who want an automated, low-effort way to track personal finances.
- Users who already receive transactional SMS/emails from banks, cards, and brokers.
- Users who want lightweight AI guidance rather than a full financial advisor.

### 2.2 Key Use Cases
1. A user wants to see their total monthly expenses broken down by category.
2. A user receives a bank SMS for a debit transaction and wants it auto-logged as an expense.
3. A user downloads a credit card or bank statement and wants all transactions imported at once.
4. A user wants to log a manual cash expense not captured elsewhere.
5. A user wants to track mutual fund/stock investments and see portfolio value over time.
6. A user wants AI suggestions on how to reduce discretionary spend or optimize savings.

---

## 3. Scope & Feature Requirements

### 3.1 Core Modules

#### 3.1.1 Dashboard (Home)
- Summary cards: total income, total expenses, total investments, net savings (current month, selectable period).
- Quick "Add" action (income/expense/investment).
- Recent transactions feed across all categories.
- Visual charts: expense-by-category pie chart, income-vs-expense trend line (monthly).

#### 3.1.2 Income Screen
- Dedicated list/detail view of all income entries.
- Fields: amount, source, date, category/tag (salary, freelance, interest, rental, other), notes, recurring flag.
- Filters: date range, source, amount range.
- Manual "Add Income" form.
- Auto-captured income entries (from SMS/email/import) flagged as "Auto-detected" with an edit/confirm step.

#### 3.1.3 Expense Screen
- Dedicated list/detail view of all expense entries.
- Fields: amount, merchant/payee, date, category (food, travel, bills, shopping, etc.), payment mode (cash/card/UPI/bank transfer), notes, recurring flag.
- Filters and search by category, date, payment mode, amount.
- Manual "Add Expense" form.
- Auto-detected expenses flagged for user confirmation before being finalized.
- Budget setting per category (optional in v1, stretch goal) with visual progress indicators.

#### 3.1.4 Investment Screen
- Dedicated list/detail view of investments: mutual funds, stocks, FDs, bonds, gold, retirement accounts, crypto (optional), etc.
- Fields: instrument name, type, amount invested, current value (manual update or import), date, broker/platform, notes.
- Portfolio summary: total invested, current value, gain/loss %, allocation breakdown by instrument type.
- Manual "Add Investment" form.

#### 3.1.5 Smart Import
- Users can upload financial statements (PDF/CSV/Excel) from banks, credit cards, or brokers.
- **Primary extraction:** The uploaded statement is sent to the Rapid Bank Statement Parsing API (RapidAPI), which extracts structured transaction data (date, description, amount, type, balance, etc.) from the statement.
- **Fallback extraction:** If the Rapid Bank Statement Parsing API fails to extract data (unsupported bank format, parsing error, low-confidence/empty result), the backend falls back to:
  1. Extracting raw text from the statement (PDF/Excel/CSV) using a text-extraction library.
  2. Sending the extracted text to an OpenRouter AI model to identify and categorize transactions (income/expense/investment, category, amount, date, merchant).
- Regardless of extraction path, all parsed transactions are classified into income/expense/investment before being surfaced to the user.
- A review screen shows parsed records before final commit, allowing edits, category reassignment, and exclusion of duplicates/irrelevant lines.
- Duplicate-detection logic to avoid double-counting entries already captured via SMS/email or manual entry.
- Support for common statement formats from major Indian and international banks, per Rapid Bank Statement Parsing API's supported-bank list; the OpenRouter fallback extends coverage to formats not natively supported by the primary API.

#### 3.1.6 SMS & Email Reading (Auto-Capture)
- With explicit user permission, the app reads SMS messages (Android `READ_SMS`/SMS Retriever) and/or connects to the user's email (via OAuth, e.g., Gmail API) to detect transaction-related messages from known bank/merchant senders.
- NLP/regex-based parsing extracts amount, merchant, date, and transaction type from message content.
- Parsed entries appear in an "Auto-detected" review queue before being committed to Income/Expense/Investment records, minimizing false positives.
- Users can manage sender allow-lists/block-lists and disable this feature entirely at any time.
- All permission requests follow Android runtime permission best practices with clear in-app rationale screens.

#### 3.1.7 AI Financial Suggestions
- Based on the user's tracked income, expense, and investment data, the app generates personalized suggestions, such as:
  - Spending pattern anomalies (e.g., category overspend vs. previous months).
  - Savings rate trends and suggested targets.
  - Investment diversification observations.
  - Simple budgeting tips.
- Delivered as a dedicated "Insights" tab plus periodic notifications (configurable frequency).
- Clear disclaimers that suggestions are informational and not certified financial/investment advice.
- Insights are generated via OpenRouter, which provides access to multiple underlying LLM providers/models through a single API; the specific model used is configurable via an environment variable rather than hardcoded, allowing the model to be changed without a code deployment.

#### 3.1.8 Authentication & Account
- Sign-up/login via email/password and OAuth (Google Sign-In).
- Secure session management with token refresh.
- Profile management, notification preferences, data export, and account deletion.

### 3.2 Feature Prioritization (MVP vs Later)

| Feature | Priority |
|---|---|
| Manual income/expense/investment entry + dedicated screens | P0 (MVP) |
| Dashboard with summary & charts | P0 (MVP) |
| Authentication (email + Google OAuth) | P0 (MVP) |
| Smart Import (PDF/CSV statements) | P1 |
| SMS auto-capture | P1 |
| Email auto-capture | P2 |
| AI financial suggestions | P2 |
| Budgeting & alerts | P2 (stretch) |
| Multi-currency support | P3 (future) |

---

## 4. Technical Architecture

### 4.1 High-Level Architecture
- **Client:** Native Android app (Kotlin recommended; Java acceptable if preferred for consistency with backend team), following MVVM architecture.
- **Backend:** Java 21, Spring Boot (REST API), deployed as a modular monolith (or microservices if scale requires it later).
- **Database:** PostgreSQL hosted on Neon (serverless Postgres).
- **Authentication:** Neon Auth (or Spring Security + JWT integrated with Neon's auth offering), supporting email/password and OAuth (Google).
- **File/Statement Storage:** Object storage (e.g., S3-compatible) for uploaded statements, referenced from Postgres.
- **Statement Parsing:** Rapid Bank Statement Parsing API (RapidAPI) as the primary extraction engine for uploaded bank/credit card statements. If it fails to extract transactions, the backend falls back to raw text extraction from the statement followed by categorization via an OpenRouter AI model.
- **AI Suggestions:** Integration with OpenRouter for generating financial insights from aggregated user data; the specific model is set via an environment variable, allowing it to be swapped without code changes.
- **SMS/Email Parsing:** On-device SMS parsing (Android) for privacy; email parsing via secure OAuth-based read-only access with server-side or on-device NLP parsing pipeline.

### 4.2 Backend Stack Details
- **Language/Framework:** Java 21, Spring Boot 3.x
- **Database:** PostgreSQL on Neon (serverless, autoscaling, branching for dev/staging/prod)
- **Auth:** Neon Auth for user identity/session management, integrated with Spring Security for API authorization (JWT-based)
- **ORM:** Spring Data JPA / Hibernate
- **API Style:** REST (JSON), versioned endpoints (`/api/v1/...`)
- **Migrations:** Flyway or Liquibase for schema versioning
- **Background Jobs:** Spring Scheduler or a queue (e.g., for statement-parsing jobs, AI insight generation)
- **Statement Parsing (Primary):** Rapid Bank Statement Parsing API (RapidAPI) — called from the backend with the statement file; returns structured transaction data.
- **Statement Parsing (Fallback):** Apache PDFBox / Apache POI / OpenCSV for raw text extraction from PDF/Excel/CSV when the primary API fails to extract; extracted text is then sent to an OpenRouter AI model for transaction identification and categorization.
- **AI Insights & Categorization Provider:** OpenRouter API, used for (a) AI financial suggestions/insights and (b) fallback categorization of statement text. Model name is not hardcoded — it is read from an environment variable (e.g., `OPENROUTER_MODEL`) so it can be changed per environment without redeploying code.
- **Security:** HTTPS everywhere, encryption at rest for sensitive fields (e.g., account numbers if stored), rate limiting, audit logging; API keys for Rapid Bank Statement Parsing API and OpenRouter stored as environment/secret-manager values, never hardcoded or committed to source.

### 4.3 High-Level Data Model (Illustrative)
- `User` (id, email, auth_provider, created_at, preferences)
- `IncomeRecord` (id, user_id, amount, source, category, date, origin[manual/sms/email/import], notes)
- `ExpenseRecord` (id, user_id, amount, merchant, category, payment_mode, date, origin, notes)
- `InvestmentRecord` (id, user_id, instrument_name, type, amount_invested, current_value, date, broker, origin, notes)
- `ImportBatch` (id, user_id, source_file, status, extraction_method[rapid_api/openrouter_fallback], created_at)
- `AutoCaptureQueueItem` (id, user_id, raw_text, parsed_type, parsed_data, status[pending/confirmed/rejected])
- `AIInsight` (id, user_id, insight_text, category, model_used, generated_at)

### 4.4 Integration Points
- Google OAuth (Sign-In + optional Gmail API read access)
- Neon Postgres + Neon Auth
- Rapid Bank Statement Parsing API (RapidAPI) for primary statement extraction
- OpenRouter API for (a) AI financial suggestions and (b) fallback statement categorization when the primary parsing API fails; underlying model configured via environment variable
- Push notification service (Firebase Cloud Messaging) for alerts/insights

---

## 5. Non-Functional Requirements

### 5.1 Security & Privacy
- All financial data encrypted in transit (TLS 1.2+) and at rest.
- SMS and email access are opt-in, with granular permission controls and the ability to revoke access at any time.
- Sensitive fields (account numbers, if captured) must be masked in the UI and encrypted in storage.
- Compliance with applicable data protection regulations (e.g., India's DPDP Act, GDPR if expanding internationally).
- No SMS/email content is used for purposes beyond transaction parsing; no data sold to third parties.

### 5.2 Performance
- Dashboard should load within 2 seconds for typical data volumes (up to ~5 years of transaction history).
- Statement import parsing for a typical file (<500 transactions) should complete within 30 seconds, with progress indication.

### 5.3 Reliability
- 99.5% API uptime target.
- Graceful degradation: if AI suggestion service is unavailable, core tracking features remain fully functional.

### 5.4 Scalability
- Backend designed to scale horizontally; Neon's serverless Postgres supports autoscaling of compute.

### 5.5 Usability
- Onboarding flow explaining permissions (SMS/email) and their value before requesting access.
- Accessibility: support Android accessibility standards (TalkBack, font scaling).

---

## 6. User Flows (Summary)

1. **Onboarding:** Sign up → grant optional permissions (SMS/notifications) → set initial preferences (currency, categories) → land on empty-state dashboard.
2. **Manual Entry:** Tap "+" → choose Income/Expense/Investment → fill form → save → reflected on dashboard.
3. **Smart Import:** Go to Smart Import → upload statement file → system parses → review/edit extracted records → confirm → records inserted into respective categories.
4. **Auto-Capture:** SMS/email received → parsed in background → appears in "Review Queue" → user confirms or edits → committed to relevant category.
5. **AI Insights:** User opens Insights tab → sees generated suggestions based on latest data → can dismiss, save, or act on suggestions.

---

## 7. Success Metrics (KPIs)

- % of transactions captured automatically (SMS/email/import) vs. manually entered.
- Weekly/Monthly Active Users (WAU/MAU).
- Average number of financial records tracked per active user.
- Retention rate (Day 7, Day 30).
- AI insight engagement rate (views, saves, actions taken).
- Smart Import success rate (successfully parsed transactions / total transactions in uploaded file).

---

## 8. Risks & Open Questions

### 8.1 Risks
- **Privacy concerns:** SMS/email reading is sensitive; requires very clear consent flows and robust security to maintain user trust.
- **Parsing accuracy:** Bank statement formats vary widely; parsing errors could lead to incorrect financial records.
- **AI suggestion quality:** Poor or generic suggestions could reduce perceived value; needs strong prompt design and guardrails against giving regulated financial advice.
- **Play Store policy:** SMS permission usage is heavily restricted by Google Play policy and requires justification and possibly a declaration form.

### 8.2 Open Questions
- Which banks/statement formats should be supported at launch?
- Should the app support multiple currencies from v1 or India-only initially?
- Will AI insights run on-device, via backend proxy to an LLM, or a hybrid model?
- What is the data retention policy for raw SMS/email content used for parsing (should it be discarded immediately after parsing)?
- Do we need explicit regulatory disclaimers/compliance review for the AI financial suggestion feature?

---

## 9. Release Plan (Proposed Phasing)

- **Phase 1 (MVP):** Auth, manual entry for Income/Expense/Investment, dedicated screens, dashboard with basic charts.
- **Phase 2:** Smart Import (PDF/CSV), SMS auto-capture with review queue.
- **Phase 3:** Email auto-capture, AI financial suggestions (Insights tab).
- **Phase 4:** Budgeting, alerts, and stretch features (multi-currency, shared accounts).

---

## 10. Appendix

- **Platform:** Android (native)
- **Backend:** Java 21, Spring Boot
- **Database & Auth:** Neon (PostgreSQL + Auth)
- **Statement Parsing:** Rapid Bank Statement Parsing API (primary), text extraction + OpenRouter AI categorization (fallback)
- **AI Insights:** OpenRouter (model configurable via environment variable)
- **App Name:** My Finance Manager
-- My Finance Manager initial schema

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    password_hash         VARCHAR(255),
    full_name             VARCHAR(200),
    auth_provider         VARCHAR(20) NOT NULL,
    provider_subject      VARCHAR(255),
    currency              VARCHAR(3) NOT NULL DEFAULT 'INR',
    email_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    preferences           TEXT,
    last_login_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));
CREATE UNIQUE INDEX ux_users_provider_subject ON users (provider_subject) WHERE provider_subject IS NOT NULL;

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE income_records (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount           NUMERIC(19, 4) NOT NULL,
    source           VARCHAR(200),
    category         VARCHAR(100),
    transaction_date DATE NOT NULL,
    origin           VARCHAR(20) NOT NULL,
    recurring        BOOLEAN NOT NULL DEFAULT FALSE,
    notes            VARCHAR(2000),
    fingerprint      VARCHAR(64)
);

CREATE INDEX idx_income_user_date ON income_records (user_id, transaction_date);
CREATE INDEX idx_income_fingerprint ON income_records (fingerprint);

CREATE TABLE expense_records (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount           NUMERIC(19, 4) NOT NULL,
    merchant         VARCHAR(200),
    category         VARCHAR(100),
    payment_mode     VARCHAR(30) NOT NULL,
    transaction_date DATE NOT NULL,
    origin           VARCHAR(20) NOT NULL,
    recurring        BOOLEAN NOT NULL DEFAULT FALSE,
    notes            VARCHAR(2000),
    fingerprint      VARCHAR(64)
);

CREATE INDEX idx_expense_user_date ON expense_records (user_id, transaction_date);
CREATE INDEX idx_expense_fingerprint ON expense_records (fingerprint);

CREATE TABLE investment_records (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    instrument_name  VARCHAR(200) NOT NULL,
    investment_type  VARCHAR(30) NOT NULL,
    amount_invested  NUMERIC(19, 4) NOT NULL,
    current_value    NUMERIC(19, 4),
    transaction_date DATE NOT NULL,
    broker           VARCHAR(200),
    origin           VARCHAR(20) NOT NULL,
    notes            VARCHAR(2000),
    fingerprint      VARCHAR(64)
);

CREATE INDEX idx_investment_user_date ON investment_records (user_id, transaction_date);
CREATE INDEX idx_investment_fingerprint ON investment_records (fingerprint);

CREATE TABLE import_batches (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_file_name   VARCHAR(500) NOT NULL,
    content_type       VARCHAR(200),
    storage_path       VARCHAR(1000),
    file_size          BIGINT,
    status             VARCHAR(30) NOT NULL,
    extraction_method  VARCHAR(30),
    error_message      VARCHAR(2000),
    total_transactions INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_import_batches_user ON import_batches (user_id, created_at);

CREATE TABLE imported_transactions (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    batch_id           UUID NOT NULL REFERENCES import_batches (id) ON DELETE CASCADE,
    transaction_type   VARCHAR(20) NOT NULL,
    transaction_date   DATE,
    description        VARCHAR(1000),
    merchant           VARCHAR(200),
    amount             NUMERIC(19, 4),
    category           VARCHAR(100),
    payment_mode       VARCHAR(30),
    source             VARCHAR(200),
    raw_line           VARCHAR(2000),
    status             VARCHAR(20) NOT NULL,
    duplicate          BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_of_id    UUID,
    confidence         DOUBLE PRECISION,
    committed_record_id UUID,
    fingerprint        VARCHAR(64)
);

CREATE INDEX idx_imported_tx_batch ON imported_transactions (batch_id, status);

CREATE TABLE auto_capture_queue (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type        VARCHAR(20) NOT NULL,
    sender             VARCHAR(320),
    raw_text           TEXT,
    parsed_type        VARCHAR(20),
    parsed_data        TEXT,
    status             VARCHAR(20) NOT NULL,
    confidence         DOUBLE PRECISION,
    committed_record_id UUID
);

CREATE INDEX idx_auto_capture_user_status ON auto_capture_queue (user_id, status);

CREATE TABLE auto_capture_settings (
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    user_id           UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    sms_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    email_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    sender_allow_list TEXT,
    sender_block_list TEXT,
    metadata          TEXT
);

CREATE TABLE ai_insights (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        VARCHAR(300),
    insight_text TEXT NOT NULL,
    category     VARCHAR(30) NOT NULL,
    model_used   VARCHAR(200),
    generated_at TIMESTAMPTZ NOT NULL,
    status       VARCHAR(20) NOT NULL,
    metadata     TEXT
);

CREATE INDEX idx_ai_insights_user ON ai_insights (user_id, generated_at);

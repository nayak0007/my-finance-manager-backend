-- Category budgets: one monthly limit per user per expense category. The unique constraint is
-- what makes the client's upsert idempotent when a sync retries.
CREATE TABLE budgets (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category      VARCHAR(100) NOT NULL,
    monthly_limit NUMERIC(19, 4) NOT NULL,
    CONSTRAINT uq_budgets_user_category UNIQUE (user_id, category)
);

CREATE INDEX idx_budgets_user ON budgets (user_id);

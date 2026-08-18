-- Платформа обучения спрашивает по почте, куплен ли курс
-- (GET /api/tech/course-access) — поиск идёт регистронезависимо.
CREATE INDEX IF NOT EXISTS idx_payment_transaction_email_lower
    ON payment_transaction (lower(email));

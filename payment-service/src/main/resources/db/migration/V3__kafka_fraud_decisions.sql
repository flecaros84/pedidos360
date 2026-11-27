-- Kafka request -> decision -> application, with durable idempotent decisions.
CREATE TABLE payments.fraud_decisions (
 payment_id UUID PRIMARY KEY REFERENCES payments.payment_attempts(id),
 card_fingerprint VARCHAR(64) NOT NULL,
 decision VARCHAR(16) NOT NULL CHECK(decision IN ('ALLOW','BLOCK')),
 reason VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMPTZ
);
CREATE INDEX idx_fraud_decisions_unpublished ON payments.fraud_decisions(created_at)
 WHERE published_at IS NULL;
-- Keep cards previously detected as fraudulent blocked after this migration.
INSERT INTO payments.fraud_blocked_cards(card_fingerprint,reason,blocked_at)
SELECT DISTINCT ON (card_fingerprint) card_fingerprint,reason,created_at
 FROM payments.fraud_alerts ORDER BY card_fingerprint,created_at DESC
ON CONFLICT (card_fingerprint) DO NOTHING;

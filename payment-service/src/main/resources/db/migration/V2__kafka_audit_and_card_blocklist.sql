-- Persist Kafka audit delivery and a durable fingerprint-only card blocklist.
ALTER TABLE payments.payment_events
  ADD COLUMN IF NOT EXISTS audit_published_at TIMESTAMPTZ;
CREATE TABLE IF NOT EXISTS payments.fraud_blocked_cards (
  card_fingerprint VARCHAR(64) PRIMARY KEY,
  reason VARCHAR(200) NOT NULL,
  blocked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fraud_blocked_at
  ON payments.fraud_blocked_cards(blocked_at DESC);

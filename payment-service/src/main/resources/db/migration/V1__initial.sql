CREATE TABLE payment_attempts (
 id UUID PRIMARY KEY, order_id UUID NOT NULL, request_key VARCHAR(80) NOT NULL, card_fingerprint VARCHAR(64),
 method VARCHAR(20) NOT NULL, status VARCHAR(32) NOT NULL, amount NUMERIC(16,2) NOT NULL,
 webpay_token VARCHAR(255), authorization_code VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_card_recent ON payment_attempts(card_fingerprint,created_at DESC);
CREATE INDEX idx_payment_order ON payment_attempts(order_id);
CREATE UNIQUE INDEX idx_payment_webpay_token ON payment_attempts(webpay_token) WHERE webpay_token IS NOT NULL;
CREATE TABLE payment_events (id UUID PRIMARY KEY, routing_key VARCHAR(100) NOT NULL,
 body TEXT NOT NULL, published_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE fraud_alerts (id UUID PRIMARY KEY, card_fingerprint VARCHAR(64) NOT NULL,
 reason VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE UNIQUE INDEX idx_payment_retry ON payment_attempts(order_id,request_key);

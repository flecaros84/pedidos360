-- Kafka recommendation requests and materialized per-user recommendations.
CREATE TABLE recommendation_requests (
 event_id UUID PRIMARY KEY,
 user_id VARCHAR(120) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMPTZ
);
CREATE TABLE user_recommendations (
 user_id VARCHAR(120) NOT NULL,
 rank INT NOT NULL,
 product_sku VARCHAR(24) NOT NULL,
 category VARCHAR(80),
 score BIGINT NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,rank)
);
CREATE INDEX idx_user_recommendations_updated ON user_recommendations(updated_at DESC);

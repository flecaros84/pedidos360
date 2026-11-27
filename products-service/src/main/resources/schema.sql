-- Esquema exclusivo del microservicio de productos.
-- La base PostgreSQL se comparte, pero Cart conserva sus tablas en public.
CREATE SCHEMA IF NOT EXISTS catalogo;

-- Transactional inventory reservation, unique per order.
CREATE TABLE IF NOT EXISTS catalogo.inventory_orders (
 order_id UUID PRIMARY KEY,
 payload_hash VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS catalogo.inventory_reservations (
 order_id UUID NOT NULL REFERENCES catalogo.inventory_orders(order_id),
 branch_code VARCHAR(32) NOT NULL,
 sku VARCHAR(24) NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity > 0),
 PRIMARY KEY(order_id,branch_code,sku)
);
CREATE TABLE IF NOT EXISTS catalogo.inventory_outbox (
 event_id UUID PRIMARY KEY,
 routing_key VARCHAR(100) NOT NULL,
 payload TEXT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMP WITH TIME ZONE,
 audit_published_at TIMESTAMP WITH TIME ZONE
);
ALTER TABLE catalogo.inventory_outbox ADD COLUMN IF NOT EXISTS audit_published_at TIMESTAMP WITH TIME ZONE;

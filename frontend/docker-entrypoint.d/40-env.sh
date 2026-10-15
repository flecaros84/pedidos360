#!/bin/sh
set -eu

# La URL de Orders comparte API Gateway con Products en AWS. Local usa :8084.
if [ -n "${ORDERS_API_URL:-}" ]; then
  ORDER_ENDPOINT="$ORDERS_API_URL"
elif [ "${AUTH_MODE:-local}" = "azure" ]; then
  ORDER_ENDPOINT="${PRODUCTS_API_URL:-http://localhost:8084}"
else
  ORDER_ENDPOINT="http://localhost:8084"
fi

cat > /usr/share/nginx/html/env.js <<EOF
window.__ENV__ = {
  AUTH_MODE: "${AUTH_MODE:-local}",
  AZURE_TENANT_ID: "${AZURE_TENANT_ID:-}",
  AZURE_CLIENT_ID: "${AZURE_CLIENT_ID:-}",
  AZURE_API_SCOPE: "${AZURE_API_SCOPE:-}",
  AZURE_REDIRECT_URI: "${AZURE_REDIRECT_URI:-}",
  AUTH_API_URL: "${AUTH_API_URL:-http://localhost:8081}",
  CART_API_URL: "${CART_API_URL:-http://localhost:8082}",
  PRODUCTS_API_URL: "${PRODUCTS_API_URL:-http://localhost:8083}",
  ORDERS_API_URL: "$ORDER_ENDPOINT"
};
EOF

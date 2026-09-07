#!/bin/sh
set -eu

cat > /usr/share/nginx/html/env.js <<EOF
window.__ENV__ = {
  AUTH_MODE: "${AUTH_MODE:-local}",
  AZURE_TENANT_ID: "${AZURE_TENANT_ID:-}",
  AZURE_CLIENT_ID: "${AZURE_CLIENT_ID:-}",
  AZURE_API_SCOPE: "${AZURE_API_SCOPE:-}",
  AZURE_REDIRECT_URI: "${AZURE_REDIRECT_URI:-}",
  AUTH_API_URL: "${AUTH_API_URL:-http://localhost:8081}",
  CART_API_URL: "${CART_API_URL:-http://localhost:8082}"
};
EOF

#!/usr/bin/env bash
set -euo pipefail

curl -fsS http://localhost:8081/actuator/health >/dev/null
echo "OK auth-service"

curl -fsS http://localhost:8082/actuator/health >/dev/null
echo "OK cart-service"

curl -fsS http://localhost >/dev/null
echo "OK frontend"

curl -fsS -H 'X-Demo-User: check-user' http://localhost:8081/api/auth/me >/dev/null
echo "OK identidad local"

echo "Pedidos360 local responde correctamente."

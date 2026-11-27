#!/usr/bin/env python3
"""Validate the backend's explicit route allowlist without contacting AWS."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[2]
text = (root / "terraform/lab/api.tf").read_text(encoding="utf-8")
assert 'route_key = "ANY /api/{proxy+}"' not in text, "Backend greedy proxy route remains"
assert re.search(r'route_key\s*=\s*"ANY /\{proxy\+\}"', text), "Frontend proxy must remain"
match = re.search(r'backend_route_keys\s*=\s*\[([^]]+)\]', text)
assert match, "Explicit backend route allowlist missing"
routes = re.findall(r'"(GET|POST|PATCH|DELETE|PUT) (/api/[^"]+)"', match.group(1))
keys = [f"{method} {path}" for method,path in routes]
assert len(keys) >= 25 and len(set(keys)) == len(keys), "Expected distinct explicit routes"
for key in ("GET /api/auth/me", "POST /api/orders", "POST /api/payments",
            "GET /api/analytics/recommendations", "GET /api/audit/recent"):
    assert key in keys, f"Missing {key}"
assert '"GET", "POST"' in text, "Webpay return must use fixed GET/POST methods"
print(f"OK: {len(keys)} explicit backend routes; frontend SPA proxy preserved")

#!/usr/bin/env python3
"""Minimal independent probes for the public frontend/API split and CORS preflight."""
import argparse
import sys
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description='Checks frontend HTML, JWT rejection and CORS preflight')
parser.add_argument('--frontend', required=True, help='e.g. https://dxxx.cloudfront.net')
parser.add_argument('--api', required=True, help='e.g. https://xxx.execute-api.us-east-1.amazonaws.com')
parser.add_argument('--origin', required=True, help='Frontend CORS origin')
args = parser.parse_args()

def request(url, method='GET', headers=None):
    req = urllib.request.Request(url, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            return res.status, dict(res.headers.items()), res.read()[:500]
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers.items()), exc.read()[:500]

frontend_status, frontend_headers, html = request(args.frontend.rstrip('/') + '/')
print('Frontend GET /:', frontend_status)
if frontend_status != 200 or b'<html' not in html.lower():
    print('FAIL: frontend is not serving HTML');sys.exit(1)

protected = args.api.rstrip('/') + '/api/orders'
unauthorized, headers, _ = request(protected)
print('Backend without JWT:', unauthorized)
if unauthorized not in (401,403):
    print('FAIL: protected endpoint accepted access or was not routed');sys.exit(1)

preflight, cors, _ = request(protected, 'OPTIONS', {
    'Origin': args.origin,
    'Access-Control-Request-Method': 'GET',
    'Access-Control-Request-Headers': 'authorization,content-type'
})
print('Preflight:', preflight, 'Allow-Origin:', cors.get('Access-Control-Allow-Origin'))
if not (200 <= preflight < 300 and cors.get('Access-Control-Allow-Origin') == args.origin):
    print('FAIL: expected allowed origin in preflight');sys.exit(1)
print('PASS: minimal external frontend/backend/CORS checks')

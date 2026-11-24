#!/usr/bin/env python3
"""Perform dependency-free structural checks. This is NOT a substitute for Maven/Docker/Terraform."""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ('auth','cart','products','orders','notifications','payment','audit','analytics','dispatch')
ERRORS = []

def check(value, message):
    if not value:
        ERRORS.append(message)

def check_yaml(path):
    try:
        import yaml
        with path.open(encoding='utf-8') as stream:
            yaml.safe_load(stream)
    except ImportError:
        print('WARN: PyYAML not installed; skipping YAML validation')
    except Exception as exc:
        ERRORS.append(f'{path.relative_to(ROOT)} invalid YAML: {exc}')

for name in SERVICES:
    folder = ROOT / f'{name}-service'
    check((folder / 'Dockerfile').is_file(), f'{name}: Dockerfile missing')
    check((folder / 'src/main/resources/application.yml').is_file(), f'{name}: application.yml missing')
    pom = folder / 'pom.xml'
    check(pom.is_file(), f'{name}: pom.xml missing')
    if pom.is_file():
        try:
            ET.parse(pom)
        except Exception as exc:
            ERRORS.append(f'{name}: invalid pom.xml: {exc}')
    if (folder / 'src/main/resources/application.yml').is_file():
        check_yaml(folder / 'src/main/resources/application.yml')

for path in [ROOT / 'docker-compose.full.yml', *sorted((ROOT / '.github/workflows').glob('*.yml'))]:
    check(path.is_file(), f'Missing {path}')
    if path.is_file():
        check_yaml(path)

for path in ROOT.rglob('*.json'):
    if 'node_modules' not in path.parts:
        try:
            json.loads(path.read_text(encoding='utf-8'))
        except Exception as exc:
            ERRORS.append(f'{path.relative_to(ROOT)} invalid JSON: {exc}')

required = ('network.tf','compute.tf','api.tf','data.tf','outputs.tf','variables.tf','websocket.tf')
for item in required:
    check((ROOT / 'terraform/lab' / item).is_file(), f'Terraform missing: {item}')
for item in ('lab-start.ps1','lab-stop.ps1','lab-status.ps1','lab-preflight.ps1','lab-deploy.ps1'):
    check((ROOT / 'scripts/lab' / item).is_file(), f'Lab script missing: {item}')

if ERRORS:
    print(f'FAIL: {len(ERRORS)} static check(s)')
    for error in ERRORS:
        print(f' - {error}')
    sys.exit(1)
print(f'PASS: structural files, {len(SERVICES)} Maven modules, YAML, JSON and XML.')
print('NOT TESTED: Java dependencies/compilation, Docker startup, Terraform provider plan, AWS integrations.')

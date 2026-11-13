#!/usr/bin/env bash
# Real optional integrations in two disposable server JVMs; uses previously accepted EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
python3 - <<'PY'
from pathlib import Path
import json,urllib.request,hashlib
root=Path('/workspace/shared/raids-reward-mods');root.mkdir(parents=True,exist_ok=True)
for entry in json.loads(Path('scripts/reward-test-mods.json').read_text()):
    target=root/entry['filename']
    if not target.exists(): urllib.request.urlretrieve(entry['url'],target)
    algorithm='sha512' if 'sha512' in entry else 'sha256'
    assert hashlib.new(algorithm,target.read_bytes()).hexdigest()==entry[algorithm],target
    print('Verified',target.name)
PY
SHIROKAI_INTEGRATION_MODE=rewards bash scripts/run-teleport-integration.sh

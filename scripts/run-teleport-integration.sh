#!/usr/bin/env bash
# Disposable test world only. Requires a prepared server and its already accepted EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
source scripts/cloud-env.sh
raids_test_root=$(mktemp -d /workspace/shared/raids-teleport-test.XXXXXXXX)
python3 - "$raids_test_root" <<'PY'
from pathlib import Path
import shutil,sys
root=Path(sys.argv[1]);base=Path('/workspace/shared/raids-test-server')
assert (base/'eula.txt').is_file() and any(line.strip()=='eula=true' for line in (base/'eula.txt').read_text().splitlines()), 'Aceite a EULA para o servidor de testes antes de executar'
for name in ['libraries','server.jar','fabric-server-launch.jar']:
    assert (base/name).exists(),f'Execute prepare-test-server.sh primeiro: {name}'
    (root/name).symlink_to(base/name,target_is_directory=(base/name).is_dir())
(root/'mods').mkdir()
for p in (base/'mods').glob('*.jar'):
    if not p.name.startswith('Shirokai-Raids-'):
        shutil.copy2(p,root/'mods'/p.name)
version=next(line.split('=',1)[1] for line in Path('gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
for p in [Path(f'build/libs/Shirokai-Raids-{version}-fabric-1.21.1.jar'),Path('build/integration/shirokai-raids-integration.jar')]:
    shutil.copy2(p,root/'mods'/p.name)
shutil.copy2(base/'eula.txt',root/'eula.txt')
(root/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
print(f'Mundo descartável: {root}')
PY
read -r -a raids_jvm_options <<< "${GRADLE_OPTS:-}"
(cd "$raids_test_root" && timeout 180 java "${raids_jvm_options[@]}" -Dshirokai.integration=true -Xms512M -Xmx3G -jar fabric-server-launch.jar nogui > integration.log 2>&1)
python3 - "$raids_test_root" <<'PY'
from pathlib import Path
import json,sys
root=Path(sys.argv[1]);result=json.loads((root/'teleport-integration-result.json').read_text())
assert result['passed'] is True,result
assert result['realClients']==0,result
print(json.dumps(result,ensure_ascii=False))
print(f'Resultado e logs preservados em {root}')
PY

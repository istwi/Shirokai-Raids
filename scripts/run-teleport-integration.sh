#!/usr/bin/env bash
# Disposable test world only. Requires a prepared server and its already accepted EULA.
set -euo pipefail
cd /workspace/Shirokai-Raids
source scripts/cloud-env.sh
raids_test_mode=${SHIROKAI_INTEGRATION_MODE:-teleport}
case "$raids_test_mode" in teleport|combat|challenge) ;; *) echo 'Modo de integração inválido' >&2; exit 1 ;; esac
raids_test_root=$(mktemp -d "/workspace/shared/raids-${raids_test_mode}-test.XXXXXXXX")
python3 - "$raids_test_root" "$raids_test_mode" <<'PY'
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
if sys.argv[2]=='challenge':
    import json
    bosses=root/'config/shirokai-raids/bosses'
    bosses.mkdir(parents=True)
    rewards=root/'config/shirokai-raids/rewards'
    rewards.mkdir(parents=True)
    (rewards/'testdrops.json').write_text(json.dumps({'rollsPerPlayer':2,'tiers':[{'id':'all','minimumContribution':0.01,'rarityChances':{'custom':100}}],'itemPools':{'custom':[{'item':'minecraft:apple','minimum':2,'maximum':2,'weight':1}]}}))
    (bosses/'rayquaza.json').write_text(json.dumps({'id':'rayquaza','species':'rayquaza','hp':3000,'scale':2.5,'damageMultiplier':1.5,'rewards':'testdrops'}))
print(f'Mundo descartável: {root}')
PY
read -r -a raids_jvm_options <<< "${GRADLE_OPTS:-}"
(cd "$raids_test_root" && timeout 180 java "${raids_jvm_options[@]}" -Dshirokai.integration=true "-Dshirokai.integration.mode=$raids_test_mode" -Xms512M -Xmx3G -jar fabric-server-launch.jar nogui > integration.log 2>&1)
python3 - "$raids_test_root" "$raids_test_mode" <<'PY'
from pathlib import Path
import json,sys
root=Path(sys.argv[1]);mode=sys.argv[2]
result=json.loads((root/f'{mode}-integration-result.json').read_text())
assert result['passed'] is True,result
assert result['realClients']==0,result
if mode=='combat':
    assert result['controlledSessions']==3 and result['effectiveDamage']==2000,result
if mode=='challenge':
    recovery=json.loads((root/'reward-recovery-result.json').read_text())
    assert recovery['passed'] is True and recovery['scenarios']==6 and recovery['realClients']==0,recovery
    print(json.dumps(recovery,ensure_ascii=False))
    menu=json.loads((root/'raid-menu-result.json').read_text())
    assert menu['passed'] is True and menu['clickChecks']==105 and menu['realClients']==0,menu
    print(json.dumps(menu,ensure_ascii=False))
print(json.dumps(result,ensure_ascii=False))
print(f'Resultado e logs preservados em {root}')
PY

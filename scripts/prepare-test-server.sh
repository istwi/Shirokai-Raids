#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Shirokai-Raids
source scripts/cloud-env.sh
root=/workspace/shared/raids-test-server
mkdir -p "$root"
curl -fLsS https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.0/fabric-installer-1.1.0.jar -o "$root/fabric-installer.jar"
curl -fLsS https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.0/fabric-installer-1.1.0.jar.sha256 -o "$root/fabric-installer.sha256"
python3 - <<'PY'
import hashlib
from pathlib import Path
root=Path('/workspace/shared/raids-test-server')
assert hashlib.sha256((root/'fabric-installer.jar').read_bytes()).hexdigest() == (root/'fabric-installer.sha256').read_text().split()[0]
PY
read -r -a raids_jvm_options <<< "${GRADLE_OPTS:-}"
(cd "$root" && java "${raids_jvm_options[@]}" -jar fabric-installer.jar server -mcversion 1.21.1 -loader 0.17.2)
python3 - <<'PY'
import hashlib, json, os, shutil
from pathlib import Path
root=Path('/workspace/shared/raids-test-server')
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches'
mc=cache/'fabric-loom/1.21.1'
metadata=json.loads((mc/'mojang_minecraft_info.json').read_text())
server=mc/'minecraft-server.jar'
assert hashlib.sha1(server.read_bytes()).hexdigest()==metadata['downloads']['server']['sha1']
shutil.copy2(server,root/'server.jar')
mods=root/'mods';mods.mkdir(exist_ok=True)
for group,artifact,version in [('com.cobblemon','fabric','1.8.3+1.21.1'),('net.fabricmc.fabric-api','fabric-api','0.116.6+1.21.1'),('net.fabricmc','fabric-language-kotlin','1.13.6+kotlin.2.2.20')]:
    jars=[p for p in (cache/'modules-2/files-2.1'/group/artifact/version).glob('*/*.jar') if not p.name.endswith('-sources.jar')]
    assert len(jars)==1,(artifact,jars)
    shutil.copy2(jars[0],mods/jars[0].name)
version=next(line.split('=',1)[1] for line in Path('gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
jar=Path(f'build/libs/Shirokai-Raids-{version}-fabric-1.21.1.jar')
assert not [p for p in mods.glob('Shirokai-Raids-*.jar') if p.name!=jar.name], 'Mova a versão anterior do mod para fora de mods antes de atualizar'
shutil.copy2(jar,mods/jar.name)
print('Servidor preparado. EULA e configuração existentes foram preservadas.')
PY

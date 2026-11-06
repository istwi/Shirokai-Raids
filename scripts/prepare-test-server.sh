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
# All versions are pinned; the official local-loader API does not need the remote version catalogue.
loader_base=https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar
curl -fLsS "$loader_base" -o "$root/fabric-loader.jar"
curl -fLsS "$loader_base.sha256" -o "$root/fabric-loader.sha256"
python3 - <<'PY'
import hashlib
from pathlib import Path
root=Path('/workspace/shared/raids-test-server')
assert hashlib.sha256((root/'fabric-loader.jar').read_bytes()).hexdigest() == (root/'fabric-loader.sha256').read_text().split()[0]
PY
java "${raids_jvm_options[@]}" --class-path "$root/fabric-installer.jar" scripts/InstallPinnedFabric.java "$root" "$root/fabric-loader.jar"
python3 - <<'PY'
import hashlib,json,subprocess,zipfile
from pathlib import Path
root=Path('/workspace/shared/raids-test-server')
with zipfile.ZipFile(root/'fabric-loader.jar') as jar:
    libraries=json.loads(jar.read('fabric-installer.json'))['libraries']
for entry in libraries['common']+libraries['server']:
    group,name,version=entry['name'].split(':')
    path=root/'libraries'/group.replace('.','/')/name/version/f'{name}-{version}.jar'
    assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'], f'Checksum divergente: {path}'
path=root/'libraries/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar'
checksum=subprocess.check_output(['curl','-fLsS','https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar.sha256'],text=True).split()[0]
assert hashlib.sha256(path.read_bytes()).hexdigest()==checksum
PY
python3 - <<'PY'
import hashlib, json, os, shutil, zipfile
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
for previous in mods.glob('Shirokai-Raids-*.jar'):
    if previous.name == jar.name:
        continue
    with zipfile.ZipFile(previous) as z:
        assert json.loads(z.read('fabric.mod.json'))['id'] == 'shirokai_raids', 'JAR desconhecido; preservado sem alteração'
    digest = hashlib.sha256(previous.read_bytes()).hexdigest()
    backup = root/'previous-mods'/f'{previous.stem}-{digest}.jar'
    backup.parent.mkdir(exist_ok=True)
    if not backup.exists():
        shutil.copy2(previous,backup)
    assert hashlib.sha256(backup.read_bytes()).hexdigest() == digest, 'Backup divergente; JAR anterior preservado'
    previous.unlink()
shutil.copy2(jar,mods/jar.name)
print('Servidor preparado. EULA e configuração existentes foram preservadas.')
PY

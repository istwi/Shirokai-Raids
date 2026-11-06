"""Reproduce the three-session Java/Showdown probe after `gradlew build`, on Linux or Windows."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

RAID_URL = 'https://cdn.modrinth.com/data/GebWh45l/versions/1Qqxt55J/cobblemonraiddens-fabric-0.13.4%2B1.21.1.jar'
RAID_SHA256 = '69345ba1d0b934bc181c01393280f8eadce437e4cb9de29b75f90a498393a070'
COBBLEMON_SHA256 = '4bc3b09d25b93b74a5822dc35b06bd6f5fa8bae36ed43a8d2b3904156a61269c'
root = Path(__file__).resolve().parent.parent
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
coordinate = cache / 'caches/modules-2/files-2.1/com.cobblemon/fabric/1.8.3+1.21.1'
jars = [p for p in coordinate.glob('*/*.jar') if not p.name.endswith('-sources.jar')]
if len(jars) != 1 or hashlib.sha256(jars[0].read_bytes()).hexdigest() != COBBLEMON_SHA256:
    raise SystemExit('Execute gradlew build primeiro; JAR oficial Cobblemon 1.8.3 ausente ou divergente')
classpath = os.pathsep.join(str(root / p) for p in ('build/classes/java/main', 'build/classes/java/integration'))
with tempfile.TemporaryDirectory(prefix='shirokai-traditional-probe-') as directory:
    directory = Path(directory)
    raid_jar = directory / 'raiddens.jar'
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', RAID_URL, '-o', str(raid_jar)], check=True)
    if hashlib.sha256(raid_jar.read_bytes()).hexdigest() != RAID_SHA256:
        raise SystemExit('JAR Raid Dens oficial divergente; auditoria interrompida')
    showdown = directory / 'showdown.zip'
    with zipfile.ZipFile(jars[0]) as jar:
        showdown.write_bytes(jar.read('data/cobblemon/showdown.zip'))
    if hashlib.sha256(showdown.read_bytes()).hexdigest() != 'aad1393c313359bcb8ca9482baede9009c55256730ef1fd1905c147d8464385e':
        raise SystemExit('Checksum do simulador divergente')
    engine=directory/'engine'
    with zipfile.ZipFile(showdown) as archive:
        archive.extractall(engine)
    subprocess.run(['node',str(root/'scripts/audit_raid_adapter.cjs'),str(engine)],check=True)
    subprocess.run([sys.executable, str(root / 'scripts/run_raiddens_audit.py'), str(raid_jar),
                    '--showdown', str(showdown), '--coordinator-classpath', classpath], check=True)

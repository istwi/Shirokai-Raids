"""Reproduce API feasibility probes against the exact official bundled simulator."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

COMMIT = 'ffe005511dd0d3ba68377fa3f95906b3837d9a0e'
SHA256 = 'aad1393c313359bcb8ca9482baede9009c55256730ef1fd1905c147d8464385e'
JAR_SHA256 = '4bc3b09d25b93b74a5822dc35b06bd6f5fa8bae36ed43a8d2b3904156a61269c'
URL = 'https://artefacts.cobblemon.com/releases/com/cobblemon/fabric/1.8.3+1.21.1/fabric-1.8.3+1.21.1.jar'
p = argparse.ArgumentParser()
p.add_argument('--archive', type=Path, help='Optional local copy of the official showdown.zip')
a = p.parse_args()
with tempfile.TemporaryDirectory(prefix='shirokai-showdown-audit-') as folder:
    root = Path(folder)
    archive = a.archive or root / 'showdown.zip'
    if a.archive is None:
        cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1/com.cobblemon/fabric/1.8.3+1.21.1'
        jars = [p for p in cache.glob('*/*.jar') if not p.name.endswith('-sources.jar')]
        jar = jars[0] if len(jars) == 1 else root / 'cobblemon.jar'
        if not jar.exists():
            subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', URL, '-o', str(jar)], check=True)
        if hashlib.sha256(jar.read_bytes()).hexdigest() != JAR_SHA256:
            raise SystemExit('Checksum do JAR oficial divergente; auditoria interrompida')
        with zipfile.ZipFile(jar) as z:
            archive.write_bytes(z.read('data/cobblemon/showdown.zip'))
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('Checksum do simulador oficial divergente; auditoria interrompida')
    with zipfile.ZipFile(archive) as z:
        z.extractall(root / 'engine')
    subprocess.run(['node', str(Path(__file__).with_name('audit_showdown.cjs').resolve()), str(root / 'engine')], check=True)

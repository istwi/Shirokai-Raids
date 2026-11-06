"""Audit the exact user-supplied artifact without installing it or copying its code."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

JAR_SHA256 = '8caa1912a0d51dff2555855e054bec6606a3b148232643c0ba6c1c6ae5a35dff'
OFFICIAL_SHA256 = '69345ba1d0b934bc181c01393280f8eadce437e4cb9de29b75f90a498393a070'
RESOURCE = 'data/cobblemonraiddens/cobblemonraiddens/showdown/conditions/raidboss.js'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('jar', type=Path)
parser.add_argument('--showdown', type=Path, help='ZIP oficial do Showdown 1.8.3 para testes no motor real')
parser.add_argument('--coordinator-classpath', help='Classes Java main e integration já compiladas, separadas por os.pathsep')
args = parser.parse_args()
if args.coordinator_classpath and not args.showdown:
    parser.error('--coordinator-classpath requer --showdown')
digest = hashlib.sha256(args.jar.read_bytes()).hexdigest()
if digest not in {JAR_SHA256, OFFICIAL_SHA256}:
    raise SystemExit('JAR diferente do artefato auditado; nenhuma execução realizada')
with zipfile.ZipFile(args.jar) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    if metadata['version'] != '0.13.4+1.21.1':
        raise SystemExit('Versão inesperada')
    resource = jar.read(RESOURCE)
print(f'JAR SHA256: {digest} ({"anexo" if digest == JAR_SHA256 else "Modrinth oficial"})', flush=True)
print(f'raidboss.js SHA256: {hashlib.sha256(resource).hexdigest()}', flush=True)
with tempfile.TemporaryDirectory(prefix='shirokai-raiddens-audit-') as directory:
    effect = Path(directory) / 'raidboss.js'
    effect.write_bytes(resource)
    subprocess.run(['node', str(Path(__file__).with_name('audit_raiddens_effect.cjs')), str(effect)], check=True)
    if args.showdown:
        if hashlib.sha256(args.showdown.read_bytes()).hexdigest() != 'aad1393c313359bcb8ca9482baede9009c55256730ef1fd1905c147d8464385e':
            raise SystemExit('Checksum do motor oficial divergente; nenhuma execução do motor realizada')
        engine = Path(directory) / 'engine'
        with zipfile.ZipFile(args.showdown) as archive:
            archive.extractall(engine)
        subprocess.run(['node', str(Path(__file__).with_name('audit_raiddens_engine.cjs')), str(engine), str(effect)], check=True)
        if args.coordinator_classpath:
            subprocess.run(['java', '-cp', args.coordinator_classpath,
                            'com.shirokai.raids.integration.TraditionalBattleProbe',
                            str(Path(__file__).with_name('traditional_battle_probe.cjs')),
                            str(engine), str(effect)], check=True, timeout=60)

"""Validate the distributable, Fabric metadata, Java level and checksum."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--version', required=True)
a = p.parse_args()
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?', a.version):
    raise SystemExit('Versão inválida')
jar = Path(f'build/libs/Shirokai-Raids-{a.version}-fabric-1.21.1.jar')
with zipfile.ZipFile(jar) as z:
    meta = json.loads(z.read('fabric.mod.json'))
    assert meta['id'] == 'shirokai_raids'
    assert meta['version'] == a.version
    assert meta['environment'] == 'server'
    assert meta['depends']['minecraft'] == '1.21.1'
    assert meta['depends']['cobblemon'] == '1.8.3+1.21.1'
    assert meta['depends']['java'] == '>=21'
    assert 'cobblemonraiddens' not in meta['depends'], 'Raid Dens não pode ser dependência de produção'
    for name in z.namelist():
        assert not name.startswith(('com/necro/raid/dens/', 'assets/cobblemonraiddens/',
                                    'data/cobblemonraiddens/')), f'Conteúdo Raid Dens incorporado: {name}'
        assert not name.startswith('com/shirokai/raids/integration/'), 'Fixture vazou para produção'
    classes = [n for n in z.namelist() if n.startswith('com/shirokai/raids/') and n.endswith('.class')]
    assert 'com/shirokai/raids/ShirokaiRaids.class' in classes
    for name in classes:
        data = z.read(name)
        assert data[:4] == b'\xca\xfe\xba\xbe'
        assert int.from_bytes(data[6:8], 'big') == 65, name
checksum = hashlib.sha256(jar.read_bytes()).hexdigest()
(jar.parent / 'SHA256SUMS').write_text(f'{checksum}  {jar.name}\n', encoding='utf-8')
print(f'JAR validado: {jar.name}; SHA-256: {checksum}')

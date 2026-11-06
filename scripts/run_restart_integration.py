"""Boot one disposable Fabric/Cobblemon world four times; perform one real SIGKILL."""
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import tempfile
import time

repository = Path(__file__).resolve().parent.parent
base = Path('/workspace/shared/raids-test-server')
assert os.name == 'posix', 'This SIGKILL harness currently requires Linux'
assert (base/'eula.txt').is_file() and any(line.strip() == 'eula=true' for line in (base/'eula.txt').read_text().splitlines()), 'Use the authorized disposable server with accepted EULA'
root = Path(tempfile.mkdtemp(prefix='raids-restart-test.', dir='/workspace/shared'))
print(f'Disposable four-boot world: {root}', flush=True)
for name in ('libraries', 'server.jar', 'fabric-server-launch.jar'):
    assert (base/name).exists(), f'Prepare the test server first: {name}'
    (root/name).symlink_to(base/name, target_is_directory=(base/name).is_dir())
(root/'mods').mkdir()
for jar in (base/'mods').glob('*.jar'):
    if not jar.name.startswith('Shirokai-Raids-'):
        shutil.copy2(jar, root/'mods'/jar.name)
version = next(line.split('=', 1)[1] for line in (repository/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
for jar in (repository/f'build/libs/Shirokai-Raids-{version}-fabric-1.21.1.jar', repository/'build/integration/shirokai-raids-integration.jar'):
    shutil.copy2(jar, root/'mods'/jar.name)
shutil.copy2(base/'eula.txt', root/'eula.txt')
(root/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25566\nview-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
config = root/'config/shirokai-raids'
(config/'bosses').mkdir(parents=True)
(config/'rewards').mkdir()
(config/'config.json').write_text(json.dumps({'automaticRaids': False, 'intervalSeconds': 7200, 'durationSeconds': 600, 'restartPolicy': 'SKIP_MISSED', 'menuTimeZone': 'America/Sao_Paulo'}, indent=2)+'\n')
boss_file = config/'bosses/rayquaza.json'
boss_file.write_text(json.dumps({'id':'rayquaza', 'name':'Restart fixture boss', 'species':'rayquaza', 'hp':12345, 'scale':2.5, 'damageMultiplier':1.75, 'rewards':'restart', 'operatorNote':'Custom file: never rewrite on boot'}, indent=2)+'\n')
(config/'rewards/restart.json').write_text(json.dumps({'maximumItems':8, 'minimumContribution':0.01, 'items':[{'item':'minecraft:apple','minimum':2,'maximum':2,'weight':100}]}, indent=2)+'\n')

def hashes():
    return {str(path.relative_to(config)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(config.rglob('*.json'))}

java = str(Path(os.environ['JAVA_HOME'])/'bin/java')
options = shlex.split(os.environ.get('GRADLE_OPTS', ''))
results = []
for phase in ('prepare', 'normal-and-crash', 'recover', 'verify-again'):
    if phase == 'normal-and-crash':
        # Deliberate offline edits must be loaded, not replaced by defaults, at the next boot.
        boss = json.loads(boss_file.read_text())
        boss.update(hp=23456, scale=3.0, damageMultiplier=2.0)
        boss_file.write_text(json.dumps(boss, indent=2)+'\n')
        general_file = config/'config.json'
        general = json.loads(general_file.read_text())
        general.update(durationSeconds=900, maxConcurrentPlayers=7)
        general_file.write_text(json.dumps(general, indent=2)+'\n')
    before = hashes()
    marker = root/f'{phase}-result.json'
    assert not marker.exists(), 'Refusing stale result file'
    args = [java, *options, '-Dshirokai.integration=true', '-Dshirokai.integration.mode=restart',
            f'-Dshirokai.restart.phase={phase}', '-Xms512M', '-Xmx3G', '-jar', 'fabric-server-launch.jar', 'nogui']
    print(f'Starting new JVM: {phase}', flush=True)
    with (root/f'{phase}.log').open('w') as log:
        process = subprocess.Popen(args, cwd=root, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic()+180
        try:
            while not marker.exists() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.2)
            assert marker.exists(), f'{phase}: no current result; inspect {root/str(phase + ".log")}'
            result = json.loads(marker.read_text())
            assert result['passed'] is True, result
            if phase == 'normal-and-crash':
                assert process.poll() is None, 'JVM exited before the intended forced crash'
                process.kill()
                code = process.wait(timeout=30)
                assert code == -signal.SIGKILL, f'Expected actual SIGKILL, got {code}'
                result['actualSigkill'] = True
                durable = json.loads((root/'world/data/shirokai-raids/state.json').read_text())
                assert durable['activeRaid'] and len(durable['deliveries']) == 1, 'Forced crash did not preserve the intended active-raid/receipt window'
            else:
                code = process.wait(timeout=max(1, deadline-time.monotonic()))
                assert code == 0, f'{phase}: server exit code {code}'
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=30)
    after = hashes()
    assert result['configHashes'] == after, f'{phase}: configuration changed between the in-JVM checkpoint and process exit'
    if phase == 'prepare':
        # Commands intentionally set positions during phase one. Check against its saved values.
        checkpoint = json.loads((config/'config.json').read_text())
        assert checkpoint['x'] == 12.25 and checkpoint['z'] == -8.75 and checkpoint['lobby']['configured'] is True
        assert before['bosses/rayquaza.json'] == after['bosses/rayquaza.json']
        assert before['rewards/restart.json'] == after['rewards/restart.json']
    else:
        assert before == after, f'{phase}: existing config files changed across the JVM boot'
    result['configHashes'] = after
    results.append(result)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    (root/'restart-integration-progress.json').write_text(json.dumps(results, ensure_ascii=False, indent=2)+'\n')

final = {'passed':True, 'jvmBoots':4, 'actualSigkill':True, 'realClients':0, 'version':version, 'phases':results}
(root/'restart-integration-result.json').write_text(json.dumps(final, ensure_ascii=False, indent=2)+'\n')
print(f'PASS four real JVM boots, one SIGKILL, original spawn/config preservation and receipt recovery: {root}', flush=True)

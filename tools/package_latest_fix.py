#!/usr/bin/env python3
"""Keep the complete published 0.1.22 binary, replacing only reviewed capture/inventory classes."""
import argparse,hashlib,json,zipfile,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--tools-cp',required=True);p.add_argument('--candidate',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--version',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
base=root/'releases/zorykai/v0.1.22-damagechat.1/Zorykai-Raids-0.1.22-damagechat.1-fabric-1.21.1.jar'
assert hashlib.sha256(base.read_bytes()).hexdigest()=='7edaf39537c447819d77e815f395ac10b73884b0868e7a2fc785b07104ce6254'
prefixes=('com/shirokai/raids/RaidCapture.class','com/shirokai/raids/RaidCapture$','com/shirokai/raids/RaidInventory.class','com/shirokai/raids/mixin/RaidQuizGuardMixin.class','com/shirokai/raids/RaidQuizGuard.class')
with zipfile.ZipFile(base) as old,zipfile.ZipFile(a.candidate) as candidate,tempfile.TemporaryDirectory() as work:
 replacements={n:candidate.read(n) for n in candidate.namelist() if n.endswith('.class') and any(n==prefix or prefix.endswith('$') and n.startswith(prefix) for prefix in prefixes)}
 assert all(n in replacements for n in ['com/shirokai/raids/RaidCapture.class','com/shirokai/raids/RaidInventory.class','com/shirokai/raids/mixin/RaidQuizGuardMixin.class','com/shirokai/raids/RaidQuizGuard.class'])
 main='com/shirokai/raids/ShirokaiRaids.class';source=Path(work)/'original.class';target=Path(work)/'patched.class';source.write_bytes(old.read(main))
 subprocess.run([a.java,'-cp',a.tools_cp,'PatchLatestRaid',str(source),str(target)],check=True);replacements[main]=target.read_bytes()
 mixins=json.loads(old.read('shirokai-raids.mixins.json'));mixins['server'].append('RaidQuizGuardMixin');replacements['shirokai-raids.mixins.json']=(json.dumps(mixins,indent=2)+'\n').encode()
 metadata=json.loads(old.read('fabric.mod.json'));metadata['version']=a.version;replacements['fabric.mod.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
 messages=json.loads(old.read('shirokai/messages.json'));fresh=json.loads(candidate.read('shirokai/messages.json'))
 for key in ['captura.equipe_indisponivel','captura.sem_espaco_bolas','quiz.bloqueado_na_raid']:messages[key]=fresh[key]
 replacements['shirokai/messages.json']=(json.dumps(messages,ensure_ascii=False,indent=2)+'\n').encode()
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as output:
  for entry in old.infolist():output.writestr(entry,replacements.get(entry.filename,old.read(entry.filename)))
  for name in sorted(replacements.keys()-set(old.namelist())):
   entry=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;output.writestr(entry,replacements[name])
with zipfile.ZipFile(base) as old,zipfile.ZipFile(a.output) as patched:
 assert set(old.namelist())<=set(patched.namelist()) and patched.testzip() is None
 changed={n for n in old.namelist() if old.read(n)!=patched.read(n)}
 assert changed<=set(replacements)
 # Recovery/authentication APIs and private damage feedback remain from 0.1.22.
 for name in ['com/shirokai/raids/RaidReturnPersistence.class','com/shirokai/raids/RaidAuthentication.class','com/shirokai/raids/api/RaidRecoveryApi.class','com/shirokai/raids/GuardRaidAccess.class']:
  assert old.read(name)==patched.read(name)
report={'baseSHA256':hashlib.sha256(base.read_bytes()).hexdigest(),'artifactSHA256':hashlib.sha256(a.output.read_bytes()).hexdigest(),'version':a.version,'changedEntries':sorted(changed),'addedEntries':sorted(replacements.keys()-set(old.namelist()))}
a.output.with_suffix('.audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(a.output);print(json.dumps(report,indent=2))

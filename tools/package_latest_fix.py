#!/usr/bin/env python3
"""Preserve the complete 0.1.24 binary and add the narrow return/spawn recovery adapter."""
import argparse,hashlib,json,zipfile,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--tools-cp',required=True);p.add_argument('--candidate',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--version',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
base=root/'releases/zorykai/v0.1.24-quizguard.1/Zorykai-Raids-0.1.24-quizguard.1-fabric-1.21.1.jar'
assert hashlib.sha256(base.read_bytes()).hexdigest()=='9709ece1b392b984d10dfaa3941d8c6a2aaff02ab459190b9cb1af9fcb81cb09'
prefixes=('com/shirokai/raids/RaidReturnFallback.class','com/shirokai/raids/RaidReturnFallback$')
with zipfile.ZipFile(base) as old,zipfile.ZipFile(a.candidate) as candidate,tempfile.TemporaryDirectory() as work:
 replacements={n:candidate.read(n) for n in candidate.namelist() if n.endswith('.class') and any(n==prefix or prefix.endswith('$') and n.startswith(prefix) for prefix in prefixes)}
 assert 'com/shirokai/raids/RaidReturnFallback.class' in replacements
 main='com/shirokai/raids/ShirokaiRaids.class';source=Path(work)/'original.class';target=Path(work)/'patched.class';source.write_bytes(old.read(main))
 subprocess.run([a.java,'-cp',a.tools_cp,'PatchLatestRaid',str(source),str(target)],check=True);replacements[main]=target.read_bytes()
 metadata=json.loads(old.read('fabric.mod.json'));metadata['version']=a.version;replacements['fabric.mod.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n').encode()
 a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as output:
  for entry in old.infolist():output.writestr(entry,replacements.get(entry.filename,old.read(entry.filename)))
  for name in sorted(replacements.keys()-set(old.namelist())):
   entry=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;output.writestr(entry,replacements[name])
with zipfile.ZipFile(base) as old,zipfile.ZipFile(a.output) as patched:
 assert set(old.namelist())<=set(patched.namelist()) and patched.testzip() is None
 changed={n for n in old.namelist() if old.read(n)!=patched.read(n)}
 assert changed<=set(replacements)
 # Authentication, capture/quiz guards and recovery APIs remain byte-for-byte from 0.1.24.
 for name in ['com/shirokai/raids/RaidReturnPersistence.class','com/shirokai/raids/RaidAuthentication.class','com/shirokai/raids/api/RaidRecoveryApi.class','com/shirokai/raids/GuardRaidAccess.class','com/shirokai/raids/RaidCapture.class','com/shirokai/raids/RaidInventory.class','com/shirokai/raids/RaidQuizGuard.class','com/shirokai/raids/mixin/RaidQuizGuardMixin.class']:
  assert old.read(name)==patched.read(name)
report={'baseSHA256':hashlib.sha256(base.read_bytes()).hexdigest(),'artifactSHA256':hashlib.sha256(a.output.read_bytes()).hexdigest(),'version':a.version,'changedEntries':sorted(changed),'addedEntries':sorted(replacements.keys()-set(old.namelist()))}
a.output.with_suffix('.audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(a.output);print(json.dumps(report,indent=2))

import json,hashlib,subprocess,shutil
from pathlib import Path
r=Path(__file__).parent; repo=Path('/mnt/storage/redsquare-android-artifacts/redesign86'); out=repo/'docs/evidence/ticket130'; index=[]
assert not out.exists(), 'Do not overwrite an existing packet'
def copy(src,rel):
 dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest)
 assert src.read_bytes()==dest.read_bytes()
 index.append({'path':str(rel),'original':str(src.relative_to(r)),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'bytes':dest.stat().st_size})
for stem,run in [('android10',43868275196),('android17',43868275286),('source',43868274993)]:
 pre=f'{stem}-{run}'
 v=json.loads((r/f'{pre}-verification.json').read_text());assert v['conclusion']=='success'
 if stem!='source':assert json.loads((r/f'{pre}-assessment.json').read_text())['allCompleteNativeAndCaptureChecksPassed']
 for suffix in ['run.json','artifacts.json','verification.json']+(['assessment.json'] if stem!='source' else []):copy(r/f'{pre}-{suffix}',Path(stem)/suffix)
 roots=[(r/f'{pre}-artifacts'/'.scratch/device-evidence',Path(stem)/'native'),(r/f'{pre}-captures',Path(stem)/'captures')]
 if stem=='source':roots=[(r/f'{pre}-artifacts'/'.scratch',Path(stem)/'checks'),(r/f'{pre}-artifacts'/'core/build/reports/tests/test',Path(stem)/'core'),(r/f'{pre}-artifacts'/'app/build/reports',Path(stem)/'lint')]
 for source,destination in roots:
  assert source.exists(),source
  for p in sorted(source.rglob('*')):
   if p.is_file() and p.suffix in ('.txt','.log','.json','.jsonl','.png','.xml','.html','.css','.js'):copy(p,destination/p.relative_to(source))
assert json.loads((r/'source-verification.json').read_text())['source']=='1e5066800bf5a48eb72693266fd9d9d170d525c1'
copy(r/'source-verification.json',Path('source-verification.json'))
for pre in ['first-red-43859381299','missing-storage-43860362013','cancelled-source-43862055997','first-ui-43862472008','adverse10-43863934379','adverse17-43863934035','android10-43865645680','android17-43865645927','android10-43866901943','android17-43866901706','source-43865105811']:
 for suffix in ['run.json','artifacts.json','verification.json']:copy(r/f'{pre}-{suffix}',Path('observations')/f'{pre}-{suffix}')
 for p in sorted((r/f'{pre}-artifacts').rglob('*-ui.txt')):copy(p,Path('observations')/pre/p.name)
for p in r.glob('cancelled-moving-ref-43859308507-*.json'):copy(p,Path('observations')/p.name)
binding={'allExecutingSources':'1e5066800bf5a48eb72693266fd9d9d170d525c1','sourceDebugRun':43868274993,'wholeAndroid10Run':43868275196,'wholeAndroid17Run':43868275286,'fixtureOrProductSourceBridgeRequired':False}
(out/'source-binding.json').write_text(json.dumps(binding,indent=2)+'\n')
(out/'packet-index.json').write_text(json.dumps({'externalOriginalRoot':str(r),'indexedFiles':len(index),'files':index},indent=2)+'\n')
for name in ['assess.py','collect-focused.py','collect-failed.py','make-packet.py']:
 shutil.copyfile(r/name,out/name)
print({'indexedFiles':len(index),'allExecutingSources':binding['allExecutingSources']})

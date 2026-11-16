import json,sys,subprocess,hashlib,zipfile,tarfile
from pathlib import Path
root=Path(__file__).parent
run,stem,expected=sys.argv[1:4]
prefix=f'{stem}-{run}'
def gh(*args):return subprocess.check_output(['gh',*args])
meta=json.loads(gh('api',f'repos/robelene112/redsquare-android/actions/runs/{run}'))
assert meta['status']=='completed'
assert meta['conclusion']=='success', 'Success collection requires a complete successful primary run'
(root/f'{prefix}-run.json').write_text(json.dumps(meta,indent=2)+'\n')
(root/f'{prefix}-original.log').write_bytes(gh('run','view',run,'--repo','robelene112/redsquare-android','--log'))
artifacts=json.loads(gh('api',f'repos/robelene112/redsquare-android/actions/runs/{run}/artifacts'))
(root/f'{prefix}-artifacts.json').write_text(json.dumps(artifacts,indent=2)+'\n')
a=next(a for a in artifacts['artifacts'] if a['name']=='android-native-proof')
p=root/f'{prefix}-original.zip';p.write_bytes(gh('api',f'repos/robelene112/redsquare-android/actions/artifacts/{a["id"]}/zip'))
sha=hashlib.sha256(p.read_bytes()).hexdigest();assert a['digest']=='sha256:'+sha
out=root/f'{prefix}-artifacts';z=zipfile.ZipFile(p);z.extractall(out)
for n in z.namelist():
 if not n.endswith('/'):assert (out/n).read_bytes()==z.read(n)
source=(out/'.scratch/proof-source.txt').read_text().strip();assert source==expected
captures=root/f'{prefix}-captures';tar_members=0
for f in sorted(set(out.rglob('*-evidence.tar')) | set(out.rglob('images.tar'))):
 with tarfile.open(f) as t:
  t.extractall(captures,filter='data')
  for m in t.getmembers():
   if m.isfile():assert (captures/m.name).read_bytes()==t.extractfile(m).read();tar_members+=1
receipt={'run':int(run),'source':source,'conclusion':meta['conclusion'],'artifactId':a['id'],'zipSha256':sha,'zipEntries':len(z.namelist()),'captureMembers':tar_members,'allExtractedMembersByteExact':True}
(root/f'{prefix}-verification.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt)
for f in out.rglob('vault-*-ui.txt'):
 text=f.read_text();print(f.name,text[-230:])

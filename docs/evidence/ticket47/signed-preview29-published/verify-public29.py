"""Run only after successful full gates and publication. APKs stay outside Git."""
from pathlib import Path
import hashlib,json,os,re,subprocess
from datetime import datetime,timezone
repo=Path('/home/david/projects/redsquare-android')
root=Path('/mnt/storage/redsquare-android-artifacts/group47-publication29')
root.mkdir(parents=True,exist_ok=True)
version,previous=29,28
expected=json.loads((repo/'docs/evidence/ticket47/signed-preview29-prepared/manifest.json').read_text())
cert='6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2'
oldsha='444f718e56fcb16a2166e6afe0fd08d6380b77df6204c48671bd20372634fed8'
base='https://davidengelen.nl/redsquare/'
responses=[]
def get(relative,name):
 p=root/name;url=base+relative
 result=subprocess.run(['curl','--proto','=https','--tlsv1.2','--fail','--silent','--show-error','--max-time','60','--output',str(p),'--write-out','%{http_code}',url],capture_output=True,text=True,check=True)
 assert result.stdout=='200',(relative,result.stdout)
 responses.append(dict(url=url,status=200,bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
 return p
page=get('','page.html');assert'latest.json'in page.read_text()
latest=json.loads(get('latest.json','latest.json').read_text())
release=json.loads(get(f'releases/{version}/manifest.json',f'release{version}.json').read_text())
assert latest==release
for key,value in expected.items():assert latest[key]==value,(key,latest[key],value)
assert latest['apkUrl']==base+f'releases/{version}/redsquare.apk'
assert latest['notes'].strip()and'\u2014'not in latest['notes']
apk=get(f'releases/{version}/redsquare.apk',f'redsquare{version}.apk')
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected['sha256']
old=json.loads(get(f'releases/{previous}/manifest.json',f'release{previous}.json').read_text())
assert old==json.loads((root/'immutable28-before.json').read_text())
assert old['versionCode']==previous and old['sha256']==oldsha and old['certificateSha256']==cert
old_apk=get(f'releases/{previous}/redsquare.apk',f'redsquare{previous}.apk')
assert hashlib.sha256(old_apk.read_bytes()).hexdigest()==oldsha
command=['docker','run','--rm','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={root},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
for label,tool,args in [('signature','apksigner',['verify','--print-certs']),('package','aapt',['dump','badging'])]:
 result=subprocess.check_output(command+[f'/opt/android-sdk/build-tools/35.0.0/{tool}']+args+[f'/artifacts/redsquare{version}.apk'],text=True)
 (root/f'{label}.txt').write_text(result)
signature=(root/'signature.txt').read_text();package=(root/'package.txt').read_text()
assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signature)[1]==cert
assert re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",package,re.M).groups()==('nl.davidengelen.redsquare',str(version),f'0.1.{version}')
assert "sdkVersion:'29'"in package
(root/'public-responses.json').write_text(json.dumps(responses,indent=2)+'\n')
(root/'result.json').write_text(json.dumps(dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(),status='PASS',versionCode=version,source=expected['commit'],sha256=expected['sha256'],certificateSha256=cert,immutablePreviousVersion=previous,immutablePreviousSha256=oldsha,physicalDeviceAcceptance=False,storeAcceptance=False),indent=2)+'\n')
print('PASS: direct public page/latest/release/APK29/hash/certificate/package/min29 and unchanged immutable28')

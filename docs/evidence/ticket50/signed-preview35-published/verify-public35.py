"""Run only after successful full gates and publication. APKs stay outside Git."""
from pathlib import Path
import hashlib,json,os,re,subprocess
import xml.etree.ElementTree as ET
from datetime import datetime,timezone
repo=Path('/home/david/projects/redsquare-android')
root=Path('/mnt/storage/redsquare-android-artifacts/availability50-publication35')
root.mkdir(parents=True,exist_ok=True)
version,previous=35,31
expected=json.loads((repo/'docs/evidence/ticket50/signed-preview35-prepared/manifest.json').read_text())
cert='6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2'
oldsha='3cc48595869335da555bb164afbb72537a65cecc03333cba099dd26dce6d4866'
base='https://davidengelen.nl/redsquare/'
responses=[]
def get(relative,name):
 p=root/name;url=base+relative
 result=subprocess.run(['curl','--proto','=https','--tlsv1.2','--fail','--silent','--show-error','--max-time','60','--max-filesize','104857600','--user-agent','redsquare-review-check/1','--output',str(p),'--write-out','%{http_code}',url],capture_output=True,text=True,check=True)
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
assert old==json.loads((root/'immutable31-before.json').read_text())
assert (root/'release31.json').read_bytes()==(root/'immutable31-before.json').read_bytes()
assert old['versionCode']==previous and old['sha256']==oldsha and old['certificateSha256']==cert
old_apk=get(f'releases/{previous}/redsquare.apk',f'redsquare{previous}.apk')
assert hashlib.sha256(old_apk.read_bytes()).hexdigest()==oldsha

performance_meta_path=get('performance/latest.json','performance-latest-after.json')
assert performance_meta_path.read_bytes()==(root/'performance-latest-before.json').read_bytes()
performance_meta=json.loads(performance_meta_path.read_text())
assert performance_meta['versionCode']==36 and performance_meta['sha256']=='e183dcc21c0c154fee578366534f49bd9d837c65e57c84e84c57ce8d1afb4680'
performance_immutable=get('performance/releases/36/manifest.json','performance36.json')
assert performance_immutable.read_bytes()==performance_meta_path.read_bytes()
performance_apk=get('performance/releases/36/redsquare.apk','performance36.apk')
assert hashlib.sha256(performance_apk.read_bytes()).hexdigest()==performance_meta['sha256']

command=['docker','run','--rm','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={root},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
for label,tool,args in [('signature','apksigner',['verify','--print-certs']),('package','aapt',['dump','badging'])]:
 result=subprocess.check_output(command+[f'/opt/android-sdk/build-tools/35.0.0/{tool}']+args+[f'/artifacts/redsquare{version}.apk'],text=True)
 (root/f'{label}.txt').write_text(result)
signature=(root/'signature.txt').read_text();package=(root/'package.txt').read_text()
assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',signature)[1]==cert
assert re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",package,re.M).groups()==('nl.davidengelen.redsquare',str(version),f'0.1.{version}')
assert "sdkVersion:'29'"in package
manifest=subprocess.check_output(command+['apkanalyzer','manifest','print',f'/artifacts/redsquare{version}.apk'],text=True)
(root/'android-manifest.xml').write_text(manifest)
tree=ET.fromstring(manifest);android='{http://schemas.android.com/apk/res/android}';application=tree.find('application')
assert tree.get('package')=='nl.davidengelen.redsquare' and tree.get(android+'versionCode')==str(version)
assert tree.find('uses-sdk').get(android+'minSdkVersion')=='29' and tree.find('uses-sdk').get(android+'targetSdkVersion')=='36'
assert application.get(android+'debuggable','false')=='false'
profileable=application.find('profileable');assert profileable is None or profileable.get(android+'shell','false')=='false'
(root/'public-responses.json').write_text(json.dumps(responses,indent=2)+'\n')
(root/'result.json').write_text(json.dumps(dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(),status='PASS',versionCode=version,source=expected['commit'],sha256=expected['sha256'],certificateSha256=cert,immutablePreviousVersion=previous,immutablePreviousSha256=oldsha,performance36LatestMetadataAndActualApkUnchanged=True,physicalDeviceAcceptance=False,storeAcceptance=False),indent=2)+'\n')
print('PASS: direct public page/latest/release/APK35/hash/certificate/package/min29 and unchanged immutable31/performance36')

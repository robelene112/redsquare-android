from pathlib import Path
import hashlib,json,os,re,subprocess,xml.etree.ElementTree as ET
from datetime import datetime,timezone
repo=Path('/home/david/projects/redsquare-android')
root=Path('/mnt/storage/redsquare-android-artifacts/availability50-publication36')
expected=json.loads((repo/'.scratch/performance-builds/36/manifest.json').read_text())
assert expected['versionCode']==36 and expected['commit']=='979fd7b21765acd9a5886dcde0b8ae988eb27451'
assert expected['sha256']=='e183dcc21c0c154fee578366534f49bd9d837c65e57c84e84c57ce8d1afb4680'
responses=[]
base='https://davidengelen.nl/redsquare/'
def get(relative,name):
    path=root/name
    response=subprocess.run(['curl','--proto','=https','--tlsv1.2','--fail','--silent','--show-error','--max-time','60','--max-filesize','104857600','--user-agent','redsquare-review-check/1','--output',str(path),'--write-out','%{http_code}',base+relative],capture_output=True,text=True,check=True)
    assert response.stdout=='200'
    responses.append(dict(url=base+relative,status=200,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    return path
page=get('performance/','page.html');assert 'latest.json' in page.read_text()
latest=json.loads(get('performance/latest.json','latest.json').read_text())
release=json.loads(get('performance/releases/36/manifest.json','release36.json').read_text());assert latest==release
for key,value in expected.items():assert latest[key]==value
assert latest['apkUrl']==base+'performance/releases/36/redsquare.apk'
apk=get('performance/releases/36/redsquare.apk','performance36.apk');assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected['sha256']
main_path=get('latest.json','main-latest-after.json');assert main_path.read_bytes()==(root/'main-latest-before.json').read_bytes()
main=json.loads(main_path.read_text());assert main['versionCode']==31 and main['sha256']=='3cc48595869335da555bb164afbb72537a65cecc03333cba099dd26dce6d4866'
main_release=get('releases/31/manifest.json','main31.json');assert main_release.read_bytes()==main_path.read_bytes()
main_apk=get('releases/31/redsquare.apk','main31.apk');assert hashlib.sha256(main_apk.read_bytes()).hexdigest()==main['sha256']
old=get('performance/releases/30/manifest.json','immutable30-after.json');assert old.read_bytes()==(root/'immutable30-before.json').read_bytes()
old_meta=json.loads(old.read_text());assert old_meta['sha256']=='e8ac3c05de58860962c630fbc35962d08a879fa10f2d856e53a59880b3167f17'
old_apk=get('performance/releases/30/redsquare.apk','performance30.apk');assert hashlib.sha256(old_apk.read_bytes()).hexdigest()==old_meta['sha256']
command=['docker','run','--rm','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={root},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
for label,args in [('signature',['/opt/android-sdk/build-tools/35.0.0/apksigner','verify','--print-certs','/artifacts/performance36.apk']),('package',['/opt/android-sdk/build-tools/35.0.0/aapt','dump','badging','/artifacts/performance36.apk']),('android-manifest',['apkanalyzer','manifest','print','/artifacts/performance36.apk'])]:
    (root/(label+('.xml' if label=='android-manifest' else '.txt'))).write_text(subprocess.check_output(command+args,text=True))
assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',(root/'signature.txt').read_text())[1]=='6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2'
manifest=ET.parse(root/'android-manifest.xml').getroot();n='{http://schemas.android.com/apk/res/android}';app=manifest.find('application')
assert manifest.get('package')=='nl.davidengelen.redsquare.performance' and manifest.get(n+'versionCode')=='36' and manifest.get(n+'versionName')=='0.1.36'
assert manifest.find('uses-sdk').get(n+'minSdkVersion')=='29' and manifest.find('uses-sdk').get(n+'targetSdkVersion')=='36'
assert app.get(n+'debuggable','false')=='false' and app.find('profileable').get(n+'shell')=='true'
(root/'public-responses.json').write_text(json.dumps(responses,indent=2)+'\n')
(root/'result.json').write_text(json.dumps(dict(status='PASS',checkedAtUtc=datetime.now(timezone.utc).isoformat(),performanceVersion=36,sha256=expected['sha256'],source=expected['commit'],mainLatestUnchanged=31,main31ActualApkUnchanged=True,immutablePerformance30MetadataAndActualApkUnchanged=True,physicalDeviceAcceptance=False,physicalTimingMeasured=False,installedIsolationPendingFullProof=True),indent=2)+'\n')
print('PASS: actual public performance36 page/metadata/APK/signature/manifest; main31 and immutable performance30 unchanged')

from pathlib import Path
import hashlib,json,os,re,subprocess,xml.etree.ElementTree as ET
from datetime import datetime,timezone
repo=Path('/home/david/projects/redsquare-android')
root=Path('/mnt/storage/redsquare-android-artifacts/performance48-publication30')
expected=json.loads((repo/'.scratch/performance-builds/30/manifest.json').read_text())
responses=[]
def get(url,name):
    path=root/name
    response=subprocess.check_output(['curl','--fail','--silent','--show-error','--max-time','60','--output',str(path),'--write-out','%{http_code}',url],text=True)
    assert response=='200'
    responses.append(dict(url=url,status=200,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    return path
base='https://davidengelen.nl/redsquare/'
page=get(base+'performance/','page.html')
latest=json.loads(get(base+'performance/latest.json','latest.json').read_text())
release=json.loads(get(base+'performance/releases/30/manifest.json','release30.json').read_text())
assert latest==release
for key,value in expected.items():assert latest[key]==value
assert latest['apkUrl']==base+'performance/releases/30/redsquare.apk'
apk=get(latest['apkUrl'],'performance30.apk')
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected['sha256']
main=json.loads(get(base+'latest.json','main-latest-after.json').read_text())
assert main==json.loads((root/'main-latest-before.json').read_text())
assert main['versionCode']==29 and main['sha256']=='aef4996a7e6a1f0c43dda6e664686c0ccf944b24b7abbe508b1c6545834514ef'
main_release=json.loads(get(base+'releases/29/manifest.json','main29.json').read_text())
assert main_release==main
main_apk=get(main['apkUrl'],'main29.apk')
assert hashlib.sha256(main_apk.read_bytes()).hexdigest()==main['sha256']
command=['docker','run','--rm','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={root},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
for label,args in [('signature',['/opt/android-sdk/build-tools/35.0.0/apksigner','verify','--print-certs','/artifacts/performance30.apk']),('package',['/opt/android-sdk/build-tools/35.0.0/aapt','dump','badging','/artifacts/performance30.apk']),('android-manifest',['apkanalyzer','manifest','print','/artifacts/performance30.apk'])]:
    (root/(label+('.xml'if label=='android-manifest'else'.txt'))).write_text(subprocess.check_output(command+args,text=True))
assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',(root/'signature.txt').read_text())[1]==expected['certificateSha256']
manifest=ET.parse(root/'android-manifest.xml').getroot();n='{http://schemas.android.com/apk/res/android}';app=manifest.find('application')
assert manifest.get('package')==expected['applicationId'] and manifest.get(n+'versionCode')=='30' and manifest.get(n+'versionName')=='0.1.30'
assert manifest.find('uses-sdk').get(n+'minSdkVersion')=='29' and manifest.find('uses-sdk').get(n+'targetSdkVersion')=='36'
assert app.get(n+'debuggable','false')=='false' and app.find('profileable').get(n+'shell')=='true'
(root/'public-responses.json').write_text(json.dumps(responses,indent=2)+'\n')
(root/'result.json').write_text(json.dumps(dict(status='PASS',checkedAtUtc=datetime.now(timezone.utc).isoformat(),performanceVersion=30,sha256=expected['sha256'],source=expected['commit'],mainLatestUnchanged=29,main29ActualApkUnchanged=True,physicalDeviceAcceptance=False,physicalTimingMeasured=False),indent=2)+'\n')
print('PASS: public performance30 page/latest/immutable/APK/identity and unchanged main29 latest/metadata/actual APK')

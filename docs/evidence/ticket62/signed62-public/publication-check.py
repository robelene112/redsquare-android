from pathlib import Path
import subprocess,sys,hashlib,json,os,re,xml.etree.ElementTree as ET
assert len(sys.argv)==5
family,version,stage,source=sys.argv[1:];assert (family,version)in [('ordinary','62')] and stage in ['before','after']
repo=Path('/home/david/projects/redsquare-android');root=Path('/mnt/storage/redsquare-android-artifacts')/f'conversation62-publication-{version}';root.mkdir(exist_ok=True)
prepared=Path('/mnt/storage/redsquare-android-artifacts/review-worktree/.scratch/review-builds/62')
expected=json.loads((prepared/'manifest.json').read_text());assert re.fullmatch('[0-9a-f]{40}',source) and expected['commit']==source;base='redsquare/' if family=='ordinary'else'redsquare/performance/';old='60';checks={}
def get(name,path):
 p=root/name
 code=subprocess.check_output(['curl','--silent','--show-error','--max-time','45','--output',str(p),'--write-out','%{http_code}','https://davidengelen.nl/'+path],text=True);assert code=='200',(path,code)
 checks[name]=dict(http=200,bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest());return p
if family=='ordinary':
 stable=[('ordinary60-immutable.json','redsquare/releases/60/manifest.json'),('ordinary60.apk','redsquare/releases/60/redsquare.apk'),('ordinary59-immutable.json','redsquare/releases/59/manifest.json'),('ordinary59.apk','redsquare/releases/59/redsquare.apk'),('ordinary58-immutable.json','redsquare/releases/58/manifest.json'),('ordinary58.apk','redsquare/releases/58/redsquare.apk'),('ordinary50-immutable.json','redsquare/releases/50/manifest.json'),('ordinary50.apk','redsquare/releases/50/redsquare.apk'),('ordinary49-immutable.json','redsquare/releases/49/manifest.json'),('ordinary49.apk','redsquare/releases/49/redsquare.apk'),('ordinary45-immutable.json','redsquare/releases/45/manifest.json'),('ordinary45.apk','redsquare/releases/45/redsquare.apk'),('performance43-page.html','redsquare/performance/'),('performance43-latest.json','redsquare/performance/latest.json'),('performance43-immutable.json','redsquare/performance/releases/43/manifest.json'),('performance43.apk','redsquare/performance/releases/43/redsquare.apk')]
else:
 stable=[('ordinary42-page.html','redsquare/'),('ordinary42-latest.json','redsquare/latest.json'),('ordinary42-immutable.json','redsquare/releases/42/manifest.json'),('ordinary42.apk','redsquare/releases/42/redsquare.apk'),('performance43-immutable.json','redsquare/performance/releases/43/manifest.json'),('performance43.apk','redsquare/performance/releases/43/redsquare.apk')]
for name,path in stable:
 p=get(stage+'-'+name,path)
 if stage=='after':assert p.read_bytes()==(root/('before-'+name)).read_bytes(),name
 if name.endswith('.apk'):
  n='60' if name.startswith('ordinary60') else '59' if name.startswith('ordinary59') else '58' if name.startswith('ordinary58') else '50' if name.startswith('ordinary50') else '49' if name.startswith('ordinary49') else '45' if name.startswith('ordinary45') else '43'
  f='performance-builds'if n=='43'else'review-builds'
  assert hashlib.sha256(p.read_bytes()).hexdigest()==json.loads(((Path('/mnt/storage/redsquare-android-artifacts/review-worktree/.scratch') if n in ['45','49','50','58','59','60'] else repo/'.scratch')/f/n/'manifest.json').read_text())['sha256']
if stage=='before':
 get('before-candidate-page.html',base)
 current=json.loads(get('before-candidate-latest.json',base+'latest.json').read_text());assert current['versionCode']==int(old)
else:
 get('candidate-page.html',base)
 latest=get('candidate-latest.json',base+'latest.json');immutable=get('candidate-immutable.json',base+f'releases/{version}/manifest.json');assert latest.read_bytes()==immutable.read_bytes()
 m=json.loads(latest.read_text())
 for key in ['versionCode','versionName','applicationId','certificateSha256','sha256','commit','variant']:assert m[key]==expected[key],key
 assert m['apkUrl']=='https://davidengelen.nl/'+base+f'releases/{version}/redsquare.apk'
 apk=get('candidate.apk',base+f'releases/{version}/redsquare.apk');assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected['sha256']
 command=['docker','run','--rm','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={root},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
 sig=subprocess.check_output(command+['/opt/android-sdk/build-tools/35.0.0/apksigner','verify','--print-certs','/artifacts/candidate.apk'],text=True);(root/'public-signature.txt').write_text(sig);assert re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',sig)[1]==expected['certificateSha256']
 badging=subprocess.check_output(command+['/opt/android-sdk/build-tools/35.0.0/aapt','dump','badging','/artifacts/candidate.apk'],text=True);(root/'public-package.txt').write_text(badging);assert f"package: name='{expected['applicationId']}' versionCode='{version}' versionName='0.1.{version}'"in badging
 manifest=subprocess.check_output(command+['apkanalyzer','manifest','print','/artifacts/candidate.apk'],text=True);(root/'public-android-manifest.xml').write_text(manifest);xml=ET.fromstring(manifest);ns='{http://schemas.android.com/apk/res/android}';a=xml.find('application');u=xml.find('uses-sdk');profile=a.find('profileable')
 assert a.get(ns+'debuggable','false')=='false'and a.get(ns+'allowBackup')=='false'and u.get(ns+'minSdkVersion')=='29'and u.get(ns+'targetSdkVersion')=='36'
 assert (profile is not None and profile.get(ns+'shell')=='true')==(family=='performance')
 checks.update(exactPreparedApk=True,establishedCertificate=True,oldImmutableReleaseUnchanged=True,otherChannelPageMetadataApkUnchanged=True,source=m['commit'],physicalDeviceAcceptance=False,physicalTimingAcceptance=False)
checks.update(result='PASS',version=int(version),stage=stage)
(root/(stage+'-check.json')).write_text(json.dumps(checks,indent=2)+'\n')
print('PASS',family,version,stage,'bookend. No physical/Play acceptance.')

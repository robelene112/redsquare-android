from pathlib import Path
import json,subprocess,hashlib,re,os,sys,xml.etree.ElementTree as ET
repo=Path('/home/david/projects/redsquare-android');version=int(sys.argv[1]);source=sys.argv[2];assert version==60 and re.fullmatch('[0-9a-f]{40}',source);artifact=Path('/mnt/storage/redsquare-android-artifacts/review-worktree/.scratch/review-builds')/str(version);meta=json.loads((artifact/'manifest.json').read_text());old=json.loads((artifact.parent/'59/manifest.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
assert meta['versionCode']==version and meta['versionName']==f'0.1.{version}' and meta['applicationId']=='nl.davidengelen.redsquare' and meta['variant']=='release' and meta['commit']==source
assert meta['certificateSha256']==old['certificateSha256']=='6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2'
assert sha((artifact/'redsquare.apk').read_bytes())==meta['sha256']
command=['docker','run','--rm','--cpus=1','--memory=512m','--user',f'{os.getuid()}:{os.getgid()}','--mount',f'type=bind,src={artifact.resolve()},dst=/artifacts,readonly','redsquare-android-build:agp8.10.1']
text=subprocess.check_output(command+['apkanalyzer','dex','code','--class','nl.davidengelen.redsquare.BuildConfig','/artifacts/redsquare.apk'],text=True)
flags={}
for field,expected in [('DEBUG',False),('PUSH_CONFIGURED',False),('REVIEW_BUILD',True)]:
 m=re.search(r'\.field[^\n]*\b'+field+r':Z\s*=\s*(true|false)',text);assert m,field;flags[field]=m[1]=='true';assert flags[field]==expected,field
for field in ['GIPHY_POSTS_API_KEY','GIPHY_CHAT_API_KEY']:
 m=re.search(r'\.field[^\n]*\b'+field+r':Ljava/lang/String;\s*=\s*("[^\n]*")',text);assert m,field;flags[field]=m[1]!='""';assert not flags[field],field
manifest=ET.fromstring((artifact/'android-manifest.xml').read_text());ns='{http://schemas.android.com/apk/res/android}';app=manifest.find('application');sdk=manifest.find('uses-sdk');assert manifest.get('package')==meta['applicationId'];assert app.get(ns+'debuggable','false')=='false' and app.get(ns+'allowBackup')=='false';assert app.find('profileable') is None or app.find('profileable').get(ns+'shell','false')=='false';assert sdk.get(ns+'minSdkVersion')=='29' and sdk.get(ns+'targetSdkVersion')=='36';assert 'com.android.talkback' not in (artifact/'android-manifest.xml').read_text()
result=dict(version=version,source=source,sha256=meta['sha256'],bytes=(artifact/'redsquare.apk').stat().st_size,certificateSha256=meta['certificateSha256'],actualDex=flags,debuggable=False,profileable=False,allowBackup=False,minSdk=29,targetSdk=36,testToolQueryInRelease=False,published=False,physicalDeviceAcceptance=False)
out=repo/f'docs/evidence/ticket61/signed{version}-prepared';out.mkdir(exist_ok=True)
for name in ['manifest.json','signature.txt','package.txt','android-manifest.xml']:(out/name).write_bytes((artifact/name).read_bytes())
(out/'apk-check.json').write_text(json.dumps(result,indent=2)+'\n');(out/'SHA256SUMS').write_text(''.join(f'{sha(p.read_bytes())}  {p.name}\n'for p in sorted(out.iterdir())if p.name!='SHA256SUMS'));print(json.dumps(result,indent=2))

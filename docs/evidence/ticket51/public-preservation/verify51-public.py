from pathlib import Path
import hashlib,json,subprocess
repo=Path('/home/david/projects/redsquare-android');out=Path('/mnt/storage/redsquare-android-artifacts/page-size51-public-preservation');out.mkdir(exist_ok=True)
checks=[('','main-page.html','docs/evidence/ticket50/signed-preview35-published/page.html'),('latest.json','main-latest.json','docs/evidence/ticket50/signed-preview35-published/latest.json'),('releases/35/manifest.json','main35.json','docs/evidence/ticket50/signed-preview35-published/release35.json'),('performance/','performance-page.html','docs/evidence/ticket50/performance36-published/page.html'),('performance/latest.json','performance-latest.json','docs/evidence/ticket50/signed-preview35-published/performance36.json'),('performance/releases/36/manifest.json','performance36.json','docs/evidence/ticket50/signed-preview35-published/performance36.json')]
records=[]
for relative,name,reference in checks:
 url='https://davidengelen.nl/redsquare/'+relative;p=out/name
 command=['curl','--proto','=https','--tlsv1.2','--fail','--silent','--show-error','--max-time','30','--max-filesize','1048576','--user-agent','redsquare-review-check/1','--output',str(p),'--write-out','%{http_code}',url]
 result=subprocess.run(command,capture_output=True,text=True,check=True);assert result.stdout=='200'
 assert p.read_bytes()==(repo/reference).read_bytes(),name
 records.append(dict(url=url,status=200,bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),independentAccepted50Reference=reference))
(out/'result.json').write_text(json.dumps(dict(status='PASS',main35AndPerformance36PagesAndLatestImmutableMetadataByteIdentical=True,actualApkBytesVerifiedSeparatelyInRequiredNativeRun=True,responses=records),indent=2)+'\n')
print('PASS: ordinary35 and performance36 pages/latest/immutable metadata remain byte-identical to accepted50')

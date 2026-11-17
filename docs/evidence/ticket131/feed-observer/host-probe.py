from pathlib import Path
import os,sys,json,subprocess,time,concurrent.futures
root=Path(__file__).parent/'feed-observer-probe';root.mkdir(exist_ok=True)
script=Path('/mnt/storage/redsquare-android-artifacts/redesign86/scripts/feed-device-check')
cases=sys.argv[1:] or ['transient','valid','null-stale','missing','unrelated','malformed','late','wrong-post']
fake=r'''#!/usr/bin/env python3
import os,sys,json,time
from pathlib import Path
p=Path(os.environ['FEED_PROBE_DIR']);case=os.environ['FEED_PROBE_CASE'];a=sys.argv[1:]
with (p/'commands.jsonl').open('a') as f:f.write(json.dumps(a)+'\n')
remote=p/'remote.xml'
xml='<hierarchy><node text="Native Red Square reference post"/><node text="Node unavailable"/></hierarchy>'
if a==['get-serialno']:print('emulator-5554')
elif a[:2]==['logcat','-c']:pass
elif a[:3]==['shell','am','force-stop']:pass
elif a[:3]==['shell','am','start']:print('Status: ok\nLaunchState: COLD\nComplete')
elif a[:3]==['shell','rm','-f']:remote.unlink(missing_ok=True)
elif a[:3]==['shell','uiautomator','dump']:
 n=int((p/'count').read_text()) if (p/'count').exists() else 0
 (p/'count').write_text(str(n+1))
 if case=='unrelated':print('error: device offline',file=sys.stderr);sys.exit(7)
 if case=='null-stale' or case=='transient' and n==0:print('ERROR: null root node returned by UiTestAutomationBridge.',file=sys.stderr)
 else:
  if case=='late':time.sleep(31)
  if case!='missing':remote.write_text('<broken' if case=='malformed' else xml.replace('Native Red Square reference post','Different content') if case=='wrong-post' else xml)
  print('UI hierchary dumped to: /sdcard/feed-check.xml')
elif a==['shell','cat','/sdcard/feed-check.xml']:
 if not remote.exists():print('cat: /sdcard/feed-check.xml: No such file or directory',file=sys.stderr);sys.exit(1)
 print(remote.read_text())
elif a[:3]==['exec-out','screencap','-p']:sys.stdout.buffer.write(b'FAKE-PROBE-NOT-NATIVE-PNG')
elif a[:2]==['logcat','-d']:pass
else:print('unexpected fake adb command',a,file=sys.stderr);sys.exit(9)
'''
def run(case):
 p=root/(case+'-'+str(time.time_ns()));p.mkdir();(p/'adb').write_text(fake);(p/'adb').chmod(0o755)
 if case=='null-stale':(p/'remote.xml').write_text('<hierarchy><node text="Native Red Square reference post"/><node text="Node unavailable"/></hierarchy>')
 env=dict(os.environ,PATH=str(p)+os.pathsep+os.environ['PATH'],FEED_PROBE_DIR=str(p),FEED_PROBE_CASE=case)
 start=time.monotonic();r=subprocess.run([sys.executable,str(script),'--evidence',str(p/'evidence')],env=env,capture_output=True,text=True,timeout=42);elapsed=time.monotonic()-start
 (p/'stdout.txt').write_text(r.stdout);(p/'stderr.txt').write_text(r.stderr)
 commands=[json.loads(x) for x in (p/'commands.jsonl').read_text().splitlines()]
 expected=case in ['transient','valid'];verdict=(r.returncode==0)==expected
 assert len([x for x in commands if x[:3]==['shell','am','start']])==1
 assert len([x for x in commands if x[:3]==['shell','am','force-stop']])==1
 if case in ['null-stale','late','wrong-post']:verdict=verdict and 29.5<=elapsed<34
 if case=='transient':verdict=verdict and int((p/'count').read_text())==2
 result=dict(case=case,result='PASS' if verdict else 'FAIL',exit=r.returncode,elapsedSeconds=round(elapsed,3),folder=str(p),qualification='Host fake-adb actual-script probe only; no native runtime or APK acceptance.')
 print(json.dumps(result),flush=True);return result
with concurrent.futures.ThreadPoolExecutor(max_workers=len(cases)) as pool:results=list(pool.map(run,cases))
(root/('results-'+str(time.time_ns())+'.json')).write_text(json.dumps(results,indent=2)+'\n')
sys.exit(0 if all(x['result']=='PASS' for x in results) else 1)

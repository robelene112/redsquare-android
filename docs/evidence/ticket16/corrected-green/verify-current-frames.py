from pathlib import Path
import subprocess, json, os, hashlib, base64
repo=Path.cwd()
packet=repo/'docs/evidence/ticket16/corrected-green'
for platform in ('android10','android17'):
 frames=packet/platform/'frames';out=packet/platform/'interop';out.mkdir(exist_ok=True)
 for backend in ('owner','official'):
  for kind in ('post','reply','quote'):
   label=f'{backend}-{kind}'
   bindings=[(repo/'build-support/references','/reference'),(Path('/opt/saito/node/node_modules'),'/opt/saito/node/node_modules'),(frames/(label+'-frame.base64'),'/frame.base64'),(frames/(label+'-source.txt'),'/source.txt'),(repo/'app/src/androidTest/assets/native-images-reference.json','/fixture.json')]
   command=['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL','--user',f'{os.getuid()}:{os.getgid()}','--cpus','1','--memory','512m']
   for src,dst in bindings:command+=['--mount',f'type=bind,src={src},dst={dst},readonly']
   command+=['--entrypoint','/usr/bin/env','mcr.microsoft.com/playwright:v1.63.0-noble','-i','/usr/bin/node','/reference/verify-gif-frame.cjs','/frame.base64',kind,'/fixture.json',backend,'/source.txt']
   result=subprocess.run(command,check=True,capture_output=True,text=True,timeout=60)
   proof=json.loads(result.stdout)
   assert proof['result']=='PASS'
   assert proof['frameSha256']==hashlib.sha256(base64.b64decode((frames/(label+'-frame.base64')).read_bytes())).hexdigest()
   (out/(label+'.json')).write_text(result.stdout)
   print(platform,label,'PASS',proof['frameSha256'],flush=True)

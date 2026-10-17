from pathlib import Path
import subprocess,json,os,sys,hashlib
repo=Path.cwd()
frames=Path(sys.argv[1]).resolve()
out=Path(sys.argv[2]).resolve();out.mkdir(parents=True,exist_ok=True)
image='mcr.microsoft.com/playwright:v1.63.0-noble'
baseline=json.loads((repo/'docs/evidence/ticket14/final-interop/web-render.json').read_text())
for source in baseline['observations']:
 for name,expected in source['hashes'].items():
  folder='quote-source' if name.endswith('_tweet.js') or name.endswith('_tweet.template.js') else 'quote-proof-source'
  assert hashlib.sha256((repo/'.scratch'/folder/name).read_bytes()).hexdigest()==expected,name
def mount(src,dst):return ['--mount',f'type=bind,src={src},dst={dst},readonly']
def run(name,args,bindings,browser=False):
 target=out/(name+'.json')
 if target.exists():
  print(name,'reuse previously completed output',flush=True);return target
 command=['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL','--user',f'{os.getuid()}:{os.getgid()}','--cpus','1','--memory','768m' if browser else '512m','--shm-size','256m','--tmpfs','/tmp:rw,mode=1777,size=256m']
 command+=mount(repo/'build-support/references','/reference')
 for src,dst in bindings:command+=mount(src,dst)
 command+=['--entrypoint','/usr/bin/env',image,'-i']
 if browser:command+=['PLAYWRIGHT_BROWSERS_PATH=/ms-playwright']
 command+=['/usr/bin/node']+args
 try: result=subprocess.run(command,check=True,capture_output=True,text=True,timeout=90)
 except subprocess.CalledProcessError as error:
  (out/(name+'-failure.txt')).write_text(error.stderr)
  print(error.stderr,flush=True);raise
 target=out/(name+'.json');target.write_text(result.stdout);parsed=json.loads(result.stdout)
 print(name,parsed.get('result','rendered'),parsed.get('frameSha256',''),flush=True)
 return target
for backend in ('owner','official'):
 for kind in ('post','reply','quote'):
  label=f'{backend}-{kind}'
  proof=run(label,['/reference/verify-gif-frame.cjs','/frame.base64',kind,'/fixture.json',backend,'/source.txt'],[(frames/(label+'-frame.base64'),'/frame.base64'),(frames/(label+'-source.txt'),'/source.txt'),(repo/'app/src/androidTest/assets/native-images-reference.json','/fixture.json'),(Path('/opt/saito/node/node_modules'),'/opt/saito/node/node_modules')])
  web=run(label+'-web',['/reference/gif-web-render.cjs','/proof.json','/sources','/children',kind],[(proof,'/proof.json'),(repo/'.scratch/quote-source','/sources'),(repo/'.scratch/quote-proof-source','/children')])
  parsed=json.loads(web.read_text())
  assert [item['hashes'] for item in parsed['observations']]==[item['hashes'] for item in baseline['observations']]
  run(label+'-browser',['/reference/gif-web-browser.cjs','/web.json','/owned.gif'],[(web,'/web.json'),(repo/'app/src/androidTest/assets/owned-giphy-animation.gif','/owned.gif'),(Path('/home/david/.cache/snake-browser-tools/node_modules'),'/browser-tools/node_modules')],True)

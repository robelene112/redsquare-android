from pathlib import Path
from collections import Counter
import sys,json,re,hashlib,shutil,tarfile,struct,zlib,xml.etree.ElementTree as ET,importlib.util
repo=Path('/home/david/projects/redsquare-android');sdk=sys.argv[1];source=Path(sys.argv[2]);target=repo/'docs/evidence/ticket59'/sys.argv[3];target.mkdir(parents=True,exist_ok=True)
sp=importlib.util.spec_from_file_location('ids',repo/'docs/evidence/ticket48/full-regression/partition-plan/test-identities.py');ids=importlib.util.module_from_spec(sp);sp.loader.exec_module(ids)
assert len(sys.argv)==5,'Require SDK, complete raw artifact directory, target label and exact runID'
metadata=json.loads(__import__('subprocess').check_output(['gh','run','view',sys.argv[4],'--repo','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,createdAt,url,jobs'],text=True))
configured=json.loads((repo/'docs/evidence/ticket59/full-identity-reference.json').read_text())
assert hashlib.sha256(Path(__file__).read_bytes()).hexdigest()==configured['collectorSha256']
frozen=json.loads(__import__('subprocess').check_output(['git','-C',str(repo),'show','caad1528391951187c6d6bb91373c32a48f5e37e:docs/evidence/ticket59/full-identity-reference.json'],text=True))
assert configured['references']==frozen['references']and configured['recordedBeforeFullExecution']is True
assert metadata['status']=='completed'and metadata['conclusion']=='success',metadata['conclusion']
executing_source=configured['executingSources']['android'+sdk] if 'executingSources'in configured else configured['executingSource']
assert metadata['headSha']==executing_source
assert int(sys.argv[4])==configured['executingRuns']['android'+sdk]
assert len(metadata['jobs']) == 3 and all(j['conclusion']=='success'for j in metadata['jobs'])
assert {j['name'] for j in metadata['jobs']} == {'native-proof (chat)', 'native-proof (social)', 'native-proof (wallet)'}
from datetime import datetime
freeze_date=__import__('subprocess').check_output(['git','-C',str(repo),'show','-s','--format=%cI','caad1528391951187c6d6bb91373c32a48f5e37e'],text=True).strip()
assert datetime.fromisoformat(freeze_date) < datetime.fromisoformat(metadata['createdAt'].replace('Z','+00:00'))
(target/'run.json').write_text(json.dumps(metadata,indent=2)+'\n')
sha=lambda b:hashlib.sha256(b).hexdigest()
def png(data):
 assert data[:8]==b'\x89PNG\r\n\x1a\n','PNG signature';i=8;raw=b'';header=None
 while i<len(data):
  n=struct.unpack('>I',data[i:i+4])[0];tag=data[i+4:i+8];b=data[i+8:i+8+n];assert zlib.crc32(tag+b)&0xffffffff==struct.unpack('>I',data[i+8+n:i+12+n])[0],'chunk CRC'
  if tag==b'IHDR':header=struct.unpack('>IIBBBBB',b)
  if tag==b'IDAT':raw+=b
  i+=n+12
 w,h,depth,color,comp,filt,inter=header;assert depth==8 and inter==0
 channels={0:1,2:3,4:2,6:4}[color];stride=w*channels+1;pixels=zlib.decompress(raw);assert len(pixels)==stride*h
 assert all(pixels[i*stride]in range(5)for i in range(h))
def validate_status_evidence(target, configured):
 import wave
 chat=target/'chat'
 originals=chat/'status-originals'
 for kind, count in [('recovery', 2), ('motion', 2), ('talkback', 1)]:
  text=(chat/f'status-{kind}.txt').read_text()
  expected_ok=f'OK ({count} tests)' if count != 1 else 'OK (1 test)'
  assert expected_ok in text and text.count('INSTRUMENTATION_CODE: -1') == 1
  assert (chat/f'status-{kind}-exit.txt').read_text().strip() == '0'
 assert (chat/'talkback-original-settings.txt').read_bytes() == (chat/'talkback-restored-settings.txt').read_bytes()
 assert sha((chat/'talkback-source.patch').read_bytes()) == configured['toolSource']['toolPatchSha256']
 tool_hashes={line.split('  ',1)[1]:line.split('  ',1)[0] for line in (chat/'talkback-tool-SHA256SUMS').read_text().splitlines()}
 assert set(tool_hashes) == {'talkback.apk','espeak.apk','source.txt','engine-source.txt','source.patch'}
 assert tool_hashes['source.patch'] == configured['toolSource']['toolPatchSha256']
 assert tool_hashes['espeak.apk'] == configured['toolSource']['espeakSha256']
 for filename, copied in [('source.txt','talkback-source.txt'),('engine-source.txt','talkback-engine-source.txt')]:
  assert sha((chat/copied).read_bytes()) == tool_hashes[filename]
 assert configured['toolSource']['talkback'] in (chat/'talkback-source.txt').read_text()
 talkback=originals/'status-talkback-evidence'
 engine=json.loads((talkback/'engine.json').read_text())
 assert engine['initialization'] == 0 and engine['defaultEngine'] == 'com.reecedunn.espeak'
 assert engine['englishAvailability'] >= 0 and 'com.reecedunn.espeak' in engine['installedEngines']
 with wave.open(str(talkback/'english-engine.wav'),'rb') as audio:
  assert audio.getnchannels() >= 1 and audio.getnframes() > 500 and audio.getframerate() > 0
  samples=audio.readframes(audio.getnframes())
  assert any(samples), 'Real engine audio must contain a signal'
 observation=json.loads((talkback/'result.json').read_text())
 service='com.google.android.marvin.talkback.TalkBackService'
 label=observation['serviceLabel'];assert label
 for filename in ['accessibility-before.txt','accessibility-after.txt']:
  dump=(talkback/filename).read_text()
  bound=re.search(r'Bound services:\{(.*?)\}\s*Enabled services:',dump,re.S)
  assert bound and (service in bound.group(1) or f'Service[label={label},' in bound.group(1)), filename
  assert f'com.android.talkback/{service}' in dump.split('Enabled services:',1)[1]
 assert observation['focus'] == 'Reference post 20' and observation['physicalAudibility'] is False
 pid=str(observation['serviceProcess']);assert pid.isdigit() and int(pid) > 0
 log=(talkback/'speech-transcript.txt').read_text()
 for summary, field in [('Node unavailable','outageEngineCompletions'),('Back online','recoveryEngineCompletions')]:
  requests=[line for line in log.splitlines() if 'Speaking fragment text=' in line and summary in line]
  assert len(requests)==1, (summary, requests)
  match=re.search(r'utteranceId=([^,\s]+)', requests[0]);assert match
  utterance=match.group(1);assert observation[field]==utterance
  assert re.search(r'\(\s*'+re.escape(pid)+r'\)',requests[0]), 'Request must come from bound service PID'
  events={}
  for line in log.splitlines():
   event=re.search(r'REDSQUARE_ENGINE_(START|AUDIO|DONE|ERROR|STOP) utteranceId=([^\s]+)(?: bytes=([0-9]+))?',line)
   if event and event.group(2)==utterance:
    assert re.search(r'\(\s*'+re.escape(pid)+r'\)',line)
    events.setdefault(event.group(1),[]).append(int(event.group(3) or 0))
  assert {'START','AUDIO','DONE'} <= set(events) and not {'ERROR','STOP'} & set(events)
  assert any(size > 0 for size in events['AUDIO'])
 motion=originals/'status-motion-evidence'
 for scale,font,theme in [(1,2.0,'DARK'),(0,1.3,'LIGHT')]:
  name=f'status-motion-{scale}-font{round(font*100)}-{theme.lower()}'
  data=json.loads((motion/f'{name}.json').read_text())
  assert data['windowMotion']['isRecomposer'] is True and data['windowMotion']['scaleFactor']==scale
  assert data['windowMotion']['setting']==scale and abs(data['fontScale']-font)<0.01 and data['theme']==theme
  frames=data['decodedOriginalFrames'];assert data['frameCount']==len(frames) and len(frames)>=2
  assert [frame['index'] for frame in frames]==list(range(len(frames)))
  times=[frame['presentationTimeUs'] for frame in frames];assert all(b>a for a,b in zip(times,times[1:]))
  assert len({sha((motion/frame['file']).read_bytes()) for frame in frames})>1
  assert (motion/f'{name}.mp4').stat().st_size>1000
  assert 'REDSQUARE_RECORDER_EXIT=0' in (motion/f'{name}-recorder.txt').read_text()
  for state in ['outage','details','recovered','after-removal']:
   assert (motion/f'{name}-{state}.png').stat().st_size>100
  search=data['searchTransition'];frames=search['decodedOriginalFrames']
  assert search['frameCount']==len(frames) and len(frames)>=2
  assert [frame['index'] for frame in frames]==list(range(len(frames)))
  times=[frame['presentationTimeUs'] for frame in frames];assert all(b>a for a,b in zip(times,times[1:]))
  assert len({sha((motion/frame['file']).read_bytes()) for frame in frames})>1
  assert (motion/f'{name}-search.mp4').stat().st_size>1000
  assert 'REDSQUARE_RECORDER_EXIT=0' in (motion/f'{name}-search-recorder.txt').read_text()
  for state in ['before','working','after']:
   for suffix in ['png','xml']:
    assert (motion/f'{name}-search-{state}.{suffix}').stat().st_size>100
 for name in ['reading-before-outage','outage-details','reading-during-outage','reading-recovered',
              'reading-after-recovery','details-remain-open-after-recovery','details-closed-after-recovery']:
  for suffix in ['png','xml']:
   assert (originals/'status-recovery-evidence'/f'{name}.{suffix}').stat().st_size>100
 for name in ['scrolled-feed-before-chat','scrolled-feed-after-chat','scrolled-feed-after-recreation',
              'scrolled-outage-before-recreation','scrolled-outage-after-recreation']:
  for suffix in ['png','xml']:
   assert (originals/'app-frame-reading-evidence'/f'{name}.{suffix}').stat().st_size>100

actual=Counter();parts={};archive_inventory={};direct_inventory={}
for part in ['chat','social','wallet']:
 artifact=source/('android-native-proof-'+part);root=artifact/f'.scratch/device-evidence/api-{"37.0"if sdk=="17"else"29"}';dest=target/part;dest.mkdir(exist_ok=True)
 assert (root/'partition.txt').read_text().strip()==part
 assert (root/'partition-complete.txt').read_text().strip()==part
 if part=='chat':shutil.copyfile(root/'talkback-tool-SHA256SUMS',dest/'talkback-tool-SHA256SUMS')
 current=ids.identities(root);actual.update(current)
 info={'identities':current.total(),'normalCompletions':0,'deliberateInterruptions':0,'continuityRecords':0,'nonzeroExits':[],'failureStatuses':[],'png':0,'xml':0,'visualErrors':[],'files':0}
 for p in sorted(root.rglob('*.txt')):
  rel=p.relative_to(root);out=dest/rel;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out);info['files']+=1
  text=p.read_text(errors='replace')
  if p.name!='profile-edit-ui.txt':
   info['normalCompletions']+=text.count('INSTRUMENTATION_CODE: -1')
   info['deliberateInterruptions']+=text.count('INSTRUMENTATION_RESULT: shortMsg=Process crashed.')
   info['failureStatuses'] += [{'path':str(rel),'code':c}for c in re.findall(r'^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$',text,re.M)if c in ['-1','-2','-3']]
  if p.name=='continuity.txt':assert text.startswith('PASS:'),str(p);info['continuityRecords']+=1
  if p.name.endswith('exit.txt')and text.strip()!='0':info['nonzeroExits'].append(str(rel))
 for pattern in ['*.json','*.properties','*.log','*.patch']:
  for p in sorted(root.rglob(pattern)):
   if p.name=='emulator.log':continue
   out=dest/p.relative_to(root);out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
 for p in sorted(root.rglob('*.tar')):
  key=part+'/'+str(p.relative_to(root));data=p.read_bytes();record={'sha256':sha(data),'bytes':len(data),'members':[]};archive_inventory[key]=record
  try:t=tarfile.open(p)
  except tarfile.ReadError as e:record['unavailable']=data.decode(errors='replace');info['visualErrors'].append({'path':key,'error':'unavailable archive'});continue
  with t:
   for member in t.getmembers():
    if not member.isfile():continue
    b=t.extractfile(member).read();entry={'name':member.name,'bytes':len(b),'sha256':sha(b)};record['members'].append(entry)
    if p.name in {'status-recovery-evidence.tar', 'status-motion-evidence.tar', 'status-talkback-evidence.tar', 'app-frame-reading-evidence.tar'}:
     name=Path(member.name)
     assert not name.is_absolute() and '..' not in name.parts
     out=dest/'status-originals'/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(b)
    try:
     if member.name.endswith('.png'):png(b);info['png']+=1
     if member.name.endswith('.xml'):ET.fromstring(b);info['xml']+=1
    except Exception as e:entry['validationError']=str(e);info['visualErrors'].append({'path':key+'/'+member.name,'error':str(e),'prefix':b[:120].decode(errors='replace')})
 for p in sorted(list(root.rglob('*.png'))+list(root.rglob('*.xml'))):
  b=p.read_bytes();rel=p.relative_to(root);key=part+'/'+str(rel);entry={'sha256':sha(b),'bytes':len(b)};direct_inventory[key]=entry
  out=dest/rel;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
  try:
   if p.suffix=='.png':png(b);info['png']+=1
   else:ET.fromstring(b);info['xml']+=1
  except Exception as e:entry['validationError']=str(e);info['visualErrors'].append({'path':key,'error':str(e),'prefix':b[:120].decode(errors='replace')})
 for p in artifact.glob('core/build/test-results/test/TEST-*.xml'):
  out=dest/'core'/p.name;out.parent.mkdir(exist_ok=True);shutil.copyfile(p,out);suite=ET.fromstring(p.read_bytes());assert int(suite.attrib.get('failures',0))==0 and int(suite.attrib.get('errors',0))==0
 # The artifact may supply only the HTML report. Retain it without inventing test counts.
 for p in artifact.glob('core/build/reports/tests/test/index.html'):
  out=dest/'core-summary.html';shutil.copyfile(p,out)
 for p in artifact.glob('app/build/reports/lint-results-*.html'):
  shutil.copyfile(p,dest/p.name)
 assert not info['nonzeroExits'] and not info['failureStatuses'],info
 parts[part]=info
reference=json.loads((repo/'docs/evidence/ticket59/full-identity-reference.json').read_text())
selected=next(v for v in reference['references']if v['sdk']==sdk)
previous=json.loads((repo/selected['path']).read_text())
assert hashlib.sha256((repo/selected['path']).read_bytes()).hexdigest()==selected['sha256']
expected=Counter({(r['path'],r['testClass'],r['test']):r['occurrences']for r in previous})
expected.update({(r['path'],r['testClass'],r['test']):r['occurrences']for r in selected['additional']})
comparison={'expected':expected.total(),'actual':actual.total(),'missing':ids.rows(expected-actual),'extra':ids.rows(actual-expected)}
assert expected==actual,comparison
# Reject newly corrupt/missing visual evidence rather than treating it as an
# accepted optional observer gap. Only exact retained53 observations may recur.
baseline=json.loads((repo/f'docs/evidence/ticket53/full{sdk}/summary.json').read_text())
visual_key=lambda e: (e['path'],e['error'],e.get('prefix'))
known={visual_key(e) for part in baseline['partitions'].values() for e in part['visualErrors']}
if sdk == '10':
 # Exact old observer paths move with unchanged owned Chat/Wallet partition goals.
 path_map = {
  'all/chat-image-history-evidence.tar': 'chat/chat-image-history-evidence.tar',
  'all/live-official/wallet-first-failure.png': 'wallet/live-official/wallet-first-failure.png',
  'all/live-official/wallet-first-failure.xml': 'wallet/live-official/wallet-first-failure.xml',
  'all/live-owner/wallet-first-failure.png': 'wallet/live-owner/wallet-first-failure.png',
  'all/live-owner/wallet-first-failure.xml': 'wallet/live-owner/wallet-first-failure.xml',
 }
 assert {p for p, error, prefix in known} == set(path_map)
 known = {(path_map[p], error, prefix) for p, error, prefix in known}
unexpected=[e for part in parts.values() for e in part['visualErrors'] if visual_key(e) not in known]
assert not unexpected, {'unexpectedVisualEvidenceErrors':unexpected}
summary={'source': executing_source,'tallyBasis':'Complete primary partitioned execution, including every public read and additive status journey. No supplemental or tail assembly.', 'sdk':sdk,'partitions':parts,'identityComparison':comparison,'totals':{k:sum(p[k]for p in parts.values())for k in ['identities','normalCompletions','deliberateInterruptions','continuityRecords','png','xml']},'rawArtifactDirectory':str(source),'limits':'Original external tar member hashes/structural validation are observations; selected originals are inspected separately. No physical/configured-provider/store acceptance.'}
(target/'direct-original-inventory.json').write_text(json.dumps(direct_inventory,indent=2)+'\n');(target/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(target/'archive-inventory.json').write_text(json.dumps(archive_inventory,indent=2)+'\n');(target/'identities.json').write_text(json.dumps(ids.rows(actual),indent=2)+'\n')
assert summary['totals']['normalCompletions']==selected['expectedNormalCompletions'],summary['totals']
assert summary['totals']['deliberateInterruptions']==selected['expectedDeliberateInterruptions'],summary['totals']
assert summary['totals']['continuityRecords']==selected['expectedContinuityRecords'],summary['totals']
assert len(archive_inventory)==selected['expectedArchives'], len(archive_inventory)
# Reject every unanticipated assumption; the accepted API29 Profile case remains explicit.
assumptions=[]
for part in parts:
 for p in (target/part).rglob('*.txt'):
  if p.name=='profile-edit-ui.txt':continue
  fields={}
  for line in p.read_text(errors='replace').splitlines():
   m=re.match(r'^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$',line)
   if m:fields[m[1]]=m[2]
   if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
    if line.strip()=='INSTRUMENTATION_STATUS_CODE: -4':assumptions.append(dict(path=str(p.relative_to(target/part)),testClass=fields.get('class'),test=fields.get('test')))
    fields={}
allowed=[]
if sdk=='10':
 fields={}
 for line in (repo/'docs/evidence/ticket52/full10/all/ProfilePublishingPreviewTest-ui.txt').read_text().splitlines():
  m=re.match(r'^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$',line)
  if m:fields[m[1]]=m[2]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   if line.strip()=='INSTRUMENTATION_STATUS_CODE: -4':allowed.append(dict(path='ProfilePublishingPreviewTest-ui.txt',testClass=fields.get('class'),test=fields.get('test')))
   fields={}
assert assumptions==allowed,(assumptions,allowed)
summary['passed']=actual.total()-len(assumptions);summary['assumptions']=len(assumptions);summary['assumptionIdentities']=assumptions
(target/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
validate_status_evidence(target, configured)
(target/'SHA256SUMS').write_text(''.join(f'{sha(p.read_bytes())}  {p.relative_to(target)}\n'for p in sorted(target.rglob('*'))if p.is_file()and p.name!='SHA256SUMS'))
print('PASS/assumptions:',summary['passed'],summary['assumptions'])

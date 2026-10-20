from pathlib import Path
from collections import Counter
import sys,json,re,hashlib,shutil,tarfile,struct,zlib,xml.etree.ElementTree as ET,importlib.util
repo=Path('/home/david/projects/redsquare-android');sdk=sys.argv[1];source=Path(sys.argv[2]);target=repo/'docs/evidence/ticket57'/sys.argv[3];target.mkdir(parents=True,exist_ok=True)
sp=importlib.util.spec_from_file_location('ids',repo/'docs/evidence/ticket48/full-regression/partition-plan/test-identities.py');ids=importlib.util.module_from_spec(sp);sp.loader.exec_module(ids)
assert len(sys.argv)==5,'Require SDK, complete raw artifact directory, target label and exact runID'
metadata=json.loads(__import__('subprocess').check_output(['gh','run','view',sys.argv[4],'--repo','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,url,jobs'],text=True))
configured=json.loads((repo/'docs/evidence/ticket57/full-identity-reference.json').read_text())
assert hashlib.sha256(Path(__file__).read_bytes()).hexdigest()==configured['collectorSha256']
frozen=json.loads(__import__('subprocess').check_output(['git','-C',str(repo),'show','285d84bac:docs/evidence/ticket57/full-identity-reference.json'],text=True))
assert configured['references']==frozen['references']and configured['recordedBeforeFullExecution']is True
assert metadata['status']=='completed'and metadata['conclusion']=='success',metadata['conclusion']
executing_source=configured['executingSources']['android'+sdk] if 'executingSources'in configured else configured['executingSource']
assert metadata['headSha']==executing_source
assert int(sys.argv[4])==configured['executingRuns']['android'+sdk]
assert all(j['conclusion']=='success'for j in metadata['jobs'])
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
actual=Counter();parts={};archive_inventory={};direct_inventory={}
for part in ['chat','social','wallet']:
 artifact=source/('android-native-proof-'+part);root=artifact/f'.scratch/device-evidence/api-{"37.0"if sdk=="17"else"29"}';dest=target/part;dest.mkdir(exist_ok=True)
 assert (root/'partition.txt').read_text().strip()==part
 assert (root/'partition-complete.txt').read_text().strip()==part
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
 for pattern in ['*.json','*.properties','*.log']:
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
reference=json.loads((repo/'docs/evidence/ticket57/full-identity-reference.json').read_text())
selected=next(v for v in reference['references']if v['sdk']==sdk)
previous=json.loads((repo/selected['path']).read_text())
assert hashlib.sha256((repo/selected['path']).read_bytes()).hexdigest()==selected['sha256']
expected=Counter({(r['path'],r['testClass'],r['test']):r['occurrences']for r in previous})
expected.update({(r['path'],r['testClass'],r['test']):r['occurrences']for r in selected['additional']})
# Complete the three independently executed opt-in goals without changing the frozen catalog.
public_config=configured['separatelyExecutedPublicReads']
for binding in public_config['executionInputEquality']:
 for ref in [executing_source,public_config['source']]:
  observed=__import__('subprocess').check_output(['git','-C',str(repo),'rev-parse',ref+':'+binding['path']],text=True).strip()
  assert observed==binding['gitObject'],('Execution input mismatch',ref,binding['path'])
public_paths={'live-owner-feed.txt','live-official-feed.txt','public-chat.txt'}
public_expected=Counter({k:n for k,n in expected.items()if k[0]in public_paths})
assert public_expected.total()==3 and expected-actual==public_expected and not actual-expected,{
 'mainMissing':ids.rows(expected-actual),'mainExtra':ids.rows(actual-expected)}
public_executions=[]
for kind,part,wanted in [('feed','wallet',{'live-owner-feed.txt','live-official-feed.txt'}),('chat','chat',{'public-chat.txt'})]:
 setting=public_config['android'+sdk][kind];run=setting['run']
 run_meta=json.loads(__import__('subprocess').check_output(['gh','run','view',str(run),'--repo','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,url,jobs'],text=True))
 assert run_meta['status']=='completed'and run_meta['conclusion']=='success'and run_meta['headSha']==public_config['source']
 assert len(run_meta['jobs'])==1 and all(j['conclusion']=='success'for j in run_meta['jobs'])
 raw=Path(setting['artifactDirectory'])/'android-native-proof'/f'.scratch/device-evidence/api-{"37.0"if sdk=="17"else"29"}'
 assert not (raw/'partition.txt').exists(),'Supplement must be its actual focused scope'
 required=set(wanted)
 if kind=='feed':
  for backend in ['owner','official']:
   required.add(f'fresh-live-{backend}.txt')
   required.update(f'live-{backend}/{name}'for name in ['screen.png','feed.png','balance.png','balance.xml','funding.png','funding.xml','result.txt','public-wallet.txt','public-search.txt','transport-status.txt'])
  required.update(['feed-ui.txt','feed-cold-launch/continuity.txt','backend-ui-evidence.tar','feed-readiness-evidence.tar'])
 else:required.add('public-chat/readiness.json')
 assert all((raw/name).is_file()for name in required),{'missingRequiredSupplementFiles':sorted(name for name in required if not(raw/name).is_file())}
 image_bindings={}
 primary_root=source/('android-native-proof-'+part)/f'.scratch/device-evidence/api-{"37.0"if sdk=="17"else"29"}'
 for name in ['system-image.txt','system-image.properties','page-size.txt','requested-page-size.txt','device-abi.txt','system-build.txt']:
  data=(raw/name).read_bytes();assert data==(primary_root/name).read_bytes(),('Supplement image/config mismatch',kind,name)
  image_bindings[name]=sha(data)

 found=ids.identities(raw)
 goals=Counter({k:n for k,n in found.items()if k[0]in wanted})
 duplicates=Counter({k:n for k,n in expected.items()if kind=='feed'and k[0]=='feed-ui.txt'})
 assert found==goals+duplicates and goals==Counter({k:n for k,n in public_expected.items()if k[0]in wanted}),ids.rows(found)
 supplemental=target/'supplemental'/kind;supplemental.mkdir(parents=True,exist_ok=True)
 (supplemental/'run.json').write_text(json.dumps(run_meta,indent=2)+'\n')
 originals={};incidental_archives={};selected_originals=[]
 for p in sorted(raw.rglob('*')):
  if not p.is_file()or p.name=='emulator.log':continue
  rel=p.relative_to(raw);b=p.read_bytes();key=rel.as_posix()
  originals[key]={'source':str(p),'bytes':len(b),'sha256':sha(b)}
  owned=key in wanted or (kind=='feed'and(key.startswith(('live-owner/','live-official/'))or key in {'fresh-live-owner.txt','fresh-live-official.txt'}))or(kind=='chat'and key.startswith('public-chat/'))
  if p.suffix=='.tar':
   members=[]
   with tarfile.open(p)as tar:
    for member in tar.getmembers():
     if not member.isfile():continue
     data=tar.extractfile(member).read()
     if member.name.endswith('.png'):png(data)
     if member.name.endswith('.xml'):ET.fromstring(data)
     members.append({'name':member.name,'bytes':len(data),'sha256':sha(data)})
   incidental_archives[key]={'sha256':sha(b),'members':members}
   continue
  if p.suffix=='.txt':
   content=b.decode(errors='replace')
   assert not re.findall(r'^INSTRUMENTATION_STATUS_CODE:\s*-(?:1|2|3|4)\s*$',content,re.M),key
   assert 'FAILURES!!!'not in content,key
   if p.name.endswith('exit.txt'):assert content.strip()=='0',key
   if p.name=='continuity.txt':assert content.startswith('PASS:'),key
  if not owned and p.suffix=='.png':png(b)
  if not owned and p.suffix=='.xml':ET.fromstring(b)
  out=(target/part if owned else supplemental/'incidental')/rel
  if owned:
   primary=source/('android-native-proof-'+part)/f'.scratch/device-evidence/api-{"37.0"if sdk=="17"else"29"}'/rel
   assert not primary.exists(),('Supplement cannot overwrite primary evidence',str(primary))
  if out.exists():assert out.read_bytes()==b,('Previously collected evidence differs',str(out))
  out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(b)
  if not owned:continue
  selected_originals.append({'path':key,'source':str(p),'sha256':sha(b)})
  info=parts[part];info['files']+=1
  if p.suffix in ['.png','.xml']:
   direct_key=part+'/'+key;entry={'sha256':sha(b),'bytes':len(b),'supplementRun':run,'source':str(p)}
   direct_inventory[direct_key]=entry
   try:
    if p.suffix=='.png':png(b);info['png']+=1
    else:ET.fromstring(b);info['xml']+=1
   except Exception as error:
    entry['validationError']=str(error);info['visualErrors'].append({'path':direct_key,'error':str(error),'prefix':b[:120].decode(errors='replace')})
 for name in wanted:
  text=(raw/name).read_text();assert 'OK (1 test)'in text and text.count('INSTRUMENTATION_CODE: -1')==1,name
  parts[part]['normalCompletions']+=1
 parts[part]['identities']+=goals.total();actual.update(goals)
 if kind=='chat':
  readiness=json.loads((raw/'public-chat/readiness.json').read_text())
  assert readiness['native']is True and readiness['readOnly']is True and readiness['completed']is True
  assert len(readiness['backends'])==2 and {r['backend']for r in readiness['backends']}=={'owner','official'}
  for row in readiness['backends']:
   assert row['authenticated']is True and row['archiveResponded']is True and row['rows']==0
   assert {'archive','relay','chat'}<=set(row['services'])
 else:
  for backend in ['owner','official']:
   assert(raw/f'fresh-live-{backend}.txt').read_text().strip()=='Success'
   assert(raw/f'live-{backend}/result.txt').read_text().startswith('PASS:')
   assert(raw/f'live-{backend}/public-wallet.txt').read_text().startswith('PASS backend='+backend)
   lines=(raw/f'live-{backend}/public-search.txt').read_text().splitlines()
   assert len(lines)==2 and all(line.startswith('PASS backend='+backend)for line in lines)
 incidental_normals=sum(p.read_text(errors='replace').count('INSTRUMENTATION_CODE: -1')for p in raw.rglob('*.txt'))-len(wanted)
 incidental_continuity=sum(p.name=='continuity.txt'for p in raw.rglob('*.txt'))
 assert incidental_normals==(1 if kind=='feed'else 0)and incidental_continuity==(1 if kind=='feed'else 0)
 assert set(incidental_archives)==({'backend-ui-evidence.tar','feed-readiness-evidence.tar'}if kind=='feed'else set())
 (supplemental/'original-file-bindings.json').write_text(json.dumps(originals,indent=2)+'\n')
 (supplemental/'incidental-archive-inventory.json').write_text(json.dumps(incidental_archives,indent=2)+'\n')
 record={'run':run,'source':public_config['source'],'scope':kind,'ownedPartition':part,'canonicalGoals':ids.rows(goals),'selectedOriginalFiles':selected_originals,'requiredFiles':sorted(required),'imageConfigBindings':image_bindings,'incidentalCompletedIdentities':ids.rows(duplicates),'incidentalContinuityRecords':incidental_continuity,'incidentalNormalCompletions':incidental_normals,'limits':'All incidental executions must pass and are retained separately; duplicate canonical feed cases and their cold-continuity do not inflate the frozen complete-goal catalog/totals. Initial full dispatch did not include these opt-in public reads.'}
 (supplemental/'assembly.json').write_text(json.dumps(record,indent=2)+'\n');public_executions.append(record)

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
summary={'source': executing_source,'separatelyExecutedPublicReads':public_executions,'tallyBasis':'Canonical complete catalog and unique goal commands; actual passed duplicate feed executions and cold-continuity are retained separately in supplemental assembly records. Initial full dispatch omitted opt-in reads.', 'sdk':sdk,'partitions':parts,'identityComparison':comparison,'totals':{k:sum(p[k]for p in parts.values())for k in ['identities','normalCompletions','deliberateInterruptions','continuityRecords','png','xml']},'rawArtifactDirectory':str(source),'limits':'Original external tar member hashes/structural validation are observations; selected originals are inspected separately. No physical/configured-provider/store acceptance.'}
(target/'direct-original-inventory.json').write_text(json.dumps(direct_inventory,indent=2)+'\n');(target/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(target/'archive-inventory.json').write_text(json.dumps(archive_inventory,indent=2)+'\n');(target/'identities.json').write_text(json.dumps(ids.rows(actual),indent=2)+'\n')
assert summary['totals']['normalCompletions']==selected['expectedNormalCompletions'],summary['totals']
assert summary['totals']['deliberateInterruptions']==selected['expectedDeliberateInterruptions'],summary['totals']
assert summary['totals']['continuityRecords']==selected['expectedContinuityRecords'],summary['totals']
print(json.dumps(summary,indent=2))
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
(target/'SHA256SUMS').write_text(''.join(f'{sha(p.read_bytes())}  {p.relative_to(target)}\n'for p in sorted(target.rglob('*'))if p.is_file()and p.name!='SHA256SUMS'))
print('PASS/assumptions:',summary['passed'],summary['assumptions'])

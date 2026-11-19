"""Whole-parent assessment of the existing native v8 journey contract."""
import argparse, hashlib, json, re, tarfile, zipfile
from pathlib import Path
import xml.etree.ElementTree as E
p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('--sdk',type=int,required=True);p.add_argument('--source',required=True)
a=p.parse_args();root=a.root
run=json.loads((root/'run.json').read_text())
assert run['status']=='completed' and run['conclusion']=='success'
assert run['headSha']==a.source and len(run['jobs'])==1 and run['jobs'][0]['conclusion']=='success'
raw=root/'raw'
index=json.loads((root/'zip-index.json').read_text())
artifacts=[v for v in index if v['name']=='android-native-proof']
assert len(artifacts)==1 and artifacts[0]['workflow_run']['id']==run['databaseId']
assert artifacts[0]['workflow_run']['head_sha']==a.source
assert artifacts[0]['digest']=='sha256:'+hashlib.sha256((root/'original.zip').read_bytes()).hexdigest()
assert (raw/'.scratch/proof-source.txt').read_text().strip()==a.source
assert (raw/'.scratch/proof-workflow.txt').read_text().strip()==a.source
with zipfile.ZipFile(root/'original.zip') as z:
 assert z.testzip() is None
 assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in z.namelist())
 original_files={v.filename for v in z.infolist() if not v.is_dir()}
 assert len(original_files)==len([v for v in z.infolist() if not v.is_dir()]) and original_files
 for name in original_files:
  assert (raw/name).is_file() and hashlib.sha256((raw/name).read_bytes()).digest()==hashlib.sha256(z.read(name)).digest(),name
 # Contact sheets and extracted TAR views are derived inspection artifacts,
 # never verifier inputs. Every other raw file must belong to this exact ZIP.
 input_files={str(f.relative_to(raw)) for f in raw.rglob('*') if f.is_file() and
  not {'all-frames-review','native-captures'}.intersection(f.relative_to(raw).parts)}
 assert input_files==original_files,(input_files-original_files,original_files-input_files)
expected=json.loads(Path(__file__).with_name('137-native-expected-contract-cases.json').read_text())
for module,count,classes in [('core',244,48),('spaces-service',22,4)]:
 suites=[E.parse(f).getroot() for f in (raw/module/'build/test-results/test').glob('TEST-*.xml')]
 assert len(suites)==classes and sum(int(s.get('tests')) for s in suites)==count,(module,len(suites))
 assert all(int(s.get('failures'))==int(s.get('errors'))==int(s.get('skipped'))==0 for s in suites)
 actual={s.get('name'):sorted(t.get('name') for t in s.findall('testcase')) for s in suites}
 assert actual==expected[module],module
rust=raw/'core/build/generated/spaces-mls-host/community-contracts.txt'
receipt=json.loads((raw/'core/build/generated/spaces-mls-host/build-receipt.json').read_text())
assert receipt['rustContracts']['exit']==0 and receipt['rustContracts']['sha256']==hashlib.sha256(rust.read_bytes()).hexdigest()
assert '4 passed; 0 failed; 0 ignored' in rust.read_text()
for case in ['admitted_independent_identity_reads_encrypted_content_after_serialized_state_reload',
 'authenticated_group_exposes_the_actual_public_signer_for_each_bound_credential',
 'removed_identity_cannot_read_future_content_that_the_remaining_member_reads',
 'confirmed_rejection_recovers_the_pending_group_before_a_different_admission']:
 assert 'test '+case+' ... ok' in rust.read_text()
dirs=list((raw/'.scratch/device-evidence').glob('*/spaces-journey'));assert len(dirs)==1
d=dirs[0]; device=d.parent
assert device.name in (f'api-{a.sdk}',f'api-{a.sdk}.0')
assert (device/'device-abi.txt').read_text().strip() in ('x86','x86_64')
assert (device/'page-size.txt').read_text().strip()=='4096'
hashes=(device/'built-apk-sha256.txt').read_text().splitlines()
assert len(hashes)==2 and all(re.fullmatch('[0-9a-f]{64}',h.split()[0]) for h in hashes)
accept=json.loads((d/'acceptance.json').read_text());assert accept==json.loads((d/'journey-outcome.json').read_text())
assert accept['source']==a.source and accept['oldPid']>0 and accept['newPid']>0 and accept['oldPid']!=accept['newPid']
assert accept['nativeTerminalPasses']==1 and accept['deliberatelyInterruptedCases']==1 and accept['productionNodeActivated'] is False
assert json.loads((d/'cleanup.json').read_text())=={'originalFailure':None,'errors':[]}
assert json.loads((d/'native-evidence-capture.json').read_text())['exit']==0
kill=json.loads((d/'process-kill.json').read_text());assert kill['oldPid']==accept['oldPid'] and kill['forceStopped'] is True and kill['expectedInterruptedTest'] is True
before=(d/'before-kill.txt').read_text();after=(d/'after-kill.txt').read_text()
assert 'test=createInviteJoinReadAndCheckpointAnUncertainOriginal' in before
assert 'FAILURES!!!' not in before and 'AssertionError' not in before and not re.search(r'^OK \(',before,re.M)
assert 'test=coldReturnReconcilesTheSameOriginalAndKeepsNewWriting' in after
assert len(re.findall(r'^OK \(1 test\)\s*$',after,re.M))==1
assert not re.search(r'FAILURES!!!|AssumptionViolated|INSTRUMENTATION_STATUS_CODE: -(?:3|4)',after)
checkpoint=json.loads((d/'checkpoint.json').read_text());assert checkpoint['pid']==accept['oldPid'] and checkpoint['phase']=='ready-for-process-kill'
assert json.loads((d/'observed-checkpoint.raw').read_text())==checkpoint
with tarfile.open(d/'native-evidence.tar') as t:
 members=t.getmembers()
 assert all(not Path(m.name).is_absolute() and '..' not in Path(m.name).parts and (m.isfile() or m.isdir()) for m in members)
 files={m.name:t.extractfile(m).read() for m in members if m.isfile()}
 recovered=json.loads(files['spaces-journey-evidence/recovered.json'])
 assert recovered['pid']==accept['oldPid'] and recovered['recoveredPid']==accept['newPid'] and recovered['phase']=='recovered'
 assert recovered['owner']==checkpoint['owner'] and recovered['backend']==checkpoint['backend'] and recovered['original']==checkpoint['original']
 screenshots=['01-communities','02-drawer-b','03-confirmed-invitation','04-independent-received-reply','04e-community-information','04a-scrolled-before-module-switch','04a-incoming-messages-preserve-anchor','04b-scrolled-after-module-switch','04c-historical-page-end','04d-explicit-latest-return','05-preview-without-joining','06-joined-independent-community','07-returned-with-draft','08-uncertain-original-and-new-draft','09-cold-return-original-and-draft']
 for name in screenshots:
  assert files[f'spaces-journey-evidence/{name}.png'].startswith(b'\x89PNG\r\n\x1a\n')
  E.fromstring(files[f'spaces-journey-evidence/{name}.xml'])
 assert 'Unsent writing survives Android restart' in files['spaces-journey-evidence/09-cold-return-original-and-draft.xml'].decode()
 assert 'Join community' in files['spaces-journey-evidence/05-preview-without-joining.xml'].decode()
status=json.loads((d/'host-status.json').read_text())
assert len(status['communities'])==2 and all(c['joined'] for c in status['communities'])
received=[text for c in status['communities'] for text in c['received']]
for text in ['A reply after joining from Android','Original awaiting Android restart']:
 assert received.count(text)==1,(text,received.count(text))
community_a=[c for c in status['communities'] if 'Original awaiting Android restart' in c['received']]
assert len(community_a)==1
community_b=[c for c in status['communities'] if c['id']!=community_a[0]['id']]
assert len(community_b)==1 and community_b[0]['received'].count('A reply after joining from Android')==1
assert sum(o['id']==checkpoint['original'] for o in status['originals'])>=2
final=json.loads((d/'host-result.json').read_text())
assert {c['id'] for c in final['communities']}=={c['id'] for c in status['communities']}
assert all(c['joined'] for c in final['communities'])
assert next(c for c in final['communities'] if c['id']==community_b[0]['id'])['received'].count('A reply after joining from Android')==1
assert next(c for c in final['communities'] if c['id']==community_a[0]['id'])['received'].count('Original awaiting Android restart')==1
assert [text for c in final['communities'] for text in c['received']].count('Original awaiting Android restart')==1
assert sum(o['id']==checkpoint['original'] for o in final['originals'])>=2
for phase in [1,2]:
 movie=d/f'phase-{phase}.mp4';assert movie.stat().st_size>1000 and b'ftyp' in movie.read_bytes()[:32]
assert not (d/'crash.log').read_text().strip().replace('--------- beginning of crash','').strip()
inventory=json.loads((d/'files.json').read_text())
prefix=str(d.relative_to(raw))+'/'
expected_inventory={name[len(prefix):] for name in original_files if name.startswith(prefix) and name!=prefix+'files.json'}
assert len({entry['path'] for entry in inventory})==len(inventory) and {entry['path'] for entry in inventory}==expected_inventory
for entry in inventory:
 path=d/entry['path'];assert not Path(entry['path']).is_absolute() and '..' not in Path(entry['path']).parts
 assert path.stat().st_size==entry['bytes'] and hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256']
assert (root/'whole-run.log').is_file()
whole=(root/'whole-run.log').read_text()
assert a.source in whole and 'BUILD SUCCESSFUL' in whole and '4 passed; 0 failed; 0 ignored' in whole
result={'contract':'ticket137 native v8','source':a.source,'run':run['databaseId'],'sdk':a.sdk,'pageSize':4096,'outcome':'PASS','oldPid':accept['oldPid'],'newPid':accept['newPid'],'core':244,'service':22,'nativeTerminalPasses':1,'deliberatelyInterruptedCases':1,'archiveSha256':hashlib.sha256((root/'original.zip').read_bytes()).hexdigest(),'qualification':'Focused owned native/process journey only. Frame/prototype inspection, final integrated regression, physical/OEM and deployed-node acceptance remain separate.'}
(root/'assessment.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))

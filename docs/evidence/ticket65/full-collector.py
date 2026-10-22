from pathlib import Path
from collections import Counter
import sys,json,re,hashlib,shutil,tarfile,struct,zlib,xml.etree.ElementTree as ET,importlib.util
repo=Path('/home/david/projects/redsquare-android');sdk=sys.argv[1];source=Path(sys.argv[2]);target=repo/'docs/evidence/ticket65'/sys.argv[3];target.mkdir(parents=True,exist_ok=True)
sp=importlib.util.spec_from_file_location('ids',repo/'docs/evidence/ticket48/full-regression/partition-plan/test-identities.py');ids=importlib.util.module_from_spec(sp);sp.loader.exec_module(ids)
assert len(sys.argv)==5,'Require SDK, complete raw artifact directory, target label and exact runID'
metadata=json.loads(__import__('subprocess').check_output(['gh','run','view',sys.argv[4],'--repo','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,createdAt,url,jobs'],text=True))
configured=json.loads((repo/'docs/evidence/ticket65/full-identity-reference.json').read_text())
assert hashlib.sha256(Path(__file__).read_bytes()).hexdigest()==configured['collectorSha256']
frozen=json.loads(__import__('subprocess').check_output(['git','-C',str(repo),'show',configured['catalogFreezeCommit']+':docs/evidence/ticket65/full-identity-reference.json'],text=True))
assert configured['references']==frozen['references']and configured['recordedBeforeFullExecution']is True
assert configured['collectorSha256']==frozen['collectorSha256'] and configured['toolSource']==frozen['toolSource']
assert metadata['status']=='completed'and metadata['conclusion']=='success',metadata['conclusion']
executing_source=configured['executingSources']['android'+sdk] if 'executingSources'in configured else configured['executingSource']
assert metadata['headSha']==executing_source
assert int(sys.argv[4])==configured['executingRuns']['android'+sdk]
assert len(metadata['jobs']) == 3 and all(j['conclusion']=='success'for j in metadata['jobs'])
assert {j['name'] for j in metadata['jobs']} == {'native-proof (chat)', 'native-proof (social)', 'native-proof (wallet)'}
from datetime import datetime
freeze_date=__import__('subprocess').check_output(['git','-C',str(repo),'show','-s','--format=%cI',configured['catalogFreezeCommit']],text=True).strip()
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

def validate_post_evidence(target):
 social=target/'social';chat=target/'chat'
 for prefix, count in [('post-presentation',4),('post-viewport',1),('post-content',1),('post-content-contexts',1),('post-status',1),('post-pending',1)]:
  text=(social/(prefix+'-ui.txt')).read_text()
  marker=f'OK ({count} tests)' if count!=1 else 'OK (1 test)'
  assert marker in text and text.count('INSTRUMENTATION_CODE: -1')==1
  assert (social/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
 assert 'OK (1 test)' in (chat/'post-talkback-ui.txt').read_text()
 assert (chat/'post-talkback-ui-exit.txt').read_text().strip()=='0'
 originals=social/'status-originals'
 def captures(folder,names):
  for name in names:
   for suffix in ['png','xml']:
    path=originals/folder/(name+'.'+suffix)
    assert path.stat().st_size>100,str(path)
 base=['compact-social-controls-with-node-counts','feed-actions-secondary','feed-full-public-identity','report-confirmation-after-menu-closed']
 base += [prefix+route for route in ['feed','thread','profile','search'] for prefix in ['same-post-','own-actions-','own-delete-confirmation-']]
 captures('post-presentation-evidence',base)
 for folder, prefix, scales in [('post-viewport-evidence','post',[100,130,200]),('post-content-evidence','content',[100,130,200]),('post-content-contexts-evidence','contexts',[100]),('post-status-evidence','status',[100,130,200]),('post-pending-evidence','pending',[100])]:
  for scale in scales:
   for theme in ['light','dark']:
    label=f'{prefix}-320-font{scale}-{theme}'
    configuration=json.loads((originals/folder/(label+'-configuration.json')).read_text())
    assert configuration['widthDp']==320 and configuration['widthPixels']==320
    assert abs(configuration['fontScale']-scale/100)<0.01 and configuration['selectedAppearance'].lower()==theme
    if prefix=='post':
     captures(folder,[label+'-'+state for state in ['identity-body','actions-counts','full-public-identity']])
     for suffix in ['likes-layout','reposts-layout']:
      layout=json.loads((originals/folder/(label+'-'+suffix+'.json')).read_text())
      expected='9223372036854775807 likes' if suffix=='likes-layout' else '12345 reposts'
      assert layout['count']==expected and layout['lineCount']>0 and not layout['didOverflowHeight'] and not layout['didExceedMaxLines']
     targets=json.loads((originals/folder/(label+'.json')).read_text())
     assert targets['touchBounds'] and targets['scrollViewport'] and targets['floatingComposer']
    elif prefix in ['content','contexts']:
     routes=['feed','thread','profile','search'] if prefix=='contexts' else ['']
     captures(folder,[label+('-'+route if route else '')+'-'+state for route in routes for state in ['identity-body','image-1','image-2','verified-quote','actions-without-counts']])
    elif prefix=='status':
     captures(folder,[label+'-'+state for state in ['unavailable','details','retry-unavailable']])
     targets=json.loads((originals/folder/(label+'-targets.json')).read_text())
     assert targets['statusTargets'] and targets['scrollViewport'] and targets['floatingComposer']
     regions=json.loads((originals/folder/(label+'-live-regions.json')).read_text())
     assert 'mergedNodes' in regions and 'text=Like unavailable' in regions['nativeNodes'] and 'Open thread by ' not in regions['nativeNodes']
    elif prefix=='pending':
     captures(folder,[label+'-'+state for state in ['pending','pending-details','available-counts-unavailable','counts-details']])
  restored=json.loads((originals/folder/(prefix+'-font-restored.json')).read_text())
  assert abs(restored['expected']-restored['observed'])<0.01 and restored['expected']==restored['originalScale']
  assert restored['currentSetting']==restored['originalSetting'] or (sdk=='10' and restored['originalSetting']=='null' and restored['originalScale']==1 and restored['currentSetting']=='1.0')
 talkback=chat/'status-originals/post-talkback-evidence'
 processes=set()
 owner_context=json.loads((talkback/'owner-more-result.json').read_text())['expectedContext']
 match=re.fullmatch(r'Post actions for (Anon-[1-9A-HJ-NP-Za-km-z]{6})',owner_context);assert match
 owner_name=match.group(1)
 expected_contexts={
  'profile':'View profile for Anon-jesTu2','thread':'Open thread by Anon-jesTu2',
  'like':'Like post by Anon-jesTu2','repost':'Repost post by Anon-jesTu2',
  'quote':'Quote post by Anon-jesTu2','more':'Post actions for Anon-jesTu2',
  'report':'Report post by Anon-jesTu2','owner-more':'Post actions for '+owner_name,
  'edit':'Edit post by '+owner_name,'delete':'Delete post by '+owner_name,
 }
 for label in ['profile','thread','like','repost','quote','more','report','owner-more','edit','delete']:
  observation=json.loads((talkback/(label+'-result.json')).read_text())
  assert observation['actualAccessibilityFocus'] is True and observation['physicalAudibility'] is False
  pid=str(observation['serviceProcess']);assert pid.isdigit() and int(pid)>0;processes.add(pid)
  context=observation['expectedContext'];assert context==expected_contexts[label],(label,context)
  log=(talkback/(label+'-speech.txt')).read_text()
  requested=[line for line in log.splitlines() if 'Speaking fragment text=' in line and context in line]
  assert requested,(label,context)
  ids={value.strip() for value in observation['engineCompletions'].split(',')};assert ids and '' not in ids
  actual_ids={re.search(r'utteranceId=([^,\s]+)',line).group(1) for line in requested}
  assert ids <= actual_ids
  for line in requested:
   assert re.search(r'\(\s*'+re.escape(pid)+r'\)',line), 'Each contextual request must come from the real service PID'
  for utterance in ids:
   events={}
   for line in log.splitlines():
    event=re.search(r'REDSQUARE_ENGINE_(START|AUDIO|DONE|ERROR|STOP) utteranceId=([^\s]+)(?: bytes=([0-9]+))?',line)
    if event and event.group(2)==utterance:
     assert re.search(r'\(\s*'+re.escape(pid)+r'\)',line), 'Each engine callback must come from the real service PID'
     events.setdefault(event.group(1),[]).append(int(event.group(3) or 0))
   assert {'START','AUDIO','DONE'} <= set(events) and not {'ERROR','STOP'} & set(events)
   assert any(size>0 for size in events['AUDIO'])
  assert (talkback/(label+'.png')).stat().st_size>100
 assert len(processes)==1

def validate_composer_evidence(target):
 social=target/'social';originals=social/'status-originals'
 for prefix,count in [('composer-focus',1),('composer-draft',2),('composer-post',1),('composer-reply',1),('composer-quote',1),('composer-work',2)]:
  text=(social/(prefix+'-ui.txt')).read_text()
  marker=f'OK ({count} tests)' if count!=1 else 'OK (1 test)'
  assert marker in text and text.count('INSTRUMENTATION_CODE: -1')==1
  assert (social/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
 def captures(folder,names):
  for name in names:
   for suffix in ['png','xml']:
    assert (originals/folder/(name+'.'+suffix)).stat().st_size>100
 def configuration(folder,prefix,theme):
  label=prefix+'-320-font130-'+theme
  row=json.loads((originals/folder/(label+'-configuration.json')).read_text())
  assert row['widthDp']==320 and row['widthPixels']==320
  assert abs(row['fontScale']-1.3)<0.01 and row['selectedAppearance'].lower()==theme
  return label
 def restored(folder,prefix):
  row=json.loads((originals/folder/(prefix+'-font-restored.json')).read_text())
  assert abs(row['expected']-row['observed'])<0.01 and row['expected']==row['originalScale']
 for theme in ['light','dark']:
  label=configuration('composer-focus-evidence','composer-focus',theme)
  captures('composer-focus-evidence',[label+'-'+state for state in ['writing','ime','returned-draft']])
  hierarchy=json.loads((originals/'composer-focus-evidence'/(label+'-hierarchy.json')).read_text())
  assert hierarchy['writingPrecedesAttachments'] is True and hierarchy['writingBottom']<=hierarchy['attachmentTop']
  ime=json.loads((originals/'composer-focus-evidence'/(label+'-ime.json')).read_text())
  assert ime['keyboardVisible'] is True and abs(ime['fontScale']-1.3)<0.01
  left,top,right,bottom=map(int,re.findall(r'-?[0-9]+',ime['sendBounds']))
  assert right-left>=47 and bottom-top>=47 and left>=0 and right<=320
  assert top>=ime['statusBottom'] and bottom<=ime['keyboardTop']
  label=configuration('composer-draft-evidence','composer-draft',theme)
  captures('composer-draft-evidence',[label+'-'+state for state in ['saved','edited','returned']])
  label=configuration('composer-draft-evidence','composer-media-storage',theme)
  captures('composer-draft-evidence',[label+'-'+state for state in ['unconfirmed-images','repaired-images']])
  for kind in ['post','reply','quote']:
   label=configuration('composer-publishing-evidence','composer-'+kind,theme)
   captures('composer-publishing-evidence',[label+'-'+state for state in ['attached','returned-ime','policy','submitted','available']])
  label=configuration('publishing-work-evidence','saved-author',theme)
  captures('publishing-work-evidence',[label+'-'+state for state in ['list','reopened']])
  label=configuration('publishing-work-evidence','queued-work',theme)
  captures('publishing-work-evidence',[label+'-'+state for state in ['list','empty','editable']])
 for folder,prefixes in [('composer-focus-evidence',['composer-focus']),('composer-draft-evidence',['composer-draft','composer-media-storage']),('composer-publishing-evidence',['composer-post','composer-reply','composer-quote']),('publishing-work-evidence',['saved-author','queued-work'])]:
  for prefix in prefixes:restored(folder,prefix)
 captures('author-post-evidence',[prefix+'-saved-author-changes' for prefix in ['native','image']])
 captures('post-queue-evidence',['feed-queued-work-before-cancellation'])

def validate_conversation_evidence(target):
 chat=target/'chat';originals=chat/'status-originals'
 for prefix,count in [('conversation-viewport',1),('private-conversation-reading',1),('group-conversation-reading',1),('group-conversation-polish',1),('conversation-send',2),('conversation-filtering',1)]:
  text=(chat/(prefix+'-ui.txt')).read_text()
  assert f'OK ({count} test'+('s)' if count!=1 else ')') in text
  assert text.count('INSTRUMENTATION_CODE: -1')==1
  assert (chat/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
 def capture(folder,name):
  for suffix in ['png','xml']:
   assert (originals/folder/(name+'.'+suffix)).stat().st_size>100
 def configuration(folder,label,scale,theme):
  row=json.loads((originals/folder/(label+'-configuration.json')).read_text())
  assert row['widthDp']==320 and row['widthPixels']==320
  assert abs(row['fontScale']-scale/100)<0.01 and row['selectedAppearance'].lower()==theme
 def restoration(folder,prefix):
  row=json.loads((originals/folder/(prefix+'-font-restored.json')).read_text())
  assert abs(row['expected']-row['observed'])<0.01 and row['expected']==row['originalScale']
 for prefix,folder,scales in [('conversation-private-latest','conversation-viewport-evidence',[100,130,200]),
     ('group-conversation-reading','group-conversation-reading-evidence',[130]),
     ('group-conversation-polish','group-conversation-polish-evidence',[100,200])]:
  for scale in scales:
   for theme in ['light','dark']:
    label=f'{prefix}-320-font{scale}-{theme}'
    configuration(folder,label,scale,theme)
    for state in ['full-details','message-actions','history-with-ime']:
     capture(folder,label+'-'+state)
  restoration(folder,prefix)
 for prefix,scales in [('private-conversation-reading',[130]),('group-conversation-reading',[130]),('group-conversation-polish',[100,200])]:
  folder=prefix+'-evidence';cell=0
  for scale in scales:
   for theme in ['light','dark']:
    label=f'{prefix}-320-font{scale}-{theme}'
    configuration(folder,label,scale,theme)
    for state in ['opened','reading-at-end','received-while-at-end','following-new-message','reading-older',
        'new-message-action-with-older-position','new-message-action-opened-latest']:
     capture(folder,label+'-'+state)
    anchor=json.loads((originals/folder/(label+'-reading-anchor.json')).read_text())
    assert anchor['oneDpEqualsOnePixel'] is True and abs(anchor['beforeTop']-anchor['afterTop'])<=1
    opened=json.loads((originals/folder/(label+'-opened.json')).read_text())
    last=json.loads((originals/folder/(label+'-new-message-action-opened-latest.json')).read_text())
    assert opened['expectedMessages']==70+cell*2 and last['expectedMessages']==72+cell*2 and last['atEnd'] is True
    cell+=1
  restoration(folder,prefix)
 for kind in ['private','group']:
  prefix='conversation-'+kind+'-send';folder='conversation-send-evidence'
  for count,theme in [(71,'light'),(72,'dark')]:
   label=f'{prefix}-320-font130-{theme}'
   configuration(folder,label,130,theme)
   for state in ['older-before-own-send','own-send-exposes-latest']:
    capture(folder,label+'-'+state)
   older=json.loads((originals/folder/(label+'-older-before-own-send.json')).read_text())
   last=json.loads((originals/folder/(label+'-own-send-exposes-latest.json')).read_text())
   assert older['atEnd'] is False and older['expectedMessages']==count-1
   assert last['atEnd'] is True and last['expectedMessages']==count
  restoration(folder,prefix)
 prefix='conversation-filtering';folder=prefix+'-evidence'
 for theme in ['light','dark']:
  label=f'{prefix}-320-font130-{theme}'
  configuration(folder,label,130,theme)
  capture(folder,label+'-older-before-filtering')
  for stage in ['blocked','revealed']:
   capture(folder,label+'-'+stage+'-without-arrival')
   anchor=json.loads((originals/folder/(label+'-'+stage+'-anchor.json')).read_text())
   assert anchor['newMessagesVisible'] is False and abs(anchor['beforeTop']-anchor['afterTop'])<=1
 restoration(folder,prefix)
 capture('conversation-viewport-evidence','conversation-private-latest-320-font100-light-opened')

def validate_chat_entry_evidence(target):
 chat=target/'chat';originals=chat/'status-originals'
 journeys=[
  ('chat-entry',['known-contacts','known-identity','invalid-key-with-ime','pasted-identity']),
  ('group-setup',['default','known-selection','invalid-key-with-ime','ready-to-create','created-members']),
  ('group-invitation-share',['default','share-return','copied']),
  ('group-invitation-recovery',['entry','invalid-with-ime','preview','preview-identities','joined']),
 ]
 for prefix,states in journeys:
  text=(chat/(prefix+'-ui.txt')).read_text()
  assert 'OK (1 test)' in text and text.count('INSTRUMENTATION_CODE: -1')==1
  assert (chat/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
  folder=originals/(prefix+'-evidence')
  for scale in [100,130,200]:
   for theme in ['light','dark']:
    label=f'{prefix}-320-font{scale}-{theme}'
    row=json.loads((folder/(label+'-configuration.json')).read_text())
    assert row['widthDp']==320 and row['widthPixels']==320
    assert abs(row['fontScale']-scale/100)<0.01 and row['selectedAppearance'].lower()==theme
    for state in states:
     for suffix in ['png','xml']:
      assert (folder/(label+'-'+state+'.'+suffix)).stat().st_size>100
    if prefix=='chat-entry':
     contact=json.loads((folder/(label+'-contact-target.json')).read_text())
     assert abs(contact['fontScale']-scale/100)<0.01
     assert contact['unclippedIdentityHeight']>0
     assert contact['visibleIdentityHeight']>=contact['unclippedIdentityHeight']-1
     left,top,right,bottom=map(int,re.findall(r'-?[0-9]+',contact['contactTargetBounds']))
     assert right-left>=48 and bottom-top>=48
  restored=json.loads((folder/(prefix+'-font-restored.json')).read_text())
  assert abs(restored['expected']-restored['observed'])<0.01 and restored['expected']==restored['originalScale']

def validate_profile_evidence(target):
 social=target/'social';originals=social/'status-originals'
 def completed(part,prefix):
  text=(part/(prefix+'-ui.txt')).read_text()
  assert 'OK (1 test)' in text and text.count('INSTRUMENTATION_CODE: -1')==1
  assert (part/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
 def captures(folder,label):
  for suffix in ['png','xml']:
   assert (folder/(label+'.'+suffix)).stat().st_size>100
 for prefix,evidence_folder,states in [
  ('profile-hierarchy','profile-hierarchy-evidence',['default','posts-selected','replies-selected','likes-selected','full-identity','mute-confirmation']),
  ('profile-editor','profile-editor-presentation-evidence',['writing-ime','unsent-preview','recovered-preview']),
 ]:
  completed(social,'profile-editor-presentation' if prefix=='profile-editor' else prefix)
  folder=originals/evidence_folder
  for scale in [100,130,200]:
   for theme in ['light','dark']:
    label=f'{prefix}-320-font{scale}-{theme}'
    data=json.loads((folder/(label+'-configuration.json')).read_text())
    assert data['widthDp']==320 and data['widthPixels']==320
    assert abs(data['fontScale']-scale/100)<0.01 and data['selectedAppearance'].lower()==theme
    for state in states:captures(folder,label+'-'+state)
    if prefix=='profile-editor':
     saved=json.loads((folder/(label+'-save-draft-target.json')).read_text())
     left,top,right,bottom=map(int,re.findall(r'-?[0-9]+',saved['targetBounds']))
     minimum=saved['minimumTargetPixels'];ime=saved['focusedWindowImeBottom']
     assert minimum>=48 and ime>0 and right-left>=minimum and bottom-top>=minimum
     assert left>=0 and right<=320 and top>=0 and bottom<=saved['displayHeight']-ime
  restored=json.loads((folder/(prefix+'-font-restored.json')).read_text())
  assert abs(restored['expected']-restored['observed'])<0.01 and restored['expected']==restored['originalScale']
 completed(social,'profile-keyboard')
 keyboard=originals/'profile-keyboard-evidence'
 expected=['Back','Refresh','Profile actions','Edit profile','Identity details','Posts','Replies','Likes','Replies','Likes']
 assert (keyboard/'physical-focus-order.txt').read_text().splitlines()==expected
 for number,label in enumerate(expected,1):captures(keyboard,f'{number}-{label.lower().replace(" ","-")}-focused')
 captures(keyboard,'likes-selected-and-focused')
 chat=target/'chat';completed(chat,'profile-talkback')
 speech=chat/'status-originals/profile-talkback-evidence';processes=set()
 for label in ['Posts','Replies','Likes']:
  result=json.loads((speech/(label.lower()+'-result.json')).read_text())
  assert result['label']==label and result['nativeSelected'] is True
  assert result['actualAccessibilityFocus'] is True and result['physicalAudibility'] is False
  pid=str(result['serviceProcess']);assert pid.isdigit() and int(pid)>0;processes.add(pid)
  log=(speech/(label.lower()+'-speech.txt')).read_text()
  requested=[line for line in log.splitlines() if 'Speaking fragment text=' in line and
   re.search(r'\b'+re.escape(label)+r'\b',line,re.I) and re.search(r'\bselected\b',line,re.I) and
   not re.search(r'\bnot\s+selected\b',line,re.I)]
  assert requested,label
  completions={value.strip()for value in result['completedSelectedUtterances'].split(',')}
  assert completions and ''not in completions
  utterances={re.search(r'utteranceId=([^,\s]+)',line).group(1)for line in requested}
  assert completions<=utterances
  for line in requested:assert re.search(r'\(\s*'+re.escape(pid)+r'\)',line)
  for utterance in completions:
   events={}
   for line in log.splitlines():
    event=re.search(r'REDSQUARE_ENGINE_(START|AUDIO|DONE|ERROR|STOP) utteranceId=([^\s]+)(?: bytes=([0-9]+))?',line)
    if event and event.group(2)==utterance:
     assert re.search(r'\(\s*'+re.escape(pid)+r'\)',line)
     events.setdefault(event.group(1),[]).append(int(event.group(3)or 0))
   assert {'START','AUDIO','DONE'}<=set(events)and not {'ERROR','STOP'}&set(events)
   assert any(size>0 for size in events['AUDIO'])
  assert (speech/(label.lower()+'-selected.png')).stat().st_size>100
 assert len(processes)==1

def validate_search_evidence(target):
 social=target/'social';originals=social/'status-originals'
 for prefix,count in [('search-entry',2),('search-presentation',1),('search-reading',1),('search-recovery',3),('search-viewport',1)]:
  log=(social/(prefix+'-ui.txt')).read_text()
  marker=f'OK ({count} tests)' if count!=1 else 'OK (1 test)'
  assert marker in log and log.count('INSTRUMENTATION_CODE: -1')==1
  assert (social/(prefix+'-ui-exit.txt')).read_text().strip()=='0'
 def captures(folder,names):
  for name in names:
   for suffix in ['png','xml']:
    assert (originals/folder/(name+'.'+suffix)).stat().st_size>100
 captures('search-entry-evidence',['keyboard-before-submit','keyboard-search-result','cleared-search','deliberate-search-after-clear','cleared-unfinished-search'])
 captures('search-presentation-evidence',['people-only','exact-identity','mixed-results','posts-only','no-results'])
 captures('search-reading-evidence',['before-opening-result','after-thread','after-profile','after-recreation'])
 captures('search-recovery-evidence',['paused-after-return','paused-after-recreation','continued-explicitly','username-retried'])
 folder=originals/'search-viewport-evidence'
 for scale in [100,130,200]:
  for theme in ['light','dark']:
   label=f'search-320-font{scale}-{theme}'
   data=json.loads((folder/(label+'-configuration.json')).read_text())
   assert data['widthDp']==320 and data['widthPixels']==320
   assert abs(data['fontScale']-scale/100)<0.01 and data['selectedAppearance'].lower()==theme
   captures('search-viewport-evidence',[label+'-'+state for state in ['invalid-query','real-keyboard','people','exact-identity','long-name','long-name-identity','long-post','post-actions']])
 restored=json.loads((folder/'search-font-restored.json').read_text())
 assert abs(restored['expected']-restored['observed'])<0.01 and restored['expected']==restored['originalScale']

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
    if p.name in {'status-recovery-evidence.tar', 'status-motion-evidence.tar', 'status-talkback-evidence.tar', 'app-frame-reading-evidence.tar', 'post-talkback-evidence.tar', 'post-presentation-evidence.tar', 'post-viewport-evidence.tar', 'post-content-evidence.tar', 'post-content-contexts-evidence.tar', 'post-status-evidence.tar', 'post-pending-evidence.tar', 'composer-focus-evidence.tar', 'composer-draft-evidence.tar', 'composer-publishing-evidence.tar', 'publishing-work-evidence.tar', 'author-post-evidence.tar', 'post-queue-evidence.tar', 'posting-evidence.tar', 'conversation-viewport-evidence.tar', 'private-conversation-reading-evidence.tar', 'group-conversation-reading-evidence.tar', 'conversation-send-evidence.tar', 'group-conversation-polish-evidence.tar', 'conversation-filtering-evidence.tar', 'chat-entry-evidence.tar', 'group-setup-evidence.tar', 'group-invitation-share-evidence.tar', 'group-invitation-recovery-evidence.tar', 'profile-hierarchy-evidence.tar', 'profile-editor-presentation-evidence.tar', 'profile-keyboard-evidence.tar', 'profile-talkback-evidence.tar', 'search-entry-evidence.tar', 'search-presentation-evidence.tar', 'search-reading-evidence.tar', 'search-recovery-evidence.tar', 'search-viewport-evidence.tar'}:
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
reference=json.loads((repo/'docs/evidence/ticket65/full-identity-reference.json').read_text())
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
summary={'source': executing_source,'tallyBasis':'Complete primary partitioned execution, including every public read and additive status, post, composer, conversation Chat entry/invitation and profile reading/editor/keyboard/actual speech and search entry/presentation/reading/recovery journeys. No supplemental or tail assembly.', 'sdk':sdk,'partitions':parts,'identityComparison':comparison,'totals':{k:sum(p[k]for p in parts.values())for k in ['identities','normalCompletions','deliberateInterruptions','continuityRecords','png','xml']},'rawArtifactDirectory':str(source),'limits':'Original external tar member hashes/structural validation are observations; selected originals are inspected separately. No physical/configured-provider/store acceptance.'}
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
validate_post_evidence(target)
validate_composer_evidence(target)
validate_conversation_evidence(target)
validate_chat_entry_evidence(target)
validate_profile_evidence(target)
validate_search_evidence(target)
(target/'SHA256SUMS').write_text(''.join(f'{sha(p.read_bytes())}  {p.relative_to(target)}\n'for p in sorted(target.rglob('*'))if p.is_file()and p.name!='SHA256SUMS'))
print('PASS/assumptions:',summary['passed'],summary['assumptions'])

import json,re,sys,subprocess,hashlib,struct,zlib,xml.etree.ElementTree as ET
from pathlib import Path
r=Path(__file__).parent; pre=sys.argv[1]; v=json.loads((r/(pre+'-verification.json')).read_text()); assert v['conclusion']=='success'
repo=Path('/mnt/storage/redsquare-android-artifacts/redesign86'); base='30bb852d4109c64a61e749d59c615b401a62df3f'; root=r/(pre+'-artifacts'); cap=r/(pre+'-captures'); source=v['source']
def show(ref,path):return subprocess.check_output(['git','show',ref+':'+path],cwd=repo,text=True)
for name in ['FilesharePreviewTest.kt','FileshareProcessRecoveryTest.kt','FileshareAttachmentAvailabilityTest.kt','FileshareAttachmentBusyTest.kt','FileshareAttachmentTestUi.kt']:
 path='app/src/androidTest/kotlin/nl/davidengelen/redsquare/'+name
 assert show(source,path)==show(base,path),('Inherited native goals changed',path)
assert show(source,'scripts/fileshare-process-check')==show(base,'scripts/fileshare-process-check')
cases=[('received-storage','FileshareStorageManagementTest'),('received-storage-failure','ReceivedFilesFailureTest'),('received-storage-motion','ReceivedFilesMotionTest'),('received-storage-viewport-320','ReceivedFilesViewportTest'),('received-storage-viewport-412','ReceivedFilesPixelViewportTest'),('file-menu','FileshareAttachmentAvailabilityTest'),('file-menu-busy','FileshareAttachmentBusyTest'),('fileshare-send','FileshareSendPreviewTest'),('fileshare-receive','FileshareReceivePreviewTest'),('fileshare-cancel','FileshareCancelPreviewTest'),('fileshare-unknown-size','FileshareUnknownSizePreviewTest'),('fileshare-notifications','FileshareNotificationPreviewTest'),('fileshare-preparation-cancel','FilesharePreparationCancelPreviewTest'),('fileshare-viewport-320','FileshareNarrowViewportTest'),('fileshare-viewport-412','FilesharePixelViewportTest'),('fileshare-large-receive','FileshareLargeReceivePreviewTest')]
def identities(text):
 pending={}; starts=[]; ends=[]
 for line in text.splitlines():
  m=re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.*)',line)
  if m:pending[m[1]]=m[2]
  m=re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)',line)
  if m:
   code=int(m[1]); assert code in (0,1),code
   (starts if code==1 else ends).append((pending.get('class'),pending.get('test'))); pending={}
 return starts,ends
groups=[]
for group,cls in cases:
 name={'FileshareSendPreviewTest':'FilesharePreviewTest','FileshareReceivePreviewTest':'FilesharePreviewTest','FileshareCancelPreviewTest':'FilesharePreviewTest','FileshareUnknownSizePreviewTest':'FilesharePreviewTest','FileshareNotificationPreviewTest':'FilesharePreviewTest','FilesharePreparationCancelPreviewTest':'FilesharePreviewTest','FileshareNarrowViewportTest':'FilesharePreviewTest','FilesharePixelViewportTest':'FilesharePreviewTest','FileshareLargeReceivePreviewTest':'FilesharePreviewTest','ReceivedFilesPixelViewportTest':'ReceivedFilesViewportTest'}.get(cls,cls)
 s=show(source,'app/src/androidTest/kotlin/nl/davidengelen/redsquare/'+name+'.kt')
 body=s.split('class '+cls,1)[1].split('\nclass ',1)[0]; methods=set(re.findall(r'@Test\s+fun\s+(\w+)',body)); assert methods
 fs=list(root.rglob(group+'-ui.txt')); assert len(fs)==1,group
 p=fs[0]; text=p.read_text(); starts,ends=identities(text); goals={('nl.davidengelen.redsquare.'+cls,m) for m in methods}
 assert len(starts)==len(ends)==len(goals) and set(starts)==set(ends)==goals,(group,starts,ends,goals)
 assert re.search(r'OK \('+str(len(goals))+r' tests?\)',text)
 assert p.with_name(group+'-ui-exit.txt').read_text().strip()=='0'
 assert 'captureExit=0' in p.with_name(group+'-capture-status.log').read_text()
 groups.append({'group':group,'class':cls,'methods':sorted(methods),'log':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
assert sum(len(g['methods']) for g in groups)==19
process=next(root.rglob('fileshare-process/continuity.txt')).parent
assert process.joinpath('continuity.txt').read_text().startswith('PASS: actual process death')
pending=json.loads(process.joinpath('pending-checkpoint.json').read_text()); recovered=json.loads(process.joinpath('recovered-checkpoint.json').read_text())
assert pending['phase']=='pending' and recovered['phase']=='recovered'
assert recovered['recoveredPid']>0 and recovered['recoveredPid']!=pending['pid']
assert recovered==dict(pending,phase='recovered',recoveredPid=recovered['recoveredPid'])
for name,method in [('recovered','coldRestartShowsInterruptedWithoutSendingAndRetainsTheDraft'),('restored','restoreOriginalEncryptedInstallationFiles')]:
 text=process.joinpath(name+'.txt').read_text(); starts,ends=identities(text)
 assert starts==ends==[('nl.davidengelen.redsquare.FileshareProcessRecoveryTest',method)] and 'OK (1 test)' in text
starts,ends=identities(process.joinpath('interrupted.txt').read_text()); assert starts==[('nl.davidengelen.redsquare.FileshareProcessRecoveryTest','terminateWithAnEncryptedPartialFileAndUnsentChatDraft')] and not ends
assert 'FAILURES!!!' not in process.joinpath('interrupted.txt').read_text()
lifecycle=cap/'received-storage-evidence/lifecycle'
for name in ['retained-after-export.png','removed-after-recreation.png','history-local-copy-removed.png']:assert lifecycle.joinpath(name).is_file(),lifecycle/name
for motion in [0,1]:
 folder=cap/'received-storage-evidence'/('motion-'+str(motion))
 for name in ['removed-at-actual-window-scale.png','confirmed-removal-frames.json']:assert folder.joinpath(name).is_file(),folder/name
 # Actual window/Compose scale observation is retained by the shared native observer.
 window=json.loads(folder.joinpath(f'scale-{motion}-window.json').read_text())
 assert window=={'isRecomposer':True,'windowMotionScale':float(motion),'systemMotionScale':float(motion)}
for width in [320,412]:
 for font in [100,130,200]:
  for theme in ['light','dark']:
   name=f'received-files-{width}-font{font}-{theme}'
   config=json.loads((cap/'received-storage-evidence'/(name+'-configuration.json')).read_text())
   assert config['widthDp']==width and abs(config['fontScale']-font/100)<.01
   assert config['selectedAppearance'].lower()==theme
   assert (cap/'received-storage-evidence'/(name+'-storage-confirmation.png')).is_file()
for name in ['actual-export-disables-removal.png','partial-removal-counted-truthfully.png','partial-removal-finished.png']:assert (cap/'received-storage-evidence'/name).is_file()
# Validate actual PNG structure and CRC rather than an arbitrary compressed-size heuristic.
for p in cap.rglob('*.png'):
 data=p.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n';pos=8;idat=[];dimensions=None;ended=False
 while pos<len(data):
  size=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];body=data[pos+8:pos+8+size];crc=struct.unpack('>I',data[pos+8+size:pos+12+size])[0]
  assert zlib.crc32(kind+body)&0xffffffff==crc
  if kind==b'IHDR':dimensions=struct.unpack('>II',body[:8]);assert all(n>0 for n in dimensions)
  if kind==b'IDAT':idat.append(body)
  pos+=12+size
  if kind==b'IEND':ended=True;break
 assert ended and pos==len(data) and dimensions and zlib.decompress(b''.join(idat))
for p in cap.rglob('*.xml'):assert ET.parse(p).getroot().find('.//node') is not None
out={'run':v['run'],'source':source,'normalMethods':21,'intentionalProcessDeathStarts':1,'groups':groups,'allCompleteNativeAndCaptureChecksPassed':True,'inheritedNativeGoalsAndBoundsByteIdentical':True,'qualifications':['Owned emulators and controlled peers/provider/filesystem unlink only.','The unlink fault adapter returns failure only for the genuine encrypted inbox remainder; business state and rendered UI are not replaced.','Actual process death is seeded partial Fileshare recovery, separate from the two normal recovery/restoration completions.','Activity recreation proves retained storage navigation and local removal history; physical OEM and live-service acceptance remain outside this scope.','Final complete application and signed beta qualification belongs to131.']}
(r/(pre+'-assessment.json')).write_text(json.dumps(out,indent=2)+'\n'); print({'run':v['run'],'normalMethods':21,'intentionalProcessDeathStarts':1,'groups':16,'result':'PASS_WITH_STATED_OWNED_SEAM_AND_SCOPE_QUALIFICATIONS'})

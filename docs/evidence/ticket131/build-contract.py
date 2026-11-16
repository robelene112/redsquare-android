import json,re,subprocess,hashlib
from pathlib import Path
packet=Path(__file__).resolve().parent; repo=packet.parents[2]
def git(*args):return subprocess.check_output(['git',*args],cwd=repo,text=True)
def classes(text):
 matches=list(re.finditer(r'^class (\w+)',text,re.M))
 return {m.group(1):set(re.findall(r'@Test\s+fun\s+(\w+)',text[m.end():matches[i+1].start() if i+1<len(matches) else len(text)])) for i,m in enumerate(matches)}
base='f7d7448ad7ae67277ddf58ac2d6761c92b3b50c5'; inherited=json.loads((repo/'docs/evidence/ticket115/full-contract.json').read_text())
current={}; source_files={}
for p in (repo/'app/src/androidTest').rglob('*.kt'):
 for name,methods in classes(p.read_text()).items():current[name]=methods;source_files[name]=str(p.relative_to(repo))
# Each independently named class is added once to its real full-scope output. One class joins an inherited invocation.
ordinary=[
 ('ConversationDeadlineRecoveryTest','social','conversation-deadline',True),('ProfileDeadlineRecoveryTest','social','profile-deadline',True),('StackDeadlineRecoveryTest','social','stack-deadline',True),('ReadDeadlineRecoveryTest','social','read-deadline',True),('ReadCancellationTest','social','read-cancellation',True),
 ('FollowingPartialFailureTest','social','following-partial',True),('PhotoPagesPreviewTest','social','photo-pages',True),('PhotoArchiveContinuationTest','social','photo-continuation',True),('PhotoLargeRowPreviewTest','social','photo-large-rows',True),('PhotoMultiStreamPreviewTest','social','photo-multistream',True),
 ('TalkIdleRecoveryTest','chat','talk-idle',True),('TalkRoutesRecoveryTest','chat','talk-routes',True),('TalkAccountReplacementTest','chat','talk-account-replacement',True),('TalkLaunchRoutePreviewTest','chat','talk-launch-route',True),
 ('FileshareAttachmentAvailabilityTest','chat','file-menu',True),('FileshareAttachmentBusyTest','chat','file-menu-busy',True),('FileshareStorageManagementTest','chat','received-storage',True),('ReceivedFilesFailureTest','chat','received-storage-failure',True),('ReceivedFilesMotionTest','chat','received-storage-motion',True),('ReceivedFilesViewportTest','chat','received-storage-viewport-320',True),('ReceivedFilesPixelViewportTest','chat','received-storage-viewport-412',True),
 ('ImagePreparationRecreationTest','social','images-preparation',False),('ProfileImagePreparationRecreationTest','social','profile-image-recreation',True),('UnfinishedTextStorageTest','social','unfinished-text-storage',True),('WalletTransferRecreationTest','social','wallet-input-recreation',True)]
extra=[]; required=[]; old_methods=[]
for cls,part,prefix,new in ordinary:
 assert cls in current and current[cls]
 for method in sorted(current[cls]):extra.append(dict(path=prefix+'-ui.txt',testClass='nl.davidengelen.redsquare.'+cls,test=method,occurrences=1))
 if new:required.append(part+'/'+prefix+'-evidence.tar')
# Additions inside existing whole-class calls retain their original paths and occurrence counts.
for p in git('diff','--diff-filter=M','--name-only',base,'HEAD','--','app/src/androidTest').splitlines():
 if not p.endswith('.kt'):continue
 before=classes(git('show',base+':'+p));after=classes((repo/p).read_text())
 for cls,methods in after.items():
  paths={r['path']:r['occurrences'] for r in inherited['expected']['17'] if r['testClass']=='nl.davidengelen.redsquare.'+cls}
  if not paths:continue
  removed=before.get(cls,set())-methods;assert not removed,(cls,removed)
  for method in sorted(methods-before.get(cls,set())):
   for path,count in paths.items():
    row=dict(path=path,testClass='nl.davidengelen.redsquare.'+cls,test=method,occurrences=count);extra.append(row);old_methods.append(row)
process=[]
for mode in ['post','post-saved','reply','quote','profile','profile-saved','continuous','storage-failure']:
 prefix='unfinished-text-process/'+mode
 methods={'interrupted.txt':'backgroundWithUnfinishedTextThenTerminateTheActualProcess','recovered.txt':'coldProcessRestoresTheExactUnfinishedTextWithoutSavingOrPublishing','restored.txt':'restoreOriginalEncryptedInstallationFiles'}
 for file,method in methods.items():extra.append(dict(path=prefix+'/'+file,testClass='nl.davidengelen.redsquare.UnfinishedTextProcessTest',test=method,occurrences=1))
 process.append(dict(partition='social',prefix=prefix,mode=mode,testClass='nl.davidengelen.redsquare.UnfinishedTextProcessTest',methods=methods))
 required.append('social/'+prefix+'/images.tar')
# Pin additive identities and make loss or replacement of any normative identity impossible.
expected={}
for sdk in ['10','17']:
 old={(x['path'],x['testClass'],x['test']):x['occurrences'] for x in inherited['expected'][sdk]}
 new={(x['path'],x['testClass'],x['test']):x['occurrences'] for x in extra}
 assert len(new)==len(extra) and not old.keys()&new.keys()
 expected[sdk]=[dict(path=k[0],testClass=k[1],test=k[2],occurrences=n) for k,n in sorted((old|new).items())]
 all_methods={method for p in (repo/'app/src/androidTest').rglob('*.kt') for method in re.findall(r'@Test\s+fun\s+(\w+)',p.read_text())}
 for row in inherited['expected'][sdk]:assert row['test'] in all_methods,row
new_invocations=sum(new for _,_,_,new in ordinary)
contract={'inheritedContractSha256':hashlib.sha256((repo/'docs/evidence/ticket115/full-contract.json').read_bytes()).hexdigest(),'baselineSource':base,'expected':expected,'ordinaryGroups':[dict(testClass='nl.davidengelen.redsquare.'+cls,partition=part,prefix=prefix,newInvocation=new,source=source_files[cls],methods=sorted(current[cls])) for cls,part,prefix,new in ordinary],'addedMethodsInInheritedGroups':old_methods,'addedProcessGoals':process,'addedIdentities':extra,'addedRequiredArchives':sorted(required),'expectedRecoveryTotals':{sdk:{k:v+{'normalCompletions':new_invocations+16,'deliberateInterruptions':8,'continuityRecords':8}[k] for k,v in inherited['expectedRecoveryTotals'][sdk].items()} for sdk in ['10','17']},'requiredArchivePaths':sorted(set(inherited['requiredArchivePaths'])|set(required)),'qualification':'Prospectively frozen additive goals. Preserve every inherited identity, goal, limit, archive and validator; eight new intentional unfinished-text starts are separate from completion. Inherited115 classification and126 queued-callback observation qualifications remain explicit.'}
assert len(contract['requiredArchivePaths'])==len(inherited['requiredArchivePaths'])+len(required)
(packet/'full-contract.json').write_text(json.dumps(contract,indent=2)+'\n')
print({'addedIdentities':len(extra),'newClasses':len({r['testClass'] for r in extra}-{r['testClass'] for r in inherited['expected']['17']}),'newInstrumentationInvocations':new_invocations,'newArchives':len(required),'classCount':len({r['testClass'] for r in expected['17']}),'expectedRecoveryTotals':contract['expectedRecoveryTotals'],'expectedIdentities':{sdk:sum(r['occurrences'] for r in rows) for sdk,rows in expected.items()}})

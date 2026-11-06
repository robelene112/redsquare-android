"""Retain exact-source native Chat grouping, original reading and original actions evidence."""
from pathlib import Path
from collections import Counter
import sys, json, subprocess, re, tarfile, hashlib, shutil, io, xml.etree.ElementTree as ET
from PIL import Image
SOURCE = '9f00665e3da30a15b50ed6e81d0d0f0ade500828'
EXPECTED = {
 'grouping': {
  'ChatGroupingPreviewTest': ['privateMessagesAtTheFiveMinuteBoundaryShareOneSenderHeader', 'theSameSenderAfterMoreThanFiveMinutesStartsAnotherHeader'],
  'GroupChatGroupingPreviewTest': ['consecutiveGroupMessagesShareAHeaderAndAnotherVerifiedSenderStartsANewOne'],
  **{k:['longNamesTextPhotoRepliesAndImeRemainReadable'] for k in ['PrivateGroupingNarrowTest','PrivateGroupingPixelTest','GroupGroupingNarrowTest','GroupGroupingPixelTest']}},
 'reading': {
  'ConversationViewportPreviewTest': ['openingACachedPrivateConversationExposesItsLatestMessage'],
  'PrivateConversationReadingPreviewTest': [], 'GroupConversationReadingPreviewTest': [],
  'GroupConversationPolishPreviewTest': ['groupDetailsAndMessageControlsRemainReachableWithNormalAndLargeText'],
  'ConversationFilteringPreviewTest': []},
 'actions': {'PrivateChatActionsPreviewTest': []}}
# Existing regression identities are pinned from their maintained source, passed as literal JSON below.
CATALOG = json.loads(Path(__file__).with_name('literal-identities.json').read_text())
assert len(sys.argv)==6, 'SDK KIND RUN RAW DEST'
sdk, kind, run, raw, dest = sys.argv[1:]
assert sdk in ['10','17'] and kind in EXPECTED
raw=Path(raw)/'android-native-proof'; root=raw/f'.scratch/device-evidence/api-{"29" if sdk=="10" else "37.0"}'
dest=Path(dest); dest.mkdir(parents=True, exist_ok=False)
metadata=json.loads(subprocess.check_output(['gh','run','view',run,'--repo','robelene112/redsquare-android','--json','databaseId,headSha,status,conclusion,url,jobs'],text=True))
assert metadata['status']=='completed' and metadata['conclusion']=='success', metadata
assert metadata['headSha']==SOURCE
assert len(metadata['jobs'])==1 and metadata['jobs'][0]['conclusion']=='success'
assert (raw/'.scratch/proof-source.txt').read_text().strip()==SOURCE
assert (raw/'.scratch/proof-workflow.txt').read_text().strip()==metadata['headSha']
actual=Counter(); failures=[]
for p in sorted(root.glob('*ui.txt')):
 fields={}
 for line in p.read_text().splitlines():
  f=re.match(r'^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$',line)
  c=re.match(r'^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$',line)
  if f:fields[f[1]]=f[2]
  elif c:
   if c[1]=='0': actual[(fields.get('class','').split('.')[-1],fields.get('test',''))]+=1
   elif c[1] in ['-1','-2','-3','-4']: failures.append((p.name,c[1],fields))
   fields={}
 assert p.read_text().count('INSTRUMENTATION_CODE: -1')==1,p
 assert re.search(r'^OK \([1-9][0-9]* tests?\)$',p.read_text(),re.M),p
 assert (p.with_name(p.stem+'-exit.txt')).read_text().strip()=='0',p
expected=Counter((cls, name) for cls,names in CATALOG[kind].items() for name in names)
assert not failures and actual==expected, {'missing':list((expected-actual).elements()),'extra':list((actual-expected).elements()),'failures':failures}
inventory=[]
for p in sorted(root.iterdir()):
 if p.suffix in ['.txt','.json','.properties'] and p.name not in ['emulator.log']:
  shutil.copyfile(p,dest/p.name)
 if p.suffix!='.tar':continue
 with tarfile.open(p) as t:
  for member in t.getmembers():
   if not member.isfile():continue
   name=Path(member.name)
   assert not name.is_absolute() and '..' not in name.parts
   data=t.extractfile(member).read()
   if name.suffix=='.png':
    with Image.open(io.BytesIO(data)) as im:im.verify()
   if name.suffix=='.xml':ET.fromstring(data)
   if name.suffix in ['.png','.xml','.json','.jsonl','.mp4','.txt']:
    out=dest/'originals'/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
    inventory.append(dict(file=str(out.relative_to(dest)),bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
for name in ['proof-source.txt','proof-workflow.txt','proof-runner.txt']:
 shutil.copyfile(raw/'.scratch'/name,dest/name)
summary=dict(source=SOURCE,sdk=sdk,scope=kind,run=int(run),methods=actual.total(),classes=len({c for c,m in actual}),identities=[dict(testClass=c,test=m,occurrences=n) for (c,m),n in sorted(actual.items())],normalCompletions=len(EXPECTED[kind]),inventory=inventory)
(dest/'run.json').write_text(json.dumps(metadata,indent=2)+'\n')
(dest/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ['inventory','identities']}))

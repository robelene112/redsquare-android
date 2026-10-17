from pathlib import Path
import tarfile,hashlib,xml.etree.ElementTree as E,json,shutil,sys
s=Path(sys.argv[1]);p=Path(sys.argv[2]);p.mkdir(parents=True,exist_ok=True)
xmls=pngs=0;inventory=[];unavailable=[]
def observe(name,data):
 global xmls,pngs
 if name.endswith('.xml'):
  try:E.fromstring(data);xmls+=1
  except E.ParseError:
   assert name in ['live-owner/wallet-first-failure.xml','live-official/wallet-first-failure.xml']
   assert data.startswith(b'cat: cache/live-feed-evidence/wallet-first-failure.xml: No such file or directory')
   unavailable.append(dict(path=name,reason=data.decode().strip()))
 if name.endswith('.png'):
  if data.startswith(b'\x89PNG\r\n\x1a\n'):pngs+=1
  else:
   assert name in ['live-owner/wallet-first-failure.png','live-official/wallet-first-failure.png']
   assert data.startswith(b'cat: cache/live-feed-evidence/wallet-first-failure.png: No such file or directory')
   unavailable.append(dict(path=name,reason=data.decode().strip()))
for f in sorted(s.rglob('*')):
 if not f.is_file():continue
 name=str(f.relative_to(s));data=f.read_bytes()
 inventory.append(dict(path=name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
 observe(name,data)
 if f.suffix=='.tar':
  try:
   with tarfile.open(f) as archive:
    for m in archive.getmembers():
     if m.isfile() and m.name.endswith(('.xml','.png')): observe(name+'!'+m.name,archive.extractfile(m).read())
  except tarfile.ReadError:
   assert f.name=='chat-image-history-evidence.tar'
   assert data.startswith(b'tar: chat-image-history-evidence: No such file or directory\n')
   unavailable.append(dict(path=name,reason='Missing optional capture directory; tar stderr plus padding, not an archive'))
   shutil.copyfile(f,p/f.name)
(p/'original-artifact-inventory.json').write_text(json.dumps(dict(root=str(s),files=inventory,xmls_parsed=xmls,pngs_including_tar_entries=pngs,unavailable_diagnostics=unavailable),indent=2)+'\n')
for name,paths in {
 'giphy-evidence.tar':['selected-credits','fresh-activity-credits','animated-restored-draft','search-newer-result'],
 'giphy-posting-evidence.tar':['owner-post-published-credits','owner-post-fresh-published-credits']
}.items():
 with tarfile.open(s/name) as archive:
  for stem in paths:
   for suffix in ['.png','.xml']:
    member=archive.getmember(name[:-4]+'/'+stem+suffix);assert member.isfile()
    d=p/'captures'/(stem+suffix);d.parent.mkdir(exist_ok=True);d.write_bytes(archive.extractfile(member).read())
print(json.dumps(dict(xmls=xmls,pngs=pngs,device_files=len(inventory),unavailable=unavailable)))

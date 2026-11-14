"""Inspect original ART dual-clock traces, preserving the sampling qualification."""
import argparse, collections, json, struct
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('trace',type=Path);p.add_argument('--expect',choices=['present','absent'],required=True)
a=p.parse_args();data=a.trace.read_bytes();end=data.index(b'*end\n')+5
header=data[:end].decode();assert 'data-file-overflow=false' in header and 'clock=dual' in header
threads_text=header.split('*threads\n')[1].split('*methods\n')[0]
threads={int(row.split('\t')[0]):row.split('\t')[1] for row in threads_text.splitlines() if '\t' in row}
main=[tid for tid,name in threads.items() if name=='main'];assert len(main)==1
methods={}
for row in header.split('*methods\n')[1].split('*end')[0].splitlines():
    if row.strip():
        fields=row.split('\t');methods[int(fields[0],16)]=(fields[1],fields[2])
magic,version,offset=struct.unpack_from('<4sHH',data,end);assert magic==b'SLOW' and version==3
size=struct.unpack_from('<H',data,end+16)[0];assert size==14
records=data[end+offset:];assert len(records)%size==0
stack=[];crypto=collections.Counter();merges=0
for pos in range(0,len(records),size):
    tid,method,cpu,wall=struct.unpack_from('<HIII',records,pos)
    if tid!=main[0]:continue
    action=method&3;method&=~3
    if action==0:
        item=methods[method];stack.append(item)
        if item==('nl.davidengelen.redsquare.vault.VaultLibrary','merge'):merges+=1
        if ('nl.davidengelen.redsquare.vault.VaultLibrary','merge') in stack and (
            item[0].startswith('nl.davidengelen.redsquare.saito.SaitoAccessScript') or
            item[0]=='nl.davidengelen.redsquare.saito.SaitoVaultRules' or item[0].startswith('org.bouncycastle.')):
            crypto['.'.join(item)]+=1
    else:
        assert action in (1,2)
        if stack and stack[-1]==methods[method]:stack.pop()
        else:raise AssertionError(('Unbalanced main trace',methods[method],stack[-3:]))
result=dict(trace=str(a.trace),mainThread=main[0],sampledMergeEntries=merges,mainCryptoEntriesUnderMerge=sum(crypto.values()),methods=dict(crypto),qualification='ART sampled call stacks during20native local refreshes; counts are observed spans, not exact invocations, physical frame rate or a general FPS gain.')
print(json.dumps(result,indent=2));assert merges>0
assert bool(crypto)==(a.expect=='present'), 'Unexpected cryptographic work on Main during library merge'

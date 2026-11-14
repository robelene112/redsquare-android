"""Inspect original sampled or instrumented ART dual-clock traces."""
import argparse, collections, json, struct
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('trace',type=Path);p.add_argument('--expect',choices=['present','absent'],required=True)
p.add_argument('--mode',choices=['sampling','method'],default='sampling')
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
stack=[];crypto=collections.Counter();merges=0;closed_merges=0
merge=('nl.davidengelen.redsquare.vault.VaultLibrary','merge')
for pos in range(0,len(records),size):
    tid,method,cpu,wall=struct.unpack_from('<HIII',records,pos)
    if tid!=main[0]:continue
    action=method&3;method&=~3
    if action==0:
        item=methods[method];stack.append(item)
        if item==merge:merges+=1
        if merge in stack and (
            item[0].startswith('nl.davidengelen.redsquare.saito.SaitoAccessScript') or
            item[0]=='nl.davidengelen.redsquare.saito.SaitoVaultRules' or item[0].startswith('org.bouncycastle.')):
            crypto['.'.join(item)]+=1
    else:
        assert action in (1,2)
        if stack and stack[-1]==methods[method]:
            if stack.pop()==merge:closed_merges+=1
        else:raise AssertionError(('Unbalanced main trace',methods[method],stack[-3:]))
qualification=('ART sampled call stacks during20native local refreshes; counts are observed spans, not exact invocations, physical frame rate or a general FPS gain.' if a.mode=='sampling' else
    'ART instrumented method entry/exit recording during20native local refreshes. All20Main merge spans must be complete. Instrumentation overhead is present; no physical frame-rate or general latency claim.')
result=dict(trace=str(a.trace),mainThread=main[0],traceMode=a.mode,observedMergeEntries=merges,observedMergeExits=closed_merges,mainCryptoEntriesUnderMerge=sum(crypto.values()),methods=dict(crypto),qualification=qualification)
if a.mode=='sampling':result['sampledMergeEntries']=merges
print(json.dumps(result,indent=2));assert merges>0
if a.mode=='method':assert merges==closed_merges==20, 'Require every complete native refresh merge span'
assert bool(crypto)==(a.expect=='present'), 'Unexpected cryptographic work on Main during library merge'

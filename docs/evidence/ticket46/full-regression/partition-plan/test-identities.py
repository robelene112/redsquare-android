from pathlib import Path
from collections import Counter
import re,json,sys

def identities(root):
    result=Counter()
    for p in sorted(root.rglob('*.txt')):
        name=p.relative_to(root).as_posix()
        if name=='profile-edit-ui.txt' and (root/'ProfileEditPreviewTest-ui.txt').exists():continue
        fields={}
        for line in p.read_text(errors='replace').splitlines():
            f=re.match(r'^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$',line)
            c=re.match(r'^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$',line)
            if f:fields[f[1]]=f[2]
            elif c:
                if c[1] in ['0','-4'] and 'class'in fields and 'test'in fields:
                    result[(name,fields['class'],fields['test'])]+=1
                fields={}
    return result

def rows(counter):
    return [dict(path=k[0],testClass=k[1],test=k[2],occurrences=n)for k,n in sorted(counter.items())]

if __name__=='__main__' and len(sys.argv)>1:
    assert sys.argv[1]=='--verify' and len(sys.argv)==5,'Use --verify CHAT_ROOT SOCIAL_ROOT WALLET_ROOT'
    expected_path=Path(__file__).with_name('expected17-identities.json')
    reference=json.loads(expected_path.read_text())
    expected=Counter({(r['path'],r['testClass'],r['test']):r['occurrences']for r in reference['expected']})
    roots=[Path(v)for v in sys.argv[2:]]
    markers=[]
    actual=Counter()
    for root in roots:
        name=(root/'partition.txt').read_text().strip()
        assert (root/'partition-complete.txt').read_text().strip()==name,'Partition did not complete'
        markers.append(name)
        actual.update(identities(root))
    assert sorted(markers)==['chat','social','wallet'],'Require exactly the three distinct full partitions'
    missing,extra=expected-actual,actual-expected
    print(json.dumps({'result':'PASS'if not missing and not extra else 'FAIL',
        'expectedCount':expected.total(),'actualCount':actual.total(),'partitions':markers,
        'missing':rows(missing),'extra':rows(extra)},indent=2))
    sys.exit(0 if not missing and not extra else 1)

if __name__=='__main__':
    old10=Path('/mnt/storage/redsquare-android-artifacts/chat44-full10-final-39048729211/.scratch/device-evidence/api-29')
    old17=Path('/mnt/storage/redsquare-android-artifacts/chat44-full17-final-39048730979/.scratch/device-evidence/api-37.0')
    new10=Path('/mnt/storage/redsquare-android-artifacts/private46-full10-39069243011/.scratch/device-evidence/api-29')
    a,b,c=map(identities,[old10,old17,new10])
    missing=a-c
    added=c-a
    assert not missing,rows(missing)
    expected=b+added
    out={'method':'Before new partition execution: accepted complete Android17 #44 identities plus actual additions in complete Android10 #46 versus complete Android10 #44. Multiplicity and relative log paths retained; assumptions count as executed identities; diagnostic-only statuses and duplicate profile aggregate excluded.',
        'oldAndroid10':str(old10),'oldAndroid17':str(old17),'currentAndroid10':str(new10),
        'oldAndroid10Count':a.total(),'oldAndroid17Count':b.total(),'currentAndroid10Count':c.total(),
        'added':rows(added),'expectedCount':expected.total(),'expected':rows(expected)}
    print(json.dumps(out,indent=2))

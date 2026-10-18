from pathlib import Path
from collections import Counter
import re,json,sys

def identities(root):
    result=Counter()
    for p in sorted(root.rglob('*.txt')):
        name=p.relative_to(root).as_posix()
        if name=='profile-edit-ui.txt' and (root/'ProfileEditPreviewTest-ui.txt').exists():
            classes=['ProfileEditPreviewTest','ProfilePublishingPreviewTest','ProfileMediaPreviewTest','ProfileNewEditPreviewTest']
            assert p.read_bytes()==b''.join((root/f'{c}-ui.txt').read_bytes()for c in classes),'Profile aggregate is not the exact duplicate concatenation'
            continue
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

if __name__ == '__main__':
    assert len(sys.argv) >= 3 and sys.argv[1] in ['--verify10', '--verify17'], 'Use --verify10 SDK_ROOT or --verify17 CHAT_ROOT SOCIAL_ROOT WALLET_ROOT'
    api = sys.argv[1][-2:]
    roots = [Path(v) for v in sys.argv[2:]]
    assert len(roots) == (1 if api == '10' else 3), 'Require the complete SDK job set'
    reference = json.loads(Path(__file__).with_name(f'expected{api}-identities.json').read_text())
    expected = Counter({(r['path'], r['testClass'], r['test']): r['occurrences'] for r in reference['expected']})
    actual = Counter()
    markers = []
    for root in roots:
        name = (root / 'partition.txt').read_text().strip()
        assert (root / 'partition-complete.txt').read_text().strip() == name, 'Partition did not complete'
        markers.append(name)
        actual.update(identities(root))
    assert sorted(markers) == (['all'] if api == '10' else ['chat', 'social', 'wallet']), 'Require exactly the complete distinct partition set'
    missing, extra = expected - actual, actual - expected
    print(json.dumps({'result': 'PASS' if not missing and not extra else 'FAIL', 'api': api,
        'expectedCount': expected.total(), 'actualCount': actual.total(), 'partitions': markers,
        'missing': rows(missing), 'extra': rows(extra)}, indent=2))
    sys.exit(0 if not missing and not extra else 1)

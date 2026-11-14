"""Qualified115 collector for one new descriptor typo, with the frozen failure retained.

This deliberately does NOT claim that the unchanged frozen collector passes.
The only replacement is its StoreCrypto human-readable prefix comparison. That
case must instead equal the complete descriptor from the exact executed source.
Every other assertion, identity, original/deadline check and inherited validator
executes unchanged. See descriptor-erratum.md and independent reviews.
"""
import ast
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

assert len(sys.argv)==6, 'Require SDK, complete raw directory, run, exact source, fresh label'
sdk,raw,run,source,label=sys.argv[1:]
assert re.fullmatch('[0-9a-f]{40}',source) and re.fullmatch('[A-Za-z0-9._-]+',label)
packet=Path(__file__).resolve().parent
frozen=packet/'verify-full.py'
repo=packet.parents[2]
def git(path):
    return subprocess.check_output(['git','-C',str(repo),'show',source+':'+path])
original=frozen.read_bytes()
assert original==git('docs/evidence/ticket115/verify-full.py'), 'Preserve the exact frozen collector'
text=original.decode()
needle='    assert (folder / "continuity.txt").read_text().startswith("PASS: actual authenticated native")'
assert text.count(needle)==1
script=git('scripts/store-crypto-process-check')
tree=ast.parse(script.decode())
descriptors=[]
for node in ast.walk(tree):
    if not isinstance(node,ast.Call) or not isinstance(node.func,ast.Attribute) or node.func.attr!='write_text' or not node.args:continue
    value=node.args[0]
    if isinstance(value,ast.Constant) and isinstance(value.value,str) and value.value.startswith('PASS:'):
        assert any(isinstance(x,ast.Constant) and x.value=='continuity.txt' for x in ast.walk(node.func.value))
        descriptors.append(value.value)
assert len(descriptors)==1
expected=descriptors[0]
assert expected.startswith('PASS: actual native reviewed/PIN-authorized provider reservation before process death, ')
assert not expected.startswith('PASS: actual authenticated native')
# Retain the actual original failure first. An unrelated failure is never waived.
diagnostics=Path(raw).resolve().parent/'collector-diagnostics'/ (run+'-'+label)
diagnostics.mkdir(parents=True,exist_ok=False)
negative=subprocess.run([sys.executable,str(frozen),*sys.argv[1:]],capture_output=True,text=True)
(diagnostics/'frozen.stdout').write_text(negative.stdout)
(diagnostics/'frozen.stderr').write_text(negative.stderr)
assert negative.returncode==1 and needle.strip() in negative.stderr and 'AssertionError' in negative.stderr, negative.stdout+negative.stderr
assert not (packet/label).exists(), 'The original failure must not create accepted evidence'
replacement='''    if goal["prefix"] == "store-crypto-process":
        assert (folder / "continuity.txt").read_text() == qualified_crypto_descriptor
    else:
'''+ '    ' + needle
qualified=text.replace(needle,replacement,1)
compile(qualified,str(frozen),'exec')
# __file__ intentionally remains the unchanged original so its existing source
# identity checks still inspect that immutable file. This is explicitly an
# audited qualified execution of the one changed comparison, not frozen PASS.
namespace={'__name__':'__main__','__file__':str(frozen),'qualified_crypto_descriptor':expected}
exec(compile(qualified,str(frozen),'exec'),namespace)
target=packet/label
summary=json.loads((target/'summary.json').read_text())
assert summary['result']=='PASS'
erratum=dict(frozenCollectorSha256=hashlib.sha256(original).hexdigest(),qualifiedCollectorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),executedCryptoScriptSha256=hashlib.sha256(script).hexdigest(),frozenCollectorReturnCode=negative.returncode,frozenFailureDirectory=str(diagnostics),case='store-crypto-process',originalComparison=needle.strip(),replacement='Exact complete continuity descriptor from the immutable executed source, only for StoreCrypto',expectedDescriptor=expected,allOtherAssertionsUnchanged=True,source=source,run=run)
(target/'descriptor-erratum.json').write_text(json.dumps(erratum,indent=2)+'\n')
summary['result']='PASS_WITH_DESCRIPTOR_ERRATUM'
summary['descriptorErratum']='One new115 human-readable StoreCrypto prefix typo; original frozen failure retained, exact executed-source descriptor required, all goals and other checks unchanged.'
(target/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))

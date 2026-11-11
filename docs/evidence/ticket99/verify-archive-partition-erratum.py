"""Audit original complete runs with the explicitly reviewed one-path catalog erratum.

The frozen verifier and contract stay byte-identical. Every original runtime,
identity, recovery and inherited visual check runs. Only the new public-Stack
archive's expected partition is corrected; original artifact paths never move.
"""
import ast
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile

assert len(sys.argv) == 6, 'Require SDK, complete raw directory, run, source and fresh label'
sdk, raw, run, source, label = sys.argv[1:]
assert sdk in ('10', '17')
packet = Path(__file__).resolve().parent
repo = packet.parents[2]
raw = Path(raw)
original = packet / 'verify-full.py'
original_bytes = original.read_bytes()
contract_bytes = (packet / 'full-contract.json').read_bytes()
verifier_sha = '2543eeaef87047deb0498de7f124ef48ed819e1e19bc7113ee221ec70ba6e651'
contract_sha = '1b44806255cddd5aa88772632c681cd39313fcb6ff8b5d60fb816f4f94464d6d'
assert hashlib.sha256(original_bytes).hexdigest() == verifier_sha
assert hashlib.sha256(contract_bytes).hexdigest() == contract_sha
for name, contents in [('verify-full.py', original_bytes), ('full-contract.json', contract_bytes)]:
    assert subprocess.check_output(['git', '-C', str(repo), 'show', source + ':docs/evidence/ticket99/' + name]) == contents
metadata = json.loads(subprocess.check_output(['gh', 'run', 'view', run, '--repo',
    'robelene112/redsquare-android', '--json', 'headSha,status,conclusion,jobs']))
assert metadata['headSha'] == source and metadata['status'] == 'completed' and metadata['conclusion'] == 'success'
assert len(metadata['jobs']) == 3 and all(j['conclusion'] == 'success' for j in metadata['jobs'])
assert {j['name'] for j in metadata['jobs']} == {'native-proof (' + p + ')' for p in ('chat', 'social', 'wallet')}
actual = set()
for part in ('chat', 'social', 'wallet'):
    artifact = raw / ('android-native-proof-' + part)
    for name in ('proof-source.txt', 'proof-workflow.txt'):
        assert (artifact / '.scratch' / name).read_text().strip() == source
    device = artifact / '.scratch/device-evidence' / ('api-29' if sdk == '10' else 'api-37.0')
    for name in ('partition.txt', 'partition-complete.txt'):
        assert (device / name).read_text().strip() == part
    actual.update(part + '/' + p.relative_to(device).as_posix() for p in device.rglob('*.tar'))
expected = json.loads(contract_bytes)['requiredArchivePaths']
old_path, actual_path = 'wallet/stack-public-evidence.tar', 'chat/stack-public-evidence.tar'
assert len(expected) == len(set(expected)) == len(actual) == 186
assert set(expected) - actual == {old_path}
assert actual - set(expected) == {actual_path}
baseline = json.loads(subprocess.check_output(['git', '-C', str(repo), 'show',
    source + ':docs/evidence/ticket97/full-contract.json']))['requiredArchivePaths']
assert len(baseline) == 181 and set(baseline) <= actual
public_archives = list(raw.rglob('stack-public-evidence.tar'))
assert len(public_archives) == 1 and not public_archives[0].is_symlink()
device = public_archives[0].parent
assert device.parent.parent.parent.name == 'android-native-proof-chat'
assert (device / 'stack-public-ui-exit.txt').read_text().strip() == '0'
log = (device / 'stack-public-ui.txt').read_text()
assert 'StackPublicReadTest' in log and 'approvedPublicNodesReturnAuthenticStackHistoryOrAnExplicitEmptyLibrary' in log
assert 'OK (1 test)' in log and 'FAILURES!!!' not in log
with tarfile.open(public_archives[0]) as archive:
    readiness = json.load(archive.extractfile('stack-public-evidence/readiness.json'))
assert len(readiness) == 2 and {r['backend'] for r in readiness} == {'owner', 'official'}
assert all(r['writes'] == 0 and r['rows'] >= r['authenticatedArticles'] >= 0 for r in readiness)
assert next(r for r in readiness if r['backend'] == 'official')['authenticatedArticles'] > 0
erratum = dict(expectedPath=old_path, actualPath=actual_path,
    correctedExpectedPath=actual_path, preservedInheritedArchivePaths=181,
    unchangedOtherCurrentArchivePaths=185, originalVerifierSha256=verifier_sha,
    originalContractSha256=contract_sha,
    adapterSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    actualArchiveSha256=hashlib.sha256(public_archives[0].read_bytes()).hexdigest(),
    publicReadiness=readiness,
    qualification='Post-run catalog correction only. Original files, identities, deadlines, assumptions, archive count, recovery and inherited validators are unchanged. No partition assembly or omitted runtime goal.')
corrected_paths = [actual_path if p == old_path else p for p in expected]
assert len(corrected_paths) == len(set(corrected_paths)) == 186 and set(corrected_paths) == actual

# Insert exactly one expected-path assignment and output-only qualification.
# The immutable original program still verifies its own bytes against source.
tree = ast.parse(original_bytes, filename=str(original))
contract_assignments = [i for i, node in enumerate(tree.body) if isinstance(node, ast.Assign)
    and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name) and node.targets[0].id == 'contract']
summary_assignments = [i for i, node in enumerate(tree.body) if isinstance(node, ast.Assign)
    and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name) and node.targets[0].id == 'summary']
assert len(contract_assignments) == len(summary_assignments) == 1
tree.body.insert(summary_assignments[0] + 1, ast.parse('summary.update(_erratum_summary_metadata)').body[0])
tree.body.insert(contract_assignments[0] + 1,
    ast.parse('contract["requiredArchivePaths"] = _corrected_expected_archive_paths').body[0])
ast.fix_missing_locations(tree)
namespace = dict(__file__=str(original), __name__='__main__',
    _corrected_expected_archive_paths=corrected_paths,
    _erratum_summary_metadata=dict(verification='frozen-goals-with-archive-partition-erratum',
        archivePartitionErratum=erratum,
        qualification='Frozen goals verified with independently reviewed archive-partition catalog erratum. Complete original three-part SDK execution; original raw artifact paths retained. No partial assembly or physical/provider/store acceptance.'))
exec(compile(tree, str(original), 'exec'), namespace)

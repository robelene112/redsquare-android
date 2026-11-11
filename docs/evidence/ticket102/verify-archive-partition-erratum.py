"""Audit original complete runs with the explicitly reviewed one-path catalog erratum.

The frozen verifier and contract stay byte-identical. Every original runtime,
identity, recovery and inherited visual check runs. Only the new native Stack-link
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
verifier_sha = 'dc73ab5f3c4f4249b1f2f8e7ba9fcea3739dcab6d6f8c0fb089c37e905e42341'
contract_sha = '9364a0eebd9181dd61ada2115abeda9b8158065da2c7d46f826b57e6cd45f4c8'
assert hashlib.sha256(original_bytes).hexdigest() == verifier_sha
assert hashlib.sha256(contract_bytes).hexdigest() == contract_sha
for name, contents in [('verify-full.py', original_bytes), ('full-contract.json', contract_bytes)]:
    assert subprocess.check_output(['git', '-C', str(repo), 'show', source + ':docs/evidence/ticket102/' + name]) == contents
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
old_path, actual_path = 'social/stack-link-evidence.tar', 'chat/stack-link-evidence.tar'
assert len(expected) == len(set(expected)) == len(actual) == 195
assert set(expected) - actual == {old_path}
assert actual - set(expected) == {actual_path}
baseline = json.loads(subprocess.check_output(['git', '-C', str(repo), 'show',
    source + ':docs/evidence/ticket101/full-contract.json']))['requiredArchivePaths']
assert len(baseline) == 194 and set(baseline) <= actual
link_archives = list(raw.rglob('stack-link-evidence.tar'))
assert len(link_archives) == 1 and not link_archives[0].is_symlink()
device = link_archives[0].parent
assert device.parent.parent.parent.name == 'android-native-proof-chat'
assert (device / 'stack-links-ui-exit.txt').read_text().strip() == '0'
log = (device / 'stack-links-ui.txt').read_text()
assert 'nl.davidengelen.redsquare.StackLinkPreviewTest' in log
assert 'aLinkedOlderArticleOpensNativelySurvivesRecreationAndReturnsToItsPostWithoutChangingTheSavedNode' in log
assert 'OK (1 test)' in log and 'FAILURES!!!' not in log
harness = subprocess.check_output(['git', '-C', str(repo), 'show', source + ':scripts/emulator-check']).decode()
chat_start = harness.index('if [[ "$full_chat" == true ]]; then')
chat_end = harness.index('if [[ "$full_chat" == true || "${TEST_SCOPE:-full}" == app-frame* ]]; then', chat_start)
assert 'verify_group_chat_native StackLinkPreviewTest 180 stack-links stack-link-evidence' in harness[chat_start:chat_end]
with tarfile.open(link_archives[0]) as archive:
    names = {member.name for member in archive.getmembers() if member.isfile()}
    required = {'stack-link-evidence/' + suffix for suffix in
        ('linked-article.png', 'linked-article.xml', 'returned-post.png', 'returned-post.xml')}
    assert required <= names
    assert all(archive.extractfile(name).read(8) == b'\x89PNG\r\n\x1a\n' for name in required if name.endswith('.png'))
    linked = archive.extractfile('stack-link-evidence/linked-article.xml').read()
    returned = archive.extractfile('stack-link-evidence/returned-post.xml').read()
    assert b'Original Markdown.' in linked
    assert b'Read this older Stack article.' in returned and b'Switch app: Red Square' in returned
erratum = dict(expectedPath=old_path, actualPath=actual_path,
    correctedExpectedPath=actual_path, preservedInheritedArchivePaths=194,
    unchangedOtherCurrentArchivePaths=194, originalVerifierSha256=verifier_sha,
    originalContractSha256=contract_sha,
    adapterSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    actualArchiveSha256=hashlib.sha256(link_archives[0].read_bytes()).hexdigest(),
    nativeLinkedArticleAndReturn=True,
    qualification='Post-run catalog correction only. Original files, identities, deadlines, assumptions, archive count, recovery and inherited validators are unchanged. No partition assembly or omitted runtime goal.')
corrected_paths = [actual_path if p == old_path else p for p in expected]
assert len(corrected_paths) == len(set(corrected_paths)) == 195 and set(corrected_paths) == actual

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

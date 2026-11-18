"""Prospective test-only closed-broker admission; every original native goal remains."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

packet = Path(__file__).resolve().parent
repo = packet.parents[3]
assert len(sys.argv) == 6 and sys.argv[1] in ['10', '17']
source = sys.argv[4]
raw = Path(sys.argv[2])
git = lambda *args: subprocess.check_output(['git', '-C', str(repo), *args])
sha = lambda data: hashlib.sha256(data).hexdigest()
bridge_path = packet / 'peer-close-source-bridge.json'
bridge = json.loads(bridge_path.read_text())
assert bridge['expectedWholeSdks'] == ['10', '17']
assert git('show', source + ':' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
assert git('show', 'HEAD:' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
changed = git('diff', '--name-only', bridge['productSource'], bridge['correctedFixtureSource'], '--', '.', ':!docs/evidence/ticket132/').decode().splitlines()
assert changed == bridge['changedNonPacketPaths'] == [
    'core/src/test/kotlin/nl/davidengelen/redsquare/saito/ControlledNodeOutageTest.kt',
    'test-fixtures/kotlin/nl/davidengelen/redsquare/ControlledFeedPeer.kt',
]
assert [fixture['path'] for fixture in bridge['fixtures']] == changed
for fixture in bridge['fixtures']:
    path = fixture['path']
    assert sha(git('show', bridge['productSource'] + ':' + path)) == fixture['beforeSha256']
    assert sha(git('show', bridge['correctedFixtureSource'] + ':' + path)) == fixture['afterSha256']
    assert sha(git('show', source + ':' + path)) == fixture['afterSha256']
assert sha(git('diff', bridge['productSource'], bridge['correctedFixtureSource'], '--', *changed)) == bridge['fixtureDiffSha256']
original = packet / 'verify-full.py'
assert sha(original.read_bytes()) == bridge['originalAdapterSha256']
assert sha((packet / 'source-profile.json').read_bytes()) == bridge['originalProfileSha256']
core_witnesses = {}


def verify_peer_close(sdk):
    assert sdk in bridge['expectedWholeSdks']
    method = 'anOversizedReplyPreparedBeforeAnOutageRemainsAnUnqueuedReplyAfterClose'
    for part in ['chat', 'social', 'wallet']:
        path = raw / ('android-native-proof-' + part) / 'core/build/reports/tests/test/classes/nl.davidengelen.redsquare.saito.ControlledNodeOutageTest.html'
        data = path.read_bytes()
        pattern = r'<tr>\s*<td class="success">' + re.escape(method) + r'</td>\s*<td class="success">[^<]+</td>\s*<td class="success">passed</td>\s*</tr>'
        assert len(re.findall(pattern, data.decode())) == 1, 'Require the actual added passing core case in every complete source job'
        core_witnesses[part] = dict(path=path.relative_to(raw).as_posix(), bytes=len(data), sha256=sha(data), method=method)


text = original.read_text()
for before, after, count in [
    ('docs/evidence/ticket132/candidate141/source-profile.json', 'docs/evidence/ticket132/candidate141/source-profile-peer-close.json', 1),
    ('docs/evidence/ticket132/candidate141/verify-full.py', 'docs/evidence/ticket132/candidate141/verify-full-peer-close.py', 2),
    ('    # Keep116\'s four exact feed placement scenes', '    verify_peer_close(sdk)\n    # Keep116\'s four exact feed placement scenes', 1),
    ('summary.update(inheritedCatalogAssessorSha256=', "summary.update(peerCloseCoreWitnesses=core_witnesses, fixtureOnlySourceBridgeSha256=sha((packet / 'peer-close-source-bridge.json').read_bytes()), inheritedCatalogAssessorSha256=", 1),
    ("    summary['qualification'] += ' The inherited131", "    summary['qualification'] += ' A separately admitted two-test-file broker-close correction preserves the unheld callback population and every receive goal, with three original passing core report witnesses. Signed141 app/runtime are unchanged and the original failed10 parent remains rejected. The inherited131", 1),
]:
    assert text.count(before) == count, (before, text.count(before))
    text = text.replace(before, after, count)
exec(compile(text, str(original) + ':prospective-peer-close-profile', 'exec'),
     dict(__file__=__file__, __name__='__main__', verify_peer_close=verify_peer_close, core_witnesses=core_witnesses))

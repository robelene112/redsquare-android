"""Prospective Android10 Store pointer readiness admission; original full inputs stay immutable."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

packet = Path(__file__).resolve().parent
repo = packet.parents[2]
assert len(sys.argv) == 6 and sys.argv[1] == '10'
source = sys.argv[4]
git = lambda *args: subprocess.check_output(['git', '-C', str(repo), *args])
sha = lambda data: hashlib.sha256(data).hexdigest()
bridge_path = packet / 'store-observer-source-bridge.json'
bridge = json.loads(bridge_path.read_text())
assert bridge['expectedWholeSdk'] == '10'
assert git('show', source + ':' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
assert git('show', 'HEAD:' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
changed = git('diff', '--name-only', bridge['productSource'], bridge['correctedFixtureSource'], '--', '.', ':!docs/evidence/ticket132/').decode().splitlines()
assert changed == bridge['changedNonPacketPaths'] == [bridge['fixturePath']]
assert sha(git('show', bridge['productSource'] + ':' + bridge['fixturePath'])) == bridge['beforeSha256']
assert sha(git('show', bridge['correctedFixtureSource'] + ':' + bridge['fixturePath'])) == bridge['afterSha256']
assert sha(git('show', source + ':' + bridge['fixturePath'])) == bridge['afterSha256']
assert sha(git('diff', bridge['productSource'], bridge['correctedFixtureSource'], '--', bridge['fixturePath'])) == bridge['fixtureDiffSha256']
original = packet / 'verify-full.py'
assert sha(original.read_bytes()) == bridge['originalAdapterSha256']
assert sha((packet / 'source-profile.json').read_bytes()) == bridge['originalProfileSha256']

def verify_store_input(view, sdk):
    assert sdk == '10'
    observed = json.loads((view / 'wallet/status-originals/store-catalogue-evidence/return-category-input.json').read_text())
    assert observed['activityWindowFocused'] is True and observed['completeStableBounds']
    assert observed['observations'] >= 2 and observed['positionChanges'] >= 0
    assert observed['budgetMillis'] == 15000 and 200 <= observed['setupMillis'] <= 15000

text = original.read_text()
for before, after, count in [
    ('docs/evidence/ticket132/source-profile.json', 'docs/evidence/ticket132/source-profile-store-observer.json', 1),
    ('docs/evidence/ticket132/verify-full.py', 'docs/evidence/ticket132/verify-full-store-observer.py', 2),
    ('    # Keep116\'s four exact feed placement scenes', '    verify_store_input(view, sdk)\n    # Keep116\'s four exact feed placement scenes', 1),
    ('summary.update(inheritedCatalogAssessorSha256=', "summary.update(fixtureOnlySourceBridgeSha256=sha((packet / 'store-observer-source-bridge.json').read_bytes()), inheritedCatalogAssessorSha256=", 1),
    ("    summary['qualification'] += ' The inherited131", "    summary['qualification'] += ' A separately admitted Android10 Store pointer observer preserves the original15s budget and one tap; signed product is unchanged and original failure remains rejected. The inherited131", 1),
]:
    assert text.count(before) == count, (before, text.count(before))
    text = text.replace(before, after, count)
exec(compile(text, str(original) + ':prospective-store-observer-profile', 'exec'),
     dict(__file__=__file__, __name__='__main__', verify_store_input=verify_store_input))

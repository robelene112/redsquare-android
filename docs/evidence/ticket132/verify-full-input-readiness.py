"""Prospective whole-SDK admission for two input-driver corrections; signed product unchanged."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

packet = Path(__file__).resolve().parent
repo = packet.parents[2]
assert len(sys.argv) == 6 and sys.argv[1] in ['10', '17']
source = sys.argv[4]
git = lambda *args: subprocess.check_output(['git', '-C', str(repo), *args])
sha = lambda data: hashlib.sha256(data).hexdigest()
bridge_path = packet / 'input-readiness-source-bridge.json'
bridge = json.loads(bridge_path.read_text())
assert bridge['expectedWholeSdks'] == ['10', '17']
assert git('show', source + ':' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
assert git('show', 'HEAD:' + str(bridge_path.relative_to(repo))) == bridge_path.read_bytes()
changed = git('diff', '--name-only', bridge['productSource'], bridge['correctedFixtureSource'], '--', '.', ':!docs/evidence/ticket132/').decode().splitlines()
assert changed == bridge['changedNonPacketPaths'] == [
    'app/src/androidTest/kotlin/nl/davidengelen/redsquare/PostPreviewTest.kt',
    'app/src/androidTest/kotlin/nl/davidengelen/redsquare/StoreCataloguePreviewTest.kt',
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


def verify_input_readiness(view, sdk):
    assert sdk in bridge['expectedWholeSdks']
    observed = json.loads((view / 'wallet/status-originals/store-catalogue-evidence/return-category-input.json').read_text())
    assert observed['activityWindowFocused'] is True and observed['completeStableBounds']
    assert observed['observations'] >= 2 and observed['positionChanges'] >= 0
    assert observed['budgetMillis'] == 15000 and 200 <= observed['setupMillis'] <= 15000
    retry = json.loads((view / 'social/status-originals/posting-evidence/legacy-retry-input.json').read_text())
    assert isinstance(retry['displayedBeforeInput'], bool)
    assert retry['displayedAtInput'] is True and retry['receivedBeforeInput'] == 1
    assert retry['unclippedBoundsBeforeInput'] and retry['unclippedBoundsAtInput']


text = original.read_text()
for before, after, count in [
    ('docs/evidence/ticket132/source-profile.json', 'docs/evidence/ticket132/source-profile-input-readiness.json', 1),
    ('docs/evidence/ticket132/verify-full.py', 'docs/evidence/ticket132/verify-full-input-readiness.py', 2),
    ('    # Keep116\'s four exact feed placement scenes', '    verify_input_readiness(view, sdk)\n    # Keep116\'s four exact feed placement scenes', 1),
    ('summary.update(inheritedCatalogAssessorSha256=', "summary.update(fixtureOnlySourceBridgeSha256=sha((packet / 'input-readiness-source-bridge.json').read_bytes()), inheritedCatalogAssessorSha256=", 1),
    ("    summary['qualification'] += ' The inherited131", "    summary['qualification'] += ' Two separately admitted test input observers preserve the original Store15s budget, one category tap and original retry/signature/next-draft goals; signed product is unchanged and both original full parents remain rejected. The inherited131", 1),
]:
    assert text.count(before) == count, (before, text.count(before))
    text = text.replace(before, after, count)
exec(compile(text, str(original) + ':prospective-input-readiness-profile', 'exec'),
     dict(__file__=__file__, __name__='__main__', verify_input_readiness=verify_input_readiness))

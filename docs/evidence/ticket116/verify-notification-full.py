"""Admit the Android10 notification-operator fixture while retaining signed131 code."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

assert len(sys.argv) in (6, 7) and sys.argv[1] == '10'
source = sys.argv[4]
packet = Path(__file__).resolve().parent
repo = packet.parents[2]
output_root = Path(sys.argv.pop()) if len(sys.argv) == 7 else packet


def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def sha(data):
    return hashlib.sha256(data).hexdigest()


profile_path = 'docs/evidence/ticket116/source-profile-notification10.json'
profile_bytes = (repo / profile_path).read_bytes()
profile = json.loads(profile_bytes)
assert git('show', source + ':' + profile_path) == profile_bytes
assert git('show', 'HEAD:' + profile_path) == profile_bytes
wrapper_path = 'docs/evidence/ticket116/verify-notification-full.py'
wrapper_bytes = Path(__file__).read_bytes()
assert sha(wrapper_bytes) == profile['wrapperSha256']
assert git('show', source + ':' + wrapper_path) == wrapper_bytes
assert git('show', 'HEAD:' + wrapper_path) == wrapper_bytes
bridge = profile['fixtureBridge']
manifest_path = 'docs/evidence/ticket116/candidate131.json'
manifest_bytes = git('show', source + ':' + manifest_path)
assert sha(manifest_bytes) == bridge['signedManifestSha256']
manifest = json.loads(manifest_bytes)
assert manifest['commit'] == bridge['signedSource']
assert manifest['versionCode'] == 131 and manifest['sha256'] == bridge['signedApkSha256']
changed = git('diff', '--name-only', bridge['signedSource'], source).decode().splitlines()
fixtures = set(bridge['allowedFixturePaths'])
assert fixtures == {'app/src/androidTest/kotlin/nl/davidengelen/redsquare/FilesharePreviewTest.kt'}
assert fixtures <= set(changed)
assert all(path in fixtures or path.startswith('docs/evidence/ticket116/') for path in changed), changed
adapter = packet / 'verify-full.py'
original = adapter.read_text()
assert sha(adapter.read_bytes()) == profile['adapterSha256']
span = "profile_path = 'docs/evidence/ticket116/source-profile.json'"
assert original.count(span) == 1
adapted = original.replace(span, "profile_path = '" + profile_path + "'", 1)
output_span = 'current_packet=packet, current_adapter_sha='
assert adapted.count(output_span) == 1
adapted = adapted.replace(output_span, 'current_packet=notification_output, current_adapter_sha=', 1)
# Only profile selection and the optional report destination are adapted.
# Every original source/time/hash/native check and evidence input is retained.
output_root.mkdir(parents=True, exist_ok=True)
exec(compile(adapted, str(adapter) + ':notification10-profile', 'exec'),
     dict(__file__=str(adapter), __name__='__main__', notification_output=output_root))
summary_path = output_root / sys.argv[5] / 'summary.json'
summary = json.loads(summary_path.read_text())
summary['result'] = 'CURRENT_SOURCE_PASS_WITH_INHERITED115_CLASSIFICATION_AND_NOTIFICATION_FIXTURE'
summary.update(notificationAdmissionWrapperSha256=sha(wrapper_bytes), fixtureBridge=bridge)
summary['qualification'] += ' Android10 changes only the owned notification test operator; all ordinary product source equals the signed131source. Android17 retains its separate exact original source/profile. No native goal/deadline is weakened.'
summary_path.write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary))

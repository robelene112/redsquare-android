"""Current-source admission and feed geometry on the immutable115 whole validator."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys

assert len(sys.argv) == 6, 'Require SDK, complete raw directory, run, exact source and fresh label'
sdk, raw, run, source, label = sys.argv[1:]
packet = Path(__file__).resolve().parent
repo = packet.parents[2]


def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def sha(data):
    return hashlib.sha256(data).hexdigest()


profile_path = 'docs/evidence/ticket116/source-profile.json'
profile_bytes = (repo / profile_path).read_bytes()
profile = json.loads(profile_bytes)
assert git('show', source + ':' + profile_path) == profile_bytes
assert git('show', 'HEAD:' + profile_path) == profile_bytes
adapter_path = 'docs/evidence/ticket116/verify-full.py'
assert git('show', source + ':' + adapter_path) == Path(__file__).read_bytes()
assert git('show', 'HEAD:' + adapter_path) == Path(__file__).read_bytes()
assert sha(Path(__file__).read_bytes()) == profile['adapterSha256']
reference = profile['productSource']
subprocess.run(['git', '-C', str(repo), 'merge-base', '--is-ancestor', reference, source], check=True)
changed = git('diff', '--name-only', reference, source).decode().splitlines()
assert all(path.startswith('docs/evidence/ticket116/') for path in changed), changed
for path, expected in profile['nativeTrees'].items():
    assert git('rev-parse', source + ':' + path).decode().strip() == expected
for path, expected in profile['inheritedSha256'].items():
    data = (repo / path).read_bytes()
    assert sha(data) == expected and git('show', source + ':' + path) == data
metadata = json.loads(subprocess.check_output(['gh', 'run', 'view', run, '--repo',
    'robelene112/redsquare-android', '--json', 'createdAt']))
frozen_at = git('log', '-1', '--format=%cI', source, '--', profile_path, adapter_path).decode().strip()
assert datetime.fromisoformat(frozen_at) < datetime.fromisoformat(metadata['createdAt'].replace('Z', '+00:00'))


def validate_feed_feedback(view):
    folder = view / 'social/status-originals/feed-refresh-evidence'
    results = []
    for name in ('reading-during-pending-refresh', 'collapsed-reading', 'results-applied', 'recovered-results'):
        measurement = json.loads((folder / (name + '-feedback-position.json')).read_text())
        assert abs(measurement['tabsBottom'] - measurement['feedbackTop']) <= 1, (name, measurement)
        assert abs(measurement['feedbackBottom'] - measurement['viewportTop']) <= 1, (name, measurement)
        assert measurement['feedbackTop'] <= measurement['noticeTop'] < measurement['noticeBottom'] <= measurement['feedbackBottom']
        assert measurement['feedbackBottom'] < measurement['viewportBottom']
        assert (folder / (name + '.png')).stat().st_size > 1000
        results.append(dict(scene=name, **measurement))
    return results


inherited = repo / 'docs/evidence/ticket115/verify-qualified-full.py'
text = inherited.read_text()
# Only these five exact spans change. All native, archive, identity, recovery,
# preflight, speech, motion, media and downstream validators execute unchanged.
adaptations = [
    ('assert source in erratum["nativeSources"]',
     'assert current_source_admitted'),
    ('temporary.cleanup()\ntarget = packet / output_label',
     'feed_geometry = validate_feed_feedback(view)\ntemporary.cleanup()\ntarget = current_packet / output_label'),
    ('"result": "QUALIFIED_PASS_IDENTITY_CLASSIFICATION"',
     '"result": "CURRENT_SOURCE_PASS_WITH_INHERITED115_CLASSIFICATION"'),
    ('"assessorSha256": digest(Path(__file__)), "erratumSha256": digest(erratum_path),',
     '"assessorSha256": digest(Path(__file__)), "erratumSha256": digest(erratum_path), '
     '"adapterSha256": current_adapter_sha, "sourceProfileSha256": current_profile_sha, "feedGeometry": feed_geometry,'),
    ('Separate qualified assessment; original frozen collector did not pass. One complete successful native primary, all3partitions.',
     'Current116source assessment with inherited115interruption classification. The115frozen collector is not claimed to pass. One complete successful native primary, all3partitions.'),
]
assert len(adaptations) == 5
for original, replacement in adaptations:
    assert text.count(original) == 1, ('Inherited span changed', original)
    text = text.replace(original, replacement, 1)
namespace = dict(__file__=str(inherited), __name__='__main__', current_source_admitted=True,
    current_packet=packet, current_adapter_sha=sha(Path(__file__).read_bytes()),
    current_profile_sha=sha(profile_bytes), validate_feed_feedback=validate_feed_feedback)
exec(compile(text, str(inherited) + ':explicit116-adaptation', 'exec'), namespace)

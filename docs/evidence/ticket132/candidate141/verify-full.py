"""Prospective additive131 admission on every immutable115 validator and116 geometry goal."""
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile

assert len(sys.argv) == 6, 'Require SDK, complete three-part raw directory, run, exact source and fresh label'
sdk, raw, run, source, label = sys.argv[1:]
packet = Path(__file__).resolve().parent
repo = packet.parents[3]

def git(*args):return subprocess.check_output(['git', '-C', str(repo), *args])
def sha(data):return hashlib.sha256(data).hexdigest()
profile_path = 'docs/evidence/ticket132/candidate141/source-profile.json'
profile_bytes = (repo / profile_path).read_bytes()
profile = json.loads(profile_bytes)
for path in [profile_path, 'docs/evidence/ticket132/candidate141/full-contract.json', 'docs/evidence/ticket132/candidate141/verify-full.py', 'docs/evidence/ticket132/candidate141/build-contract.py']:
    assert git('show', source + ':' + path) == (repo / path).read_bytes()
    assert git('show', 'HEAD:' + path) == (repo / path).read_bytes()
assert sha(Path(__file__).read_bytes()) == profile['adapterSha256']
assert sha((packet / 'build-contract.py').read_bytes()) == profile['catalogBuilderSha256']
assert sha((packet / 'full-contract.json').read_bytes()) == profile['contractSha256']
reference = profile['productSource']
subprocess.run(['git', '-C', str(repo), 'merge-base', '--is-ancestor', reference, source], check=True)
assert all(p.startswith('docs/evidence/ticket132/') for p in git('diff', '--name-only', reference, source).decode().splitlines())
for path, expected in profile['nativeTrees'].items():assert git('rev-parse', source + ':' + path).decode().strip() == expected
for path, expected in profile['inheritedSha256'].items():
    assert sha((repo / path).read_bytes()) == expected
    assert git('show', source + ':' + path) == (repo / path).read_bytes()
metadata = json.loads(subprocess.check_output(['gh', 'run', 'view', run, '--repo', 'robelene112/redsquare-android', '--json', 'createdAt']))
frozen_at = git('log', '-1', '--format=%cI', source, '--', profile_path, 'docs/evidence/ticket132/candidate141/full-contract.json', 'docs/evidence/ticket132/candidate141/verify-full.py', 'docs/evidence/ticket132/candidate141/build-contract.py').decode().strip()
assert datetime.fromisoformat(frozen_at) < datetime.fromisoformat(metadata['createdAt'].replace('Z', '+00:00'))
addition = json.loads((packet / 'full-contract.json').read_text())


def verify_runtime_sources(contract):
    for path, old in contract['runtimePreflightSourceSha256'].items():
        current = (repo / path).read_bytes()
        assert git('show', source + ':' + path) == current
        if path != 'scripts/emulator-check':assert sha(current) == old
        else:
            original = git('show', profile['preflightSource'] + ':' + path)
            assert sha(original) == old and sha(current) == profile['harnessSha256']
            marker = b'if [[ "${TEST_SCOPE:-full}" == candidate-update* ]]'
            assert original.count(marker) == current.count(marker) == 1
            assert original.split(marker)[0] == current.split(marker)[0], 'Heap/provisioning prefix changed'


def extend_contract(original):
    assert sha(git('show', source + ':docs/evidence/ticket115/full-contract.json')) == addition['inheritedContractSha256']
    result = dict(original)
    for platform in ['10', '17']:
        before = Counter({(r['path'], r['testClass'], r['test']):r['occurrences'] for r in original['expected'][platform]})
        before = Counter({(p, c, 'removingTheLocalRecordRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost' if c == 'nl.davidengelen.redsquare.PostPreviewTest' and m == 'startingAnotherDraftRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost' else m): n for (p, c, m), n in before.items()})
        added = Counter({(r['path'], r['testClass'], r['test']):r['occurrences'] for r in addition['addedIdentities']})
        after = Counter({(r['path'], r['testClass'], r['test']):r['occurrences'] for r in addition['expected'][platform]})
        assert not before.keys() & added.keys() and after == before + added
        assert addition['expectedRecoveryTotals'][platform] == {key:value + {'normalCompletions':46, 'deliberateInterruptions':11, 'continuityRecords':11}[key] for key,value in original['expectedRecoveryTotals'][platform].items()}
    assert len(addition['addedProcessGoals']) == 11
    assert len(addition['addedRequiredArchives']) == 35
    assert set(addition['requiredArchivePaths']) == set(original['requiredArchivePaths']) | set(addition['addedRequiredArchives'])
    assert not set(original['requiredArchivePaths']) & set(addition['addedRequiredArchives'])
    result.update(expected=addition['expected'], expectedRecoveryTotals=addition['expectedRecoveryTotals'], requiredArchivePaths=addition['requiredArchivePaths'])
    return result


def validate_added_process(raw, sdk, expected, terminal, interrupted, classify):
    root = Path(raw) / 'android-native-proof-social/.scratch/device-evidence' / ('api-29' if sdk == '10' else 'api-37.0')
    assert interrupted.total() == 5, 'Keep all five inherited115 module starts'
    for goal in addition['addedProcessGoals']:
        folder = root / goal['prefix']
        pending = json.loads((folder / 'pending-checkpoint.json').read_text())
        recovered = json.loads((folder / 'recovered-checkpoint.json').read_text())
        assert pending['phase'] == 'pending' and pending['mode'] == goal['mode'] and pending['pid'] > 0
        assert recovered == dict(pending, phase='recovered', recoveredPid=recovered['recoveredPid'])
        assert recovered['recoveredPid'] > 0 and recovered['recoveredPid'] != pending['pid']
        assert (folder / 'continuity.txt').read_text() == 'PASS: ' + goal['mode'] + ': actual background process loss, new PID, exact unfinished native text, unchanged explicit draft, identity/node retained, zero publication.\n'
        assert (folder / 'capture-status.txt').read_text().splitlines()[0] == '0'
        for filename in ['recovered.txt', 'restored.txt']:
            text = (folder / filename).read_text()
            assert 'OK (1 test)' in text and 'FAILURES!!!' not in text
        cls, method = classify((folder / 'interrupted.txt').read_text(), goal['testClass'], goal['methods']['interrupted.txt'])
        key = (goal['prefix'] + '/interrupted.txt', cls, method)
        assert expected[key] == 1 and terminal[key] == interrupted[key] == 0
        interrupted[key] = 1
    assert interrupted.total() == 16


def validate_added_evidence(view, sdk):
    posts = view / 'social/status-originals/post-submission-evidence'
    observed = dict(line.split('=', 1) for line in (posts / 'fresh-held-original-observation.txt').read_text().splitlines())
    assert int(observed['heldOriginalQueries']) > 0 and observed['mainThreadHeartbeat'] == 'true' and observed['transmitted'] == '0'
    for scene in ['fresh-held-original-ready', 'fresh-independent-text-and-image', 'reopened-exact-original']:
        assert (posts / (scene + '.png')).stat().st_size > 1000
        assert (posts / (scene + '.xml')).stat().st_size > 0
    store_input = json.loads((view / 'wallet/status-originals/store-catalogue-evidence/return-category-input.json').read_text())
    assert store_input['activityWindowFocused'] is True and store_input['completeStableBounds']
    assert store_input['observations'] >= 2 and store_input['positionChanges'] >= 0
    assert store_input['budgetMillis'] == 15000 and 200 <= store_input['setupMillis'] <= 15000
    retry = json.loads((view / 'social/status-originals/posting-evidence/legacy-retry-input.json').read_text())
    assert isinstance(retry['displayedBeforeInput'], bool)
    assert retry['displayedAtInput'] is True and retry['receivedBeforeInput'] == 1
    assert retry['unclippedBoundsBeforeInput'] and retry['unclippedBoundsAtInput']
    late = json.loads((view / 'social/status-originals/read-cancellation-evidence/late-oversized.json').read_text())
    assert late['cancelled'] is True and late['cancelledSuccessfulRead'] is False
    assert late['lateBodyBytes'] > 16 * 1024 * 1024 and late['replayed'] == 2
    assert late['activeSuccessfulRead'] is True and late['activeArticles'] == 1
    assert late['connectionsBefore'] == late['connectionsAfter'] and late['connectionsBefore'] > 0
    assert late['publications'] == 0
    # Keep116's four exact feed placement scenes, without substituting its older input profile.
    folder = view / 'social/status-originals/feed-refresh-evidence'
    for scene in ['reading-during-pending-refresh', 'collapsed-reading', 'results-applied', 'recovered-results']:
        item = json.loads((folder / (scene + '-feedback-position.json')).read_text())
        assert abs(item['tabsBottom'] - item['feedbackTop']) <= 1
        assert abs(item['feedbackTop'] - item['viewportTop']) <= 1
        assert item['feedbackTop'] <= item['noticeTop'] < item['noticeBottom'] <= item['feedbackBottom'] < item['viewportBottom']
        assert (folder / (scene + '.png')).stat().st_size > 1000
    photos = view / 'social/status-originals/photo-pages-evidence'
    for mode in ['feed', 'search', 'older-search', 'following', 'large-rows', 'multi-search']:
        data = json.loads((photos / (mode + '-reads.json')).read_text())
        events = data['callbacks']; assert len(events) < 256
        queued = sum(event.get('queued') is True and event['bytes'] > 16 * 1024 * 1024 for event in events)
        assert queued == data['oversizedReplies']
        assert queued == 0 if mode == 'multi-search' else 1 <= queued <= 4 if mode == 'large-rows' else queued == 1
        assert data['reachedPosts'] == (8 if mode == 'large-rows' else 20) and data['publications'] == 0
        if mode == 'following':assert abs(data['cachedAnchorBefore'] - data['cachedAnchorAfter']) <= 2
    # The physical29 Cancel fixture keeps its original5000ms goal and truthful owned cleanup.
    if sdk == '10':
        files = view / 'chat/status-originals/fileshare-evidence'
        context = json.loads((files / 'cancel-shade-fixtures.json').read_text())
        lookup = json.loads((files / 'cancel-lookup.json').read_text())
        restored = json.loads((files / 'cancel-shade-restoration.json').read_text())
        assert context['messageCount'] >= 3 and context['ownedMessages'] == max(0, 3 - context['existingMessages'])
        assert lookup['budgetMillis'] == 5000 and 0 <= lookup['elapsedMillis'] <= 5000
        assert restored == dict(ownedChannelRemoved=True, ownedNotificationsRemoved=True)
        geometry_path = files / 'cancel-scroll-geometry.jsonl'
        if geometry_path.exists():
            for line in geometry_path.read_text().splitlines():
                point = json.loads(line)
                assert point['gestureTop'] >= max(point['listTop'], point['headerBottom']) + 8
                assert point['gestureBottom'] <= min(point['listBottom'], point['shelfTop']) - 8
                assert point['gestureBottom'] - point['gestureTop'] >= 48
                if point['messageBodyBottom'] >= 0:assert point['gestureBottom'] <= point['messageBodyBottom'] - 8



parent_catalog = json.loads((repo / 'docs/evidence/ticket131/catalog-erratum/inputs.json').read_text())
erratum = dict(parent_catalog, logicalArchiveRequirements=286, physicalArchives=277,
               normalCompletions={'10':391, '17':392})
raw = Path(raw)
# Keep physical originals and the logical capture requirements separate. No
# missing named TAR is manufactured or substituted into the original raw tree.
witnesses = {}
member_index = {}
for logical, requirement in erratum["archiveAliases"].items():
    part, name = requirement["archive"].split("/", 1)
    archive_path = raw / ("android-native-proof-" + part) / ".scratch/device-evidence" / (
        "api-29" if sdk == "10" else "api-37.0") / name
    archive_sha = sha(archive_path.read_bytes())
    if requirement["archive"] not in member_index:
        members = {}
        with tarfile.open(archive_path) as archive:
            for member in archive.getmembers():
                path = Path(member.name)
                assert not path.is_absolute() and ".." not in path.parts
                assert not member.issym() and not member.islnk()
                if member.isfile():
                    data = archive.extractfile(member).read()
                    assert member.name not in members
                    members[member.name] = {"bytes": len(data), "sha256": sha(data)}
        member_index[requirement["archive"]] = members
    members = member_index[requirement["archive"]]
    assert requirement["witnesses"] and len(set(requirement["witnesses"])) == len(requirement["witnesses"])
    assert all(name in members and members[name]["bytes"] > 0 for name in requirement["witnesses"]), logical
    witnesses[logical] = dict(archive=requirement["archive"], archiveSha256=archive_sha,
                             witnesses={name: members[name] for name in requirement["witnesses"]})

def qualify_catalog(original):
    result = dict(original)
    result["expected"] = {}
    for platform, rows in original["expected"].items():
        mapped = []
        moved = set()
        for row in rows:
            key = (row["path"], row["testClass"], row["test"])
            selected = [move for move in erratum["identityPathMoves"]
                        if key == (move["from"], move["testClass"], move["test"])]
            assert len(selected) <= 1
            if selected:
                assert row["occurrences"] == 1
                row = dict(row, path=selected[0]["to"])
                moved.add(key)
            mapped.append(row)
        assert len(moved) == 2
        assert Counter((row["testClass"], row["test"], row["occurrences"]) for row in mapped) == Counter(
            (row["testClass"], row["test"], row["occurrences"]) for row in rows)
        assert len({(row["path"], row["testClass"], row["test"]) for row in mapped}) == len(mapped)
        result["expected"][platform] = mapped
    result["expectedRecoveryTotals"] = {platform: dict(totals) for platform, totals in original["expectedRecoveryTotals"].items()}
    for platform, totals in result["expectedRecoveryTotals"].items():
        assert totals["normalCompletions"] + 1 == erratum["normalCompletions"][platform]
        totals["normalCompletions"] += 1
    logical = original["requiredArchivePaths"]
    assert len(logical) == erratum["logicalArchiveRequirements"]
    assert set(erratum["archiveAliases"]) <= set(logical)
    physical = {erratum["archiveAliases"].get(name, {"archive": name})["archive"] for name in logical}
    assert len(physical) == erratum["physicalArchives"]
    result["requiredArchivePaths"] = sorted(physical)
    return result


def emit_qualified_summary(summary, target):
    summary.update(inheritedCatalogAssessorSha256=parent_catalog['qualifiedAssessorSha256'],
                   inheritedFrozenAdapterPassed=False,
                   inheritedFrozenFailureSha256=sha((repo / ('docs/evidence/ticket131/catalog-erratum/frozen-failure' + sdk + '.txt')).read_bytes()),
                   logicalArchiveRequirements=286, physicalArchives=277)
    summary['qualification'] += ' The inherited131 two identity path moves, one disjoint invocation count and ten original archive aliases remain explicit. Its frozen original collector did not pass. No native goal, deadline or physical archive is invented or waived.'
    (target / 'logical-archive-witnesses.json').write_text(json.dumps(witnesses, indent=2) + '\n')
    (target / 'archive-alias-original-members.json').write_text(json.dumps(member_index, indent=2) + '\n')
    (target / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary))

inherited = repo / 'docs/evidence/ticket115/verify-qualified-full.py'
text = inherited.read_text()
adaptations = [
 ('assert source in erratum["nativeSources"]', 'assert current_source_admitted'),
 ('for path, checksum in contract["runtimePreflightSourceSha256"].items():\n    assert digest(repo / path) == checksum\n    assert git("show", source + ":" + path) == (repo / path).read_bytes()', 'verify_runtime_sources(contract)'),
 ('metadata = json.loads(subprocess.check_output([', 'contract = extend_contract(contract)\nmetadata = json.loads(subprocess.check_output(['),
 ('actual.update(interrupted_start_identities)', 'validate_added_process(raw, sdk, expected, terminal_identities, interrupted_start_identities, verified_interruption_identity)\nactual.update(interrupted_start_identities)'),
 ('temporary.cleanup()\ntarget = packet / output_label', 'validate_added_evidence(view, sdk)\ntemporary.cleanup()\ntarget = current_packet / output_label'),
 ('"result": "QUALIFIED_PASS_IDENTITY_CLASSIFICATION"', '"result": "INTEGRATED132_PASS_WITH_INHERITED115_126_AND131_CATALOG_QUALIFICATIONS"'),
 ('"verifiedModuleInterruptedStarts": interrupted_start_identities.total(),', '"verifiedModuleInterruptedStarts": 5, "verifiedUnfinishedTextInterruptedStarts": 11, "verifiedIntentionalStarts": interrupted_start_identities.total(),'),
 ('"assessorSha256": digest(Path(__file__)), "erratumSha256": digest(erratum_path),', '"assessorSha256": digest(Path(__file__)), "erratumSha256": digest(erratum_path), "adapterSha256": current_adapter_sha, "sourceProfileSha256": current_profile_sha, "additiveContractSha256": current_contract_sha,'),
 ('Separate qualified assessment; original frozen collector did not pass. One complete successful native primary, all3partitions. Exact five contracted intentional-start observations are separate from terminal completions/assumptions; all original goals and remaining validators retained. Originals remain external. No partial assembly or physical/provider/store acceptance.', 'Current132 additive-source assessment with inherited115 classification and126 queued-callback observation qualifications. The old frozen collector is not claimed to pass. One complete successful current-source primary, all3partitions. Five inherited module and eleven added unfinished-text intentional starts remain separate from terminal completions/assumptions. Every original goal and downstream validator remains. No prefix assembly or physical/provider/store acceptance.'),
]
for original, replacement in adaptations:
    assert text.count(original) == 1, ('Inherited span changed', original)
    text = text.replace(original, replacement, 1)
emission = '(target / "summary.json").write_text(json.dumps(summary, indent=2) + "\\n")\nprint(json.dumps(summary))\n'
assert text.count(emission) == 1 and text.endswith(emission)
text = text.replace(emission, 'emit_qualified_summary(summary, target)\n', 1)
namespace = dict(__file__=str(inherited), __name__='__main__', current_source_admitted=True, current_packet=packet,
    current_adapter_sha=sha(Path(__file__).read_bytes()), current_profile_sha=sha(profile_bytes), current_contract_sha=sha((packet / 'full-contract.json').read_bytes()),
    emit_qualified_summary=emit_qualified_summary, verify_runtime_sources=verify_runtime_sources, extend_contract=lambda inherited: qualify_catalog(extend_contract(inherited)), validate_added_process=validate_added_process, validate_added_evidence=validate_added_evidence)
exec(compile(text, str(inherited) + ':explicit131-additive-adaptation', 'exec'), namespace)

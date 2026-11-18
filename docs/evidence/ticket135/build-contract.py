"""Freeze the six additive #133/#135 native goals over accepted beta141 inputs."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess

packet = Path(__file__).resolve().parent
repo = packet.parents[2]
source = '278d1a1a41f8a8eec5b0e0ff2b7d9ed133fce23a'
baseline = '4c661c926acc758e861f086400da099f57ce2cd9'
parent_packet = 'docs/evidence/ticket132/candidate141/'
current_packet = 'docs/evidence/ticket135/'


def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def read(path):
    return git('show', source + ':' + path)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def methods_at(revision, path):
    if not git('ls-tree', revision, '--', path):
        return set()
    text = git('show', revision + ':' + path).decode()
    classes = list(re.finditer(r'^class (\w+)', text, re.M))
    return {(match[1], method) for index, match in enumerate(classes)
            for method in re.findall(r'@Test\s+fun\s+(\w+)', text[match.end():
                classes[index + 1].start() if index + 1 < len(classes) else len(text)])}


expected_additions = {
    ('QuotePreviewTest', 'loadedOriginalRetainsItsGeometryWhileRecycledQuotesWaitForThePublicCache'): 'quote-ui.txt',
    ('QuotePreviewTest', 'delayedAndStaleArchiveRefreshesUpdateBothQuotesWithoutRestoringAConfirmedDeletion'): 'quote-ui.txt',
    ('QuoteRecyclingMotionTest', 'loadedQuoteKeepsItsRenderedGeometryAcrossNativeScrollsAtNormalAndZeroMotion'): 'quote-recycling-motion-ui.txt',
    ('LikeCountPreviewTest', 'genuineZeroFeedTotalPaintsEveryTapAndSurvivesConversationNavigation'): 'like-count-ui.txt',
    ('LikeCountPreviewTest', 'missingFeedTotalAdvancesBeforeAuthenticationAndSurvivesLateAndStaleTotals'): 'like-count-ui.txt',
    ('LikeCountPreviewTest', 'missingReplyTotalKeepsLocalTapsThroughHydrationRefreshAndReopening'): 'like-count-ui.txt',
}
added, removed = set(), set()
for path in git('diff', '--name-only', baseline, source, '--', 'app/src/androidTest').decode().splitlines():
    if path.endswith('.kt'):
        before, after = methods_at(baseline, path), methods_at(source, path)
        added |= after - before
        removed |= before - after
assert added == set(expected_additions) and not removed
contract = json.loads(read(parent_packet + 'full-contract.json'))
parent_contract_sha = sha(read(parent_packet + 'full-contract.json'))
rows = [dict(path=path, testClass='nl.davidengelen.redsquare.' + cls, test=method, occurrences=1)
        for (cls, method), path in sorted(expected_additions.items())]
for sdk in ('10', '17'):
    before = Counter((row['path'], row['testClass'], row['test']) for row in contract['expected'][sdk])
    after = Counter((row['path'], row['testClass'], row['test']) for row in rows)
    assert not before.keys() & after.keys()
    contract['expected'][sdk] = sorted(contract['expected'][sdk] + rows,
                                       key=lambda row: (row['path'], row['testClass'], row['test']))
    contract['expectedRecoveryTotals'][sdk]['normalCompletions'] += 2
contract['addedIdentities'] += rows
contract['addedMethodsInInheritedGroups'] += [row for row in rows if row['testClass'].endswith('.QuotePreviewTest')]
for cls, prefix, folder in [('QuoteRecyclingMotionTest', 'quote-recycling-motion', 'quote-motion-evidence'),
                             ('LikeCountPreviewTest', 'like-count', 'like-count-evidence')]:
    contract['ordinaryGroups'].append(dict(testClass='nl.davidengelen.redsquare.' + cls, partition='social',
        prefix=prefix, newInvocation=True, source='app/src/androidTest/kotlin/nl/davidengelen/redsquare/' + cls + '.kt',
        methods=sorted(method for name, method in expected_additions if name == cls)))
    archive = 'social/' + folder + '.tar'
    assert archive not in contract['requiredArchivePaths']
    contract['addedRequiredArchives'].append(archive)
    contract['requiredArchivePaths'].append(archive)
contract['addedRequiredArchives'].sort(); contract['requiredArchivePaths'].sort()
contract['parent132ContractSha256'] = parent_contract_sha
contract['qualification'] += ' Issues133/135 add six native methods, two invocations and two physical archives. All parent identities, process starts, limits and qualified catalog mappings remain.'
assert len(contract['requiredArchivePaths']) == 288 and len(contract['addedRequiredArchives']) == 37

# Retain the complete accepted parent assessor and every downstream validator.
# Each exact replacement is prospective, counted and covered by the new hashes.
adapter = read(parent_packet + 'verify-full.py').decode()


def change(before, after, count=1):
    global adapter
    assert adapter.count(before) == count, (before, adapter.count(before))
    adapter = adapter.replace(before, after, count)


change('repo = packet.parents[3]', 'repo = packet.parents[2]')
change(parent_packet + 'source-profile.json', current_packet + 'source-profile.json')
change(parent_packet + 'full-contract.json', current_packet + 'full-contract.json', 2)
change(parent_packet + 'verify-full.py', current_packet + 'verify-full.py', 2)
change(parent_packet + 'build-contract.py', current_packet + 'build-contract.py', 2)
change("p.startswith('docs/evidence/ticket132/')", "p.startswith('docs/evidence/ticket135/')")
change("'normalCompletions':46", "'normalCompletions':48")
change("assert len(addition['addedRequiredArchives']) == 35", "assert len(addition['addedRequiredArchives']) == 37")
change('logicalArchiveRequirements=286, physicalArchives=277', 'logicalArchiveRequirements=288, physicalArchives=279', 2)
change("normalCompletions={'10':391, '17':392}", "normalCompletions={'10':393, '17':394}")
change('INTEGRATED132_PASS_WITH_INHERITED115_126_AND131_CATALOG_QUALIFICATIONS', 'INTEGRATED133_135_PASS_WITH_INHERITED_QUALIFICATIONS')
change('Current132 additive-source assessment', 'Current133/135 additive-source assessment')
apk_receipt = b'sha256sum "$artifact" "$repo_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" ' + bytes((92, 10)) + b'    > "$evidence/built-apk-sha256.txt"\n'
change("assert original.split(marker)[0] == current.split(marker)[0], 'Heap/provisioning prefix changed'",
       'receipt = ' + repr(apk_receipt) + "\n            assert current.count(receipt) == 1 and original.count(receipt) == 0\n            assert original.split(marker)[0] == current.replace(receipt, b'', 1).split(marker)[0], 'Heap/provisioning prefix changed beyond the exact additive APK receipt'")

normative = "'docs/evidence/ticket135/build-contract.py']"
change(normative, "'docs/evidence/ticket135/build-contract.py', 'docs/evidence/ticket135/verify-prerequisites.py']")
change("assert sha((packet / 'full-contract.json').read_bytes()) == profile['contractSha256']", """assert sha((packet / 'full-contract.json').read_bytes()) == profile['contractSha256']
assert sha((packet / 'verify-prerequisites.py').read_bytes()) == profile['prerequisiteValidatorSha256']
import importlib.util
import re
prerequisite_spec = importlib.util.spec_from_file_location('prerequisite_evidence', packet / 'verify-prerequisites.py')
prerequisite_module = importlib.util.module_from_spec(prerequisite_spec)
prerequisite_spec.loader.exec_module(prerequisite_module)
prerequisite_receipts = {}
core_witnesses = {}
bridge = json.loads((repo / 'docs/evidence/ticket132/candidate141/peer-close-source-bridge.json').read_text())""")
peer_close = read(parent_packet + 'verify-full-peer-close.py').decode()
peer_function = peer_close[peer_close.index('def verify_peer_close'):peer_close.index('\n\ntext = original.read_text()')]
change('def validate_added_evidence(view, sdk):', peer_function + """

def validate_added_evidence(view, sdk):
    verify_peer_close(sdk)
    prerequisite_receipts.update(prerequisite_module.verify_prerequisites(view, raw, sdk))""")
change('summary.update(inheritedCatalogAssessorSha256=', 'summary.update(prerequisiteEvidence=prerequisite_receipts, peerCloseCoreWitnesses=core_witnesses, inheritedCatalogAssessorSha256=')
compile(adapter, str(packet / 'verify-full.py'), 'exec')
(packet / 'verify-full.py').write_text(adapter)
(packet / 'full-contract.json').write_text(json.dumps(contract, indent=2) + '\n')

profile = json.loads(read(parent_packet + 'source-profile-peer-close.json'))
profile.pop('signedProductSource', None)
profile.update(productSource=source,
    catalogBuilderSha256=sha(Path(__file__).read_bytes()), adapterSha256=sha(adapter.encode()),
    prerequisiteValidatorSha256=sha((packet / 'verify-prerequisites.py').read_bytes()),
    contractSha256=sha((packet / 'full-contract.json').read_bytes()),
    harnessSha256=sha(read('scripts/emulator-check')),
    nativeTrees={path: git('rev-parse', source + ':' + path).decode().strip() for path in profile['nativeTrees']},
    basis='Prospective complete133/135 prerequisite delivery before functional Spaces. Exact new APK-input hash receipt is the only admitted provisioning-prefix addition. All six new native goals and actual original-frame/counter witnesses are additive.',
    qualification='Preserve all inherited115/126/131/132 evidence qualifications, aliases, intentional-start distinctions and original rejected observations. Require one fresh successful three-part parent per SDK, separate actual image review and signed update/public delivery; no prefixes or focused substitutions.')
for name in ('verify-full.py', 'build-contract.py', 'full-contract.json', 'source-profile.json',
             'verify-full-peer-close.py', 'source-profile-peer-close.json', 'peer-close-source-bridge.json'):
    profile['inheritedSha256'][parent_packet + name] = sha(read(parent_packet + name))
(packet / 'source-profile.json').write_text(json.dumps(profile, indent=2) + '\n')
print(json.dumps(dict(source=source, addedMethods=len(rows), identities={sdk: sum(row['occurrences'] for row in goals)
    for sdk, goals in contract['expected'].items()}, logicalArchives=288, physicalArchives=279), indent=2))

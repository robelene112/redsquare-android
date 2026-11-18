"""Freeze #132's source-derived additions while preserving the qualified139 catalog."""
import ast
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

packet = Path(__file__).resolve().parent
repo = Path(os.environ.get('REDSQUARE_PROOF_REPO', str(packet.parents[3])))
source = 'b929927aa84e0ed0850d60b520871d8d630d0f54'
baseline = 'e72ec7deab926c97e655e3d7c224ea1c159a9a44'

def git(*args):
    return subprocess.check_output(['git', '-C', str(repo), *args])

def read(path):
    return git('show', source + ':' + path)

def sha(data):
    return hashlib.sha256(data).hexdigest()

parent = json.loads(read('docs/evidence/ticket131/full-contract.json'))
contract = json.loads(json.dumps(parent))
old_name = 'startingAnotherDraftRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost'
new_name = 'removingTheLocalRecordRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost'
rename = dict(testClass='nl.davidengelen.redsquare.PostPreviewTest', before=old_name, after=new_name,
              reason='The same explicit-discard assertions now use Remove local record; Start another draft separately retains the original.')
post_source = read('app/src/androidTest/kotlin/nl/davidengelen/redsquare/PostSubmissionPreviewTest.kt').decode()
methods = set(re.findall(r'@Test\s+fun\s+(\w+)', post_source))
existing = {r['test'] for r in parent['expected']['17'] if r['testClass'].endswith('.PostSubmissionPreviewTest')}
assert existing <= methods and len(existing) == 3 and len(methods) == 7
def declared_classes(text):
    starts = list(re.finditer(r'^class (\w+)', text, re.M))
    return {m[1]: set(re.findall(r'@Test\s+fun\s+(\w+)', text[m.end():starts[i+1].start() if i+1 < len(starts) else len(text)]))
            for i, m in enumerate(starts)}
observed_added = set()
observed_removed = set()
for path in git('diff', '--name-only', baseline, source, '--', 'app/src/androidTest').decode().splitlines():
    if not path.endswith('.kt'):
        continue
    before = declared_classes(git('show', baseline + ':' + path).decode())
    after = declared_classes(read(path).decode())
    for name in before.keys() | after.keys():
        observed_added.update((name, m) for m in after.get(name, set()) - before.get(name, set()))
        observed_removed.update((name, m) for m in before.get(name, set()) - after.get(name, set()))
assert observed_removed == {('PostPreviewTest', old_name)}
assert observed_added == {('PostSubmissionPreviewTest', m) for m in methods - existing} | {('PostPreviewTest', new_name)}
added = [dict(path='post-submission-ui.txt', testClass='nl.davidengelen.redsquare.PostSubmissionPreviewTest',
              test=name, occurrences=1) for name in sorted(methods - existing)]
modes_text = read('scripts/unfinished-text-process-check').decode()
modes = ast.literal_eval(re.search(r'^MODES = (.*)$', modes_text, re.M)[1])
assert list(modes[:8]) == [g['mode'] for g in parent['addedProcessGoals']]
assert modes[8:] == ('post-separated', 'reply-separated', 'quote-separated')
new_process = []
new_archives = []
for mode in modes[8:]:
    goal = dict(parent['addedProcessGoals'][0], mode=mode, prefix='unfinished-text-process/' + mode)
    new_process.append(goal)
    new_archives.append('social/' + goal['prefix'] + '/images.tar')
    for filename, method in goal['methods'].items():
        added.append(dict(path=goal['prefix'] + '/' + filename, testClass=goal['testClass'], test=method, occurrences=1))
for sdk in ('10', '17'):
    rows = contract['expected'][sdk]
    renamed = [r for r in rows if r['testClass'] == rename['testClass'] and r['test'] == old_name]
    assert len(renamed) == 1 and renamed[0]['occurrences'] == 1
    renamed[0]['test'] = new_name
    before = Counter({(r['path'], r['testClass'], r['test']): r['occurrences'] for r in rows})
    extra = Counter({(r['path'], r['testClass'], r['test']): r['occurrences'] for r in added})
    assert not before.keys() & extra.keys()
    contract['expected'][sdk] = sorted(rows + added, key=lambda r: (r['path'], r['testClass'], r['test']))
    for key, count in [('normalCompletions', 6), ('deliberateInterruptions', 3), ('continuityRecords', 3)]:
        contract['expectedRecoveryTotals'][sdk][key] += count
contract['addedIdentities'] += added
contract['addedProcessGoals'] += new_process
contract['addedRequiredArchives'] = sorted(contract['addedRequiredArchives'] + new_archives)
contract['requiredArchivePaths'] = sorted(contract['requiredArchivePaths'] + new_archives)
contract['preservedIdentityRenames'] = [rename]
contract['parent131ContractSha256'] = sha(read('docs/evidence/ticket131/full-contract.json'))
contract['qualification'] += ' Issue132 adds four native cases and three actual-process modes. The explicit-discard identity is renamed without removing its assertions; all other parent goals and limits remain.'

adapter = read('docs/evidence/ticket131/verify-full.py').decode()
assert adapter.count('repo = packet.parents[2]') == 1
adapter = adapter.replace('repo = packet.parents[2]', 'repo = packet.parents[3]')
def change(before, after, count=1):
    global adapter
    assert adapter.count(before) == count, (before, adapter.count(before))
    adapter = adapter.replace(before, after, count)

change('docs/evidence/ticket131/source-profile.json', 'docs/evidence/ticket132/candidate141/source-profile.json')
change('docs/evidence/ticket131/full-contract.json', 'docs/evidence/ticket132/candidate141/full-contract.json', 2)
change('docs/evidence/ticket131/verify-full.py', 'docs/evidence/ticket132/candidate141/verify-full.py', 2)
change("'docs/evidence/ticket132/candidate141/verify-full.py']:", "'docs/evidence/ticket132/candidate141/verify-full.py', 'docs/evidence/ticket132/candidate141/build-contract.py']:")
change("assert sha(Path(__file__).read_bytes()) == profile['adapterSha256']", "assert sha(Path(__file__).read_bytes()) == profile['adapterSha256']\nassert sha((packet / 'build-contract.py').read_bytes()) == profile['catalogBuilderSha256']")
change("p.startswith('docs/evidence/ticket131/')", "p.startswith('docs/evidence/ticket132/')")
change("        added = Counter({", "        before = Counter({(p, c, 'removingTheLocalRecordRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost' if c == 'nl.davidengelen.redsquare.PostPreviewTest' and m == 'startingAnotherDraftRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost' else m): n for (p, c, m), n in before.items()})\n        added = Counter({")
change("{'normalCompletions':40, 'deliberateInterruptions':8, 'continuityRecords':8}", "{'normalCompletions':46, 'deliberateInterruptions':11, 'continuityRecords':11}")
change("assert len(addition['addedProcessGoals']) == 8", "assert len(addition['addedProcessGoals']) == 11")
change("assert len(addition['addedRequiredArchives']) == 32", "assert len(addition['addedRequiredArchives']) == 35")
change('assert interrupted.total() == 13', 'assert interrupted.total() == 16')
change("\"verifiedUnfinishedTextInterruptedStarts\": 8", "\"verifiedUnfinishedTextInterruptedStarts\": 11")
change('INTEGRATED131_PASS_WITH_INHERITED115_AND126_QUALIFICATIONS', 'INTEGRATED132_PASS_WITH_INHERITED115_126_AND131_CATALOG_QUALIFICATIONS')
change('Current131 additive-source assessment', 'Current132 additive-source assessment')
change('eight added unfinished-text intentional starts', 'eleven added unfinished-text intentional starts')
change('extend_contract=extend_contract,', 'extend_contract=lambda inherited: qualify_catalog(extend_contract(inherited)),')

# Reuse the reviewed qualified139 catalog representation, never modifying its inputs.
qualified = read('docs/evidence/ticket131/verify-qualified-catalog.py').decode()
alias_code = qualified[qualified.index('# Keep physical originals'):qualified.index('def emit_catalog_summary')]
parent_erratum = json.loads(read('docs/evidence/ticket131/catalog-erratum/inputs.json'))
logical = len(contract['requiredArchivePaths'])
physical = len({parent_erratum['archiveAliases'].get(p, {'archive': p})['archive'] for p in contract['requiredArchivePaths']})
assert logical == 286 and physical == 277
extra_code = '''
parent_catalog = json.loads((repo / 'docs/evidence/ticket131/catalog-erratum/inputs.json').read_text())
erratum = dict(parent_catalog, logicalArchiveRequirements=286, physicalArchives=277,
               normalCompletions={'10':391, '17':392})
raw = Path(raw)
'''+alias_code+'''
def emit_qualified_summary(summary, target):
    summary.update(inheritedCatalogAssessorSha256=parent_catalog['qualifiedAssessorSha256'],
                   inheritedFrozenAdapterPassed=False,
                   inheritedFrozenFailureSha256=sha((repo / ('docs/evidence/ticket131/catalog-erratum/frozen-failure' + sdk + '.txt')).read_bytes()),
                   logicalArchiveRequirements=286, physicalArchives=277)
    summary['qualification'] += ' The inherited131 two identity path moves, one disjoint invocation count and ten original archive aliases remain explicit. Its frozen original collector did not pass. No native goal, deadline or physical archive is invented or waived.'
    (target / 'logical-archive-witnesses.json').write_text(json.dumps(witnesses, indent=2) + '\\n')
    (target / 'archive-alias-original-members.json').write_text(json.dumps(member_index, indent=2) + '\\n')
    (target / 'summary.json').write_text(json.dumps(summary, indent=2) + '\\n')
    print(json.dumps(summary))

'''
change("inherited = repo / 'docs/evidence/ticket115/verify-qualified-full.py'", extra_code+"inherited = repo / 'docs/evidence/ticket115/verify-qualified-full.py'")
change("namespace = dict(__file__=str(inherited)", '''emission = '(target / "summary.json").write_text(json.dumps(summary, indent=2) + "\\\\n")\\nprint(json.dumps(summary))\\n'
assert text.count(emission) == 1 and text.endswith(emission)
text = text.replace(emission, 'emit_qualified_summary(summary, target)\\n', 1)
namespace = dict(__file__=str(inherited)''')
change('verify_runtime_sources=verify_runtime_sources,', 'emit_qualified_summary=emit_qualified_summary, verify_runtime_sources=verify_runtime_sources,')

change("'docs/evidence/ticket132/candidate141/verify-full.py').decode().strip()", "'docs/evidence/ticket132/candidate141/verify-full.py', 'docs/evidence/ticket132/candidate141/build-contract.py').decode().strip()")

# Require the new native witnesses as well as every inherited evidence predicate.
new_evidence = '''    posts = view / 'social/status-originals/post-submission-evidence'
    observed = dict(line.split('=', 1) for line in (posts / 'fresh-held-original-observation.txt').read_text().splitlines())
    assert int(observed['heldOriginalQueries']) > 0 and observed['mainThreadHeartbeat'] == 'true' and observed['transmitted'] == '0'
    for scene in ['fresh-held-original-ready', 'fresh-independent-text-and-image', 'reopened-exact-original']:
        assert (posts / (scene + '.png')).stat().st_size > 1000
        assert (posts / (scene + '.xml')).stat().st_size > 0
'''
new_evidence += "    store_input = json.loads((view / 'wallet/status-originals/store-catalogue-evidence/return-category-input.json').read_text())\n    assert store_input['activityWindowFocused'] is True and store_input['completeStableBounds']\n    assert store_input['observations'] >= 2 and store_input['positionChanges'] >= 0\n    assert store_input['budgetMillis'] == 15000 and 200 <= store_input['setupMillis'] <= 15000\n    retry = json.loads((view / 'social/status-originals/posting-evidence/legacy-retry-input.json').read_text())\n    assert isinstance(retry['displayedBeforeInput'], bool)\n    assert retry['displayedAtInput'] is True and retry['receivedBeforeInput'] == 1\n    assert retry['unclippedBoundsBeforeInput'] and retry['unclippedBoundsAtInput']\n    late = json.loads((view / 'social/status-originals/read-cancellation-evidence/late-oversized.json').read_text())\n    assert late['cancelled'] is True and late['cancelledSuccessfulRead'] is False\n    assert late['lateBodyBytes'] > 16 * 1024 * 1024 and late['replayed'] == 2\n    assert late['activeSuccessfulRead'] is True and late['activeArticles'] == 1\n    assert late['connectionsBefore'] == late['connectionsAfter'] and late['connectionsBefore'] > 0\n    assert late['publications'] == 0\n"
change('    # Keep116\'s four exact feed placement scenes', new_evidence+'    # Keep116\'s four exact feed placement scenes')

(packet / 'full-contract.json').write_text(json.dumps(contract, indent=2)+'\n')
(packet / 'verify-full.py').write_text(adapter)
compile(adapter, str(packet / 'verify-full.py'), 'exec')
profile = json.loads(read('docs/evidence/ticket131/source-profile-feed-observer.json'))
profile.update(productSource=source, signedProductSource=source,
               catalogBuilderSha256=sha(Path(__file__).read_bytes()),
               adapterSha256=sha(adapter.encode()), contractSha256=sha((packet/'full-contract.json').read_bytes()),
               harnessSha256=sha(read('scripts/emulator-check')),
               nativeTrees={p:git('rev-parse', source+':'+p).decode().strip() for p in profile['nativeTrees']},
               basis='Prospective fresh complete10/17 for replacement141 productb929. Native methods and catalog counts remain additive132; exact two input-driver corrections, the actual late-callback/current-Stack reader witness and the corrected oversized-only broker writer admission are required. Only ticket132 evidence documentation may differ after product freeze. Every prior failed/cancelled parent and candidate140 remain excluded.',
               qualification='Preserve all inherited115/126/131 qualifications, original failed collectors, source-derived catalog representation and native/public/continuity/evidence validators. New original-preservation goals are additive; explicit-discard rename retains its assertions and deadline.')
for p in ['docs/evidence/ticket131/full-contract.json','docs/evidence/ticket131/verify-qualified-catalog.py','docs/evidence/ticket131/catalog-erratum/inputs.json','docs/evidence/ticket131/catalog-erratum/frozen-failure10.txt','docs/evidence/ticket131/catalog-erratum/frozen-failure17.txt']:
    profile['inheritedSha256'][p] = sha(read(p))
for p in ['docs/evidence/ticket132/build-contract.py', 'docs/evidence/ticket132/full-contract.json', 'docs/evidence/ticket132/verify-full.py', 'docs/evidence/ticket132/source-profile.json', 'docs/evidence/ticket132/store-observer-source-bridge.json', 'docs/evidence/ticket132/source-profile-store-observer.json', 'docs/evidence/ticket132/verify-full-store-observer.py', 'docs/evidence/ticket132/input-readiness-source-bridge.json', 'docs/evidence/ticket132/source-profile-input-readiness.json', 'docs/evidence/ticket132/verify-full-input-readiness.py']:
    profile['inheritedSha256'][p] = sha(read(p))
(packet / 'source-profile.json').write_text(json.dumps(profile, indent=2)+'\n')
print(json.dumps({'identities':{sdk:sum(r['occurrences'] for r in rows) for sdk,rows in contract['expected'].items()},
                  'recoveryTotalsBeforeInheritedCatalogCorrection':contract['expectedRecoveryTotals'],
                  'logicalArchives':logical,'physicalArchives':physical,'newNativeMethods':sorted(methods-existing)},indent=2))

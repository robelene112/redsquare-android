"""Evaluate whole original library-runtime parents against prospective v1."""
import argparse, hashlib, io, json, re, tarfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('root',type=Path)
p.add_argument('--sdk',type=int,required=True)
p.add_argument('--page-size',type=int,required=True)
a=p.parse_args(); root=a.root
source='fbe105980a53da6e5ed620ccc08c882062ab503a'
run=json.loads((root/'run.json').read_text())
assert run['status']=='completed' and run['conclusion']=='success'
assert run['headSha']=='fbe105980a53da6e5ed620ccc08c882062ab503a'
assert len(run['jobs'])==1 and run['jobs'][0]['conclusion']=='success'
raw=root/'raw/android-native-proof'
assert (raw/'.scratch/proof-source.txt').read_text().strip()==source
assert (raw/'.scratch/proof-workflow.txt').read_text().strip()=='fbe105980a53da6e5ed620ccc08c882062ab503a'
assert json.loads((root/'zip-index.json').read_text())[0]['name']=='android-native-proof'
expected={'SpaceCryptoTest': ['removingAMemberAdvancesPrivateContentKeysWhileRemainingMembersCanStillRead', 'restoringAnUnjoinedDeviceRetainsItsOriginalPublicPackageAndWalletBinding', 'explicitAdmissionAndAuthenticatedHistoryGrantRecoverAnEarlierPrivateRecordAfterReload', 'confirmedRejectionStatesSurviveReloadAndDoNotActivateAnUnacceptedMembership', 'portableHistoryKeysRecoverOldContentWithoutCloningAnActiveSenderIntoTheFreshGeneration'], 'SpaceJournalTest': ['anAuthenticatedCreationEstablishesExactlyOneOwnerAndDuplicateReceiptDoesNotCreateAnother', 'anInvitationAdvancesOnlyTheAuthorizedControlChainAndDoesNotEnrollItsHolder', 'anExplicitSignedJoinWithTheBearerProofAdmitsOneMemberAndAdvancesTheChannelEpoch'], 'SpaceMlsReferenceTest': ['independentBouncyCastleMemberReadsTheNativeWelcomeExporterAndHistoryMessage', 'anUnboundedForeignKeyPackageIsNotAdmittedEvenWithAValidWalletBinding'], 'SpaceReplyTest': ['aLargeSignedReadReplyPreservesItsExactPayloadWithoutRelaxingOrdinaryPublicationLimits']}
for cls,cases in expected.items():
 text=(raw/f'core/build/reports/tests/test/classes/nl.davidengelen.redsquare.spaces.{cls}.html').read_text()
 for case in cases:
  assert re.search(r'<td class="success">'+re.escape(case)+r'</td>\s*<td class="success">[^<]+</td>\s*<td class="success">passed</td>',text), (cls,case)
evidence=list((raw/'.scratch/device-evidence').glob('*/spaces-mls-ui.txt'))
assert len(evidence)==1
d=evidence[0].parent; transcript=evidence[0].read_text()
assert (d/'spaces-mls-ui-exit.txt').read_text().strip()=='0'
assert 'class=nl.davidengelen.redsquare.SpaceMlsRuntimeTest' in transcript
case='actualJniAndKeystoreRecoverAdmittedHistoryWithoutWritingAnUnprotectedKeyring'
assert 'test='+case in transcript and 'numtests=1' in transcript
assert re.search(r'^OK \(1 test\)\s*$',transcript,re.M)
assert not re.search(r'FAILURES!!!|AssumptionViolated|INSTRUMENTATION_STATUS_CODE: -(?:3|4)',transcript)
assert (d/'spaces-mls-capture-status.log').read_text()=='captureExit=0\ntimeoutSeconds=30\n'
archive=d/'spaces-mls-evidence.tar'
with tarfile.open(archive) as t:
 members=t.getmembers()
 assert 1<=len(members)<=4
 for member in members:
  relative=Path(member.name)
  assert not relative.is_absolute() and '..' not in relative.parts
  assert member.isdir() or member.isfile()
  assert member.size<=1_000_000
 files=[m for m in members if m.isfile()]
 assert [m.name for m in files]==['spaces-mls-evidence/runtime.json']
 runtime=json.loads(t.extractfile(files[0]).read())
assert runtime['sdk']==a.sdk and runtime['pageSize']==a.page_size
assert runtime['abi'] in ('x86','x86_64') and runtime['pid']>0
hashes=[line.split()[0] for line in (d/'built-apk-sha256.txt').read_text().splitlines()]
assert len(hashes)==2 and all(re.fullmatch('[0-9a-f]{64}',v) for v in hashes)
assert runtime['installedApkSha256']==hashes[0]
assert runtime['installedTestApkSha256']==hashes[1]
assert runtime['keystoreRoundTrip'] is True
assert runtime['encryptedFileBytes']>0 and runtime['snapshotBytes']>0
assert runtime['historyBeforeGrant']=='unavailable'
assert runtime['historyAfterGrant']=='exact original content'
assert 'not UI' in runtime['qualification']
for field in ['confirmedRejectionBranchReloaded','rejectedCandidateFutureDenied','archiveOnlyHistoryRecovered','freshGenerationSeparated']:
 assert runtime[field] is True
assert runtime['rejectedMembershipEpoch']==0
assert runtime['operationalSnapshotVersion']==2
import xml.etree.ElementTree as E
for module,count,classes in [('core',243,48),('spaces-service',5,1)]:
 suites=[E.parse(p).getroot() for p in (raw/module/'build/test-results/test').glob('TEST-*.xml')]
 assert len(suites)==classes and sum(int(v.get('tests')) for v in suites)==count
 assert all(int(v.get('failures'))==int(v.get('errors'))==int(v.get('skipped'))==0 for v in suites)
expected_native_inputs={'spaces-mls/Cargo.toml': '9d81c148917324b3b1b59aa1f09b2ee5260c68a3ec8c8f0040eeced8ac7510b7', 'spaces-mls/Cargo.lock': '3929649c69d8065fb2f41f21930050257f86ad366e1a76cbeb06bb907486d21d', 'spaces-mls/src/lib.rs': 'e65ba486d553dc5dbaae3d640432babb9bac42ee48a23952f5112c7b693176ee', 'build-support/spaces-mls.py': '41a31115d6da88239b64ed308a6e2d2fdc9241812432d654c9345870e57cd067', 'build-support/spaces-toolchain.json': '84c3817c11b5e35ebf64a58e2470c54f8a021426e17b022024acabe77304dba1', 'spaces-mls/tests/community.rs': '6e915bd6308ebdf495895fe167291d3ca99229b7d392020b1065554937ff9762'}
for relative,android in [('core/build/generated/spaces-mls-host/build-receipt.json',False),('app/build/generated/spaces-mls-jni/build-receipt.json',True)]:
 receipt=json.loads((raw/relative).read_text())
 assert receipt['rust']=='1.100.0' and receipt['ndk']=='30.0.16248370'
 for path,sha in expected_native_inputs.items():
  if android and path=='spaces-mls/tests/community.rs':continue
  assert receipt['inputs'][path]==sha,(relative,path)
 assert {o['abi'] for o in receipt['outputs']}==({'arm64-v8a','armeabi-v7a','x86','x86_64'} if android else {'host'})
 assert all(o['bytes']>0 and re.fullmatch('[0-9a-f]{64}',o['sha256']) for o in receipt['outputs'])
 if not android:
  rustlog=raw/'core/build/generated/spaces-mls-host/community-contracts.txt'
  assert receipt['rustContracts']['exit']==0 and hashlib.sha256(rustlog.read_bytes()).hexdigest()==receipt['rustContracts']['sha256']
  assert '4 passed; 0 failed; 0 ignored' in rustlog.read_text()
service=(raw/'spaces-service/build/reports/tests/test/classes/nl.davidengelen.redsquare.spaces.service.SpacesServiceStoreTest.html').read_text()
for case in ['authenticatedPrivateReadingBindsTheNonceToItsCallerAndConsumesItOnce','anActualServiceProcessTerminationRetainsTheOriginalAcceptedOperation','aSignedJoinIsStoredAsARequestWithoutPretendingMembershipOrKeysAreReady','anIndependentHttpClientReceivesTheActualSignedDurableResult','aLostCreationAcknowledgmentReopensWithTheOriginalReceiptAndOneOwner']:
 assert re.search(r'<td class="success">'+re.escape(case)+r'</td>\s*<td class="success">[^<]+</td>\s*<td class="success">passed</td>',service),case

log=root/'whole-run.log'
assert log.is_file()
build=log.read_text()
for case in [
 'admitted_independent_identity_reads_encrypted_content_after_serialized_state_reload',
 'authenticated_group_exposes_the_actual_public_signer_for_each_bound_credential',
 'removed_identity_cannot_read_future_content_that_the_remaining_member_reads']:
 assert 'test '+case+' ... ok' in build
assert '4 passed; 0 failed; 0 ignored' in build
assert 'test confirmed_rejection_recovers_the_pending_group_before_a_different_admission ... ok' in build
result=dict(contract='ticket137 recovery-aware library runtime v3',run=run['databaseId'],source=source,
 sdk=a.sdk,pageSize=a.page_size,runtime=runtime,
 sha256={str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in [archive,evidence[0],log]},
 outcome='PASS',qualification='Library/JNI/Keystore only. No functional UI, distinct process, deployed service, OEM or whole137 acceptance.')
(root/'assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))

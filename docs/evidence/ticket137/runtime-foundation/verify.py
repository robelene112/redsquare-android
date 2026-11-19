"""Evaluate whole original library-runtime parents against prospective v1."""
import argparse, hashlib, io, json, re, tarfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('root',type=Path)
p.add_argument('--sdk',type=int,required=True)
p.add_argument('--page-size',type=int,required=True)
a=p.parse_args(); root=a.root
source='467ce2c5a59adf63c135067081a591dfbbd99ae3'
run=json.loads((root/'run.json').read_text())
assert run['status']=='completed' and run['conclusion']=='success'
assert run['headSha']=='f7993d042563be29fda15aed6f3928763ca902e6'
assert len(run['jobs'])==1 and run['jobs'][0]['conclusion']=='success'
raw=root/'raw/android-native-proof'
assert (raw/'.scratch/proof-source.txt').read_text().strip()==source
assert (raw/'.scratch/proof-workflow.txt').read_text().strip()=='f7993d042563be29fda15aed6f3928763ca902e6'
assert json.loads((root/'zip-index.json').read_text())[0]['name']=='android-native-proof'
expected={
 'SpaceCryptoTest':[
  'removingAMemberAdvancesPrivateContentKeysWhileRemainingMembersCanStillRead',
  'restoringAnUnjoinedDeviceRetainsItsOriginalPublicPackageAndWalletBinding',
  'explicitAdmissionAndAuthenticatedHistoryGrantRecoverAnEarlierPrivateRecordAfterReload'],
 'SpaceJournalTest':['anAuthenticatedCreationEstablishesExactlyOneOwnerAndDuplicateReceiptDoesNotCreateAnother'],
 'SpaceMlsReferenceTest':[
  'independentBouncyCastleMemberReadsTheNativeWelcomeExporterAndHistoryMessage',
  'anUnboundedForeignKeyPackageIsNotAdmittedEvenWithAValidWalletBinding']}
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
log=root/'whole-run.log'
assert log.is_file()
build=log.read_text()
for case in [
 'admitted_independent_identity_reads_encrypted_content_after_serialized_state_reload',
 'authenticated_group_exposes_the_actual_public_signer_for_each_bound_credential',
 'removed_identity_cannot_read_future_content_that_the_remaining_member_reads']:
 assert 'test '+case+' ... ok' in build
assert '3 passed; 0 failed; 0 ignored' in build
result=dict(contract='ticket137 library runtime v2 unchanged goals, workflow-only source bridge',run=run['databaseId'],source=source,
 sdk=a.sdk,pageSize=a.page_size,runtime=runtime,
 sha256={str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in [archive,evidence[0],log]},
 outcome='PASS',qualification='Library/JNI/Keystore only. No functional UI, distinct process, deployed service, OEM or whole137 acceptance.')
(root/'assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))

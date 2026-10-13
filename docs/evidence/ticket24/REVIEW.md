# Private Chat review

## First protocol increment c102f89...7dbaba3

### Standards

Confirmed case-sensitive imported passphrase compatibility defect: converting
accepted uppercase saved aes_secret to bytes and back to lowercase changes
CryptoJS KDF input. Preserve the original validated string and add an
independently signed uppercase fixture. A second documented compatibility finding: formatter fields are coerced via
asString, allowing single-element arrays. Require actual string primitives
and verify correctly signed malformed formatter rejection. Zero actionable
Fowler smells; both findings cite CODING_STANDARDS compatibility rule.

### Spec

Two findings against imported history recovery/string passphrase compatibility:
legacy publickey field is ignored despite pinned keychain migration; accepted
uppercase/mixed-case saved aes_secret is normalized. Preserve backup contents
while selecting the compatible field/passphrase and verify independent vectors.
All26 evidence hashes, three reference copies and core77 counts verified.
UI/transport/final recovery acceptance remains pending, not claimed.

## Protocol fixes 7dbaba3...42dfd6f

### Standards

Prior compatibility findings resolved; zero new hard violations. One actionable
Duplicated Code heuristic: identical string-type validation for formatter/body.
Shared private validator now applied, core81/debug/testAPK3m24s passing; review
of that cleanup and new peer discovery still pending. All55 hashes/core81 and
qualified actual Crypto failures verified.

### Spec

Zero findings. Legacy field and exact-case passphrase fixes preserve imported
backup data; Relay original signed bytes authenticate before decrypt. All55
hashes/core81/referencecopies/fourvalid+threeinvalid vectors verified. Native
transport/UI/final recovery remain pending.

## Shared validator and authenticated discovery42dfd6f...0997419

### Standards

Prior duplication resolved; zero hard/actionable heuristics. Bounded strictUTF8
metadata matches pinned service|domain|name ordering, authenticated sameconnection,
no endpoint use. Fixture callback placement/scope mappings and allfive hashes
verify; source evidence correctly distinguishes pending actual runtime.

### Spec

Zero findings. Authentication/disconnect discovery gates and original string
validation preserved; no custom backend/domain following. Existing trailing
Archive callback bindings restored. Allfive hashes/core81/source GREEN and
qualified mixed/cleanRED evidence verified; no UI/full acceptance claim.

## Original Relay receive and socket callbacks0997419...3c35a77

### Standards

Zero hard code violations/actionable heuristics. Bounded explicitly untrusted
framing, original signature before decrypt, authenticated incoming callbacks,
13accept/14reject and existing request behavior verified. All73 hashes and
qualified one-case original evidence agree. One handoff correction: private
contract/reference/generator already committed; fixed all inherited future notes
while preserving group contract protection.

### Spec

Zero findings. Outer Relay provides no identity authority, original inner and
participants authenticate independently, altered inner rejected. Callbacks
preserve request IDs without promising durable/read receipt. Core82/manifests/
referencecopies and qualified original one-case evidence verified. Newtwo-case
result belongs to3c35, retained separately; UI/cache/backend/recovery unfinished.

## First native history UI6df3ef0...7e5811e

### Standards

Zero hard violations or actionable heuristics. Contacts expose only public
metadata, original authentication/dedup and Block semantics preserved. All16
evidence hashes verify; initial UI RED is qualified rather than treated as
passed. Cache, sending and recovery remain unfinished.

### Spec

Zero findings in this bounded increment. Boolean added matches pinned
mods/qrscanner/lib/add-contact.js:15; group entries excluded. Available rows
authenticate/decrypt before display; no cache/send/recovery claim.

## Private cache6f02f73...0620289

### Standards

One hard continuity finding, zero actionable Fowler heuristics. Refresh assigns
cache.load and later cache.save results over current verified memory. After a
failed local save, an unavailable or pruned Archive read can discard a message
while Chat remains open, contradicting CODING_STANDARDS:16–17 and the warning
limited to closing Chat. Merge retained memory, including original ciphertext
envelopes for successful persistence retry. All31 manifest entries verify;
encryption/scoping/refusal to overwrite unreadable history otherwise hold.

### Spec

One same continuity finding against parent#1 keep-reading-through-failures and
#25 available-plus-local history. Add failed-save, offline/empty Refresh and
repair verification. Both RED XMLs/failure original, seven hashes and core82
source GREEN verified. No actual process-death or completed-ticket claim.

## Cache retention fixes e8f6808...43399a7

### Standards

Prior hard finding resolved; zero new hard code violations or actionable
heuristics. Scoped authenticated entries/envelopes merge by original signed ID;
persistence retries retain unsaved entries and warning remains while IDs are
absent from disk. All59 manifest records including parent repetitions verify.
One current UI README drift (recorded RED still pending/open) corrected next.

### Spec

Zero findings, prior loss-on-Refresh finding resolved in source. All43 unique
UI evidence hashes, actual two-case RED and earlier recreation GREEN verify.
Unexecuted repair gates and shared capture filenames correctly qualified.
Unchanged corrective runtime, actual death, send and full acceptance pending.

## Live selected-conversation reception db62258...fed20de

### Standards

One correctness finding, zero actionable Fowler heuristics: an incoming saved
history snapshot can be published after a newer history save and incorrectly
mark already durable entries missing. Keep the warning tied to history load/save
attempts; incoming updates only merge verified entries. All84 manifest records
verify; original failure image and three-case RED match their qualified claims.
Authentication, contact isolation, persistence before ACK and cleanup hold.

### Spec

Zero findings in the bounded reception increment. All71 unique UI evidence
hashes, original failure image/five XMLs, three-case RED and core82/source GREEN
verify. Native corrective result pending; list listener, send, actual death and
whole-ticket acceptance not inferred.

## Incoming warning ordering fed20de...d62a964

### Standards

Prior warning-order finding resolved; zero new hard findings or actionable
heuristics. Incoming updates only merge authenticated entries and cannot change
the history load/save warning. Compilation hash verifies1m25s; runtime source
association remains explicit.

### Spec

Zero findings. Retained envelopes, contact isolation and persistence before ACK
remain intact; no new core/APK/runtime claim from compilation alone.

## First Chat permission integration a3b148a...f2b33cf

### Standards

Zero hard findings or actionable heuristics. One call reuses the optional
permission component without changing shared behavior or requiring alerts for
reading. Fifteen RED hashes and new compilation hash verify; original failure
PNG/six XMLs agree with four-case RED28.341s and unreached Skip/recreation.

### Spec

Zero findings. Contextual first-Chat offer, optional dismissal and unchanged
initial feed match parent scope. All117 UI manifest records verify. Compilation
only, exit143 despite Gradle SUCCESS and unchanged exit0 recheck qualified.
Corrective runtime and remaining DM acceptance pending.

## First private send f072178...e06a22c

### Standards

One recovery-guidance finding, zero actionable heuristics. Initial work.load
failure leaves composer disabled; history Refresh cannot reload unchanged work.
Use an actual reopen action or safe reload trigger. Candidate persistence,
identity/backend guards, cancellation checks and qualified forwarding otherwise
hold. All21 RED hashes/four build records, nine XMLs/failure original verify.
Attached command exit143 distinguished from original container exit0/SUCCESS.

### Spec

Zero bounded findings. Original encrypted candidate saved/validated before any
attempt; shared lock checks wallet/backend/candidate before submission. Original
signed inner relayed; Submitted/Forwarded avoid delivery/read confirmation. All182
UI manifest records, actual missing-composer RED and container success verify.
Actual outgoing recovery/independent client and whole-ticket acceptance were
pending at review time; subsequent native GREEN retained separately.

## Private send guidance/layout and native client check e06a22c...94326b2

### Standards

Prior guidance finding resolved; zero new hard findings or actionable heuristics.
Read errors name actual reopen recovery; later send/save errors do not promise
Refresh retry. Compact layout preserves state logic. All24 GREEN records and
three added build/research records verify. Native envelope matches independent
client check; default reference copies unchanged. Earlier clipped recreated text
and pending corrected runtime are accurately qualified.

### Spec

Zero findings. Actual unsafe2->1 native envelope decrypts through pinned Crypto
wrapper and matches independently reproduced signature. All209 UI evidence
hashes/tenXMLs/three qualified originals verify; default reference copies remain
unchanged. Corrected layout/runtime and actual process death remain pending.

## New-contact metadata review8615857...a26945b

### Standards

Zero hard findings, one Duplicated Code heuristic: contact discovery and contact
addition repeat the group predicate. Extract the same private predicate.

### Spec

One compatibility finding: imported keys:null should initialize an empty keychain
when adding a first contact, matching pinned keychain initialization. Preserve
original options and identity. Prior evidence claims remain qualified.

## Contact-entry correction reviewa26945b...33b755e

### Standards

Zero new hard findings or actionable heuristics; duplicate predicate resolved.
Verified51 new evidence records and core84. Fresh-contact runtime pending.

### Spec

Zero findings. Null imports, original options, same-identity active metadata,
verified persistence and cancellation recovery are consistent. Verified21 protocol
and30 native RED records. No full-ticket completion inferred.

## Public-reader review33b755e...e2eb51d

### Standards

Zero hard findings or actionable heuristics. Explicit scopes, fixed endpoints,
bounded strict replies, ephemeral probes and qualified compilation are consistent.

### Spec

Zero findings. Reads only, no submit/relay/identity mutation. Empty response limited
to unused random pair; actual runtime and nonempty interoperability stillpending.

## Imported-ID core reviewd4d5f01...f2cd911

### Standards

Zero hard findings or actionable heuristics. Original authenticated counterpart
discovery, preserved options and qualified core/public read evidence are consistent.

### Spec

One compatibility finding: valid backups mix DM64hex hashes and generated public-key
group IDs in the same chat.groups list. Throwing on recognized group keys blocks
unrelated private recovery. Skip those group entries and cover modern/legacy lists.

## Imported-ID native reviewf2cd911...655c1e5

### Standards

Zero hard findings or actionable heuristics. Shared original/privategroup verified
loader and protected callback consistent; evidence records53verify, core85.

### Spec

One partial-recovery finding: groupA successful/cache saved, latergroupB failure
prevents publication of A's recovered contact. Isolate failures pergroup and publish
successes with incomplete discovery status. MixedID compatibility finding resolved.

## Partial-recovery fix review37532b6...a6c525c

### Standards

Zero source hard findings or actionable heuristics.58 new evidence records/compile
verify. Stale latest-source and redo-minimum handoff wording corrected.

### Spec

Zero source findings; partial recovery resolved.412 UI hashes verify; chronology
accurate. Same handoff correction recorded separately from source acceptance.

## List-incoming tracer reviewa6c525c...6829cbf

### Standards

Zero source hard findings or actionable heuristics.56 new GREEN and two compile
records verify, boundedredelivery/exactcleanup/qualifiedpendingRED consistent.

### Spec

Zero findings.470 UI hashes/24 XMLs/originals verify; no listener implementation
inferred from tracer, both prior nine-caseGREEN checkpoints correctly qualified.

## List/cache discovery review6829cbf...4734f93

### Standards

One correctness finding: initial retained/portable IO snapshots may replace a
newer live-published contact. Merge into current wallet/backend-scoped contact state.
Zero actionable heuristics.30 RED records/build verify; original failure confirmed.

### Spec

One partial-key recovery finding: one old exchanged-key ciphertext whose secret
is missing from an imported wallet hides the entire cache/contact, including a
readable implicit-key message. Preserve unreadable ciphertext and expose verified
readable history/contact plus partial warning.501 UI hashes verify.

## Contact merge and partial-key tracer review4734f93...89c09bf

### Standards

Prior ordering finding resolved; zero new hard findings or actionable heuristics.
Twenty-eight new GREEN and two compile records verify. Runtime/race limits clear.

### Spec

Zero new findings.531 UI hashes/twelveXMLs/qualified originals verify. Previous
mixed-key recovery finding remainsopen, intentional11caseRED/minimumpending.

## Partial cached-key source review4f9fb46...59ba616

### Standards

One readability-warning regression: page.second is applied only after successful
cache saving, hiding rejected/unreadable rows when saving fails. Record independently
from persistence. Zero actionable heuristics;30RED records/build verify.

### Spec

Zero findings; cached partial-key preservation resolved in source.563 UI hashes
verify; stronger restored-secret tail/RED qualification accurate. NativeGREENpending.

## Corrected restored-key / warning tracer review59ba616...ecc3d56

### Standards

Zero new hard findings or heuristics.30attemptrecords/twocompile logsverify; exact
body cardinality corrected without relaxing recovery/storage/assertions.

### Spec

Zero new findings.595 UI hashes/13XMLs/originalsverify. Known warning source
remainspending genuineRED; no acceptance inferred from first fixture failure.

## Independent warning fix reviewecc3d56...df9891d

### Standards

Prior warning finding resolved; zero new hard findings or actionable heuristics.
32 RED records/build verify, offline assertion strengthened and warning scope clear.

### Spec

Zero new findings.628 UI hashes/14 XMLs/twooriginalsverify; corrected key-restoration
casepassed separately from missingwarning failure; native12GREENwaspendingat review.

## Different-contact receiver review, df9891d...256b67f

### Standards

Zero documented breaches and zero actionable smell findings. Source saves authenticated other-contact originals in that encrypted conversation before ACK without entering selected display. New native/controlled-peer test checks ACK, separation, discovery and retained reading; native-generated fixture is explicitly not independent crypto proof. Class180 preserves individual assertions/waits; raw exit supplements mandatory OK gate. RED evidence accurately leaves GREEN/process-death/whole acceptance pending.

### Spec

Zero actionable findings. Receiver supports #25 complete native DM send/receive/read and local-cache recovery. Tracer verifies ACK, separation, other-contact discovery/read/recreation without redelivery. Both source22b07ed RED runs match13cases/12PASS+one line260failure; rawexit0/OKgate rejects. Reviewer verified755 parent manifest records and14XML perrun. Actualprocessdeath and GREEN remainpending.

Findings: Standards0; Spec0.

## Retained-contact list return review, 256b67f...53fd84b

### Standards

Zero documented breaches and zero actionable smell findings. The two-line change keys discovery to wallet/backend/navigation and limits it to the list; returning rescans encrypted history off main, preserves newer merged contacts and cancellation. It is the smallest correction for the observed failure. Evidence separates successful ACK from failed discovery and keeps GREEN pending.

### Spec

Zero actionable findings. List-only navigation-keyed scanning supports #25 recoverable local history and parent identity-scoped cache using current wallet/backend. Both attempts correctlyattribute256b67f13/12PASS+one line263failure; ACK/separation pass before discoveryfails. Both14XML parse andparentmanifestverifies. NativeGREEN andwholeacceptancepending.

Findings: Standards0; Spec0.

## Existing-client receive/display compatibility audit, c102f89...53fd84b

### Spec

Two concrete #25 compatibility gaps remain. Pinned Chat construction adds outside mention outputs and still encrypts the two-person DM; popup passes getMentions and enables mentions for DMs. Exact-two-output validation and outgoing .single reject validmessages. Require originalsigner actualparticipant, both actualparticipants addressed, successfuldecryption/exactcanonicalgroup; extraoutputs remainmentions, notmembers. Outgoingrecovery mustidentifyunique successfullydecrypted canonicalcounterpart amongaddressedcandidates. Requiringmentioned metadata forextras isnotupstream receivebehavior. Addindependent pinnedJS/WASM bothdirection unsafe3mention vectors with wronggroup/signaturecontrols.

Ordinary SaitoInput.getInput(false) usesinnerText; notallmessages areHTML. Reply construction canprepend sanitizedblockquote; websanitizes/rendersthatmarkup whereasnativeText exposestags. Imagesbelongtolatermediatickets. Need independentencryptedwebreplybody +literal-anglecontrol preservingoriginalsignedbytes andreadablequote/body; noexistingbrowserUIequivalenceclaim. SourcecachedChat matches exactpinSHA.

Primary paths: pinned node/mods/chat/chat.js:1489, node/mods/chat/lib/chat-manager/popup.js:837 and523, node/lib/saito/ui/saito-input/saito-input.js:307, node/mods/chat/chat.js:1761, revision9a4ca7e9442eb5e872168ced361ce741d7d139d7. Reviewer performedonlyreadonlypublicsource audit.

## Selected Refresh discovery-error correction, 53fd84b...dd02415

### Standards

Zero documented breaches and zero actionable smell findings. Removing the earlyreturn restores selected Refresh error recomputation while keeping navigation/backendkeys, offmain storage/contactmerge/cancellation. Minimal captured-regression correction. Evidence separates passingothercontact fromtwo storagefailures; runtimeGREENpending.

### Spec

Zero actionable findings. Selected Refresh canrecompute staleloaderrors while listreturn stilldiscovers savedcontacts. Both source53fd84b attempts13/11PASS+2line311failures; othercontactpasses. Both15XMLparse/parentmanifestverifies. Secondcontact10recreation qualifiedcachedtextwhileLoading, notcompletedArchive. Mention/markupfindings remainunresolved; GREEN/processdeath/wholeacceptancepending.

Findings: Standards0; Spec0.

## Existing-client mention protocol correction, dd02415...5716d23

### Standards

Zero documented breaches and zero actionable smell findings. Core preserves originalsignature/participantaddressing/decryption/canonicalpair, own-authored recoveryacceptsunique decodedcounterpart. Independentclient vectors coverbothdirections/originalpreservation/wronggroup/thirdwalletdenial. RetainedXML10/oneRED then10GREEN. Threecorpora56ad...match; originalfourvectors/malformedcasesmatchexistingcorpus. Optionalmode respondsdemonstratedcompatibilityneed. DocsseparateprotocolGREEN frompendingnativecache/wholeacceptance.

### Spec

Zero new actionable findings. Participant-subset validation implements pinned encrypted DM outside mentions; signature/author/addressing/decrypt/canonicalpair remainmandatory, extraoutputs stayoutsideconversation. Vectorsbothdirections preservesignedoriginal/group/text, rejectwrongexpectedgroup/thirdwalletaccess; copies/hashmanifestmatch. Prior13case nativeGREEN correctlyreferencesdd02415 with15XMLeach. Newnativecachetracer anduncorrectedcachevalidatorseparated. NativeGREEN/replydisplay/wholeacceptancepending.

Findings: Standards0; Spec0.

## Mentioned ciphertext cache correction, 5716d23...a05c2d6

### Standards

Zero documented breaches and zero actionable smell findings. One-line correction allowsadditionalauthenticatedmentionoutputs while retainingoriginalsignature/participantauthorship/addressing/cipherbody. Newmessages fullprivate/decrypt/canonicalgroupbeforepersistence; retainedopaquecipherpreviouspolicy. Minimumdemonstratedcompatibilitychange, noformat/identity/newabstraction. Docsseparate initialreadfromcachefailure andkeepGREENpending.

### Spec

Zero actionable findings. Fixsupports#25 encryption/localcache formentionedDMs; fullprivateverificationbeforewrite/display remains. RetainedRED5716d23 14/13PASS+one line326failureafternetworkreadpassed. All16XMLparse/parentmanifestverifies. Boundedcachefixonly; wholeacceptancepending.

Findings: Standards0; Spec0.

## Existing private controls and own mentioned history, a05c2d6...529aa5b

### Standards

Zero documented breaches and zero actionable smell findings. Native tests use independent outgoingmentionvector/importedID andrealpersonalpolicyadapterfixture; bodyvisibility throughUI. Blockearlyreturn staysinsidecleanup/resource scopes; exactopaque wallet/backend/preferences/policyfamilies restoredafterActivityclose. Uniquecaptures improveattribution. Prior14caseGREEN separatedfromcompiled17casependingexecution; no productsourcechange/processdeath/wholeacceptanceclaim.

### Spec

Zero actionable findings. Testscover#25 blockedincoming/mutedChataccessible/importedownmentioned recovery. Blockchecks hiddenbefore/afterrecreate; removingblockwithArchiveunavailable mustrestoreretainedtext. Preconditionpolicybytes includedinsnapshot/restoration. Prior14GREENa05 bothSDKs65.297/105.853/15XML andmanifestverified; new17executionpendingatreview.

Findings: Standards0; Spec0.

## Exact original Retry tracer, 529aa5b...976158b

### Standards

Zero documented breaches and zero actionable smell findings. Independentoriginal/realencryptedadapter precondition; assertions observe explicitaction/networkinterruption/persistedstatus/recreation/transport. Bothretrybroadcasts mustmatchalloriginalsignedbytes afterfreshsinglehopremoved; Relayretainsoriginalsignature; opening/recreating leavescountunchanged. Cleanup/scopedearlyreturn retainsopaqueidentityfiles. Prior17GREEN separatedfrom18pending/Retryminimumunimplemented.

### Spec

Zero actionable findings. Supportsparent retryoperationidentity/no duplicatevisibleeffects and#25 truthfulrecovery. Real encryptedadapterseededindependentoriginal→noautosend→explicitretry/interruption/recreatedSubmitted/secondexplicitretry/Forwardedretention. Exactoriginalnetwork/Relayid/recipienttext checked, singularmessage nodes preventduplicatevisibleentries. SyntheticdurableUNKNOWN notactualprocessinterruption. Prior17GREEN/all24XML/manifestsverify. No RetryGREEN/wholeacceptanceclaim.

Findings: Standards0; Spec0.

## Explicit original Retry correction, 976158b...e6cd339

### Standards

Zero new documented breaches and zero actionable smell findings. Retryreusessavedoriginal; storechecksactivewallet/backend/id/eligiblepriorstatus anddurablyUnknownbeforenetwork. Existingguards/mutex retained; no newcipher/signature/autosend. Newmessage/drafthandling separate. CapturedmissingRetryminimum; docsleaveGREENpending/unexecutedtailqualified. XMLprose25→26 knowncorrection, notsourcefinding.

### Spec

Zero actionable findings. Parentretryidentity/no duplicateeffects metbypreservedoriginal; prepareRetry validatesfullwallet/backend/id/status, durableUnknownbeforeexistingclaimguard. Unknowndrafteditingblocked; Submittedblank offersRetry, nonblanknewdraftusesnewSend. Sendsdisableoverlap/preparationmutex, loadsnotransmitting. RED97618/17PASS+missingRetry; all26XMLparse/manifestverify. READMEcorrect26/proserecountnowcorrected. NativeGREEN/processdeath/wholeacceptancepending.

Findings: Standards0; Spec0.

## Bounded Retry readiness, e6cd339...c9465f8

### Standards

Zero documented breaches and zero actionable smell findings. Positive15sisEnabledwaits precedeoriginalstrictassert/tap, noneweakerpredicates. Productsourcesunchanged; ordering supportstimingpossibilitybutcausehypothesis. SDK17 completed18GREEN separatedfrom10failedsecondRetryassert; exactbytes/Forwardedtail notattributedtofailed10. New10executionpendingatreview; 26XMLrecountcorrected.

### Spec

Zero actionable findings. Persistentdisabledstate stillfails15s; broadcastidentity/Relay/noauto/recreatedassertsunchanged. PriorSDK17e6 18PASS96.785 and10e617PASS+disabledsecondRetry78.969 correctlyattributed/all27XML/manifestverify. 17provesoldertestsameproduct, notnewwaitexecution. New10/processdeath/wholeacceptancependingatreview.

Findings: Standards0; Spec0.


## Pending Chat work import tracer, c9465f8...fd6a382

### Standards

Zero documented breaches and zero actionable smell findings. The emulator guard precedes both fixture-directory creation and document writing. Provisioning targets the isolated .dev.test package and an explicitly public unsafe wallet fixture. The test uses actual SAF, strict disclosure and disabled-confirmation assertions, cancellation, identity/backend preservation and absence of transmissions. Existing opaque cleanup remains in place. The 18-case Android 10 GREEN is attributed to c9465f8; Android 17 remains the earlier same-product-source run with older assertions.

### Spec

One actionable finding: cancellation checks UNKNOWN in the existing composer but does not reload saved Chat work. The parent requires isolation and preservation of outgoing work; retained memory can mask a changed durable record. Read the real ChatWorkStore after cancellation and require its original encoded transaction and UNKNOWN status. No fixture privacy or scope finding. Earlier 18-case evidence is accurately qualified; the nineteenth native result and whole-ticket acceptance remain pending at review.

Findings: Standards 0; Spec 1, durable preservation verification. The following change adds the requested adapter read; it requires review and native execution before acceptance.


## Saved Chat work risk inspector, fd6a382...483d8fe

### Standards

One documented recovery gap. The scanner normalizes .new filenames, then checks only decoded base contents. An unfinished .new beside a valid empty base can return false, contrary to its comment and verification documentation. EncryptedLocalFile.load reads the base/backup, not the unfinished file. This conflicts with CODING_STANDARDS.md: “Preserve user identity and recoverable work through updates and connection failures.” Detect .new conservatively before loading the base. Orphan .new already fails closed. Zero heuristic findings. Shared decoding, wallet/backend restrictions and original authentication remain appropriate.

### Spec

Zero new actionable findings; the prior durable cancellation verification gap is resolved. Inspection covers the outgoing wallet across both approved backends, validates canonical conversation names and authenticated originals, and invokes existing replacement safeguards without transmission or identity mutation. Cancellation reloads UNKNOWN and the independent original bytes. The actual 19-case RED and its unexecuted later assertions are accurately qualified. Native GREEN and whole-ticket acceptance remain pending.

Findings: Standards 1, unfinished save detection; Spec 0. The following correction conservatively treats a matching .new file as retained work after validating the conversation filename. Compilation, review and native execution remain pending.


## Unfinished Chat save warning, 483d8fe...8a85351

### Standards

Prior finding resolved; zero new documented breaches or actionable smell findings. The scanner validates the wallet/backend/conversation filename, then treats a matching .new file as retained work before loading the base. An unfinished save can no longer be classified as empty because its existing base is empty. Authenticated decoding remains in place for completed records. Documentation correctly separates native runs at 483d8fe from this correction.

### Spec

Zero actionable findings. Conservative detection supports the outgoing-wallet pending-work acknowledgement safeguard without trusting temporary contents as a valid transaction. Exact wallet/backend family restrictions remain. No send, signing or replacement mutation occurs. Current native runs cannot establish execution of this new branch; whole-ticket acceptance remains pending.

Findings: Standards 0; Spec 0. Local test APK compilation subsequently passes in 2m50s, exit 0. Native verification of the corrected source remains pending.

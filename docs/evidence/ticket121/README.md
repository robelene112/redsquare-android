# Selected-image preparation, ticket121

All eight complete relevant scopes pass independent Standards and Spec source/
evidence review with zero remaining findings. This completes ticket121's agreed
software qualification, without claiming whole-app/beta acceptance.

## Behavior and source

A selected image owns its bounded provider read and normalization in the existing
wallet/backend/draft ViewModel, independently of a visible Activity. A replacement
screen awaits the same operation and commits its result once. It does not reopen
the picker or restart normalization. Post/reply/quote, private/group Chat, profile
picture and banner use their existing encrypted drafts. Latest caption/Bio input
remains editable during the provider read and is preserved by the commit. Send
waits for preparation; failure retains previous work with explicit reselection.
Original image bounds, metadata removal, crop ratios and15-second provider deadline
remain. This is Activity recreation, not recovery of an unprepared URI after process
loss. Existing prepared-media/process recovery remains separately exercised.

Current source: `24db78cf05e147f7243be5503566df5b79c9f871`.
Source/core/lint uses `77bb6d44fa06f5f9b48932c60c7e643d991e7954`.
Native sources59dc7f351 and3b1df5ddd differ only in the reviewed test fixtures and
harness. Product source is identical across the accepted source and native inputs.
Exact patches and immutable inventories are in `source-bridges.json`.
The current Chat test fixture is byte-identical to source77bb. Its original ten
methods run together under120seconds; the two new methods run separately under
180seconds in the same complete parent. Selector sets are disjoint and cover all
12methods, preserving all bodies, assertions and action deadlines.

## Original adverse observations and retained replacements

Post red43762460920 at5a8cec70a reaches the selected provider, recreates the actual
Activity and loses the original reader: completion reports EPIPE. Intermediate
post green43762824038 at6ca947276 completes the same read once and displays the
prepared image. Its original helper produced no capture directory; that malformed
archive and successful native result remain retained as intermediate evidence,
not final acceptance.

Chat red43762957591 at72279849e likewise reports EPIPE after selected group-image
recreation, then its expanded12-method parent hits the original120-second bound.
Profile red43763242090 atfc58d99ea finds no editable Bio control during preparation.
All originals remain outside Git without successful-prefix acceptance.

Source/debug43762675913 atd116 was replaced during integration. Source/debug
43763850571 atdd5 fails because the new fixture names AndroidWalletStore in the
wrong package. The correction uses the existing class. Four in-progress parents
43764201715/43764204298/43764206961/43764209192 atd29 were stopped for that known
compile defect. Metadata, full logs, original ZIPs and source/digest receipts for
all five replaced parents remain retained and excluded.

The first complete image parents43764819079/43764821479 at59 fail before selection
in new reply/quote navigation: earlier test feeds obscure the old signed root.
The fixture now captures, clears and restores that backend feed and exact root
conversation/author-proof records. A valid archive callback supplies declared
synthetic history. The first profile parents43764828517/43764830921 reach the new
Bio, encrypted image, crop and pixel assertions, then assertHealthy rejects a
valid profile-history query because the peer's default branch assumes field4.
An established empty history callback corrects that fixture; assertHealthy remains.

Complete Chat17 parents43764835279 at59 and43766344441 at3b hit the unchanged
120-second legacy-suite bound without an individual assertion failure. Restoring
the original fixture structure and explicit disjoint invocation sets preserves
the original ten-case bound. Class loading/JIT was an investigation hypothesis;
the precise cause of these cumulative timeouts remains unproved. Both failed
parents stay retained without prefix acceptance.

## Complete relevant scope matrix

| Scope | Android10 | Android17 | Native source |
| --- | --- | --- | --- |
| Images, selected post/reply/quote and original lifecycle/process checks |43766334903|43766337212|3b1df5ddd|
| Profiles, original video precondition and selected avatar/banner |43766339724|43766341978|3b1df5ddd|
| Composer, all eight unfinished-text process modes and storage checks |43764823815|43764826162|59dc7f351|
| Chat images, original recovery/storage and selected private/group |43764833243|43767582211|59dc7f351 /24db78cf0|

Images per SDK completes21 ordinary and two process methods, with three distinct
intentional starts. Composer per SDK completes11 ordinary and16 process methods,
with eight intentional starts. All eight #120 modes retain exact text, earlier
Saved drafts, wallet/backend and zero publication after genuine process death.

Profiles17 completes22 ordinary and one process method, with two intentional
starts. Profiles10 completes21 ordinary and one process method, with two starts
and exactly one inherited assumption:
`ProfilePublishingPreviewTest#newerProfileStillBlocksOlderRetryAfterPublicCacheSavingFailsAndEditorNavigation`.
Its unchanged source requires API30+ for the `.new` cache-obstruction scenario.
Chat per SDK completes14 ordinary and four process methods, with two intentional starts.
Final Chat17 runs its ten original methods in111.326seconds and the two additions
in11.984seconds, under their unchanged120/180second bounds. Across all eight
parents:181normal completions,30verified intentional starts and one inherited
assumption, totaling212observed invocations. Android10 provisioning probes remain
separate from those counts.
Intentional process starts never count as normal completions.

Source/debug43764357762 passes with224 core tests across44 suites, zero failures
or ignored tests, lint0errors/94warnings/3hints. Core and gateway tasks explicitly
use Gradle FROM-CACHE; app and instrumentation Kotlin compile and lint run at the
recorded source. This qualification does not claim a fresh core execution.

## Original evidence and independent review

Complete logs, fresh GitHub metadata, server-digest-verified ZIPs, every raw file,
TAR and per-archive original, collectors and assessor code remain at:
`/mnt/storage/redsquare-android-artifacts/selected-images121`.

Raw/capture indexes, summaries, exact source bridges and selected real native
PNG/XML originals are retained here. Every archive member has its own source
archive, byte count and digest. Separate archive extraction preserves all versions;
flattened captures are only a last-archive-wins convenience view. Each Chat scope
has89members flattening to84files with five explicit collisions. Both versions remain
separately retained and indexed, as well as in the unchanged original TARs/ZIP.
Image snapshots likewise retain every repeated archive version separately.
Across the eight native scopes,892raw originals contain980separately verified
archive members; their convenience capture views contain939unique files. Source
verification adds55raw originals. Counts are separated instead of summing aliases.

The unchanged Chat storage-only history class has no capture writer. Its named
`chat-image-history-evidence.tar` contains the literal missing-directory output.
That original is retained with a strict, named qualification; every other Chat
archive is mandatory and verified. It does not replace either storage method's
normal native completion or the new visible private/group captures.

Both independent Standards and Spec reviewers accept current product/source
bridges and source evidence with zero remaining findings. They independently
verify fresh GitHub metadata, full-log equality, server ZIP digest, every original
ZIP/TAR/member byte, source-bound catalogs, indexes and all assessor assertions
for all eight complete reviewed scopes. Both axes also verify every final packet
copy and accept the packet with zero remaining findings and the stated qualifications.

Public beta remains0.1.131. Whole-app qualification and the combined signed beta
belong to131. Physical/OEM/live-service/provider/schema/funding/Play gates remain
separate. No production node/provider operation, public financial write or owner
phone development QA occurred. Preserve the canonical dirty checkout.

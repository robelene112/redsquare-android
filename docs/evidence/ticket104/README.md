# Native Saito Talk, issue104

Implementation and verification packet for [issue104](https://github.com/robelene112/redsquare-android/issues/104).
Review base: `c02fa1e3fb795b7e252f0973235bfc852ec891b4`.
Pre-run frozen source: `78d29df48f6e54b7f9515929ca7a8d34b4d3b741`.
**Acceptance and delivery remain pending while the complete runs execute.**

## Behavior and protocol

Native private-Chat audio/video calls and group rooms use compatible invitation
links, explicit prejoin/answer, real participants, media controls, camera switch,
audio output, foreground return/hang-up and background/screen-off voice. Camera
pauses outside visibility. An invitation alone starts no microphone or camera.
The saved node, identity, drafts, Chat and Fileshare retain their ownership.
Process loss interrupts a call; stale notification actions cannot restart or end
another call. Recording, transcription, scheduling, livestream and local screen
broadcast are outside this slice.

Authority is official Saito `e6c937c686495a37d8fc94ecc894189e23a453c3`:
Videocall, dialer, StreamManager, Stun and Relay. The owned Chrome fixture executes
all five hash-checked bodies with wallet/serialization/UI/network framework
adapters. Browser canvas/WebAudio and a debug/emulator-only input after actual
Android AudioRecord.read provide controlled media. Real codecs, DTLS/RTP,
decoded pixels, PCM, native capture and lifecycle are observed. This does not
establish physical microphone/speaker/camera quality, OEM behavior or live
community frontend/NAT/TURN acceptance. Native signalling uses owned endpoints;
no public development transactions or community recipients are used.

## Frozen complete verification

[Contract](full-contract.json) and [collector](verify-full.py) add24 identities,
15 classes/archives,16 normal completions, one deliberate interruption and one
continuity record. Accepted103 files and validators are unchanged. Both complete
runs must contain their three original successful partitions from the exact
source. No partial SDK assembly is accepted.

| SDK | Required identities | Classes | Archives | Normal | Deliberate interruptions | Continuities | Run |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Android10 | 687 | 254 | 212 | 275 | 39 | 38 | [43236585970](https://github.com/robelene112/redsquare-android/actions/runs/43236585970), cancelled before acceptance |
| Android17 | 688 | 254 | 212 | 276 | 39 | 38 | [43236591741](https://github.com/robelene112/redsquare-android/actions/runs/43236591741), cancelled before acceptance |

Keep the inherited API29 Profile assumption, unavailable Chat archive and four
failure-capture qualifications explicit. API29 has no later per-package shell
commands for resetting USER_SET/USER_FIXED; its actual denial/retry executes in
an isolated unfixed fixture, with original/restored package/flag observations.
Android17 requires exact restored microphone flags. Both full environments expose
two emulator cameras and require an actual front/back switch with fresh frames.
The separate single-camera journey verifies that Switch is unavailable.

## Independent source review

Both axes review the full production diff to frozen78d29df48. These are source
reviews; runtime and public delivery acceptance remain separate.

### Standards

No remaining hard violations or material baseline smells found. Earlier findings
are addressed: lease failures release only their owner, SDK delivery avoids the
authority/signalling-thread lock cycle, existing peers bootstrap Fileshare,
camera failures reset requested controls, preview mirroring changes on switch,
Chat fallback validation is shared, and Off/signalling preserve call-token
ownership. The frozen verifier retains inherited goals and adds Talk evidence.

### Spec

No remaining actionable findings identified. Earlier local Mute, capture Off,
invitation routing and mirror findings are addressed. Mesh verification now waits
for actual decoded video and PCM on every browser leg within the unchanged30s
bound and retains the accepted immutable snapshot. Shared transport, camera
availability, alternate invitation and reading-state fixes preserve the requested
consent and navigation behavior. No scope creep identified.

Source review totals: Standards0, Spec0. No worst outstanding finding on either axis.

## Original failures and limits

Original focused executions and complete logs remain outside Git under
`/mnt/storage/redsquare-android-artifacts/talk-native/proof-<run>` and adjacent
`.log` files. Focused passes on earlier source do not substitute for final full
acceptance. The maintained [handoff](../../handoffs/IMPLEMENTATION.md) records the
chronology and precise causes established by original evidence, including actual
routing/control/capture defects versus provisioning or premature observations.
Earlier zero-valued mesh video samples were not evidence of decoded group video;
the strengthened frozen goal must pass independently. The earlier permission-row
and reading-offset failures remain retained. A later pass alone proves no
unmeasured original race.

The browser uses the original upstream peer-close behavior. Native file/session
continuity after local Talk teardown does not promise that a remote browser will
retain its own connection when it deliberately hangs up. Approved URL handling
is observed with a package-constrained implicit VIEW; default association for
third-party HTTPS domains remains qualified. Closed/killed-app incoming ringing
has no new provider or reliability claim. Physical/OEM, selected-node Relay,
community frontend, NAT/TURN and Play/provider acceptance stay under36.

## Subsequent direct-call diagnosis, pending final acceptance

The two initial complete runs were cancelled after direct-Chat failures required
a production correction. They contribute no complete acceptance. a516 aggregate
43236312266/43236317715 and f940 diagnostic43238236355/43238241471 reproduce
JOINING/absent PCM under the original25s bound. The native trace records ignored
glare followed by an answer while candidate handling discarded all intervening
ICE candidates.656171ae1 validates and queues those candidates under the existing
128 bound until accepted SDP; the accepted SDP/ICE credentials still govern connectivity. Focused
results and final complete runs remain pending. Temporary diagnostic logs must
be removed before final delivery. Signed local candidate117 from78d29df48 is
superseded and must never be promoted; public beta remains116.

The strengthened mesh observation in43236537892 passes actual native JUnit and
all four decoded video/PCM legs. Its workflow fails afterward on temporary Chrome
profile deletion. The isolated process-group cleanup correction is distinct from
application acceptance and retains the original failed outcome.

The corrected656171ae1 direct audio/video journeys pass on Android10
(43239654847) and Android17 (43239660656), with actual decoded media, native
controls and exact unsent-draft return under the original25s goal. The only
production behavior change from diagnostic red to green is retaining reordered
ICE candidates. Temporary probes are removed for final source verification.
The cleanup wait-order finding is corrected in cb1f72d12 and independently
confirmed. Final complete and signed-update acceptance are still pending.

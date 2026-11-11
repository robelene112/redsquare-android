# Fast-scroll responsiveness, issue101

Review base: `c8f1654a1`, accepted beta109. Signed [beta0.1.110](https://davidengelen.nl/redsquare/) is delivered. Both complete SDK runs, signed updates, independent reviews and exact public delivery verification pass with the qualifications below.

A nondebuggable performance app drives three rounds of20 actual native swipes,
retaining Android frame statistics, Perfetto and native screenshots/window dumps.
Whole baseline43024558841 and composition-attribution43028401807 succeed.
The latter trace explicitly identifies main-thread `SocialActionOperations.busy()`
waiting34.398700ms on `SaitoLocalState.lock` while the background owner executes
`FileDescriptor.sync()`. Further waits cover name and avatar cache saves.
[Original trace query](busy-lock-contention.csv) retains exact observed names and times.
The large original traces, APKs and other diagnostics remain outside Git under
`/mnt/storage/redsquare-android-artifacts/scroll101`.

Only the rendering status read now reads the reactive SnapshotStateMap directly.
All actual operation acquisition, repeated-like queue mutations, identity changes
and durable journal writes retain their existing synchronization. No animation,
content, key, selected node, reading position or transaction format changes.
Diagnostic composition libraries opt in only for the isolated trace build;
ordinary release and default performance builds retain their original inputs.

The [actual old-source RED](lock-red/feed-scroll-io-ui.txt), run43032048915 on
`fc150cfecc904040706b8001f2b9ef7d492a03c6`, renders a new real signed post only
after8375ms, failing the predeclared3000ms goal and8000ms controlled store gate.
The test performs an actual owned PublicStringStore write and delays its enclosing
durable transaction; it does not claim that physical fsync itself lasts8000ms.
The owned worker stops and owned originally absent files restore even on failure.
The rendered-while-held assertion observes the actual native post before cleanup;
the final successful PNG is explicitly captured after cache restoration.

The pre-run101 contract retains every accepted100 goal, archive and original
deadline. Exactly one native identity/class/archive/normal completion per SDK
is added:651/652 identities,228classes,194archives,247/248normal completions,
37deliberate interruptions and36continuity records. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications remain.
The verifier changes only ticket paths and temporary prefix from100; inherited94
visual/motion/speech validators are unchanged. Full and signed-update gates remain pending.
[Independent source reviews](source-reviews.json) pass both axes with zero findings.

Diagnostic qualifications: public live content, network, JIT and runner scheduling
are uncontrolled; SwiftShader is a software emulator renderer. Compare the same
three-round workload but do not infer a controlled physical FPS improvement from
different live feed contents. Controlled native responsiveness regression provides
the causal before/after goal. No physical Pixel/OEM smoothness, store or provider
acceptance is claimed; parent1/36/55/84/86 remain open.

Original failed/cancelled diagnostics remain failed:43019853257 lacked the assumed
CI debug key (fixed with a disposable key);43021486726 failed trace path permission;
43023200562 failed classification of invalid OS completion timestamps;
43025397095 was cancelled before runtime after checking tracing handshake codes;
43025562386 failed native window startup observation;43026810992 required a footer
below the viewport of a long real post. Subsequent observer corrections preserve
actual public UI, capture failures and retain incomplete OS timestamps as unknown.
No failed execution contributes whole-run acceptance. A local optimizer experiment
did not complete its bounded build and was removed; no optimizer speed claim.

The [post-fix trace run](after-diagnostic/summary.json),43034190819 on`b381590d6`,
succeeds under the same three-round swipe workload. Main-thread busy-lock
contentions change from4/1/3 to0/0/0. This supports removal of that particular
storage wait. Raw timings remain mixed: first-trial main-thread maximum worsens
from70.15ms to151.73ms, with ongoing cold renderer/shader work. All original
frame samples and unknown completion counts remain. No general FPS improvement
or physical performance acceptance is inferred.

[Independent actual artifact reviews](artifact-reviews.json) pass both axes.
Candidate110 is42,650,666bytes, SHA256`dd548a3f6d4abb37b94aad25d1073e2be428c30376e79a3fdb0b776201b37414`,
compiled from`64494ef9e`. Exact downloaded staged bytes, existing certificate,
ordinary package/min29/target36, nondebuggable/nonprofileable/backupfalse manifest
and bundled license pass. The ordinary latest manifest remains109 at staging.

Focused runs43034181461/43034186221 fail before application build/runtime because
the Ubuntu24 runner cannot resolve its package mirror. Their original failures
are [retained](setup-failures). Same-source, unchanged-goal executions use the
already supported Ubuntu22 runner pool; failed setup never becomes an app pass.

Complete attempts43034659290/43034665079 and supported-pool attempts43035974534/
43035978384 contribute no application acceptance. The initial Android10 attempt
fails dependency setup in all jobs; Android17 has setup failures and one cancelled
setup job. The remaining supported-pool focused/full and initial update attempts
are explicitly cancelled while still in dependency setup. Fresh primary metadata
before cancellation is retained alongside final conclusions. No partial app
partitions are assembled.

The [reviewed CI correction](ci-setup-reviews.json) removes only the exact failing
Azure-first mirror line from the hosted image's existing APT mirror list and
requires nonempty remaining official archives. Package signature validation stays
intact. GitHub's [official runner setup](https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/scripts/build/configure-apt-sources.sh)
confirms Ubuntu22 and24 share that mirror map. New whole10/17, focused and signed
update runs execute from`50c224f80`. The [exact source bridge](source-equivalence.json)
checks every tracked top-level Git object except docs/.github, plus the frozen
contract/verifier: candidate110's compiled application/build/test/harness inputs
remain identical. Its compiled-source identity stays`64494ef9e`.

The preceding50c mirror change is a historical, insufficient attempt. Its fresh
runs also stop during dependency provisioning; no application gate is accepted.
The selected focused10 run is cancelled to obtain its original setup log, which
shows archive.ubuntu.com also failing DNS. The bounded [actual remote probe](runner-dns-probe/observations.log),
run43038383935, confirms all three Ubuntu names fail for ordinary and APT users,
while the kernel.org Ubuntu mirror resolves and returns HTTP200 for both.
Google and GitHub DNS work. This rules out an APT-user permission explanation.

Final source`54e06c357` prefers that observed reachable HTTPS mirror withpriority0
and preserves every original fallback and Ubuntu signature validation. The
temporary probe workflow is removed. [Both independent reviews](ci-reachable-mirror-reviews.json)
pass, including the [new exact22-object source bridge](source-equivalence-mirror.json)
from candidate110's compiled`64494ef9e`. The50c receipt remains historical only.
Fresh complete SDK runs43038811822/43038816776, focused43038801167/43038806657
and actual signed-update43038821274/43038826400 now execute from54e.

Both focused native runs43038801167/43038806657 pass at163ms/434ms under the
original3000ms goal while the controlled durable transaction remains held. Owned
worker/files restore cleanly. These are controlled UI responsiveness observations,
not physical FPS. Both actual signed83-to110 updates43038821274/43038826400 pass
exact downloaded bytes, ordinary/debug identity isolation, unsent draft, offline
post and Light preference through cold launch. See the focused/update packets.

Whole54e attempts43038811822/43038816776 are not accepted. The Android10 Wallet
job fails an unchanged gateway JVM assertion at line109; its actual recorded
request can precede the dispatcher that captures verification. Published
MockWebServer5.4.0 source confirms queue-before-dispatch ordering. The narrow
[fixture correction](gateway-observer-fixture/qualification.json) waits for the
unchanged snapshot only within the original five-second arrival goal;30-second
class and every signature/routing/payload/session/count assertion remain. The
local class passes in4.223s. Both [independent reviews](gateway-observer-reviews.json)
pass; the original failure cause is not claimed proven by repetition. Remaining
old full jobs are cancelled and contribute no partial acceptance. Fresh whole
10/17 runs43040719802/43040725444 execute from`75920db2d`, with an
[exact product and fixture bridge](fixture-source-equivalence.json) to the same
compiled candidate110. Complete execution and final delivery remain pending.

Both final complete runs now pass on exact`75920db2d63eff0d3cc5b2e621609c5bd13349ac`:
[Android10](full-final-android10/summary.json),43040719802, and
[Android17](full-final-android17/summary.json),43040725444. Each succeeds in all
three primary jobs. The unchanged pre-run frozen verifier accepts651/652
identities,228classes,194archives,247/248normal completions,37deliberate
interruptions and36continuity records. The inherited API29 Profile assumption,
unavailable Chat archive and four failure-capture qualifications remain explicit.
No partial jobs, new exception or weakened deadline contributes acceptance.
Final independent evidence review and public promotion remain pending.

[Final independent acceptance reviews](final-reviews.json) pass both axes with
zero findings, independently checking all1878 complete-run originals and144
signed-update originals. All23 product/source-bridge objects match. The exact
existing candidate110 is promoted at16:17UTC. [Public delivery](public-delivery110.json)
verifies HTTP200, exact latest/immutable manifests and notes, page loading, and
the actual downloaded42,650,666byte APK with the approved SHA256.
[Independent delivery reviews](delivery-reviews.json) pass both axes.

Issue101 is complete in beta110. Parent1/36/55/84/86 remain open. No physical
Pixel/OEM performance, provider activation, store submission, Saito publication
or production node operation is claimed. Stop until another issue is requested.

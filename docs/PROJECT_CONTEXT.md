# Red Square Android project context


## Active fast-scroll diagnosis #101

The owner reports intermittent visible stuttering during fast scrolling and asks
for improvements. Work on one [performance issue](https://github.com/robelene112/redsquare-android/issues/101),
starting from accepted beta109 / `c8f1654a1`. Measure actual Android frames and
system traces in the separate nondebuggable performance package before choosing
an application correction. Existing startup/function tests are not scroll timing.
The release configuration currently leaves R8 optimization disabled; this is a
configuration observation, not a measured cause. Existing packaged profiles are
present, so do not claim the APK has no baseline profiles.

The first diagnostic uses the existing hosted disposable Android17 emulator and
public native UI/OS observation seams, with a separate CI-signed performance APK.
No public version reservation, owner installation, production deployment, live
transaction or provider change is needed. Public-feed/network/content timing and
SwiftShader rendering limit the observation; do not claim physical Pixel/OEM
smoothness. Keep ordinary latest109 and parent issues unchanged. Whole baseline4 run43024558841 records three actual scroll rounds. The trace
then identifies main-thread monitor contention in SocialActionOperations.busy,
waiting on SaitoLocalState.lock while a background thread is in FileDescriptor.sync.
The native FeedScrollIoPreviewTest now reproduces rendering new signed posts
while an unrelated owned durable transaction remains pending. Its3000ms UI goal
and8000ms controlled gate were defined before the fix. Old-source run43032048915
failed the real UI goal at8375ms; its owned worker and files restored cleanly. Keep all atomic claims, journal writes and identity checks serialized.
The rendering-only status read now uses the reactive SnapshotStateMap directly,
without the unrelated durable-I/O lock. All operation claims and identity/journal
mutations remain serialized. Focused green and complete acceptance are pending. The R8 experiment did not complete
its bounded local build and was removed without claiming a performance result.
See #101 for acceptance.

## Delivered robust display names #100

Signed [beta 0.1.109](https://davidengelen.nl/redsquare/) resolves available public
author names across feed, conversations, profiles, activity and Chat. The reported
keys resolve to digirok@saito and CryptoKen@saito through the official Saito name
directory. The owner node returned empty reverse maps for both accounts during
actual read-only comparisons.

Directory labels are separate from authenticated registered ownership. The app
retains exact public keys for identity and actions, shows name provenance in
Identity details, and preserves the strict current-registrar verification and
verified-name cache. No historical signer was added to trust. The optional
saito.io directory uses a separate read-only identity and can be disabled under
Settings, Node without changing the selected content/Chat node. Provider caches
are independent; visible names retry after transient errors and survive ordinary
navigation and offline reading. Draft data-flow documentation records the lookup.

Both complete three-part Android 10/17 runs pass on `759f46f7e`: 650/651 frozen
identities, 227 classes and 193 archives per SDK, with all inherited goals and
deadlines retained. Both independent final Spec and Standards reviews pass with
zero actionable findings. Actual signed 83-to-109 updates preserve wallet,
draft, cached content, preference and separate debug identity on both emulators.
The immutable APK is 42,650,666 bytes, SHA256
`6d3a7cb431815c6338f5bd84fca633ccd10f258bcf3653241e74ee0910a5fdb3`,
compiled from `de0ae9b60`. An exact source bridge retains that APK across two
reviewed instrumentation-only corrections. Public latest, immutable manifest,
page, notes and actual downloaded bytes are verified.

The [ticket100 evidence](evidence/ticket100/README.md) retains original failed executions,
source bridges, complete results and delivery receipts. The inherited Android 10
Profile assumption, unavailable Chat archive and failure-capture qualifications
remain explicit. Fresh agreement checkpoints have no prior receipt; its narrow
restoration correction relies on the original failing checkpoint and source review.
Later isolation captures preserve the reading anchor; the original five-second
assertion establishes the next label, without separate native ink/timing proof.
No physical/OEM/provider/Play acceptance or production node change is claimed.
Issue #100 is complete. Keep #1/#36/#55/#84/#86 open and stop until another issue
is requested. Preserve the canonical checkout.

## Delivered native Stack reading #99

Signed [beta 0.1.108](https://davidengelen.nl/redsquare/releases/108/redsquare.apk) replaces ordinary Stack
sample stories with genuine signed public articles from the explicitly selected
node. Real authors, covers and Markdown images, followed authors, My articles,
bounded history and retained offline reading use the shared wallet and controls.
Local visual/Markdown drafts are durable; review copies Markdown and opens the
actual Stack website with its browser wallet. Native publication, restricted
access, payments and subscriptions remain first-release work under #1.

The owner node currently has no Stack archive; choose saito.io in Settings for
its available public articles. Native read-only checks exercise both approved
backends without substituting data, switching nodes silently or publishing.
Historical design fixtures are debug-only, separate from the ordinary route.

Complete Android 10 [42963703866](https://github.com/robelene112/redsquare-android/actions/runs/42963703866)
and Android 17 [42966576451](https://github.com/robelene112/redsquare-android/actions/runs/42966576451)
pass all three partitions. Frozen goals are verified with an independently
reviewed archive-partition catalog erratum: 642/643 identities, 220 classes,
186 archives, 239/240 normal completions, 37 deliberate interruptions and 36
continuity records. Only the inherited API29 Profile assumption remains.
The inherited unavailable Chat archive and failure-only captures remain qualified.
Both independent final reviews and actual signed 83-to-108 update gates pass.
Public immutable/latest manifests, page and downloaded APK match exactly.

Complete Android 10 source is `f7c92f01961c28be567d07c868625d78d1a3ee89`.
Complete Android 17 and compiled APK source are `706833f8fa332fb56e1d55e8a04839348a4ff2d0`.
The fixture-only rule retains the complete Android 10 run with exact product/core/build/
workflow/harness equality and a fresh whole Android 17 run. One test readiness wait changed
within its original budget. No partitions from different SDK runs are assembled;
the early failed Android 17 run remains failed and its underlying cause is unproved.

APK: 42,581,034 bytes, SHA256
`720b6d129afa1fa1e42a57ce1aeee2377d5c7a22d12f414d531e317f1b19772e`.
The established package/certificate and min29/target36 remain unchanged.
See [original evidence, qualifications and reviews](evidence/ticket99/README.md).
This completes only #99; stop. Parent #1/#36/#55/#84/#86 stay open.
Physical/OEM/provider/Play acceptance remains under #36 and OWNER_INPUTS.md.
No owner-phone QA, production-node/service changes or owner-wallet operations
were performed. Source checkout remains the isolated codex/unified-redesign
worktree; preserve the canonical checkout.

## Delivered UI fixes #97 and #98

Signed [beta 0.1.107](https://davidengelen.nl/redsquare/) delivers the requested
feed and profile corrections. Refresh completion stays below the tabs at the top
of the visible posts viewport, preserves the reading pixel and lets taps reach
posts underneath. Profile history uses a neutral information icon and "History
may be limited", with accessible Details explaining local/node history and
possibly missing older posts, replies or likes. Real loading, errors and recovery
controls remain separate.

Complete Android 10 [42914306789](https://github.com/robelene112/redsquare-android/actions/runs/42914306789)
and Android 17 [42931114125](https://github.com/robelene112/redsquare-android/actions/runs/42931114125)
pass all three partitions and the frozen verifier: 634/635 identities, 215 classes,
181 archives, 37 deliberate interruptions and 36 continuity records per SDK.
Only the inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-107 updates on both SDKs,
binary verification and exact public APK/page/manifest verification pass.
No partial SDK assembly is used. Earlier unsuccessful runs remain qualified.

The compiled APK and complete Android 10 use source
`8ab10cdd08909f969d485f0a4cb555e438e9bcc0`. Complete Android 17 uses fixture source
`82c01e74da76abd2117bedb3eeed73e60945a8cd`, tag `proof-ui9798-fixture-final`.
Exact production/core/toolchain objects match. The documented fixture-only rule
retains the complete Android 10 run while fresh Android 17 verifies stronger
input/window readiness inside the original budgets. Temporary probes are removed;
later Save/window readiness failure causes remain unproved. See [feed evidence](evidence/ticket97/README.md) and [profile evidence](evidence/ticket98/README.md).

APK: 42,388,522 bytes, SHA256
`55dbc679d116079f0e3d47c83db1073ffeeaac82633bd7fccf4e046e94ef41b3`.
The established package/certificate are preserved. This completes only #97 and
#98; stop after recording delivery. Keep #1, #36, #55, #84 and #86 open. Physical,
OEM, provider and Play acceptance remain under #36 and [OWNER_INPUTS.md](../OWNER_INPUTS.md).
No production node or service changes were made.

## Delivered fix #96

Signed [beta 0.1.105](https://davidengelen.nl/redsquare/) completes
[issue96](https://github.com/robelene112/redsquare-android/issues/96).
In Conversation, the visible Reply action beneath each post now opens a reply
to that exact post, including replies to replies. The redundant header bubble
is removed; Back, history information and post-body reading navigation remain.
Per-parent drafts, exact parent/thread/recipients, recreation and successful-send
return are preserved. Long-parent replies expose the writing field at large
text sizes without forcing focus or the keyboard.

Complete Android 10 [42887785531](https://github.com/robelene112/redsquare-android/actions/runs/42887785531)
and Android 17 [42878955982](https://github.com/robelene112/redsquare-android/actions/runs/42878955982)
pass all three partitions and the pre-run frozen verifier: 634/635 identities,
215 classes, 181 archives, 37 deliberate interruptions and 36 continuity records
per SDK. The single inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-105 update checks, binary gate
and post-promotion public APK/page/manifest verification pass. No partial assembly.

Compiled APK and both complete-run sources are
`c1c1d1066b28fb9eff626bc27908d8c79e409382`. The APK is 42,388,522 bytes, SHA256
`2975aa69337016bbfaa57ba368ff082135bc4dc094deb57c794e83e7c79a3d0a`, using the
established package and certificate. Later commits retain evidence/documentation.
See [ticket96 evidence](evidence/ticket96/README.md) for originals, reviews and qualifications.
This completes only issue #96; stop. Physical/OEM/provider/Play acceptance remains open
under #36 and [OWNER_INPUTS.md](../OWNER_INPUTS.md). Keep #1, #36, #55, #84 and #86 open. No node or service changes.

## Delivered fix #95

Signed [beta 0.1.104](https://davidengelen.nl/redsquare/) completes issue #95.
Successful posts, replies and quotes return to reading with brief "Post submitted"
feedback. The signed original remains durable for background observation without
resending it or erasing the next draft. Actual failures remain recoverable.

Complete Android 10/17 verification, independent reviews, actual signed 83-to-104
updates and public APK/page/manifest checks pass. Exact runs, source equivalence,
counts and limits are in [the handoff](handoffs/IMPLEMENTATION.md) and
[ticket95 evidence](evidence/ticket95/README.md). Stop after this delivery.
Physical/OEM, provider and Play acceptance remain under #36 and
[OWNER_INPUTS.md](../OWNER_INPUTS.md). Keep #1/#36/#55/#84/#86 open.

## Previous delivery #94

Issue #94's prototype-fidelity repair is delivered as signed beta **0.1.102**:
[archived APK](https://davidengelen.nl/redsquare/releases/102/redsquare.apk). The public latest pointer,
actual APK checksum and exact immutable notes are verified after promotion.
Frozen source: `1c7cb53d8c077aa1cd132deec22a1c5a72cc2d3c`, tag
`proof-prototype94-loaded-activity`. Production last changed at be37e8c42; later
changes are tests/evidence only, with an exact whole-tree bridge retained.

Both complete runs pass all three partitions: Android 10 [42452255196](https://github.com/robelene112/redsquare-android/actions/runs/42452255196)
and Android 17 [42451092093](https://github.com/robelene112/redsquare-android/actions/runs/42451092093).
The unchanged frozen Android 17 collector confirms 630 identities and no assumptions. The Android 10 collector
confirms 629 identities: 628 passed and the single inherited
API29 Profile assumption. Both catalogs have no missing or extra identities.
Both independent final packet reviews find no actionable defects. No partial or tail assembly is used.
Original 180-second class bounds, 214 classes and 629/630 expected identities remain frozen.

Actual signed 83-to-102 update/isolation passes Android 10 [42454280787](https://github.com/robelene112/redsquare-android/actions/runs/42454280787)
and Android 17 [42454286099](https://github.com/robelene112/redsquare-android/actions/runs/42454286099).
Independent actual artifact and update-native reviews find no actionable defects.
APK: 42,355,754 bytes; SHA256 `616a151011887ba2f19be9cba8fb23d69f33d4b4da15d02bebcc142d1ec14fb7`.
Signing certificate, package, minimum API29 and target API36 and vendored license are verified.
Never promote superseded candidates 93 through 101.

Prototype authority: `c95ecaa01abb71c576f052c7cc2303356416091c`,
`design-3d989e9e8406`, plus owner amendments: rounded named module selector,
real identity/data, separated profile history, compact repeatable heart, exact-post
Activity grouping and collapsing reading header. Do not copy sample identities or
artwork as live data. Stack remains explicitly a local design preview; actual
network/publication/payment/subscription work stays under issue #1.

Current originals, frozen collector results and qualified independent reviews are
in [ticket94 evidence](evidence/ticket94/README.md). Historical failed executions
remain failed. Known baseline missing failure-only captures are recorded by the
frozen collector; they are not missing application identities or waived goals.
Physical Pixel performance, actual provider delivery, business/policy setup and
Play acceptance remain pending under #36 and [OWNER_INPUTS.md](../OWNER_INPUTS.md).
Do not automatically close issues #1/#36/#55/#84/#86. Canonical checkout, other work,
production nodes and services remain untouched. Issue #94 is the previous accepted checkpoint; the later95 delivery is recorded above.

## Desired outcome

Build a professional, fast Android app for Saito's Red Square. Preserve familiar
Red Square navigation and social behavior for existing users. Turn speed and
polish into measurable acceptance criteria during the scope grill.

Performance target: on the owner's Google Pixel 7, a cold launch with cached
posts available reaches an interactive feed within two seconds. Show cached
content first and refresh in the background. Measure first-ever launch
separately: target an interactive feed within five seconds on the same Pixel 7
over stable Wi-Fi. Detailed network conditions and timing boundaries must be
defined in the specification so these targets can be tested consistently.

## Decisions already made

These are the original scope decisions, including questions that were open during
grilling. Published specs/tickets and the current handoff record the implemented
details. The completion-status section below identifies actual remaining work.

- The owner selected **Angels’ Foundry** as the app publisher on 2026-10-18.
  Use the business dossier's spelling, with the apostrophe after the s. This is
  the chosen publisher name; registered legal identity, account verification and
  actual public contact details remain to be confirmed for release. On 2026-10-19
  the owner confirmed that KVK registration is not yet complete.
- The app's display name is Red Square. The native application ID is
  `nl.davidengelen.redsquare`. Signed review updates retain the established
  certificate and package as documented in REVIEW_BUILDS.md. Actual Play signing
  enrollment and publisher configuration remain release #36 work.
- First-release baseline: the core social experience, including feed, threads,
  posting/replies, likes/reposts, profiles, following, in-app notifications and
  wallet creation/import/export. Additional features are decided individually;
  selecting this baseline does not automatically exclude them.
- Preserve existing Red Square capabilities within the agreed app scope.
  Research and compatibility verification are the agent's responsibility.
  Incomplete research alone must not be used to propose feature deferral.
- Include ordinary reposts and quote posts with commentary/images in the first
  release. Historical published source supports standalone quotes; current
  official source and both inspected served bundles use a different retweet/
  commentary path. Verify native/web transaction and display interoperability
  against explicit baselines rather than assuming a node update fixes this.
  Quote-post deferral was rejected by the owner. See
  [the quote-post trace](research/REDSQUARE_QUOTES.md) and
  [corrected version provenance](research/SAITO_VERSION_PROVENANCE.md).
- Include a Following feed showing only accounts the user follows, alongside
  the selected backend's general feed. Both views use that backend's available
  history and respect applicable personal controls and backend moderation.
  Native following behavior must be verified rather than inferred from web UI
  placeholders. See [the inspected baseline](research/REDSQUARE_BASELINE.md).
- Users can edit the text of their own posts and delete their own posts in the
  first release, using compatible signed Red Square actions. Deletion removes
  the post from normal application views; explain that historical or independent
  copies may remain. Edit timing, conflict handling and deletion cache behavior
  need specification. See [the inspected baseline](research/REDSQUARE_BASELINE.md).
- Users can edit their profile banner, bio and custom profile picture inside
  the app in the first release. Keep the generated Saito avatar as the default.
  Preserve compatibility with Saito Profile updates. See
  [the inspected profile features](research/REDSQUARE_PROFILES.md).
- Users can optionally choose and register a Saito username inside the app
  in the first release. Registration must not delay immediate first use.
  Preserve compatibility with Saito Registry; verify its availability and
  registration behavior for both supported backends.
- Attaching and viewing images in posts and replies are included in the first
  release. Attachment formats, limits and other media types remain open.
- The first release includes the Red Square GIF search and picker supplied by
  Giphy, for composing posts and replies. See
  [the inspected web media features](research/REDSQUARE_MEDIA.md).
- YouTube links in posts support playback inside the app in the first release,
  preserving the web experience's embedded videos. Playback begins only when
  the user taps; videos must not autoplay.
- Offline reading of cached posts and saving drafts are included in the first
  release. Posts and Chat messages explicitly sent while offline queue for
  automatic delivery when connectivity returns, with a visible Queued status
  and cancellation before sending. Saved drafts remain unsent until the user
  presses Send. This decision does not authorize queued SAITO transfers.
- Queued posts and Chat messages remain tied to the backend selected when
  the user pressed Send. Switching backends pauses those items until their
  original backend is selected again; switching must not reroute them.
- Show a small, non-blocking banner at the top with a red dot while disconnected.
  On reconnection, briefly show a green dot and "Back online", then hide the
  banner. Preserve feed position and composed work. Distinguish loss of internet
  from an unreachable selected backend so the banner describes the actual
  condition. Report recovery only when that condition has cleared.
- Product text must not use em dashes. Use plain punctuation throughout the app.
- The first-release interface is English, structured to add translations later.
  User posts and messages can use any language and punctuation; the app's copy
  conventions must not rewrite user content.
- Optional Android push notifications for replies and mentions are included in
  the first release, including delivery while the app is closed. The delivery
  infrastructure and initial notification defaults remain open.
- Ask for notification permission when someone first opens Chat or Notifications,
  with an option to skip. First launch must still open directly into the feed.
- New Chat messages also support push notifications, with controls to mute
  individual conversations inside Chat. Android system settings control the
  notification categories, accessible through a shortcut in the app. Categories
  distinguish replies, mentions, private chats and group chats.
- Private-chat notifications show "New message" by default. Users can opt in
  to message previews. Verify notification payloads and rendering preserve this
  privacy choice, including when the app is closed.
- Searching for posts and people is included in the first release. Search
  coverage and behavior depend on the selected backend's available data and
  still need to be defined.
- Personal controls to mute or block accounts are included in the first release,
  alongside backend moderation. The active wallet's personal mute and block
  choices apply on both supported backends; switching servers must not undo
  them. Backup/recovery behavior and remaining display details need specification.
- Account muting hides that account's posts and notifications while keeping
  Chat messages accessible. Account blocking also hides incoming Chat messages
  from the account. Explain these effects briefly where users choose the action
  and where they manage muted/blocked accounts. Distinguish account muting from
  conversation muting, which silences that conversation's notifications while
  keeping its messages visible. See
  [the inspected moderation behavior](research/REDSQUARE_MODERATION.md).
- Reporting posts for moderation is included in the first release. Report
  to the currently selected backend's moderators, with the destination clearly
  shown before submission. Submission acknowledgement and handling still need
  specification; reporting is not an automatic removal rule.
- Saito Chat integration is included in the first release, preserving its place
  in the familiar web Red Square experience. It includes private one-to-one
  conversations and group chats. Remaining Chat behavior and native
  interoperability requirements still need specification. See
  [the inspected chat integration](research/REDSQUARE_CHAT.md).
- Users can create named group chats, invite people and manage membership inside
  the first-release app. Follow Saito Chat's existing permissions: members can
  view membership and leave; admins can remove members and appoint additional
  admins. Verify invitation and membership-update interoperability. See
  [the inspected group capabilities](research/REDSQUARE_BASELINE.md).
- Match Saito Chat's existing privacy model in the first release: encrypted
  private one-to-one conversations, with group chats clearly labelled as
  unencrypted. Preserve protocol compatibility and verify native encryption
  behavior; this decision does not claim forward secrecy or complete history
  recovery from wallet import.
- Chat supports sending and viewing image and GIF messages in the first release,
  preserving compatibility with existing Saito Chat messages. Arbitrary live
  file transfer is a separate integration, not included by this media decision.
- First-release Chat history combines locally cached messages with available
  history recoverable from the selected backend or compatible peers, using the
  imported wallet's available conversation information and decryption keys.
  Archives may retain messages beyond active-chain retention. Preserve cached
  messages through ordinary updates and outages, including messages the backend
  no longer serves. Explain missing older history without promising permanent
  availability or complete recovery after reinstalling or changing phones.
  Guaranteed complete history migration and a separate complete-history backup
  are deferred. Cache retention and identity isolation need specification.
- Native Android in Kotlin, with Jetpack Compose and a Kotlin/JVM Saito core
  selected during implementation feasibility. See
  [the native integration decision](adr/0002-native-kotlin-protocol.md).
- Support Android 10 and newer in the first release. Verify the minimum version
  alongside the owner's Pixel 7 running Android 17 during the initial milestone.
- Support light and dark themes, following Android's system setting by default.
  Users can override the theme inside the app.
- Design the app to add further Saito modules, such as Saito Stack, without
  rebuilding shared wallet identity and backend connectivity. Red Square and
  Chat should have distinct feature boundaries. The owner subsequently included
  Saito Stack in the first release. The approved redesign #86 establishes its
  module shell with explicitly labelled review content; real archive, publishing,
  subscription and payment integration remains later first-release work under1.
  Never present a sample route as working production networking. See
  [the extensibility decision](adr/0001-shared-saito-foundation.md).
- iOS is deferred. A later iOS app would be a separate Swift implementation.
- User wallet creation, import and export are in scope. Wallet identity and
  local data must survive ordinary in-place app updates.
- The first release supports one active wallet per installation, shared by
  Red Square and Chat. Importing a replacement wallet requires a clear,
  explicit confirmation before replacing the active identity.
- Use the current Snake app's wallet import safeguards as the baseline:
  validate the file and key pair before mutation, preview a same-wallet restore
  versus a different-wallet switch, and require a fresh outgoing-wallet backup
  before replacement when its balance is positive or unknown. With a verified
  zero balance, strongly recommend backup but allow skipping it. Bind backup
  confirmation to the outgoing identity and selected import file. Require
  acknowledgement when a registered name or pending/unknown work belongs to
  the outgoing identity. Failed imports must preserve or restore the previous
  wallet, and success must verify the saved and active identities agree.
  Apply the already-agreed Android device authentication to wallet exports.
  Snake-specific receipt checks need corresponding Android/Saito checks rather
  than introducing Snake game features into this app. See
  [the inspected Snake safeguards](research/SNAKE_WALLET_SAFEGUARDS.md).
- The first-release wallet also displays balances and supports sending and
  receiving SAITO. Sending SAITO and exporting the wallet require system device
  authentication using supported biometrics or the phone's PIN. Transfer
  sending requires a live connection and a final confirmation showing the
  recipient, amount and fee before device authentication. SAITO transfers do
  not use the offline post/Chat queue. Unavailable-authentication handling,
  transfer status and error behavior still need specification.
- A fresh installation opens directly into the feed, creates a local wallet
  automatically, and offers a clearly visible import-existing-wallet option.
  Reading does not require completing a setup wizard.
- Users can begin posting and chatting without a mandatory backup step.
  Offer a dismissible backup reminder and clearly available export/recovery
  guidance. Seamless first use is an acceptance requirement: wallet creation
  must not introduce a setup wizard or block UI interaction. Actual cold-start
  timing and fresh-wallet delivery must be verified on Android. See
  [the inspected first-use prerequisites](research/FIRST_USE.md).
- Wallets are non-custodial: signing keys belong on the user's device. Never
  upload private wallet material to the VPS or GitHub.
- Preserve compatibility with existing Saito wallet and Red Square behavior;
  prove native signing and transaction interoperability rather than assuming it.
- The first release includes choosing between `https://saito.davidengelen.nl`
  and `https://saito.io`, with `https://saito.davidengelen.nl` selected by default
  on a fresh installation. Switching keeps the same wallet and identity without
  reimporting. These are the only selectable backends in the first release;
  users cannot configure a custom backend. Wallet imports must not silently
  add or select another backend. Feed switching behavior and control placement
  remain open. Archives and moderation can differ between backends.
- Keep the selected backend during an outage. Do not automatically fall back
  to the other server. Users can still choose to switch manually.
- Block production is deferred and is not an Android backend prerequisite.
- Use Matt Pocock's project-local skills. Implement tickets sequentially,
  without Ralph or parallel ticket implementation.

## Review and distribution goals

The owner's primary physical review device is a Google Pixel 7 running
Android 17, as reported by the owner. Plan for a stable review link with
build notes and an installable update; screenshots or video can assist review.
Updates should install over the same application and preserve its wallet.
Signing identity, application ID, versioning, backup behavior and device checks
must therefore be settled before delivery automation is claimed complete.

The owner delegated the review channel choice to whichever is easiest to iterate
on. Start with signed APK review builds at a stable link, with build notes and
an in-app update notice linking to the latest review build. One-time installation
permission for the browser/download source and ordinary Android installation
confirmation are acceptable for development review. Keep package identity,
signing and versioning consistent and verify wallet/local-data continuity.
Plan signing compatibility for later Google Play distribution before the first
review installation. Use Play internal testing when its setup makes it useful;
it is not a prerequisite for the initial development loop. See
[the reviewed distribution options](research/PLAY_REVIEW_DISTRIBUTION.md).

Google Play is the intended public distribution channel. APK distribution is
also an option. Store accounts, eligibility and submission policy must be
verified against current official sources when release work begins.

## Early beta visual priority

On 2026-10-13 the owner requested UI work immediately after private Chat #25
so he can show a beta to friends. Complete #25, then implement the bounded
[early visual beta slice #37](https://github.com/robelene112/redsquare-android/issues/37)
and publish a signed review update before resuming the remaining feature tickets.
Polish the already implemented feed, navigation, typography, spacing and Chat
while preserving the familiar Red Square experience and existing safeguards.
This changes delivery order, not the accepted feature scope. Integrated native
acceptance is complete under35. The owner parked actual physical Pixel measurements
and final continuity under36 on2026-10-19; their original requirements remain.

## Initial review milestone

On 2026-10-14 the owner requested further UI work after the active group Chat
ticket #26 so he can show the beta to friends. #37 is already complete and its
signed visual beta is published. After #26, inspect the current app for a bounded
polish pass, especially the new group screens and first-use presentation, before
resuming later feature tickets. Preserve the accepted feature scope and safeguards.

The first reviewable build prioritizes local wallet creation/import and a live
Red Square feed from either supported backend, including backend selection.
Prove native Saito connectivity and identity compatibility before adding posting,
Chat and the rest of the accepted release scope. Apply the agreed wallet import
safeguards and use this build to start physical-device performance verification.
This is a delivery priority, not a reduction of the first-release feature scope
or authorization to bypass the subsequent specification and ticket phases.

## Scope and current completion status

The original native functional tickets #2 through #35 and the supplementary UI slices
are implemented. The current delivery is the approved redesign #86 and prototype
fidelity correction #94, followed by post-submission completion #95, with exact
state and verification above. The original
specification #1, physical/provider/store release #36 and owner visual acceptance #55
remain independent. Audit #84 and parent #86 are not closed automatically by #94.

Repeated likes accept taps immediately without waiting for archive confirmation,
pending/failure UI or the superseded two-per-second cap. Each tap queues a distinct
signed action on the selected node; node-served counts remain authoritative.
Unavailable history stays honestly unavailable. Cached content and imported wallet
identity do not imply permanent history or actual provider delivery.

Use GitHub Issues for the current backlog and dependencies. Parked owner inputs
remain in OWNER_INPUTS.md and do not block independent native app work. Do not
ask the owner to repeat routine emulator or integration checks on their phone.

## Specification authority

GitHub Issues contain approved specifications and implementation tickets.
This file preserves project-wide context; it is not a substitute for a feature
specification. As of 2026-10-08 the release specification and all 35 tickets
are published on GitHub. The planning files contain pointers, not duplicate
specifications. Discover current work from GitHub and Git history in later
sessions. Record actual verification limits rather than treating authorization
as proof that a device check or integration has passed.

# Project context

## Current owner feedback, November 7, 2026: prototype fidelity rejected

Owner authorized completing issue94's prototype-fidelity repair. Root is the
only implementation writer in `/mnt/storage/redsquare-android-artifacts/redesign86`;
preserve canonical Chat WIP. Beta89 remains latest. Do not claim delivery,
promote historical candidates or start another issue. No production-node changes.

Current production/source: `7d20760c4cc9c8d41fc844e6733ddd97ea4259aa`.
Both source-review axes through7d report zero actionable findings. Complete
Android10run42393108712 and17run42393108364 are running on exact7d, with unchanged
frozen catalog/checker. All full4cc runs42390887070/42390887217 FAIL: Wallet10
feedback offscreen, Avatar three-target clipping, Escape regression on17 and
window-observation race on10. Its17 wallet partition passed; no partial/full mix.
Actual originals remain in `/mnt/storage/redsquare-android-artifacts/prototype94-full4cc-{10,17}`.

New production corrections after signed93:
- `WalletDialog` operation progress/results now remain outside its scroll body,
  immediately above the footer. A failed actual export/authentication previously
  left its message below the new balance/identity content. Existing visible-error
  assertion failed full4cc10 at WalletExportPreviewTest175. All operation callbacks,
  authentication/PIN/provider/readback/import safeguards and message meanings stay.
  Shared status motion remains; a small result host retains tone through exit.
- Shared native sheets regain Escape before any row has keyboard focus. A callback
  wrapper belongs only to the sheet's actual dialog window, delegates other keys,
  tracks initial down/uncancelled up, consumes repeats, resets on focus loss and
  restores the previous callback without replacing a subsequent owner. Module17
  full4cc image proves Account stayed open. Module10 image shows Stack restored;
  test now waits for both outcomes together within the same original5s budget.
- Avatar cache fixture now physically collapses chrome with one measured drag
  ending at zero velocity. It requires all THREE complete targets simultaneously
  inside the same viewport before unchanged pixels/rechecks/three signed>720KB
  sources/heap/deadlines. Previously only7px of the third picture was in view.
  No individual row reveal, fake list position or app/test-clock mutation.

Focused pending on b1 (wallet/avatar): export10/17 runs42392627216/42392627041;
avatars10/17 42392627246/42392627264. On7d: module-header10/17 42393008501/
42393008445 and broaderUI-regression10/17 42393008480/42393008551. These do not
replace fresh complete gates. Further native findings must be repaired honestly.

Signed93 is HISTORICAL STAGED, NOT PROMOTABLE after the new production fixes.
Actual producer4eae, SHA256931c7cd1c2e8938ff484310ff6fa461953ce0014607ac867d6d9b72714dc7c72.
Its actual update/isolation10/17 runs42388351996/42388351928 passed. Receipts and
445-path4eae→4cc equivalence are retained under ticket94, explicitly historical.
Signed94 was built on b1 before the Escape correction, so it is also OBSOLETE,
not staged. Gradle success3m28, signed manifest generated; outer exec eventually
returned143. Do not overwrite either version. Once native QA supports7d, build
fresh95+, stage only, verify actual signed update/isolation on bothSDKs, complete
same-final-source gates/collectors and native review, then promote unchanged notes.
No local builder is active. The existing notes are in staged93-manifest.json.

Earlier repair passes remain bounded/historical:
- First-use320dp/200% reading123→223px green42384551466; Conversation fixed-header
  post-scroll48dp red42383076638→green42384549365 viaz-priority.
- Refresh success overlay preserves reading, applied-result ordering and2500ms:
  actual two-method focused17green42387892609. Temporary probes removed.
- Node at-rest physical-drag source45 green42388559625 at systemscales0/1, actual
  identical pre/postresponse frames and exact post19y226. All original deadlines
  remain; no fast-fling or physical-phone claim.
- Policy foreground-window navigation/radio discovery focused10/17 PASS42390443450/
  42390443660. Exact TWO radio choices, audio/PID/selection checks and5s budget
  retained.
- Profile full48dp target/32dp paint/128dp cover/64dp avatar4cc10/17 green42390800414/
  42390800513. Source scrolls measured overflow before paint capture. Focused-only
  visual review20PNG/XMLpairs found zero actionable profile appearance findings,
  inventory `/tmp/prototype94-profile-final-spec-review.json`; not current whole-app
  acceptance. Earlier source/template fixtures are not fresh7d native proof.
- Media prototype128dp expectation with loading/loaded geometry equality, real
  pixels and normal/zero animation focused10green42390665139. The old3:1 assertion
  was explicitly obsolete against c95ecaa01, not a blanket acceptance waiver.

Parents1/36/55/84/86, physical Pixel/provider/KVK/Play and actual Stack integration
remain independent. Actual final native/profile/menu/motion review and all complete
collectors are still required before publication. Preserve retained failures.

The owner reports that published beta89 does not resemble the approved native
prototype. Issue94 is reopened for design integration; the redesign must not be
called visually complete. Existing functional/update/publication evidence remains
valid within its recorded scope, but the prior zero-finding native reviews did
not adequately establish prototype-to-beta visual correspondence.

Read the reference on `prototype/native-shell-live-design`, particularly
`shell-ui/.../Blocks.kt` and `prototype/native-shell/design.json`. Compare actual
screens directly at matching viewport, theme, font scale and equivalent content.
Confirmed profile differences include the cover/avatar composition, an additional
public-key/Identity details row and underlined full-width tabs instead of rounded
prototype chips. Preserve later agreed module-selector changes and real identity,
available data and wallet safeguards. Sample names, artwork and follower totals
must not be copied as live data. Beta89 remains published; this feedback update
changes no app code, tests, APK, node or deployment. Issue55 remains open.

## Delivery checkpoint, November6,2026

The owner-authorized five-ticket redesign cohort90/93/81/83/94 is delivered as
signed ordinary beta89 at https://davidengelen.nl/redsquare/. Complete same-source
Android10/17 gates, frozen collectors, both actual native/motion review axes,
original signed83→89 continuity and actual public checksum verification pass.
See `docs/evidence/ticket94/README.md` for exact producer/consumer provenance,
retained failures and bounded review limits. Normal updates retain package and
certificate. Stack remains explicitly a designpreview; real Stack integration
is still first-release work under1. Owner visual55, audit84/spec86 and physical/
provider/KVK/Play36 acceptance remain independent. This delivery does not close
those parents or authorize starting another ticket automatically.

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
  enrollment and publisher configuration remain release36 work.
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
  Saito Stack in the first release. The approved redesign86 establishes its
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

The app-wide product grill has settled the platform, release baseline, wallet
safeguards, social/Chat capabilities, backend choice, offline behavior, review
approach and initial delivery priority. On 2026-10-08 the owner authorized
sequential implementation of the proposed 35 tickets with a commit after each
completed ticket and final app review. This covers the proposed testing
boundaries and breakdown; do not request routine approval again.
[Specification #1](https://github.com/robelene112/redsquare-android/issues/1)
and its 35 sub-issues (#2 through #36) are published with native dependencies.
The original native implementation tickets2through35 and prior supplementary
slices were closed at the functional checkpoint on2026-10-19. Ordinary42 is
published after complete Android10/17 verification and two-axis review;
matching43 has separate signed35/43 installation/isolation proof. These functional
checks do not establish final app-wide UI quality.

The owner reviewed beta42 and explicitly rejected the claim that UI work was
finished. The active native backlog is now [app-wide UI55](https://github.com/robelene112/redsquare-android/issues/55),
consolidated from three independent whole-app audits, plus the requested
[Node wording56](https://github.com/robelene112/redsquare-android/issues/56) and
[repeat-like behavior57](https://github.com/robelene112/redsquare-android/issues/57).
The owner authorized implementing the complete backlog without routine stops.
On2026-11-01 the owner superseded57's local two-Likes-per-second cap. Like
controls now accept repeated taps immediately, without waiting for archive
confirmation or showing Like pending/failure UI. Each tap sends a distinct
signed action to the selected backend; node-served counts remain authoritative.
Issue71 owns this updated behavior and its controlled-peer verification.
Use the linked child issues and genuine tracker dependencies for sequential work.
The shared content-first frame58 is the first major UI slice. Rendered walkthroughs,
accessibility/motion checks and owner review of the signed result are required
before declaring app-wide UI polish accepted. Earlier closed37/39 are bounded
history, not proof that the current UI is done.

KVK, accounts, real provider/gateway activation and physical release checks remain
parked under release36 and OWNER_INPUTS.md. They do not block the native UI work.
Specification1 remains open. Discover current status from the tracker and handoff.

The following were the original specification/planning topics. Their native
functional behavior is implemented in the original tickets; these are historical
scope pointers. Current UI/repeat-like work is linked above, while actual remaining
provider/device/policy/store acceptance is in36. Future changes should retain the
accepted baseline and ask the owner only when a new decision materially changes
behavior or scope.

- Attachment limits, archive/search coverage, report acknowledgement and detailed
  personal-control/history behavior, grounded in the existing Saito capabilities.
- Secure wallet storage, compatible import/export format, identity-isolated local
  work, interruption recovery and unavailable-authentication behavior.
- Native Saito integration, protocol-version differences, any required backend
  adapter and push delivery while the app is closed.
- UI toolkit, feature architecture, selector placement and precise performance
  measurement conditions.
- Application ID, signing compatibility, build/update automation and phone review
  evidence. Verify store eligibility when release work begins.

## Specification authority

GitHub Issues contain approved specifications and implementation tickets.
This file preserves project-wide context; it is not a substitute for a feature
specification. As of 2026-10-08 the release specification and all 35 tickets
are published on GitHub. The planning files contain pointers, not duplicate
specifications. Discover current work from GitHub and Git history in later
sessions. Record actual verification limits rather than treating authorization
as proof that a device check or integration has passed.

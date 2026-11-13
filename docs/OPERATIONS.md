# Environment and integration references

## Project boundary

Source: `/home/david/projects/redsquare-android` on the live netcup VPS,
hostname `v2202609429510531338`, accessed as `david`.

GitHub: `https://github.com/robelene112/redsquare-android`.

The owner authorized making this repository public on2026-10-10. GitHub confirms
PUBLIC visibility. Before publication, Gitleaks8.30.1 scanned all308 reachable
commits and the text of36 issues/16 comments. Three historical detections were
reviewed: two known public secp256k1 generator points and one published unsafe
scalar1 reference key, with no unexpected credential finding. No tracked signing
keystore or production wallet file was identified. The scanner and raw reports
remain local under ignored `.scratch/publication-audit/`. This records the scan's
scope, not a guarantee covering every binary artifact or remote Actions log.
Signing secrets stay outside Git. Public visibility does not publish the app to
Google Play or change any node. Standard GitHub-hosted runner minutes are free
for public repositories; normal execution limits still apply.

The repo has a native Kotlin wallet/signature core, a Compose wallet screen and
an isolated Docker build. Signed wallet-foundation previews and build notes are
published at `https://davidengelen.nl/redsquare/`. Debug identity is isolated with
the `.dev` suffix. The diagnostic is explicit and debug-only. Live feeds and
safeguarded wallet import/export have emulator verification; see the current
handoff for accepted slices and remaining completed-app physical review.

The reviewed Android delivery requirement is ordinary in-place updates with
persistent wallet identity. Package/signing/version handling is implemented in
the review scripts. Actual signed 5-to-8 updates and identity continuity passed
on Android 10 and 17; see [verified evidence](research/WALLET_PREVIEW_VERIFICATION.md).
See [review build operations](REVIEW_BUILDS.md). Keep signing secrets outside Git
and record their management without their values.

## Saito authority on this VPS

When accessing the Saito node or Snake, first read
`/home/david/snake-stickerbook/AGENTS.md` and its current operations guide.
For Saito-specific APIs and transaction design, also read
`/home/david/.codex/skills/saito-dev/SKILL.md` when available.

The app repository does not authorize node deployment. Changes to services,
production configuration, wallet state or on-chain transactions require their
own explicit authorization. Never copy private options, keys or database content
into this repository, issue bodies or tool output.

For pinned source-only reads on this host, `david` cannot traverse `/opt/saito`
directly. An isolated Docker read-only mount succeeds. Use an empty environment,
disabled network, read-only container and named Git objects, with
`git --git-dir=/source/.git show <pin>:<source-file>`. Worktree discovery under
this build image rejected ownership even with per-command `safe.directory`;
explicit Git-directory reads succeeded. The cause of that difference was not
investigated. Do not change global Git configuration or mount writable node data
just to read source. Never use this source-read path for private options/databases. Named Git-object reads at
that directory failed with "not a git repository" on 2026-10-09. No repair was
attempted. A named public deployed Archive file was instead read through an
isolated network-disabled/read-only chroot and recorded by file hash in
[Following verification](research/NATIVE_FOLLOWING_VERIFICATION.md). This proves
a deployed-file observation, not a newly verified Git revision. Do not repeat
old Git reads or change live ownership/configuration without first checking
the maintained Snake operations entry point.

As inspected on 2026-10-07:

- Production node source/runtime: `/opt/saito/node`, service `saito.service`.
- Staging node: `/opt/saito-staging/node`, service `saito-staging.service`.
- Backend candidates: `https://saito.davidengelen.nl` and `https://saito.io`.
- The production node had block production disabled and was following the same
  recent chain as the checked Saito endpoints. This is a dated observation;
  verify readiness and freshness again before integration work.
- Red Square code is in `mods/redsquare/redsquare.js`. Its current browser flow
  relies on Saito transactions and archive/peer requests; inspected web routes
  are not a complete JSON API for a native app.
- Wallet behavior references are in `/home/david/snake-stickerbook/snake.js`
  and `web/wallet-loader.js`, alongside the local wallet import/bridge tests.
- The Saito runtime pin is documented by Snake's
  `scripts/saito-runtime.json`. Treat the deployed version as authority and
  re-read that pin when planning protocol integration.

Do read-only research first. Native signing, wallet export compatibility and
mobile feed access require explicit evidence. Wallet/signature reference tests
now exist in the native implementation; network/feed interoperability remains
work for the subsequent tickets.

## Native Vault integration under #105

Vault uses the shared wallet and selected node. Read-only service discovery on
2026-11-13 found `vault` and `Store` at saito.io, but neither at the owner node.
An HTTP module page alone does not establish an available peer service. Do not
activate services or change the saved node as part of app implementation.

Protocol references execute saito-wasm 0.3.15 against official source
`0db0062d873369dc013046ed77c97410b5683b64`. These are synthetic, explicitly unsafe
fixture identities, never funded consensus mints. The owner node's observed
saito-js package is 0.3.2; the reference pin does not establish its runtime version.
Standard retrieval authenticates the signed file and its access commitment,
and observes current selected-node ownership before a new request. Saved copies
are wallet/node scoped and encrypted locally, with explicit removal and export.
The operator can read the remote content. Expiry or transfer cannot recall copies.

The native file bound is 4 MiB, below the reference browser's 100 MiB. It preserves
the existing 8 MiB signed-envelope and 16 MiB peer-response budgets. Local copies
use individually encrypted 2 MiB parts committed by one metadata record. Native
`vault-reading` scopes cover reading, offline restart/export/removal, unavailable
service, moved keys, wrong replies, local-save failure, bounded key pagination,
12 theme/text/width cells and byte-exact 4 MiB export. Focused checks do not replace
#115's complete integration, signed-update and combined-beta acceptance.

## Native Stack article integration

Signed beta108 delivers the ordinary native public-reading route. Complete
Android 10/17, independent packet reviews, actual signed updates and exact public
delivery pass; see [ticket99 evidence](evidence/ticket99/README.md). Historical
design fixtures do not accept live behavior. No production node was modified.

Issue #99 replaces ordinary sample article routes with signed public Stack reads.
Protocol authority: the deployed named public Stack source observed November11
(SHA256 `1e22660820753d3755593da325dab630dae6e3179fe10e5ec04ce7feb2670f85`),
the documented owner pin, and official `e6c937c686495a37d8fc94ecc894189e23a453c3`
`node/mods/stack/stack.js` and `lib/ui/view-post.js`. No runtime/configuration changes.

Use Archive load with `field1=Stack`, `field4=stack:post` and a bounded created-at
cursor. Ten-row pages account for embedded cover sizes; the existing peer and
Archive response limits remain unchanged. Authenticate original envelopes before
reading `module=Stack`, `request=create stack post request`, `data.type=stack_post`.
An article's `parent_id` identifies a revision's root; author and logical signature
jointly identify the native article so another signer cannot replace its content.
Covers may be raw base64, data URIs or imageUrl. Markdown's `stack:image:<id>`
references resolve only through the signed `images` entries (`id`, `mime`, `data`).
Restricted messages with access hashes/scripts do not become public articles.

The selected node remains explicit. On November11 the owner endpoint advertised
no Stack service and returned zero Stack rows. The official endpoint advertised
Stack and returned10 rows, authenticated by the native decoder as9 logical articles.
The owner /stack URL returned its public404 page. Empty/unavailable reading must
not silently change node or substitute sample stories. A real node-setting action
and original Stack website links let users make their own choice. Real native
publication/access/payment/subscriptions remain under #1, not this reading slice.
Only historical preview/editor/access tests opt into an explicit debug-only design
fixture. Shared module/header/menu goals exercise the real native Stack route with
owned signed fixtures. New native reading, continuation/recreation, local draft
and approved public-read journeys supplement every existing identity/deadline.

The active reading window and cursor are separately stored by wallet/backend/author
filter so older loaded articles survive navigation and outages even after the newest
cache reaches its byte budget. Ordinary local drafts are wallet-scoped, durable
and retain visual plus canonical Markdown. Internal and shared navigation await a
final visual snapshot and atomic save. The publication review explicitly copies
Markdown and opens the selected Stack website with the browser wallet; it does not
claim a native broadcast, purchase or entitlement. Native publication/access remains
under #1. The prior disposable design draft had no on-disk format to migrate.

### Native Stack reading links102

Approved public `/stack`, `/stack/<author>` and `/stack/<author>/<signature>` URLs
use the native reader through `StackLink` and the app URI handler. The signed post
URL is the destination; unsigned preview hints cannot retarget it. Other hosts,
unsupported URLs and explicit website publication/access/Open original actions
retain the external browser path. Markdown reading links inherit the native handler;
deliberate website actions use `LocalExternalUriHandler` to avoid routing loops.

An exact article read uses Archive `sig`, the requested `field2` author, the existing
Stack fields and `limit=1`. Authenticate the envelope and match its author/revision to the URL before
collapsing logical revisions. Do not substitute the newest story or depend on the
newest discovery page containing the requested revision. Unavailable/rejected
articles retain Retry and the original signed URL for an explicit website action.

The URL's approved node is temporary, visible reading context. It does not write
the saved node choice. Entry scope also records the current wallet and saved node;
changing either invalidates that context. Retained module state restores the
originating feed/thread/profile when leaving the link reader. Ordinary Stack
module state is separate from this one active linked scene.

The linked reading window is one bounded file per wallet/node, identified by its
exact target in metadata. Opening many URLs must not create one file or retained
scene per article. General author windows/cursors remain separate. Cancelled
readers check coroutine ownership under the shared public-store lock before
committing, so an old link cannot replace a newer reading window after navigation.
While a link image is unavailable/loading, its input guard preserves child Retry
and scrolling and prevents repeated Retry taps from activating the parent card;
the loaded image remains a normal way to open the article.

## Native Saito Talk

Issue104 adds native direct Chat audio/video and Talk rooms. The compatibility
pin is official Saito `e6c937c686495a37d8fc94ecc894189e23a453c3`, using Videocall,
StreamManager, dialer, Stun and Relay. Protocol/source hashes, actual media tests,
release state and qualifications are in [the evidence packet](evidence/ticket104/README.md).

Talk shares one `SharedRtcPeer` per wallet/backend/counterparty with Chat and
Fileshare. Negotiated ordered data-channel42 and independent media/file leases
must retain coherent SDP and routing. Local capture controls cannot wait for a
Relay acknowledgement. Wallet/node/block changes invalidate their exact call;
notification actions require its current token. Process loss interrupts the call.
An unsigned invitation alone authorizes neither identity nor capture. Signed
signalling, the actual DTLS peer, room membership, permission and explicit consent
precede transmitted media. Voice continues under an ongoing foreground service;
camera pauses while the app is not visible.

Node transport ownership is separate from WebRTC ownership. `SaitoPeer` shares
one authenticated physical socket per wallet and exact endpoint; disposing one
feature releases only its lease. Pinned Saito module callbacks look up the latest
socket by public key, so separate same-wallet feature sockets can misroute replies.
Request IDs use one positive process-wide namespace without reset or wrap across
reconnects. Random initialization reduces, without guaranteeing, uniqueness
across separate process starts. Receiver callbacks and publication drain also
retain independent feature ownership.

At the controlled-peer seam, use `goOffline()` to establish an actual outage:
it rejects new upgrades and retires admitted sockets, including incomplete
handshakes. `rejectNewConnections=true` alone intentionally leaves an existing
connection usable. Preserve all inherited outage/recovery assertions and bounds.

Controlled Relay callbacks run on the WebSocket reader by default. When a test
holds a Relay reply while requiring incoming acknowledgements on that same
connection, supply an owned `relayResponseExecutor` so only the reply waits.
Release its gate and verify executor termination before restoring wallet/files;
worker validation failures must reach fixture health. Keep the original deadlines
and require the exact incoming request ID. A delayed-handshake test must first
retire already authenticated routes and positively observe a fresh held handshake.

Approved `/videocall` HTTPS links and the native return route open prejoin.
Opening a link does not change the saved node. Package-directed intent evidence
does not establish default association with a third-party HTTPS domain.
Native camera.any, back-camera, autofocus and microphone features are explicitly
optional. Preserve these declarations: CAMERA permission otherwise implies a
required back-camera feature during package filtering. Actual built `aapt dump
badging` must show all four as non-required.

Owned Android/browser tests do not establish live selected-node Relay/Stun,
TURN/NAT, fully initialized community-client behavior or physical ARM/OEM media
quality. Keep these qualifications under open36, alongside existing provider,
closed-app push and Play gates. This feature authorizes no production node or
provider changes. See [the data inventory](research/PLAY_DATA_INVENTORY_2026_10_15.md#native-saito-talk-november-12-development-addendum-104).

## Android GIF configuration

The Posts integration reads optional Gradle properties
`giphyDebugPostsApiKey` and `giphyReleasePostsApiKey` for the corresponding
application variants. Both default to empty. An unconfigured build preserves
existing GIF viewing but reports Search unavailable. A configured key is built
into the APK; changing it requires a new build and an ordinary signed update.
Do not claim live search acceptance from the owned provider fixtures.

Use a key whose holder authorizes this Android/Posts integration. Neither the
Saito server's browser key nor service discovery establishes that permission.
Chat uses separate `giphyDebugChatApiKey` and `giphyReleaseChatApiKey` properties
through the native integration child #43 of #31. The Chat screen scopes the
shared picker/renderer to that configuration; it never falls back to the Posts
key. Current controlled recovery work is not whole Chat-GIF acceptance.
The existing owner setup question
remains pending; these instructions do not request another account or key.

For the maintained Docker wrapper, Gradle's user directory is the host's
ignored `.scratch/gradle-cache`, mounted at `/gradle-cache`. An operator can
place the appropriate property in its `gradle.properties`, preserving other
settings and keeping that file private. Do not put values in tracked files,
command-line arguments, logs or issue comments. `scripts/review-build` uses
the same wrapper and the Release property. The normal host `~/.gradle`
directory is not mounted by that wrapper. No configuration was installed here.

After authorized configuration, verify actual provider search, selection,
attribution and recovery on Android before closing #16. Build and publish the
next monotonic signed review version through [review operations](REVIEW_BUILDS.md).
The [data-flow inventory](research/GIPHY_DATA_FLOW_2026_10_17.md) describes
the implemented direct API and media requests and remaining release facts.

## Agent workflow provenance

Matt Pocock's skills are pinned to commit
`f3fc5632f401156837ee3872f14fe33ccf1024ea` from 2026-10-07.
`.agents/skills.lock.json` records file hashes; `.agents/UPSTREAM_LICENSE`
preserves the upstream MIT license. Install/update only this repo's
`.agents/skills/` unless explicitly asked to affect other projects.

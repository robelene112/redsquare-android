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

## Android GIF configuration

The Posts integration reads optional Gradle properties
`giphyDebugPostsApiKey` and `giphyReleasePostsApiKey` for the corresponding
application variants. Both default to empty. An unconfigured build preserves
existing GIF viewing but reports Search unavailable. A configured key is built
into the APK; changing it requires a new build and an ordinary signed update.
Do not claim live search acceptance from the owned provider fixtures.

Use a key whose holder authorizes this Android/Posts integration. Neither the
Saito server's browser key nor service discovery establishes that permission.
Chat configuration is separate work in #31. The existing owner setup question
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

# Saito and Red Square version provenance

Read-only investigation on 2026-10-08. Production sources were inspected through
a network-disabled Docker runner with read-only mounts. Public GitHub sources,
HTML, JavaScript and source maps were fetched without executing the application.
No wallet/options/environment/database contents were read, transactions submitted,
services changed or node updates performed.

## Outcome

The owner's node is behind current official development, but its Red Square
redesign is official upstream code, not an owner-specific replacement. The old
`SaitoTech/saito-lite-rust` implementation is archived and is not the current
vanilla baseline. Both current official source and the JavaScript actually
served by `saito.io` still use the redesigned retweet path, including its
commentary-display limitation. Updating alone is not an established fix for
quote posts. Sources and qualifications follow below.

## Repository and deployed revision

- The active official repository is [SaitoTech/saito](https://github.com/SaitoTech/saito).
  Its default branch is `prod`. Its inspected head was
  [`214504ecece76d1cd20a245fe4468eccbfca2951`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951),
  committed 2026-09-29 at 15:04:28 UTC, message `fixing publication worlflow`.
  The [official repository metadata](https://api.github.com/repos/SaitoTech/saito)
  reports that it is not archived.
- Official `staging` was
  [`e9f3635fa56450008774f2d8d76731121268fe7a`](https://github.com/SaitoTech/saito/commit/e9f3635fa56450008774f2d8d76731121268fe7a),
  committed 2026-10-08 at 12:52:48 UTC, message
  `Add Explorer header navigation menu component`. This is a development branch,
  not proof of what a particular public server has deployed.
- Deployed Git metadata under `/opt/saito` identifies HEAD as
  [`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
  committed 2026-09-16 at 04:20:21 UTC, message
  `Merge branch '263/test-rectification' into staging`.
  This agrees with the maintained local pin in
  `/home/david/snake-stickerbook/scripts/saito-runtime.json:2-7`, which specifies
  the active repository, this candidate commit, saito-js/wasm `0.3.2` and tested
  Node `v24.13.0`. The authority and runtime paths are documented in
  `/home/david/snake-stickerbook/docs/OPERATIONS.md:10-31`.
- The [official commit comparison](https://github.com/SaitoTech/saito/compare/9a4ca7e9442eb5e872168ced361ce741d7d139d7...214504ecece76d1cd20a245fe4468eccbfca2951)
  reports `prod` 99 commits ahead, zero behind. Its API file list is truncated
  at 300 files; absence from that list is not evidence a module stayed unchanged.
  Direct file comparison confirms Red Square changed.

Three deployed files match the pinned official commit byte for byte. The hashes
were independently calculated from local static source and the corresponding
official raw files:

| Path relative to `/opt/saito/node` | SHA-256 at deployed/pinned revision |
| --- | --- |
| `mods/redsquare/redsquare.js` | `e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8` |
| `mods/redsquare/lib/ui/overlays/compose.js` | `35819245c4a2e6fb37c69cb651d32d2343a9c76daf297f0a56059793dde3c771` |
| `mods/redsquare/lib/tweet.js` | `07ea0b50eac7c21ac8452b51fdb7bdda27efb5de7c62e5288a220ffaf23bf908` |

Corresponding pinned sources:
[RedSquare](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js),
[composer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js),
[Tweet](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js).

Read-only tracked worktree status showed a modification to
`node/lib/saito/core/server.ts` (one insertion and one deletion) and a deleted
issuance-file path. It showed no tracked RedSquare or Chat changes. This check
excluded untracked files and does not claim there are no separately installed
modules: the custom Snake module has its own maintained source and publisher,
as documented in the operations guide. No issuance contents were inspected.
The deployed Git metadata cannot establish a complete intact repository history:
the metadata query printed HEAD but also reported a missing tree object. The
selected-file hash comparisons independently establish their source provenance.

## Actual public builds

The [official Red Square HTML](https://saito.io/redsquare/) referenced
`/saito/saito.js?build=1791446074`. The
[owner's Red Square HTML](https://saito.davidengelen.nl/redsquare/) referenced
`/saito/saito.js?build=1790715873`. These are public build identifiers, not Git
SHAs. Both bundles expose public source maps:
[official source map](https://saito.io/saito/saito.js.map) and
[owner source map](https://saito.davidengelen.nl/saito/saito.js.map).

The fetched [official bundle](https://saito.io/saito/saito.js) was 9,632,005 bytes,
SHA-256 `754087df8c15863a6c2f82f84848de05af75ea1b3e4982ba96bca96a52733788`.
The [owner bundle](https://saito.davidengelen.nl/saito/saito.js) was 4,605,534 bytes,
SHA-256 `4b5636b7cc8e4469c9b033afd8b234c56ce27b338c7a6371060a41fa2103eba6`.
Bundle size/hash differences also reflect selected modules and build settings;
they do not independently identify the source commit.

The official source map contains the redesigned RedSquare composer, receiver
and notification implementations. It also includes `hasReplied()` and replier
tracking in `mods/redsquare/lib/tweet.js`, which appear in inspected `staging`
but not inspected `prod`:
[staging Tweet source](https://github.com/SaitoTech/saito/blob/e9f3635fa56450008774f2d8d76731121268fe7a/node/mods/redsquare/lib/tweet.js#L583).
This establishes that the served module includes newer behavior than the
inspected `prod` Tweet file. It does not establish saito.io's exact Git SHA or
that every bundled file matches the current staging head.

## Corrected quote-post baseline

The archived repo's
[`7200a9886f3dd02f66e8d6c146960e2a5402929f`](https://github.com/SaitoTech/saito-lite-rust/commit/7200a9886f3dd02f66e8d6c146960e2a5402929f)
is dated 2026-09-04 at 04:32:57 UTC and explicitly marks the repository deprecated
and archived. Its older
[quote composer](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/post.js#L305)
creates a standalone post carrying `retweet_tx` when commentary/images are
present. That is valid historical feature evidence, not evidence of current
official deployment behavior. This corrects the baseline implied by
[the earlier quote trace](REDSQUARE_QUOTES.md).

Current `prod` source instead:

1. [Builds optional text/images with the original signature](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L577)
   and [always sends a retweet transaction for that composer mode](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L657).
2. [Preserves the interaction as RedSquareRetweet](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1251).
   The [receiver updates the original's counters and retweeters](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1479)
   and adds a notification, rather than constructing a separate commentary post.
3. [Labels commentary/image interactions as quotes](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L62),
   but [renders the referenced original](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L137).
   Its [ordinary feed loader selects RedSquare](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/loader.js#L277),
   rather than RedSquareRetweet.

The actual official public source map confirms the same paths: embedded source
`dist/bundler/default/mods/redsquare/lib/ui/overlays/compose.js:985` calls
`createRetweetTransaction`; `mods/redsquare/redsquare.js:2408-2533` stores the
retweet and updates its original, ending with a notification;
`mods/redsquare/lib/notification.js:94-98` classifies quotes and `:151-163`
renders their original. There is no old `retweet_tx` field in the fetched bundle.
The owner's current source map follows this redesigned path too. These are
static code findings, not a live posting experiment or confirmation of every
server-side deployment detail.

The app's agreed inclusion of quote posts is a separate product decision and
remains in scope. Native rendering and cross-backend transaction behavior need
an explicit compatibility design; claiming an ordinary node update restores
the old standalone quote implementation would be unsupported.

## Update recommendation

Plan a separately authorized maintenance assessment and staged update to a
reviewed, pinned official release before freezing native protocol integration.
The deployed revision is demonstrably older, and current RedSquare includes
substantial feed-loader, notification-state and curation changes:
[revision comparison](https://github.com/SaitoTech/saito/compare/9a4ca7e9442eb5e872168ced361ce741d7d139d7...214504ecece76d1cd20a245fe4468eccbfca2951).
Preserve the custom Snake module, server customization, wallet identity and data
obligations using the maintained operations workflow. Do not jump to an
unreviewed staging head merely because the public bundle appears newer.

This inspection establishes version lag, not an urgent update requirement or a
specific security failure. It also does not establish that an update alone
resolves quote presentation. App development can continue with explicit version
baselines and compatibility checks for both supported backends. No update has
been performed or authorized by this research.

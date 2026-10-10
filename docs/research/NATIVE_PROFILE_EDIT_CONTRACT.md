# Native profile editing contract

Read-only source research for #18, 2026-10-10. A/B are official Saito pins
`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`. A Profile file matches the existing
reference, SHA-256 `15777808a5e4e88603b87dec7c28af177b418a9a46d0d8278ed569a25fc23d93`.
Sources establish contracts, not current deployment revisions or runtime
interoperability. No node access, app initialization, private data, builds or
live writes.

## Signed state and ordering

Both pins publish a complete signed snapshot:
`{module:"Profile",request:"update profile",snapshot:1,data:{description,image,banner,...}}`.
Avatar field is `image`. Sender merges a partial change into its complete own
profile before signing; null/undefined remove fields. A snapshot containing only
a changed bio would erase unrelated avatar/banner/extension fields. Preserve
verified existing fields, including `archive` and unknown extensions, within
bounded payload limits. Do not invent editable identity or display-name fields.
[A sender:354–380](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L354),
[B sender:356–382](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/profile/profile.js#L356).

Recipients are self only, using the wallet default fee; A/B default amount/fee
is zero. Unfunded construction is supported by source, not demonstrated delivery.
Profile identifies the account from transaction `from[0].publicKey`, not a field
inside data. Native must verify signed bytes, exact module/request, valid snapshot
marker (`1` or `true`) and requested author before exposing content.
[A receiver:444–456](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L444),
[wallet defaults:33–87](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L33).

Read `{field1:"Profile",field2:author,limit:100}` through selected-peer Archive.
Readers choose the newest snapshot by signed transaction timestamp; if none
exists, merge legacy updates oldest to newest. A snapshot wins over legacy
updates even if some legacy timestamp is later. Equal timestamp order depends
on returned order; source defines no signature tie-breaker. Do not use unsigned
Archive `updated_at` as profile version authority. Receiver live-cache assignment
has no newer-timestamp guard, so native should order validated state itself.
[A read/reconstruction:280–350](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L280),
[B receiver:446–470](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/profile/profile.js#L446).

Server reception runs through `onConfirmation(conf=0)`; no Profile-specific peer
write handler was found. Server archives every received Profile transaction
with `preserve:1`; storage defaults index signer as field2 and self recipient
as field3. Browser watched-key cleanup deletes previously indexed local history
when snapshots arrive; this is not server history pruning. Neither preserved
archive flag nor the bounded query guarantees permanent recovery.
[B confirmation:64–72](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/profile/profile.js#L64),
[A cleanup/save:475–509,536–561](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L475),
[storage defaults:149–164](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L149).

## Images and bio

Web banner selection crops at 3:1, obtains a canvas data URL, then resizes to
at most 1920×1024 while reducing JPEG quality toward 512 KiB of data-URL text.
That target is an iterative stopping condition, not an enforced transaction
limit. Images are embedded strings in the signed snapshot, not separate server
uploads. No profile-specific byte limit or bio limit is enforced in these
sender/receiver paths. Avatar rendering already accepts `image`; no avatar
upload control was found in the inspected original web profile.
[crop:48–79](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-photo-cropper/saito-photo-cropper.js#L48),
[resize:2286–2327](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L2286),
[banner dispatch:45–51](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L45),
[render:512–555](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-profile/saito-profile.js#L512).

Native policy should reuse safe media decoding, finite encoded/decoded sizes and
pixel limits, preserve arbitrary bio Unicode/newlines as plain text, and check
the **whole serialized snapshot** against the existing 1 MiB outgoing message cap.
Two individually acceptable images plus metadata can exceed that cap. Resize
before signing; report failure without destroying the prior profile or selection.
Omit removed image/banner fields from a full snapshot so generated-avatar/blank
banner fallback returns. These bounds are native policies, not Saito consensus
rules or evidence that every historic profile fits them.

## Exact generated Saito identicon

A/B pin `identicon.js` 2.3.3. Its npm tarball SHA-1
`c505b8d60ecc6ea13bbd991a33964c44c1ad60a1` was checked; identicon.js bytes match
author repository commit `4fad7cafec1b7a4d896015e084e861625ef5d64f`.
Saito passes BLAKE3 hex of the public-key **UTF-8 Base58 string**, size 420,
margin 0, saturation 0.6, brightness 0.4, format SVG. Background remains the library
default opaque RGB 240,240,240. [Saito call:514–528](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L514),
[package pin:62](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/package.json#L62),
[npm metadata](https://registry.npmjs.org/identicon.js/2.3.3).

The first 15 hex nibbles select even=foreground/odd=background. Indices 0–4
populate the middle column top-down; 5–9 populate mirrored columns 1/3; 10–14
populate columns 0/4. Hue is last 7 hex nibbles divided by 0xfffffff. Match the
library's HSL conversion and rounded RGB; cell size 84 at 420, SVG foreground
stroke 2.1. Native Canvas can reproduce this geometry without loading arbitrary
SVG. [algorithm:26–89,110–124](https://github.com/stewartlord/identicon.js/blob/4fad7cafec1b7a4d896015e084e861625ef5d64f/identicon.js#L26),
[SVG geometry:155–181](https://github.com/stewartlord/identicon.js/blob/4fad7cafec1b7a4d896015e084e861625ef5d64f/identicon.js#L155).

Independent vector seam: existing unfunded synthetic public keys → pinned WASM
BLAKE3 hash → author's pinned Identicon SVG/foreground/rectangles. Compare native
hash, cell occupancy and RGB, plus device rendering. Include a key with a leading
hash zero and varied hue. No vectors or runtime rendering were executed here.

## Recommended recovery and verification

Use verified signed envelopes for cached public profiles; isolate drafts/pending
updates by owner/backend. Load own available state before publishing a complete
snapshot, and do not interpret failed lookup as an empty profile. Retain selected
media/local edits during outage; only explicit submission can publish them.
Persist candidate before network use, retry original bytes and reconcile exact
`field1=Profile,sig=candidate.id`, author and payload. Distinguish available from
superseded by a newer verified snapshot. Preserve newest verified state through
stale responses, failed saves and ordinary updates.

Backend switching preserves wallet identity while profile availability can differ;
do not reroute pending updates. Wallet replacement must load the replacement
identity's own public profile and keep outgoing work separate. Verify cancellation,
picker/save failures, stale/out-of-order profiles, extension-field preservation,
legacy migration, actual interruption and generated/custom image fallback using
controlled peers. No backend change is demonstrated necessary; live Archive
readiness and actual publication/cross-client display remain unverified.

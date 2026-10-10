# Native author edit and delete contract

Read-only primary-source inspection, 2026-10-10, for #14. A/B are official
`SaitoTech/saito` pins `9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`; H is archived
`SaitoTech/saito-lite-rust@7200a9886f3dd02f66e8d6c146960e2a5402929f`.
Public pinned source was inspected, including already fetched quote references.
Neither current deployment SHA nor actual write interoperability was established.
No node access, private wallet/configuration/database reads, builds or writes.

## Signed messages and recipients

A/B use `module:"RedSquare"`, request `"edit tweet"`, data
`{tweet_id: original.id, text: replacement}`; deletion uses request
`"delete tweet"`, data `{tweet_id: original.id}`. Both constructors preserve
provided fields, make an unsigned wallet transaction, and append non-self
recipient keys; composer/menu signs and propagates it.
[A constructors:1268–1327](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1268),
[B constructors:1064–1123](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1064).

Current edit recipients are the original author and distinct original `tx.to`
keys. Delete recipients are distinct original `tx.to` keys. With author-only
actions and wallet self output, both reduce to self followed by original
non-self recipients. Native should preserve this ordering and deduplicate.
[A composition:594–612,688–703](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L594),
[B delete:219–239](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L219).
H uses the same requests/data and adds supplied keys before signing.
[H constructors:1725–1772](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1725).

## Ownership, edits and conflicts

Current menu exposes and invokes actions only for the active author's posts.
Receiver edit authorization compares original `from[0].publicKey` against edit
`from[0].publicKey`, for both memory and archive paths.
[A menu:76–95,207–221](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L76),
[A edit receiver:1871–1927](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1871),
[B edit receiver:1642–1694](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1642).

An edit applies only when its signed timestamp is strictly greater than the
original's optional `edit_ts`. Equal/older edits leave it unchanged. A/B set
`update_tx=edit.serialize_to_web()`, `edit_ts` and `updated_at`, then rewrite the
original Archive row. They do **not** separately archive the edit command.
Original signed transaction/message bytes and ID remain intact; changed
`opt` carries the signed edit envelope. H uses the same author check and
strictly-newer rule, with its older timestamp metadata update path.
[H edit:1828–1870](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1828).

Current rendering reads nested `update_tx` text while retaining original images,
references and identity. H rendering does likewise. Neither rendering helper
independently validates nested author/target/signature. Native must do so:
verify original and nested envelope, require RedSquare/edit tweet, matching
target/author, bounded string text and a coherent signed timestamp. Derive edit
ordering from valid signed actions, not untrusted optional timestamps alone.
[A render:468–485](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L468),
[B render:466–483](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.js#L466),
[H render:130–136](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L130).

No enforced edit-age window appears in the inspected menu/receiver, despite
composer helper text. Do not invent a time limit. Text-only editing does not
replace attached media. Current receiver uses timestamp ordering rather than
compare-and-swap against a prior edit ID.
[B menu:207–216](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L207),
[B helper:66–68](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L66).

## Deletion and truthful reconciliation

A/B delete the original Archive row only after author equality. They decrement
available parent reply/original quote counters, and server runtimes save the
delete command as another `RedSquare` row, identified by command signature.
Memory/DOM removal happens before the archive author check, so native must not
copy that ordering for received actions. H similarly removes memory first,
checks author before archive deletion and saves the server command.
[A delete:1932–2037](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1932),
[B delete:1699–1804](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1699),
[H delete:1879–1957](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1879).

Delete-command availability alone proves no removal: even an unrelated signer
can reach the command-save path. Absence alone proves no deletion either:
moderation/pruning/unavailability can cause it. Native should reconcile both an
exact verified own command (`field1=RedSquare,sig=candidate.id`) and the original
target lookup, with original author known from verified content. If a successful
lookup still serves the original, remain pending; on lookup failure retain an
uncertain status. Do not promise historical erasure.

## Recommended native behavior, inferred

Persist original signed candidates before sending, bound to wallet/backend/target.
Recheck active identity and backend before submit; original-candidate retries
avoid accidental replacement edits or duplicate delete side effects. Peer `{}`
callbacks only acknowledge handler completion, not edit application or deletion.
[B handlers:843–853](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L843).

For edit reconciliation, reload the original and validate its nested `update_tx`.
Matching candidate means observed; a newer valid edit means conflict/superseded,
shown explicitly without silently replacing user text. Preserve the newest verified
cached edit across stale responses. Save failures must preserve prior content/work.

For deletion, use durable selected-backend deletion records and remove the target
from normal feed, Following, profile, liked, thread and cached views. Distinguish
pending local removal from observed server removal. Filter authorized delete
commands separately from post rendering; avoid resurrection from stale cache.
Quoted original previews need an unavailable placeholder without deleting the
quote author's commentary. Existing web deletion removes visible copies and
leaves a deleted active thread for the timeline.
[B manager:1512–1521](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/manager.js#L1512).

These source contracts require no demonstrated backend schema/protocol change.
Controlled receiver, signature, recovery/conflict and cross-client rendering
tests remain necessary; none were executed by this research.

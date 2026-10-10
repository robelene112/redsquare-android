# Native likes and ordinary reposts

Ticket #12, review base `58aed22`; final production source `76197a4`.
Accepted seams are specification #1 boundaries 1–4: observable Android flows
against controlled compatible peers, independent protocol vectors, and recovery.
See [the source contract](NATIVE_SOCIAL_INTERACTIONS_CONTRACT.md).

## Delivered behavior

Likes and signature-only ordinary reposts share selected-wallet/backend state
across feed, thread and profile. The original signed candidate is saved and
reopened before any transmission. Retry first checks exact verified availability;
accepted-but-disconnected operations retain their identity rather than generating
another action. Failed saving prevents transmission. Wallet replacement and
backend selection isolate pending actions; returning permits original recovery.
A separate host-controlled process kill verifies cold recovery with no resend.

Counts are unsigned backend metadata, never fabricated local increments. Fresh
responses update all views; old cached rows cannot overwrite newer observations.
An archived repost can remain available without increasing its served count.
Count-refresh failure preserves verified action success and offers a view refresh,
rather than an impossible disabled action retry.

## Observable red/green evidence

| Behavior | Actual failing run | Passing run |
| --- | --- | --- |
| Like controls and served count across three views | 38016710571: missing four-like control | 38017288554: 1/1, 6.12s |
| Ordinary repost with unchanged served count | 38017707757: missing two-repost control | 38018201097: 2/2, 7.646s |
| Fresh profile count replaces stale overlay | 38019559322: five remains after refresh to nine | 38020050365: 7/7, 18.809s, inspected nine-like image |
| Verified action with failed count refresh | 38020139458: missing warning, impossible Retry | 38022793183: 8/8, 18.469s; inspected warning |

Additional regressions passed without fabricated reds: 38018695180 (4/4,
12.883s) covers both accepted-disconnect retries; 38019199079 (6/6, 17.241s)
adds failed saving and backend isolation. 38020050365 also covers wallet
replacement/restoration and actual cold-process reconciliation. Safe logs and
inspected images are retained under `docs/evidence/ticket11/`.

Independent pinned WASM/Elliptic vectors pass two core cases. Actual Android
like/repost frames from 38018695180 independently pass deserialization, exact
request/target/recipient checks, and transaction/routing-hop signature checks.
Public frames and verification JSON are retained in `ticket11/four-green`.
These establish cross-runtime protocol compatibility, not live server receipt
or web visual acceptance. No production transaction or private material uploaded.

## Full-suite fixture failures and corrections

Production source is unchanged after `76197a4`. Three full runs exposed existing
fixture assumptions after the taller social rows were introduced:

| Run | Actual failure | Reviewed fixture correction |
| --- | --- | --- |
| 38020831495 | Android10 browse 6/8: two offscreen reply waits | 3824286 waits for refresh and scrolls to unchanged reply assertions |
| 38021449614 | Browse 8/8; Following 4/8: offscreen root waits | 49d34a0 scrolls to each post/author |
| 38022046842 | Browse 8/8, Following 8/8, posting 15/15, queue 6/6; social preparation misses inherited cached root before kill | 13107f2 clears only public fixture caches during preparation; cold recovery retains pending state |

Review additionally strengthened excluded-post assertions at `afaf68c`: search
the complete list and require the specific missing-node result, rejecting other
scroll/layout failures. Focused Following passes Android10 38022220807 (8/8,
21.698s) and Android17 38022557015 (8/8, 130.472s). Both logs and inspected
history images are retained. All production and fixture review axes report zero
actionable findings; see [the review](../evidence/ticket11/REVIEW.md).

## Final acceptance and limits

Full `38022793183` at `13107f2` passes core21, assembly/lint, browse8,
Following8, posting15, queue6, social8 and all five actual process recovery
stages on Android10. Social cases take18.469s; cold reconciliation2.885s.
Feed/backend then fails1/8: the cache-fault test demands a newly inserted post
be visible while the list correctly retains the older post's position. The
inspected image shows the cache warning and preserved older post, with the new
card above the viewport. The fixture now waits for refresh completion and scrolls
to the new post before its unchanged content/warning assertions. The distinct
position-preservation test is unchanged. Test assembly50sPASS; production source
unchanged. Safe failure logs and inspected images are retained in
`ticket11/full-fourth-failure`. The passing count-warning bitmap is partly
clipped and does not establish layout acceptance. Fresh full acceptance pending.

No new review APK, physical Pixel performance, force-stop delivery or live
production-write acceptance is claimed.

# Native likes and ordinary reposts

Ticket #12, review base `58aed22`. Accepted seams: observable Android flows
against controlled compatible peers and independent protocol vectors, including
failure, retry and identity/backend recovery (specification #1 boundaries 1–4).
The primary-source contract is in NATIVE_SOCIAL_INTERACTIONS_CONTRACT.md.

First flow: like a signed reference post, verify compatible request/target and
recipient order, observe only backend-served counts, and retain coherent action
state across feed, thread and profile. The fixture serves four likes initially
and five only after receiving the signed operation. It requires one transaction.

Test candidate `789ff6d`; local debug/test assembly passes58s. Focused Android10
run `38016710571` fails24.504s waiting for served "4 likes" at line65, after
the signed root appears. Its inspected screenshot shows no like/count controls.
This is missing behavior, not a build or fixture failure. Public evidence is
retained in ticket11/first-red.

A shared selected-wallet/backend state now persists original signed likes before
transmission, validates exact availability on retry, and shares observed action/
count state across feed/thread/profile. Counts remain unsigned backend metadata,
never local increments. Unavailable/failed state retains the original candidate
and offers Retry. Local debug/test assembly passes1m41s; runtime green is next. No production transactions, private wallet uploads or new APK.


Focused `38017288554` at `94a7afc` passes1/1 in6.12s: one compatible signed like,
exact availability and served4-to-5 count coherent across feed/thread/profile.
Public logs and inspected Profile image are retained in ticket11/first-green.
The ordinary-repost tracer requires signature-only data, original recipients,
verified availability and an unchanged served2 count. This models archived
availability without implying a timestamp-sensitive receiver counter increased.
Local test assembly51s passes; focused ordinary-repost red is next.


Actual ordinary-repost red `38017707757` at `dc2914b` passes the like case,
then fails at missing served "2 reposts" (line123); total24.926s,2 cases/1 failure.
A verified action-kind decoder now distinguishes like tweet/RedSquareLike from
retweet/RedSquareRetweet. Original candidate persistence, exact availability,
selected identity/backend checks and shared display state cover both actions.
Ordinary repost data contains only the original signature. No local count
increments. Local core/debug/test assembly passes1m59s; two-case green is next.


Focused `38018201097` at `183b264` passes2/2 in7.646s, including ordinary repost
availability with unchanged served count2. Inspected Conversation image and
public logs are retained in ticket11/two-green. Pure pinned WASM/Elliptic
fixtures generated through the network-disabled/read-only/empty-environment
runner prove exact native like/repost signed bytes; both lower-boundary cases
pass locally25s. They are regression proof, not fabricated functional reds.

Accepted-then-disconnected recovery regressions now reopen the real activity and
require Retry to query the original action, retain the original signature and
transmit exactly once. Both kinds are exercised. Public frame captures contain
only controlled zero-payment operations for independent WASM/Elliptic checking.
Local test assembly53s passes; focused four-case regression is next.

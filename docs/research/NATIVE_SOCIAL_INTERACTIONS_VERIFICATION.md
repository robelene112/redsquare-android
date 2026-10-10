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


Focused `38018695180` at `f2fce96` passes4/4 in12.883s. Both accepted-disconnect
cases reopen and reconcile the original action without a second transmission.
Their actual public Android frames independently pass pinned WASM deserialization,
exact request/target/recipient checks and Elliptic verification of the transaction
and routing-hop signatures. This is cross-runtime protocol evidence, not live
server receipt or web visual acceptance. Public proofs are retained in
 ticket11/four-green. Failed saving/no-transmission/repaired Retry and pending
original-backend isolation regressions compile55s; focused six-case run is next.

Focused `38019199079` at `26ee962` passes all six cases in 17.241s, including
failed saving and original-backend isolation. Interim review found stale served
counter overlays, an impossible retry instruction after verified success, and
the missing social suite in full acceptance. The next regression refreshes a
profile after the backend changes its served count from five to nine. Test
assembly passes 53s; production is unchanged while reproducing the finding.

Actual `38019559322` at `323c17f` fails 1 of 6 cases in 34.04s at the
required nine-like count after Profile Refresh (line84). The inspected screenshot
still displays five likes. Public log/image retained in ticket11/refresh-red.
Fresh server responses now update the shared observation in all three views;
cache reopening does not supersede it. Conversation refresh prefers fresh rows
when merging by identity. Local debug/test assembly passes 1m32s. Actual process
recovery and wallet replacement regressions are also included in the next run.

Focused Android10 `38020050365` at `3f95442` passes all seven cases in18.809s,
plus cold reconciliation after actual process death with no retransmission.
Wallet replacement/restoration and original action recovery pass. Inspected
Profile image shows nine likes after refresh; cold recovery image shows Liked.
Public logs/images retained in ticket11/refresh-green. The subsequent reviewed
PID guard correction avoids rejecting a new WorkManager process; final full
acceptance must exercise that corrected fixture. Count-warning red is pending
in Android17 `38020139458` at `529a768`.

Actual Android17 `38020139458` at `529a768` fails 1 of8 cases in90.235s
waiting for the count-refresh warning after Liked (line180). The inspected
bitmap shows a disabled Liked and impossible action Retry instruction. Other
seven cases and actual process recovery pass. Public reproduction retained in
ticket11/count-warning-red. The fix preserves verified availability and reports
count-refresh failure separately; any fresh served observation clears that
warning. Missing target data also produces a count warning. Local core and
debug/test assembly passes1m38s. Final full Android10/17 and source reviews next.

First full `38020831495` at `76197a4` passes core/release/debug/test/lint,
then stops at Android10 browse6/8 in51.587s: two Independent Saito reply waits
time out at lines468/422. Inspected conversation images show the original and
new social rows filling the viewport. The old lazy-list fixture did not scroll
to materialize the reply rows. Three corresponding live/cached reply checks now
wait for refresh completion and scroll to the same reply before their unchanged
content/history assertions. Local test assembly54sPASS. Production unchanged.
Both final production-source review axes have zero actionable findings. Fixture
fix review and fresh full acceptance next; no runtime fix success claimed yet.

Second full `38021449614` at `3824286` passes source checks and browse8/8
in15.909s, then Following4/8 in71.24s: four old waits for the Native reference
post timeout behind the first feed card. Inspected bitmap shows that first
card filling the viewport. Following's shared post-wait helper now waits for
refresh completion and scrolls to known post content. Multi-author and two-post
checks explicitly scroll to each target; filtering/persistence/error assertions
remain unchanged. Local test assembly50sPASS. Production unchanged; fixture
review and fresh full acceptance next. No success for this fixture claimed yet.

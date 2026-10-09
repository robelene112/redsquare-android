# Ticket #8 interim review

Read-only parallel review at aef6742 against base44cae5d. Both axes follow the
project-local code-review skill. This is an intermediate report, not acceptance.
Final full device verification and fixes remain outstanding.

## Standards

No demonstrated hard breach. Two heuristic Duplicated Code findings:

1. AndroidProfileCache and AndroidFeedCache repeat bounded envelope input,
   encoding, AtomicFile commit, file/directory sync and byte verification.
   Share their storage boundary while keeping separate signed decoding and
   retention policies.
2. The thread/profile-like reference generators repeat pure transaction
   construction, manual preimage assembly, WASM/Elliptic verification and
   envelope serialization. Share reference-only signing while preserving
   independent verification and message-specific fixtures.

An additional test-evidence concern from the review: fresh browse tests leave
their conversation/profile caches intact. Clear fixture-owned public caches at
each case's setup so retained text cannot satisfy a intended fresh-read wait.
Do not erase cache during the within-case restart being tested.

## Specification

1. P1: signed arbitrary thread_id is accepted by decoding, while the new cache
   constructor requires128 lower-case hex characters outside error handling.
   Malformed signed content can crash navigation. Ticket#8 failure behavior
   needs relationship validation or usable handling, plus an app regression.
2. P2: the latest20 author records are split into Posts/Replies with no older
   page control. Twenty newer roots can hide available older replies behind
   an empty view. Likes similarly stops at20. Preserve a cursor and allow
   older pages, including those with only the other tab's entries.
3. P2: conversation reply identities and liked-post identities lack profile
   actions. Ticket#8 identity navigation needs those entry points and an
   observable regression.

No scope creep. Pending persistence/full checks are acceptance work and are
separate from these source defects. Final review must check all actionable
findings after fixes, with exact source and runtime evidence.

Standards:2 heuristic findings, strongest duplicated durability logic.
Spec:3 findings, strongest malformed-thread navigation crash.

## Fix progress

Profile persistence source aef6742 passes focused4-case runs37932930501/
37932934354 on Android10/17. Images/logs inspected/retained in profile-cache-green.
The storage/reference duplication has been extracted at review stage; every
existing signed fixture is semantically identical under the independently
verified reference runner. Fresh browse tests now clear their fixture-owned
public caches once at setup, preserving the cache during the tested restart.
Actual runtime for these refactors and specification fixes remains pending.

The four established flows pass at refactored c631f4d in37934353219; its fifth
case provides the actual malformed-relationship red. Minimal validation a70e370
passes all five cases in Android10 run37935302107. Public logs/images retained
in malformed-thread-red/green. Android17/final full and repeated review remain.
Older profile pages and remaining identity entry points still need fixes.

## Repeated review at dfc5000

### Standards

No demonstrated hard breach. Earlier duplicated durability/reference signing
and contaminated fresh-read setup are resolved. One low-severity heuristic
Duplicated Code finding remains: Feed and Likes readers repeat bounded archive
row traversal, cursor extraction, ignored accounting and ID deduplication.
Share traversal while retaining typed signed decoding and membership rules.

### Specification

The repeated Spec reviewer found zero remaining source issues: signed
relationships are validated, both profile history paths page, and reply/liked
authors open profiles with Back restoring the original tab. Full acceptance
was still pending. The separate Standards reviewer subsequently identified an
additional specification edge: refreshing after a cache-write failure replaces
already fetched profile content with an empty disk snapshot, contradicting
the error message. Actual eight-case run 37941624799 confirms this failure,
with seven previous cases passing. Source 4e8bd02 retains the verified in-memory
snapshot; focused green and final full verification remain pending.

Standards: 0 hard breaches and 1 low-severity duplication judgement remaining.
Spec: 1 subsequently demonstrated recovery issue, fix awaiting device evidence.

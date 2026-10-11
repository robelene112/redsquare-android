# Post reporting review

Ticket [#22](https://github.com/robelene112/redsquare-android/issues/22), parent
[#1](https://github.com/robelene112/redsquare-android/issues/1), whole-ticket
review base `9ecb97bee6bc451ce5e95dd1ca787234080bc967`.

## Standards

Whole source review through `ac808f9`: zero residual documented-standard
violations or actionable Fowler heuristic findings. Reports belong to one
wallet/backend/post, preserve exact signed intent, and adopt validated newer
receipts without another transmission. Receipt commits share the existing
identity lock through the peer's guarded physical enqueue boundary. Public
record updates validate the actual durable head and preserve acknowledgement.

Earlier findings are resolved: shared load ownership avoids a row cancellation
race; permission/obstruction cleanup preserves the primary failure and attempts
independent restoration; stale acknowledgement cannot be overwritten by an
older pending view. Actual restored-receipt adoption passes the native tracer;
the original coroutine scheduling interleaving is not claimed reproduced.

Bounded fixture/evidence reviews through `92d80f3`: zero residual findings.
One duplicated conversation assertion was removed. Conversation and nested
reply fixtures now scroll the actual lazy list to exact displayed replies,
with original deadlines and all downstream identity/thread/recipient/rejection
predicates preserved. No product source changed after `ac808f9`.

## Specification

Whole source review through `ac808f9` and bounded fixture/evidence reviews
through `92d80f3`: zero actionable implementation or scope findings.
Confirmation names the selected reporting destination. Compatible signed
type12 requests use self outputs and never type4 broadcast. Strict correlated
empty-object acknowledgement means the handler replied, including absent
targets; it does not establish moderator review, removal or chain inclusion.

Observed wire14/nonblank application errors remain delivery-uncertain and
distinct from missing/malformed replies. Saved details preserve that distinction
through reopening. Explicit retry uses the original signed candidate and
destination. Load/save failures, identity/backend isolation and actual process
recovery have qualified native evidence. No automatic resend or failover is
introduced; reporting does not hide the post.

## Verification status

Focused Android10 run38179643110 at`5b36d1b` passes11ordinary cases105.625s,
including actual SAF/device-authenticated wallet roundtrip, save faults,
explicit errors, both destinations and restored-receipt adoption without
another report. Independent cold recovery passes1case5.056s after actual
process4222→4306, same wallet/backend/original transaction and retained635-byte
public record. Root individually inspected4receipt and3process original PNGs,
then separately parsed7XMLs. Durable restored-receipt-green has23verified
hashes and recount12completed cases/2records/1actual death stage.

Independent pinned WASM/Elliptic and extracted A/B receiver evidence is retained
in first-green and NATIVE_REPORT_VERIFICATION.md. It establishes compatible
native bytes and both pinned handlers, not deployed moderation behavior.

The first full pair stopped at old browse pre-scroll assumptions. The next pair
passed corrected browse and stopped at the nested-reply pre-scroll assumption.
Original failed captures and raw counts are retained with their limits. The
reviewed reply correction was exercised by full10run38180155391 and
full17run38180252201 at the same`709b58d68da1b773050b33e4e459343500fa9c96`,
both public_feed_read=true. Android10 passes full acceptance below. Android17 passes corrected
browse8cases69.827s/posting18cases104.96s, then stops before the rejection
composer while reopening a published one-byte oversized image fixture:
preparation5cases/4PASS/1FAIL13.502s, FileNotFoundException at127. Reporting and
later full17 scopes are unreached, with no in-scope PNG/XML for setup failure.
Durable full17-image-fixture-failure retains3hashes. The current fixture
correction publishes one complete pending write under a unique name, checks
the recognizable oversized source's exact positive size and begins owned
cleanup before allocation. Provider reclassification remains a hypothesis.
Both image fixture/docfix reviews clear through92d80f3. Focused images17run
38181457132 at367cba4 PASSES20cases/5records/3actual interrupted stages;
preparation/loading5PASS17.507s includes the exact positive-size guard and
unchanged prior-draft/rejection/no-send predicates. Durable image-fixture-green
retains13hashes and all countable logs. No successful rejection PNG/XML exists;
no bitmap claim or exact provider-cause inference. Full17retry38181575469 at
92d80f3 remains active with public_feed_read=true.

Full10run38180155391 PASSES191reported cases/190PASS+existingAPI30+assumption,
48records/16actual death stages; core55/0/0, release/lint, signed5→8/coldwallet/
cachecontinuity and both public reads. Reporting11PASS92.703s; actualdeath
23785→23879/cold1PASS4.34s/635-byte public record preserves original identity/
backend/candidate and exact explicit retry without automatic send or type4.
Root individually inspected41ordinary+3process original PNGs/separate44XMLs,
then both public originals. Full10README qualifies two byte-identical ACK views
rendered as strips and the public original observations. Durable final-full/
android10 retains202hashes and all raw countable logs, exact original/durable
recount191/48/16+assumption. Production/core/reference/build/harness/workflow
unchanged709→92, only existing image fixture/setup and docs/evidence change.
WORKFLOW permits retaining full10 while completing outstanding17.
Both final10 bounded evidence axes are0: each independently verified202hashes,
raw counts, qualified originals, continuity and empty source equivalence.
Final17 acceptance and its bounded evidence review remain pending.
Ticket22 stays open. No new phone APK, physical Pixel, live report, moderator
decision, arbitrary power-loss or permanent-history claim.

# Search ticket review

Ticket #20, parent #1. Review base
`3be555be85586b68c0d05c2c8e69d3976bbffe89`. Reviews inspect actual committed
diffs, with separate read-only specification and standards agents.
This is interim evidence; people, durable cache and final verification remain
unfinished. No ticket closure or physical-device acceptance.

## Standards

At `eceb9d6`, one source race: revision validation could republish an older
captured result list after archive publication. Fixed at `cc590f0` by validating
both captured-list identity and the current revision stamp before assignment.
Source review clears the fix; the unchanged reply tracer passes on that commit.
The race itself was not reproduced in runtime.

The paging tracer waited for an uncomposed older lazy row before scrolling.
Corrected at `4498614`: wait for the observed completed read and enabled action,
scroll to the exact older text, and retain its display assertion.

At `42ebbb8`, the 1,000-post retention bound could discard newly fetched older
matches while continuing to advance cursors. Fixed at `f1ebc3e`: prioritize
matching posts, explain retained-only coverage, clear paging/retry targets and
stop subsequent stream reads at capacity. No 1,000-row runtime claim yet.

Source also supports explicitly scrolling to a prepended quote before asserting
visibility, because a keyed list can retain the prior first-item anchor.
Fixed at `f1ebc3e` without reducing the exact display/cursor/count gates.

Re-review `42ebbb8…f1ebc3e`: zero residual documented breaches or actionable
Fowler smells. Focused paging GREEN38135039545 at54e806f now passes2tests/40.598s;
original artifacts and inspection limits are recorded in paging-green.
No1,000-row runtime or final ticket acceptance claim.

## Specification

At `eceb9d6`, zero findings in the first reply-search/navigation slice. At
`cc590f0`, one tracer-sensitivity gap: Retry and paging could discard previously
verified results without failing the existing test. Fixed at `4498614` by
positively scrolling to and displaying the newest result after both operations.

At `42ebbb8`, explicitly reach the prepended quote before its exact visibility
assertion. Fixed at `f1ebc3e`; reviewed against the parent's requirement to
verify displayed state, with AndroidX keyed-list source support.

Re-review `42ebbb8…f1ebc3e`: zero residual findings in the bounded implemented
slice. Independent stream cursors/pending targets, successful-read retention,
strict progression and explicit retained-history limit are coherent. People,
durable cache, broader failures/recovery and final native/public acceptance
remain known unfinished work.

## Offline cache tracer review

Both axes at7c897e1 identified OFFICIAL's default ordinary Archive callback
rejecting search without field4. Test-only fix supplies explicit healthy empty
fixed-criteria search responses. The primary app assertions are unchanged.
Other recovery/selection/identity/nosend gates are coherent in source. Native
cache RED and production implementation remain pending.

Bounded re-review7c897e1…0190919:0 residual findings per axis. Corrected fixture
compilation PASS1m26s; native cache RED38135929831 pending.

## Cache implementation review

At32770eb, Spec0 implemented-slice findings. Standards1 hard source race:
archive publication could overwrite a newer author revision during IO retention.
Fix validates actual list identity and revision stamp before publication/cursor
advancement, retrying the merge with the already-received page, without another
network read. No runtime reproduction claim. Standards1 judgment smell: search
duplicates the public store encoded-size budget; consolidate in review cleanup
after first cache GREEN. Combined initialcache compilation PASS2m42s; guard
compilation/re-review and native GREEN pending.

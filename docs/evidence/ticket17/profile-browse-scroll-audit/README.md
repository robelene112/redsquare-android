# Lazy profile-history fixture audit

Source aea25ca full Android10 run38110320329: browse8/20.384s, seven pass,
one existing offline-cache flow fails at46. `performScrollTo` cannot find its
target node after the offline error adds header content. This is different from
the earlier located-but-not-displayed assertion. A LazyColumn can omit offscreen
rows from the composed tree; missing target does not by itself prove lost data.
The source retains the in-memory history through this failed refresh. Actual
successful scroll/read remains pending for this flow.

The cold cached-profile flow now passes Posts/Replies/Likes/history checks.
Its separately gated original PNG/XML captures visibly contain the exact root
post, independent liked reply and qualified history warning. Root individually
viewed all four PNGs and parsed all four XML files. Offline first-failure capture
shows only the tall header/status/warning, with no post text in the visible
hierarchy. PNG/XML are separate observations. All exact raw logs and original
PNG/XML remain unchanged here. Later full groups are not reached.

The same fixture now searches and scrolls the LazyColumn using the existing
performScrollToNode operation, then retains the exact positive display assertion.
Posts, warnings and tabs in the two affected flows use this helper; no longer
require an already-composed target. No production change. Local compilation,
committed review and fresh Android10 runtime are pending.

Android17 full38110321794 onaea25ca continues independently. Its production,
core, build support and harness are identical to this subsequent test-only fix.
If it passes, verify the changed browse class separately on Android17 instead
of repeating unaffected full checks. Any additional functional failure requires
its own investigation and may require fresh broader checks.

Lazy-list correction local test assembly PASS1m8s, exit0, rawlog retained.
Committed bounded review and fresh verification remain pending.

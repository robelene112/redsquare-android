# Dismissed reply replay RED

Actual Android10 run38825316132 at1aa84767366522181679e8e4c9551b83ef7181e9
completed FAILURE: two cases, one PASS/one FAIL8.702s. The preserved first reply
journey passes. The new case creates its alert, physically swipes, observes both
shade-text disappearance and zero new active Android alerts, then redelivers the
same reference. The continuous silence assertion observes one new active alert
where zero is required. The remainder of that silence interval, success capture
and later no-write tail in this failing case are unexecuted. Independent fixture
cleanup runs; the separate original case subsequently passes its own complete tail.

Root viewed all three original PNGs by actual bytes and parsed all three XMLs.
The original-case reply-alert.png shows Red Square/Replied to your post. The
new-case initial dismissed-reply.png is mostly black with a clock and does not
visually establish its loaded alert; its hierarchy contains the reply text.
The failure bitmap shows system notices without a Red Square row, despite the
earlier manager count1. Screenshot and hierarchy sampling are not atomic with
the manager observation. Do not describe these two new-case bitmaps as proof of
visible reappearance, settled transition timing or an exact Android scheduling
cause. Raw assertions establish positive dismissal and later manager reappearance;
source still invokes notify on every accepted replay without a durable ledger.
An earlier retained-alert replay also occurs before dismissal, so no sole causal
attribution to the final call or settled earlier OS update is claimed.

Core114/debug/test assembly pass; release/lint and full scopes are unexecuted.
Instrumentation shell0 is not a JUnit pass. Scope.txt is not reached after failure.
This is local injected transport, not FCM, closed-app public delivery, process
durability, tap navigation, both-SDK acceptance or whole32 completion. Encrypted
installation state/signing material and APKs are not retained. Public24 unchanged.

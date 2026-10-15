# Dismissed reply replay RED

Actual Android10 run38825316132 at1aa84767366522181679e8e4c9551b83ef7181e9
completed FAILURE: two cases, one PASS/one FAIL8.702s. The preserved first reply
journey passes. The new case creates its alert, physically swipes, observes both
shade-text disappearance and zero new active Android alerts, then redelivers the
same reference. The continuous silence assertion observes one new active alert
where zero is required. The remainder of that silence interval, success capture
and later no-write tail in this failing case are unexecuted. Independent fixture
cleanup runs; the separate original case subsequently passes its own complete tail.

Root forwarded all three originals by actual bytes and parsed all three XMLs.
Rendered previews conflicted between root and independent reviewers. A separate
network-disabled, read-only Chromium canvas decode resolves the file content:
both initial images, dismissed-reply.png and reply-alert.png, have the exact same
RGBA notification-card region (x0..319,y180..316) as the earlier first-GREEN
reply-alert.png, which shows Red Square/Replied to your post. All three regions
hash1644fa3bb8dac18cc9d85bab0475ade5af15bac784093141f5fc49667d9019e3.
They are not black in that region. Original file hashes and pixel measurements
are in png-pixel-observations.jsonl; inspect-png-pixels.cjs is the read-only
reproducer, run with named evidence/tools mounts in Playwright1.63.0, networknone,
CPU1/memory768MiB. A separate stdlib PNG decode agrees on region white counts.
No image was edited or regenerated. The cause of conflicting rendered previews
is unproved; do not describe a black app frame from those previews.

The failure original shows system notices without a Red Square row, despite the
earlier manager count1; its card-region hash differs. Screenshot and hierarchy
sampling are not atomic with the manager observation. Do not describe that
bitmap as proof of visible reappearance, settled timing or exact Android
scheduling cause. Raw assertions establish positive dismissal and later manager
reappearance; source invokes notify on every accepted replay without a durable
ledger. An earlier retained-alert replay also occurs before dismissal, so no
sole causal attribution to the final call or settled earlier OS update is claimed.

Core114/debug/test assembly pass; release/lint and full scopes are unexecuted.
Instrumentation shell0 is not a JUnit pass. Scope.txt is not reached after failure.
This is local injected transport, not FCM, closed-app public delivery, process
durability, tap navigation, both-SDK acceptance or whole32 completion. Encrypted
installation state/signing material and APKs are not retained. Public24 unchanged.

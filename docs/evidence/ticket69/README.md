# Accessible fullscreen images69

Review base: b7ec3c979e4187b7ab3a690a64962c9a0d7fa222. This issue uses the
existing native UI/controlled signed media and actual TalkBack seams. No new
provider or live publishing is involved. Complete acceptance and signed delivery
remain pending.

Native RED: run40183253293 at90b7fc6802b420baa01c55bc0ad6a33d51ac0522 opens
the original viewer with a physical keyboard and fails on the missing Zoom out
control. Original log and native failure captures are in `red-keyboard-40183253293`.
This is an expected failing specification, not accepted runtime proof.

Focused2e4 runs40184158249/40184160059 reached the new keyboard focus loop but
failed its incorrect global uniqueness assertion: the inactive parent Compose
root retains its thumbnail focus. The revised check requires exactly one focused
node inside the Dialog and proves the actual parent window has no keyboard focus.
Native TalkBack40184161840 completed contextual control speech and passed48dp
bounds, then focused Compose's text-only descriptive child below the adjustable
SeekBar. The new non-clickable observation now selects the native focusable
control, retaining the requirement for completed image position and zoom speech.
All three failures are retained with their original outcomes.

Source review found missing gallery replacement motion and duplicated movement
definitions. Both are corrected; actual native normal/zero motion recordings,
real pinch and menu pan are additive at the existing native seam. Final acceptance
remains pending.

Both09a keyboard runs completed every application assertion, including actual
modal keyboard confinement and return to the origin, then rejected a diagnostic
capture predicate: it looked for image-position text instead of the attachment's
content description. The capture now verifies that native description; no
application assertion or deadline changes. The three new viewer classes retain
independent results even if one fails; any failure still rejects the whole run.

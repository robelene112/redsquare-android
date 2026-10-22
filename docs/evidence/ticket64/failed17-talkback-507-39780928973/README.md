# Rejected reader startup, Android17

Run39780928973 at exact507d35d8192c2d8cd536010be79c06d2a0295e3a failed18.72s/180
in the original status class before profile speech. The added in-scope diagnostic
records focusedText=null with Red Square active/focused. No Speaking fragment
exists in the service log. At the original readiness capture the service is
bound but touchExplorationEnabled and serviceHandlesDoubleTap are false; both
are true in the later failure dump. The image revision matches accepted63.

Hypothesis: service binding precedes completion of its asynchronous reader
initialization, racing the application's initial window focus. The existing
15s service-readiness observation now also requires those actual input flags
before opening the app. No timeout is increased; the original5s Wallet-focus
condition and every speech/focus/status assertion remain. This is a hypothesis
under test, not proof of a reader or app defect. Corrected execution is pending.

Verified all34 SHA256SUMS entries; this later README is excluded.
Original members and bindings are retained. Public63 remains current.

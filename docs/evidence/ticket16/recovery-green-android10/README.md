# Intermediate native video recovery

Focused Android10 run38082750948 atbc35794 PASS3 cases16.916s.
It includes first tap/contact/installed-app Referer, rotation to undersized
fallback with no WebView or extra API request, a fresh portrait tap with
actual visible player bounds at least200dp, and the thread recovery flow.

The thread flow validates the archive's selected signed thread ID, then observes
HTTP503 load failure, deliberate Retry to provider error153, canonical external
ACTION_VIEW (stubbed at that boundary), Retry to ready, Close to the conversation,
and Back to the feed. No transaction is emitted.

The original provider-error PNG was individually inspected: its explanation,
Retry, Close and external button are visible. The small-window PNG/XML here
are raw captures from this run; its assertions pass. Individually inspected
equivalent small-window and portrait-bounds originals from the preceding
two-case GREEN38082402358 at6fd854c show the external fallback and a black
controlled-player surface with ready/close/external text. They do not show real
media. XML is captured later than each PNG, not guaranteed the same frame.

Controlled responses establish application integration and recovery, not live
YouTube media playback. Lifecycle/provider integration and full10/17 acceptance
remain outstanding. Raw logs/captures are preserved.

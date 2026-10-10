# Rotation failure before root playback host

Focused Android10 run38081746532 atff12bd6 completes two cases18.652s.
First tap passes. The rotation tracer opens the signed explicit-link video,
observes readiness and exactly one provider request, rotates to landscape,
then fails after10s waiting for the small-window fallback (line105).
The retained stages positively locate that operation. The individually inspected
original failure PNG shows the landscape app header/feed controls without the
video dialog. The XML is a later capture, not guaranteed the same frame.

This reproduces loss of selected playback across rotation. It does not
establish the cause of earlier incomplete size runs or actual YouTube playback.
Provider responses are controlled and the signed post uses public unsafe fixture
material. No production transaction is emitted. Raw captures/logs are preserved.

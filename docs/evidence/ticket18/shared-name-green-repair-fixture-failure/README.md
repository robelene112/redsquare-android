# Shared names pass; repaired-cache fixture fails before settlement

Android10 [run38122217674](https://github.com/robelene112/redsquare-android/actions/runs/38122217674),
source71bc703c8ee212d65dd168d855e8e8048ebf93d7: eight of nine pass in43.727s.
The complete shared-name case passes feed/profile/conversation/reply, target-key
hint and signed namecheck, unchanged scalar2 reader, reader handshakes and zero
broadcasts. Root individually viewed both retained shared PNGs and their XMLs:
exact scalar1 root body and NativeCase7@saito are visible. Conversation capture
also shows independent reply loading; name display does not gate it.

The cache-save case shows a verified label/warning under the obstruction,
removes the obstruction, remounts Profile and observes a fresh namecheck, then
fails at line164 expecting the warning absent. Root individually viewed original
failure PNG/XML, which retain the warning and correct owner. Observing a request
is not a completed proof/cache save, and the new shared directory retains its
previous label during retry. A bounded wait for warning disappearance is the
next fixture correction, preserving the later unavailable-namecheck recreation
and cached-name/no-warning assertions. This evidence alone does not prove the
warning would eventually clear or establish a rendering/runtime cause.
Later repaired-cache/recreation and actual process stage were not reached.
No live namespace, current browser DOM, chain or physical-device proof.

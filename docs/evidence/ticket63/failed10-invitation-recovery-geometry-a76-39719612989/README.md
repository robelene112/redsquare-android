# Android10 physical Preview readiness, rejected nominal-window assumption

[Run39719612989](https://github.com/robelene112/redsquare-android/actions/runs/39719612989)
executesa760034905163b69d3f49cfd20e57341bdbae5e8. Whole/native job FAILURE,
17.020s/180. The first100% Light cell stops in the original10second keyboard
readiness bound before its first physical Preview tap. No invalid-error, paste,
preview, Cancel, Join or later-cell acceptance follows from this outcome.

Root viewed actual failure PNG/XML and original pre-tap geometry. The focused
window reports IME bottom246 on the320x640 display. Native and Compose Preview
bounds agree at[92,338][304,386], a48px-high target above keyboard top394. The
accessibility IME window is[0,394][320,740]: its nominal bottom extends below the
display. Requiring that nominal bottom to equal640 incorrectly blocks readiness.
This is a directly observed fixture condition, distinct from the untraced earlier
physical-tap/feedback failures. It proves no production or animation defect.

The next fixture change intersects that actual window with the display rectangle
and records both nominal and visible bounds. Every original keyboard/48dp/physical
single-tap/error/class/protocol/restoration goal and deadline remains. No retry.
Fresh complete Android10 verification is required; final full scopes still use
the same final source on both SDKs.

All10 archive originals and terminal logs remain bound by21 checksums and original
archive/member hashes. README excluded. Raw:
/mnt/storage/redsquare-android-artifacts/group63-10-recovery-geometry-a76-39719612989.
No complete native, whole63 or signed delivery claim. Public62 unchanged.

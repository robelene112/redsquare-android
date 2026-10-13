# Wallet notification isolation journey, cleanup failure

Android10 run38326390887 at6be5695 reports31 cases:30PASS/one wallet cleanup
failure,123.866s, zeroOK/deaths/assumptions/core73/debug/test. Wallet journey
itself completes real SAF unsafe1->unsafe3->unsafe1, preview/zero-balance skip/
acknowledge/confirm, selected Owner, replacementMention1unread/noLike/Mark, returned
OwnerLike+Mention/NoUnread, and positive authenticated Owner1+unsafe3/no writes.
Failure at NotificationsPreviewTest.kt:1195 is the exact authorized JSON cleanup
guard. Minimal host imports retain fewer options than canonical unsafe exports;
review identified this mismatch before runtime. No prior-wallet restoration claim.

Correction1273647 provisions complete canonical unsafe1/3 export fields in exact
order, preserving strict cleanup guard; both correction review axes zero. Provider
build1m32PASS. Root parsed two XML and viewed both originals: Owner replacement
view shows actor2 Mention/1unread; returned Owner shows actor2 Mention+Like and
NoUnread/disabled Mark. Five hashes include original runner plus two PNG/XML.
These screenshots support journey assertions, not successful final cleanup or
whole-ticket acceptance. Corrected runtime remains pending.

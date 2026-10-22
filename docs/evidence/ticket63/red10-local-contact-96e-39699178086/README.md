# Native Android10 RED: explicit private-chat entry

Run39699178086 is terminal FAILURE at exact96e7821a53a88fe3dad306211cdc36778c2e4aaa.
The single new ChatEntryPreviewTest method fails at its15second New chat readiness
assertion, line57, after opening actual Chat in the first320dp/100%/Light cell.
Original class bound180seconds; actual method20.499seconds. Source build succeeds.
The actual screen still places Groups and a raw Public key/Open chat row above
known contacts, with no New chat action. Root viewed the original failure PNG.
This is the intended first vertical RED slice, not a complete63 or regression pass.
All later known-contact/key/IME/font/theme goals remain unreached in this run.

One original archive with six members is retained and bound by SHA256; original
instrumentation, platform/configuration and Activity lifecycle observations stay
with it. The extraction helper's printed PASS means retention only. Its initial
wrong artifact-folder assumption failed after the complete download; rerunning
retention against the actual android-native-proof directory succeeds without any
native retry, new execution or altered artifact. No test or application goal was
changed. Activity closure and independent restoration produce no extra failure;
this is not separate process-death acceptance.

Local final-source app/test compilation succeeds2m2/explicit0 after correcting a
Unit-returning text-replacement chain. Fixture/scopes have paired Spec0/documented
Standards0/heuristic0 review. Production is unchanged at this RED checkpoint.
Original raw artifacts:
`/mnt/storage/redsquare-android-artifacts/chatentry63-red10-96e-39699178086`.
Public62 remains unchanged. No physical/provider/store or completed63 acceptance.

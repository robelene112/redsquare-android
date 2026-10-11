# Signed username confirmation first native RED

Issue #19, source5c07260, Android10/API29 run38114940866.
Three cases: unavailable and pending/reopening/exact-retry PASS; confirmation
fails waiting for owned status at UsernamePreviewTest.kt:160. Total32.8s,
one failure. The current published registrar correctly rejects the synthetic
unsafe scalar2 proof. An explicit debug emulator/loopback authority was requested
by the fixture but not implemented at this source. No actual namespace ownership
or cached-name recovery assertion was reached. Original PNG viewed individually;
separate XML inspected and shows NativeCase7 with the unavailable message.
Raw files retained verbatim. No live registration, node change or secret included.

SHA256:

- username-confirmation-failure.png:711bfd2ba507d1a56ec50ebeeb3f91491f58be6d7c243b3bfaebc5823d58a672
- username-confirmation-failure.xml:f5158418717baf63837b9768bd98e404d0d53df2db4716950c5ad768c217223d
- username-ui.txt:97a29cbf88ed155788f2ba63abf8b7d726b1e0be61b252773f6bc23f99e4b849

# Pending username request first native RED

Issue #19, source00bbead, Android10/API29 run38114016651.
Two native cases: unavailable flow PASS; pending registration RED at missing
Register name, UsernamePreviewTest.kt:89. Total8.319s, one failure. The successful
empty namecheck makes availability visible, without reservation. Original PNG
viewed individually and separate XML inspected. The pending/recreation/exact-retry
assertions were not reached; no accepted persistence/retry claim follows.
Raw files copied verbatim. No live write, reset of wallet or node change.

SHA256:

- username-ui.txt:9cf6ed71bec9923cb1b3fc5306ea3658ab26e81641534b657d6d87c61e057118
- username-pending-failure.png:03058f5c0c3e6ddcedbb0d46127768d2bee9a6793bc5dc33f121b631b23ad54a
- username-pending-failure.xml:a96c4b5b90eec290c3e971c59a455df2acb2c5e29f6e402c685a770ace386796

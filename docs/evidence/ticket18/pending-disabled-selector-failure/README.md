# Pending request disabled-field fixture failure

Issue #19, source11348ee, Android10/API29 run38114449227.
Two native cases: unavailable PASS; pending fails at the disabled field selector,
UsernamePreviewTest.kt:102. One failure/8.549s. Earlier assertions observed exactly
one emitted registration, expected author/registrar/message and pending status.
Recreation, exact Retry and frame-report persistence were not reached.

Original PNG individually viewed and separate XML inspected show locked
NativeCase7, Retry registration and pending text. These are not atomic captures.
The reviewer identified the faulty SetTextAction selector before this runtime
finished. Fix5bc5de4 checks exact EditableText plus disabled state, preserving the
acceptance value gate. Both review axes report0 residual bounded findings.
Corrected runtime pending. Raw files are verbatim original evidence.

SHA256:

- username-ui.txt:3fbfc6a06307522cd60e76fb7e798cf16fab778c8187b6bbea52a9d377cce513
- username-pending-failure.png:e1784424dc51097aaa82d89ddfe908e44416fdd05983a8d7fab020ed692ac570
- username-pending-failure.xml:5ce386f4f56bb4e0144ea10e8e107dc0ea87ba4c2b224d26e317bf4fef4841f8

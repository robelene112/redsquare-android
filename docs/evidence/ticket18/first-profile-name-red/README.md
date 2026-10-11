# Confirmed registration recovery and profile-name native RED

Issue #19, sourcecb6ffd5, Android10/API29 run38115737996.
Four cases: earlier three PASS; new flow reaches actual directed registration,
independent signed proof, current owned-name display, then recreation with a new
empty Registry read and retained saved name/no Register or Retry/no resend.
It fails waiting for the full name on Your profile at UsernamePreviewTest.kt:256.
Total39.573s, one failure. Source/core/debug/test build54s. Original registration
and profile-failure PNGs individually viewed; separate XMLs inspected. Profile
shows Anon-jesTu2 plus the unchanged public key, establishing the intended missing
name-display behavior. Profile name/public-key final assertions and success
capture were not reached. The actual emitted synthetic-registrar frame was not
retained by this version; existing native wire assertions are not independent
WASM/cross-client proof. No live registration, namespace ownership or actual
process death is claimed. Raw originals retained verbatim; PNG/XML non-atomic.

SHA256:

- username-profile-failure.png:956e006c784c9eb3c9bc16314a135f29a6aed8bdfe9b642a618c788185de6aca
- username-profile-failure.xml:f91ec6b20729a451ad8f2c1902fa95155810542fbf5d3b0f6d17a23b3f0c4857
- username-registered.png:61e848ccb9cb6d1c394db130118de65d8147958cc78bbf823e9f4ce99e9405aa
- username-registered.xml:deef9f5f834d287dd19c5685f6d9354b3c04a6dd02e2478bc3a39fb9fb07d353
- username-ui.txt:cd2ac847fa485659b88692b71bf13be6ee6446d495f1cb450dcd11c50b641cf1

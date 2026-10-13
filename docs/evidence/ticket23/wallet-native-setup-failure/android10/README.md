# Wallet notification fixture setup failure

Android10 run38325244254 atdd23b9f reports31 tests:30PASS/one setup failure,
103.888s, zeroOK/deaths/assumptions; core73/debug/test pass. Wallet test fails
at NotificationsPreviewTest.kt:1101, documents.isDirectory||documents.mkdirs(),
before Activity launch or wallet import. No wallet failure capture exists because
setup fails before the capture scope. The other30 cases, including corrected
before>51 retry guard, pass individually. One hashed original runner log retained.

Target-app instrumentation cannot directly create files under the test-provider
package UID. Correction6be5695 uses the established host run-as provider setup,
positive-size runtime guards and exact two-file cleanup. No wallet-import product
failure or picker/navigation hypothesis is established. Earlier cleanup correction
bfd0906 preserves primary failures and permits only exact unsafe1/3 fixture JSONs
before restoring original opaque wallet; both review axes zero. Corrective native
run38326390887 is pending; no successful wallet isolation claim yet.

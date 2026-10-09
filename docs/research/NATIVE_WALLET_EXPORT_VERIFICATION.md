# Native authenticated wallet export verification

Ticket #6, review base2d16be4. Parent #1 authorizes observable Android flows,
protocol/backup reference compatibility and recovery checks. No extra test-seam
approval is needed for this authorized sequence. Local implement/tdd applies.

First slice: on an actually unsecured isolated emulator, Export wallet must
explain setting a device screen lock, retain the active identity and grant no
export. It uses real KeyguardManager availability, not a stubbed authentication
success. Run37870867861 atfbf75e0 compiled and reproduced the expected functional red:
Export wallet node missing. The real unsecured-device precondition passed and
the current wallet was displayed. Durable evidence: ticket05/no-lock-red.
Minimal affordance/setup error passes Android 10 run37871388107 at91d3a1a:
one actual UI test, unchanged identity and the expected setup guidance. The
public screenshot was downloaded and inspected. This verifies availability
handling only.
There is no authenticated document export yet and #6 is not complete.

Next slices verify real system PIN authentication/cancellation and SAF document
save/readback, lifecycle interruption and provider failures. Compatible options
must remain intact, while secret JSON stays off logs/screenshots/evidence.
A successful picker URI is not a successful backup. Dismissible reminder and
recovery guidance must preserve immediate first use.

Second slice adds a real PIN credential and Android DocumentsProvider in the
test APK only. The system file picker grants access to a real JSON file; the
test independently reopens it and checks compatible wallet identity/version.
UiAutomator 2.4.0 resolves and the instrumentation Kotlin compilation passes.
The actual authentication/export implementation is still absent, so this
test is expected to reproduce a functional red before that implementation.
Run37885161740 failed at credential-setup verification, before app export:
the test expected the wrong capitalization/text. Android 10 source prints
`Lock credential verified successfully`. The fixture is corrected to that
literal; this failed setup is not a functional authentication/export red.

Corrected run37885541772 atf2855e3 reaches the export action after real secure
device/PIN verification and fails at the missing Android picker, line64.
The no-lock test still passes. This is the actual second-slice functional red;
its log is retained under ticket05/pin-functional-red.

The implementation now launches CreateDocument, binds an operation to the
current wallet object, requests platform biometric/PIN authentication and
writes only after its success callback. It uses truncating SAF output, checks
provider errors, closes output, reopens through EOF, compares exact bounded
bytes and parses the compatible wallet before reporting success. It invalidates
the operation on dismissal/destruction without saved authentication state.
Runtime green, cancellation/fault coverage and reminder/recovery copy remain
pending. This is an intermediate slice, not completed ticket acceptance.

Primary research is in ANDROID_AUTHENTICATED_WALLET_EXPORT.md and
ANDROID_EXPORT_TEST_HARNESS.md. Pixel biometric/timing review remains #35.

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

Runs37886184520 (Android 10) and37886187308 (Android 17), source2d467b5,
both reach the real picker but cannot find the test provider root. The
Android 17 screenshot shows the test APK repeatedly crashing. Registration
is present in the merged test manifest. The standalone Kotlin provider may
depend on a Kotlin runtime normally supplied by the target APK's instrumentation
classloader; the provider's separate process cannot assume that classloader.
That cause is suspected, not established by the retained logs. Replacing the
fixture with platform-only Java removes the runtime dependency, while leaving
production export code unchanged. Crash diagnostics now whitelist only class
names and stack locations, never raw messages/backup contents. Both original
logs and public screenshots are retained under provider-fixture-failure.
Local assembled test APK class-definition inspection confirms the Java
provider is packaged and `kotlin.jvm.internal.Intrinsics` is not. This supports
the dependency diagnosis; it does not retrospectively supply the missing
original runtime exception trace.

Android 10 run37886921271 at a071b6b passes both no-lock and real PIN/save
tests. The saved file is independently reopened, imported and checked for the
displayed public key/version5.677; its bytes are cleared and the test file
deleted. The public success screenshot was inspected and retained under
first-pin-green/api-29. Repeated Android 10 run37887432827 also passes those
two tests, then reproduces the reminder functional red: fresh installation
does not show Back up your wallet. The reminder failure is retained separately.
The reminder now persists a per-public-identity dismissal preference, remains
nonblocking and disappears after a verified export. This preference is never
proof of a fresh outgoing backup. A scrollable recovery guide describes private
keys, an unencrypted JSON file, identity recovery and available Chat history.

Android 17 run37886923608 reaches System UI after the picker, but cannot find
legacy credential field IDs. Its protected-screen screenshot is black, so it
does not establish the screen's contents. Test interaction is being adapted
using public control metadata and actual hardware key events; this does not
stub authentication or change production export. Runtime green on Android 17
and reminder green remain pending.

Primary research is in ANDROID_AUTHENTICATED_WALLET_EXPORT.md and
ANDROID_EXPORT_TEST_HARNESS.md. Pixel biometric/timing review remains #35.

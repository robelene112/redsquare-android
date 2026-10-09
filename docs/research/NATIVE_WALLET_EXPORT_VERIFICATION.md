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
Minimal affordance/setup error is implemented; its runtime green is pending.
There is no authenticated document export yet and #6 is not complete.

Next slices verify real system PIN authentication/cancellation and SAF document
save/readback, lifecycle interruption and provider failures. Compatible options
must remain intact, while secret JSON stays off logs/screenshots/evidence.
A successful picker URI is not a successful backup. Dismissible reminder and
recovery guidance must preserve immediate first use.

Primary research is in ANDROID_AUTHENTICATED_WALLET_EXPORT.md and
ANDROID_EXPORT_TEST_HARNESS.md. Pixel biometric/timing review remains #35.

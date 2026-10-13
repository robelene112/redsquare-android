# First private Chat protocol increment

Review base c102f891f6041f08199905728d302e19b7588abd. Ticket#25 remains active.

Receive tracer: RED missingdecoder/31s, GREEN1/56s. Native-send tracer: RED
missingcreate/28s, GREEN2/58s. Exact independent Saito ciphertext/signature/
network/envelope and strict zero-payment restoration match. Shared regression
core76/0fail/0ignored and debug/testAPK passed in2m59s.

Saved exchanged-key tracer: actual RED3tests/1 comparison failure/55s; GREEN
full core77/0fail/0error/0ignored in1m. Imported keys take priority for sending,
and reading falls back to implicit ECDH for older available history. This later
core change has not yet had a new APK/runtime check.

Independent generator extended with public unsafe ephemeral3/4 secret and
unsafe1/2 signed identities. Two isolated network-disabled executions are byte
identical; SHA d30596ac8b3f10a02b9e42fd8b418a7189d02f95c2ffe176477c5534081d4778.
Fixed test entropy only; ordinary creation uses SecureRandom. These are lower
protocol checks, not UI/Relay/Archive/backend recovery or ticket acceptance.

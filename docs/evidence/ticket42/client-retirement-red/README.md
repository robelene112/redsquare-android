# Interrupted retirement: observed recovery failure

Exact226530ee gateway-only task FAIL35s: existing3PASS, strengthened client journey
1FAIL atHTTP boundary line46, expected201/got410 when the same wallet opts back in.
Before that failure the client binds, loses the acknowledgement, reconstructs its
journal, retires the outgoing wallet, binds the replacement and opts out. The
owned boundary then drops that retirement acknowledgement after actual200commit.
The next same-wallet opt-in tries the retired ID and fails. Re-enabled new-ID and
final newest-subscription opt-out tails remain unexecuted. No core task ran here.
Five manifested files retain build, three JUnit files and eleven source hashes.
This verifies the ordinary interrupted opt-out issue identified by source review,
not actual Firebase/cloud delivery or a new test boundary.

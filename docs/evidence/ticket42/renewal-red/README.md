# Intended renewal failure

Exact compiled source `bcf0ca97b0141e85498907bb9062d854e52c52f1`. Local gateway-only task fails28s with2completed
journeys: original signed binding/restart/keyless retirement PASS; new renewal
FAIL at line131, expected201 but received409 for a current signed renewal.
The new journey's initial challenge/binding succeed. Later newest-status, delayed
older request, idempotent renewal and post-restart renewal tails are unexecuted
in this failure. No change to the original journey or production behavior.

This is the same owned HTTP/protocol boundary and ordinary installation/backend
lifecycle, not actual Firebase rotation or a new test campaign. No core-test run,
Android/cloud/process-crash/full#42 or production operation is claimed. Three
manifested outputs retain the actual failure and seven committed source hashes.

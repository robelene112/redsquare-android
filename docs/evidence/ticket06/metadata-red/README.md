# Outgoing metadata safeguard failure

Source: `18f87d3`. Android 10 run `37910062409`; Android 17 run
`37910066475`. Both pass the six existing import UI tests, including explicit
replacement, recreation, fixed backend preservation and original-wallet restore.

The isolated metadata scenario creates and exports a local wallet through actual
Android file selection and system PIN authentication. It imports the test-only
scalar1 wallet, then selects the original wallet's backup for replacement.
Both runs time out at line 61 waiting for `Other assets could not be checked`.
The production implementation at this revision uses only the SAITO snapshot
and ignores the outgoing wallet's unsupported crypto metadata.

The missing safeguard is a functional failure. No successful risk-preview
screenshots exist from these runs. Logs are retained here; private backup
documents remain in the isolated emulator and are not downloaded or retained.
Earlier Android 17 picker/root failures are historical and are not explained
merely by these six passing tests.

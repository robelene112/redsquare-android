# Android 10 focused metadata failure

Run38164297170 at732e67f: all8ordinary personal-controls cases PASS74.394s, including actual SAF replacement/restoration and system PIN cleanup. The separate interrupted native phase reports Process crashed and reached the public pending checkpoint. The host validates public checkpoint fields, then fails its `run-as test -s` guard check. Its discarded stdout/stderr prevent proving the cause or guard absence. No cold recovery was run or accepted.

Root individually viewed the original choices-before-death PNG and separately parsed its XML: enabled Unmute for the muted account, enabled Unblock for the other account. This capture precedes the commit and does not prove surviving guard metadata.

Harness correction uses the established `run-as stat -c %s` path, retains command return/output (metadata only), and still requires a positive encrypted guard size before actual old-PID disappearance and independent cold recovery. No encrypted contents, wallet or options are read into artifacts. No product or assertion changes.

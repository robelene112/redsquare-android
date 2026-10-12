# Missing-lock fixture interruption

Run38207970800 at615cbcc passes source/core66/debug/test/release/lint, but ordinary
6reports5PASS/1failure85.896s atPreview218, requiring the controlled peer to have
received the wallet response before Authenticate and send. No copy assertion,
setup/reopening acceptance, successful completion or later death stage is reached.

Root fully viewed the original failure PNG and separately parsed XML: exact
recipient/6.25/fee0.1/owner server and Authenticate and send are visible. No setup
message has yet been requested. SaitoPeer.authenticate verifies the signed server
challenge then completes after enqueueing the wallet response; use immediately
closes/cancels this review-only socket. Server receipt is not a supported return
postcondition. The source explains why that assertion is unsupported, not the
measured scheduling or cancellation cause of this particular observation.

The fixture replaces this extra guard with positive observation of a successfully
sent signed server challenge. All exact review, actual unsecured device, no type4,
accurate transfer-copy and reopened funding/noPending assertions remain. Product
unchanged. Four indexed hashes preserve raw log/counts/original PNG/XML.

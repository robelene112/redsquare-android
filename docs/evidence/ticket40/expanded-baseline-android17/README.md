# Expanded baseline, Android17

Run38755418301 at6bcb3794 FAILED: four reported, four failures,46.395s.
No application changes. Group/private recovered-photo captions and group/private
first-use text with visible backup reminder all fail the immediate exact-editor
visibility assertion after Android reports the IME visible. This is after initial
editor readiness and, in photo cases, initial displayed-image readiness. Send,
privacy bounds, signed delivery and later assertions were not reached.

Root inspected all four original failure PNGs and parsed all four XML files.
PNGs show the keyboard, panned upper content and no visible composer/Send.
The separate earlier XML snapshots report focused editable text at146to194;
these observations are not atomic and do not prove a single stable layout.
The immediate assertion might also observe transition timing. Consequently the
corrected tracer adds a bounded10s visibility wait without weakening the final
assertion; production correction still needs fresh actual runtime verification.
The next photo-readiness correction separately waits for known raster pixels;
this run did not fail its initial photo-description assertion.

Initial two-case reminder-dismissed text baseline remains SUCCESS, not RED.
This expanded failed run does not establish a failure in every composition case,
completed send/wallet assertions or any physical Pixel result. All four tests
ran their independent scoped restoration in finally. The harness exits before
its success-only scope.txt; its absence is expected, not missing acceptance.
No production node/wallet/chain/service/configuration writes. No original
installation bytes left the device.

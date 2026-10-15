# Full-scope reopening attempt, Android10

Run38758235562 atc0b143eb remains FAILED. The four-case keyboard class
reports0PASS/4FAIL in56.98s. All four fail.
Every failure is at the SECOND readySend, waiting10s for the actual IME after
the reopening semantics performClick (line161/122). Initial IME/editor/privacy/
Send bounds, physical Back dismissal, exact draft/photo, restored header/reminder/
group controls and no outgoing work were reached first. Later Send assertions
are unexecuted in failed cases. All later full application/protected/public/tail
gates are unexecuted; this failed run is not complete acceptance.

Root inspected group-photo-failure.png and private-text-failure.png.
All twelve XML files parsed; twelve PNGs retained. Editing toolbars/selection are
visible in relevant failure captures; private text can show the restored backup
with the focused editor near the screen bottom. PNG/XML are separate snapshots.
The precise failed reopening-touch cause is unproved. The fixture correction
uses a physical trailing-padding tap within positive native editor visible bounds
and keeps actual IME, exact draft, privacy, native Send and wire assertions.
No timeout/budget or production-source change. Corrected focused/full runtime
acceptance remains required. No device/production/store/physical Pixel claim.

The scoped test finally restores original protected files/preferences/wallet/
backend. Retained captures are public synthetic fixtures, no private originals.

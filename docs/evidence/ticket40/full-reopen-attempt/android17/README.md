# Full-scope reopening attempt, Android17

Run38758240578 atc0b143eb remains FAILED. The four-case keyboard class
reports1PASS/3FAIL in69.043s. Three fail; group photo completes all checks and sends once.
Every failure is at the SECOND readySend, waiting10s for the actual IME after
the reopening semantics performClick (line161/122). Initial IME/editor/privacy/
Send bounds, physical Back dismissal, exact draft/photo, restored header/reminder/
group controls and no outgoing work were reached first. Later Send assertions
are unexecuted in failed cases. All later full application/protected/public/tail
gates are unexecuted; this failed run is not complete acceptance.

Root inspected group-text-failure.png, private-photo-failure.png, private-text-failure.png and group-photo-explicitly-sent.png.
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

# Initial text tracer, Android17

Run38754302286 atd63de718 SUCCESS: two real-keyboard text scenarios PASS51.341s.
No production changes. Both cases physically tap visible Send above the actual
IME and verify one exact signed/raw/Relay text, empty composer, full wallet/
backend and independent original-installation restoration. Core114 and debug/
test assembly are part of this focused workflow; no full acceptance claim.

Root inspected original group-keyboard-open.png and private-keyboard-open.png.
The group composer is vertically cramped; private's app-wide header is panned
offscreen, while the local Chat header and Send remain visible. These observations
do not establish that every keyboard configuration fails or justify a fix on
their own. Actual privacy bounds, recovered photo captions and the still-visible
first-use backup reminder are added in the next tracer. This initial fixture
explicitly dismissed the backup reminder via existing ChatFixtureState setup.

Initial local compiler attempt failed because performTextReplacement returns
Unit and was chained into performClick. The two operations were split; compilation
then PASS1m44s. This was a test compilation error, not an application failure.
The actual native run uses the corrected compiled test. No node/credential/store
changes or physical Pixel results. Protected group-contract file untouched.

# Reply viewport fixture correction

Full Android10 run38056259800 at7dcea19 passes core/build/release/lint and seven
of eight browse cases. The malformed-thread case fails its displayed authentic-
reply assertion. Inspected bitmap shows a valid visible conversation/root and
its social actions, with the next reply below the viewport. The new separated
conversation action row reduces list height; the fixture only waited for reply
semantics, without scrolling it. Other conversation cases already scroll their
reply targets. The bounded correction adds the same actual-target scroll before
the unchanged displayed reply assertion. Existing malformed-reference rejection,
controlled peer health checks and15s wait remain. No production change or timeout
extension. Corrected runtime acceptance pending.

Standards:0 actionable. Existing observable reply assertion now follows actual
reply scrolling, matching the other conversation fixtures; no documented breach
or actionable smell. Spec:0 actionable. Rejection, controlled criteria, health
and15s waits remain intact. Local test compilation PASS59s. Full same-SHA pair
38056769062/38056770920 is pending with both public reads enabled.

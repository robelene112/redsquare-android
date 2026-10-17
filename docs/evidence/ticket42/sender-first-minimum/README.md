# First sender minimum: failed response handling

Exact35e1eb93 owned build FAIL52s, exit1. Gateway prior5PASS/new1FAIL at31,
expectedAccepted/gotRetry10000; core task UP-TO-DATE. First HTTP send occurs,
but acceptance assertion fails before request-body and later outcome checks.
Source inspection identified exact-length response reading of a shorter body.
Reviews Standards2/Spec1 also require preserving known HTTP classification when
body reading fails and computing expiry/TTL after credential refresh. This is
superseded failure evidence, not acceptance. Seven manifested files retain raw
build, five JUnit and source hashes. No ADC/cloud/account operation.

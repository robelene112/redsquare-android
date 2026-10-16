# Scheduled Archive timeout observation: android17

Run[38982507198](https://github.com/robelene112/redsquare-android/actions/runs/38982507198)
SUCCESS at5463170d091ed58f3ad90686c184ca0875cea08f. Existing11 social cases
PASS in128.618s; both full worker journeys PASS in35.981s. Core114/0/0 and
selected native build/runtime pass; the other SDK is skipped in this run.

The added journey authenticates against a real controlled peer, whose ordinary
Archive callback observes/validates the request but withholds its prepared reply.
An actual scheduled retry appears after at least10s from initial submission,
including scheduling/authentication. Existing peer request deadline is12s inside
the receiver's15s overall deadline; this does not establish that the outer15s
deadline specifically fired or measure a response-only interval. No fake clock
or reduced timeout was used.

Recovery releases withholding and supplies normal signed data. The same journey
checks the actual pending duplicate UUID, visible OS alert without Activity,
completed duplicate with unchanged OS postTime, fullwallet/backend, zero outgoing
writes and cancellation/execution drain before scoped restoration. The original
malformed-response journey also passes with those same checks. No independent
restoration JUnit phase is counted.

Root parsed all6 XML and viewed the3 timeout originals: pending launcher, reply
alert and unchanged alert after redelivery. Malformed-mode captures are separately
retained; their current assertions/logs pass. Launcher imagery alone is not a
WorkManager-state proof. Fresh native reply versus independent parent provenance
is unchanged; this adds delivery verification, not wire/signature interoperability.

This is live-process scheduling only. No actual process restart/autonomous wakeup,
Firebase receipt, locked/Doze delivery, physical Pixel result or complete#32 claim.
SHA256SUMS covers every retained file except itself.

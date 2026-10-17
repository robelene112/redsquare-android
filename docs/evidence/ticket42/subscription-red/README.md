# Intended subscription lifecycle failure

Source `6bb9175e87ee77ecc5023b31566e0a57380c29fb`. The exact compiled source bytes match the recorded committed
files in[source metadata](source-files.json). Local `:push-gateway:test` compiles
and fails43s: one ordinary HTTP journey, one failure. At line39 it requires a
wallet challenge HTTP201 but the no-op runtime returns501. This is the intended
missing-behavior failure, not a compilation/setup failure.

Signed bind, server-instance restart, retained status, revocation without wallet
key and old-bind retry after retirement are later unexecuted tails in this run.
No claim is made that they pass or that a JVM process was killed/restarted. The
fixture uses a temporary loopback listener, synthetic installation/authority and
fresh test-only wallet. No real Firebase registration/delivery or production
node/account/gateway operation. Core tests were not run by this gateway-only task.

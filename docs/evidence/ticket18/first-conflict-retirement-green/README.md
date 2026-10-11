# Intermediate confirmed-conflict retirement

Android17 run38128516957 at e29604ecaf883554356814a309b46b7105ecc21c:
15 UI cases PASS126.964s, plus actual cold-process recovery1PASS4.449s.
The lost-race journey now verifies Cancel followed by read-only recreation,
explicit confirmation, durable blank form after another recreation, no send
while retiring/checking another name, and a different signed candidate only on
Register. Original individually inspected PNG/XML show the reopened blank
form with enabled Check, then frozen NativeOther9 with enabled Retry/pending.
Runtime assertions establish the emitted bytes and earlier offscreen gates.
Root and both review agents individually inspect these two pairs and raw logs.

The process host intentionally kills PID5146; recovery PID5226 retains the
original wallet/backend/signature/frame/envelope. Read-only reconciliation does
not send; an explicit Retry sends the original frame. Checkpoints and original
logs are retained. No additional independent-WASM audit of this intermediate
frame is claimed. These pictures are the UI conflict journey, not process
screenshots. The interrupted log's process crash is the expected host-kill step.

Own-name warning retention/repair and request-storage obstruction/corruption/
repair also pass within the15 cases. This is an intermediate proof: e29604e's
retirement payload still has the later identified unauthenticated-header gap.
It does not verify95d1811's authenticated marker or header-corruption tracer,
full regression, physical Pixel behavior, live registration or chain inclusion.

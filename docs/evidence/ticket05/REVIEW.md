# Ticket #6 review and recovery verification

Review base `2d16be4`, first reviewed HEAD `bd3902b`. Both independent axes
inspect the actual committed diff. The ticket is still active; this is not
completed acceptance.

## Standards

Two findings: full verification omitted export/reminder tests, and the success
test duplicated its existing picker helper. Both are corrected in `f095a00`:
full now calls the flows, and the shared helper selects the drawer row by its
Android title resource instead of the identically named toolbar title. Runtime
validation and review of fixes remain pending.

## Specification

Two findings: required provider-failure/interruption coverage is absent, and a
stalled provider can keep Export disabled indefinitely. Blocking best-effort
deletion also delays failure completion. A controlled Android provider read
pipe now stays open for 60 seconds, with a test requiring a readable failure
within 40 seconds, unchanged identity and a retryable Export action. Its actual
functional red must be observed before implementing the independent deadline.
Additional write/readback and lifecycle/process recovery checks remain pending.

Normal real-PIN export and reminder/recovery pass both Android 10 and 17.
Wrong-PIN/cancel checks currently exposed repeated-picker fixture ambiguity,
corrected by selecting the actual drawer row. No wallet JSON is in evidence.
Physical Pixel biometric/performance acceptance remains #35.

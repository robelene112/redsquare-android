# Opaque gateway wake: intended missing-processing RED

Run38986422596 remains **FAILURE** at exact4f56e70e1daa4f337f7a267503d474be80c62baf.
The existing11 cases pass87.424s. Worker class3 cases reports2PASS/1FAIL37.422s:
the two existing Archive journeys pass and the new opaque wake fails at
`Unavailable gateway receipt finished as SUCCEEDED`. The no-op worker actually
runs. This establishes missing processing/retry, not a gateway outage observed
by that no-op implementation.

The new gateway-request, recipient-proof, recovery, publication, duplicate and
later preservation assertions are unexecuted in this failed new journey. Existing
journeys retain their own passing assertions. Normal scoped cleanup is in finally;
no restoration error accompanies the primary missing-processing failure.

The recipient-proof positive assertions at later452f4595 are not part of this
runtime source and are not proven by this RED. They require the current wallet's
signature over the literal domain/full resolver URL/wake before reference
resolution, following the existing authenticated-gateway proposal. No independent
web interoperability claim is made for this new local request contract.

All21 retained files are hashed, all7 XMLs parsed. Root viewed only the new opaque
failure original. Earlier-mode originals remain retained, not newly claimed
visually inspected. The image observation does not establish an application
rendering defect. Source38986388089 SUCCESS includes core114/0/0, debug/test/release
assembly and lint. No incoming FCM, configured SDK, deployed gateway, production
node/account mutation, full#32 or new beta acceptance.

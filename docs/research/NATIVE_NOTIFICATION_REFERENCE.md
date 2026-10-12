# In-app notification compatibility reference

Issue#24, accepted seams1–4. Generated2026-10-12 from the pinned SaitoWASM0.3.2
andElliptic6.6.1 using pure signed-reference.cjs. WASMhashf59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e,
sourcepin9a4ca7e9442eb5e872168ced361ce741d7d139d7. Published unsafe scalars1/2
only; never fund. No node/wallet/options/database initialization or network writes.
The reference helper checks WASMpreimagehash/signature, independentEllipticverify
andfulltransaction roundtrip before exposing detached Archive envelopes.

First tracer combines the independently signed existing root post from
NATIVE_THREAD_REFERENCE.json (unsafe1author) and a newly independently signed
unsafe2 `like tweet` referring to that root, with actual unsafe1 outputrecipient.
Archive field3 names unsafe1 and field4 names the original, matching both inspected
module indexing contracts. Metadata alone never supplies signer/eligibility.
Whole JSON SHA256:2b8a817154dae7ab0918baaed1f792a74e062b904d33a3b35d05d075502149c9;
2980bytes. Documentation andnativeasset copies are identical.

Reproduction uses the existing build image, networknone/read-only container and
rootfilesystem mount, root inside the container for chroot, empty environment,
/opt/saito-runtime/node-v24.13.0/bin/node and the exact tracked generator path.
The first attempt inherited the image's unprivileged user and failed chroot before
Nodeexecuted. Corrected `--user0` with unchanged read-only/network isolation passed.
This is reference-runner setup failure, not cryptographic/application/node failure.
No hostsecurity policy/capability/production configuration changes were made.

The first native journey uses the existing realbinary authenticated controlled
peer andisolated .dev emulator. It observes the feed before opening Notifications,
skips contextualpermission, expects incominglike/verifiedactor, opens original
Conversation andactorProfile, verifies actualowner-index reads/no emittedpayment/
report, then restores full opaquepriorwallet andpriorbackend independently.
First hosted RED is pending; no feature/runtime/closed-app/Pixel acceptance yet.
Primary source contracts remain NATIVE_NOTIFICATIONS_CONTRACT.md. Existing source
A/B differences, missing originals, mentioncoverage and archive-retention limits
must be preserved rather than claiming complete network history.

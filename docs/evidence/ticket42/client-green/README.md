# Durable subscription client: local acceptance

Exact2a1d774d, clientbasec71038b3. Corrected core/gateway task PASS1m29s,
corefresh114/0/0 and gateway4/0/0. Source reviews report Standards0/Spec0 after
fixing the original Standards1/Spec2. The [first minimum](../client-first-minimum/README.md)
is superseded. Both actual failures remain: the [no-op client](../client-red/README.md)
and [lost retirement acknowledgement](../client-retirement-red/README.md).

The public portable client binds through actual owned gateway HTTP with real Saito
signatures and synthetic identities. The external HTTP boundary drops its first
bind acknowledgement only after server201. A reconstructed client with the same
durable filesystem journal and replacement wallet retires the old ID without the
old wallet signature, then binds the replacement owner/backend/FID. The journey
next loses retirement acknowledgement after actual200, reconstructs again and
opts in with the same wallet. It completes pending retirement and obtains a new
ID. Final opt-out must explicitly target that newest ID; its status is inactive.
The final assertion cannot pass by observing the already-retired original ID.

The source also signals retry if fresh null demand appears while binding authority
remains, instead of silently returning success. This inter-read race is reviewed
source behavior, not a separately executed controlled race in this journey.
Six manifested outputs retain build, three JUnit files, fresh core index and
eleven source hashes. Final evidence reviews at724a8455 report Standards0/Spec0. Android Keystore,
actual SDK/FID rotation, client-process crash, Google delivery, public deployment
and whole#42 acceptance remain unclaimed. Beta24 unchanged.

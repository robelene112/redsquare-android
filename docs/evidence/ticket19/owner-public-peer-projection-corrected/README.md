# Bounded public registrar projection

2026-10-15T19:06:46Z, one read-only HTTPS GET to Owner /json/peers. HTTP200, validated exact {peers:array} wrapper and every row public-key schema. Published registrar sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK has zero matching verified peer rows in this snapshot.

Pinned getPeers filters verified peers rather than connected-only peers. This supports absence from the verified snapshot, not absence of any TCP connection. Effective Registry authority and useful forwarding are unestablished; service state was not collected. It is a plausible connection-related explanation for the separate native timeout, not a proved cause or repair. No node upgrade, peer change, live registration or production writer was performed.

External subprocess supervisor bounds worker execution to12s; response read capped256KiB/2048rows, redirects rejected. Full peer response existed only transiently in worker memory. Only fixed published-registrar booleans/counts/status were retained; unrelated keys/keyLists/ids were never printed or saved. Initial wrong-wrapper attempt remains a schema failure in its separate directory. Both review axes reported zero remaining collector findings before corrected execution.

Source context: ../../../../research/OWNER_REGISTRY_READINESS_NEXT_CHECK_2026_10_15.md. This is a host public-status observation, not native application acceptance.

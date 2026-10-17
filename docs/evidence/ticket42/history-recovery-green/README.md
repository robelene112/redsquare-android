# Durable recovery from unavailable retained ancestry

Exact0d3b3e94 owned build PASS1m50s, gateway10/0/0/chain10.588s,
core task UP-TO-DATE. Ten manifested files retain raw build, eight JUnit and23
source hashes. Source Standards0/Spec0.

Both approved selectors complete prior direct-reader/processed-cursor/reorg
checks. The seventh Full response advertises a different tip and an unretained
ancestor2m0/cc. HTTP shows no active cursor and an explicit ancestry warning,
without another block download. A new server instance retains that recovery
state; the eighth request must be SPV with zero cursor/hash/fork. Available
independently signed full blocks are processed, then the ninth request must carry
their exact persisted Full cursor. Bootstrap, reorg and ancestry warnings all remain.

The RED first failed with the old retained cursor object. Its separate null-oracle
mismatch was corrected to accept missing or explicit JSON null before GREEN;
no strengthened-RED run is claimed. Non-social fixture: preserving bindings/dedup
and withdrawing uncertain pending chain events during reset are source-reviewed,
not separately executed combinations. Bootstrap trimming after interior missing
heights and other unavailable-history paths are also source-reviewed.

This resumes available recent history; it does not reconstruct unavailable blocks
or prove consensus/finality, actual Google/device delivery or whole42/32 completion.
Parent-proof availability and whole-ticket integrated review remain next.

Final evidence/doc reviews ate13a6a12 report Standards0/Spec0.

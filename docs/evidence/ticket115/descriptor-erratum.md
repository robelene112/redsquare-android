# Initial collector descriptor correction

The initial complete attempts at source `dfe6cb855` failed during Android lint in
all six jobs. Every emulator runtime step was skipped. Their whole original
artifacts remain retained; no native result from those runs contributes acceptance.

While those builds ran, a static check found a separate new115collector typo:
it expected every process success descriptor to start `PASS: actual authenticated
native`. The unchanged Crypto script writes `PASS: actual native reviewed/PIN-authorized
provider reservation before process death, ...`. Its actual native/PIN/process
requirements were unaffected. An intermediate qualified wrapper was reviewed but
never used for acceptance, and is retired rather than shipped as unused tooling.
Its history remains in Git at `186bbccf1`.

The fresh complete runs must use a newly committed strict contract/collector
before dispatch. Every process goal now freezes its full exact descriptor from the
unchanged original script; the collector checks exact equality. No original class,
identity, count, request/application deadline, artifact, visual, speech, media or
inherited94/103/104validator is removed or weakened. Both complete SDKs and the
actual signed update must be rerun for the Activity-provider correction and the
new candidate. No partial assembly or post-run qualification is planned.

The original contract/collector are available byte-for-byte at the original
executing source above. Keep their hashes and the failed lint/build originals in
the final packet. Public beta127remains unchanged; staged128is superseded.

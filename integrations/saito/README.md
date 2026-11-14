# Bound Store payment recovery

`store-payment-recovery.patch` is an additive compatibility change for official
Saito revision `0db0062d873369dc013046ed77c97410b5683b64`. It is a reviewable
source artifact. It has not been installed on a live provider. See
[ADR 0003](../../docs/adr/0003-bound-store-payment-invoices.md).

The original reservation API can resume a different open Store purchase without
identifying its saved carrier. Repeating it after completion can allocate a new
invoice. Native checkout therefore requires `store_purchase_protocol: 1`, a
matching saved `request_id` and `purchase_signature`, and the exact network and
amount before exposing payment instructions.

The patch preserves existing browser fields and adds a read-only
`buysaito purchase status` request to the existing authenticated Mixin route. It
scopes status to the requesting wallet, ticker, Store purpose and original
request identity. Missing status never allocates an address. A provider-wide
reservation queue serializes address selection and insertion. A repeated saved
request returns its original invoice, including after completion. A conflicting
open reservation keeps its original identity and is rejected by native checkout.

The source reference executes the pinned, hash-checked service and transfer
methods with this exact patch in a disposable directory. Its adapters own the
database, public fixture credentials, address and clock; the payout uses actual
saito-wasm 0.3.15. These checks do not establish live provider readiness, treasury
operation or funded consensus. Run from the repository root with external
runtime/source/parser paths:

```
node test-fixtures/reference/store-payment-provider-reference.cjs \
  /path/to/reference-runtime /path/to/pinned-source /path/to/acorn.js \
  /path/to/output.json integrations/saito/store-payment-recovery.patch
```

The generator verifies the original files and parser hashes, verifies the patch
applies, executes the provider methods, and removes its temporary patched copy.
It performs no network access. Its output records the exact patch hash.
Deployment, provider funding and a fully initialized live end-to-end purchase
remain unchecked under STORE_LIVE in issue 36. Never interpret an app beta build
as authorization to change a node, activate a provider or make a public payment.

# Pending username request first native GREEN

Issue #19, source5bc5de42f035e3ff0dcff530db656f8becffe74d,
Android10/API29 run38114695697. Both native cases PASS8.858s; source/core/debug/
test build37s. Retained name is exact and disabled, Activity recreation sends
nothing, explicit Retry sends byte-identical actual frames, authenticated public
wallet unchanged. Recreation is not process death. No live registration.

Actual native frame independently verified with pinned WASM0.3.2/hash and
Elliptic6.6.1 by verify-registry-native-frame.cjs: one zero input, published
registrar output first and wallet output second, exact top-level Registry body,
transaction and routing signatures, detached envelope. Generated emulator wallet
public identity/signatures only; no private material. No namespace ownership,
network inclusion or current deployed-client claim follows.

Original pending and unavailable PNGs individually viewed, all three XMLs
inspected. Pending and reopened PNGs are byte-identical (hash below), both320x640.
The tool presentation of the reopened path appeared as just the status bar;
its file identity establishes identical original pixels to the full pending
capture. No renderer or capture-timing cause is claimed. PNG/XML are separate
observations. Originals copied verbatim, not modified or regenerated.

Both pending source review axes have0 residual findings after5bc5de4 corrects
one disabled-field selector. Full dual-version runtime, process death, signed
confirmation/caching and name-display/import safeguards remain unfinished.

SHA256:

- username-ui.txt:ca2039b36197e22b9371f25f6348d51b38502ba21cc9ef0b1da3af183777804d
- username-native-frame.json:6c5856f24e25e165aff0a4d1e77894e95126fcd8d1a87a7421a3704141aea216
- independent-native-verification.json:cd7eca30e59bd6027f320f631e20bd31bc6ca542969868fd52653a448e28339d
- username-unavailable.png:7e04b9a6898883d0639a235f6a79823098da022d6a9597e08b44012eac51125d
- username-unavailable.xml:0fadd142d97e99a314efc5650742429b331a8d3e1680c13c1cb0ee4f70302475
- username-pending.png:46dca2f61983c8f8f0f10d85148824f5074846bb59635577401e2a30e93014a0
- username-pending.xml:e8660774a3236355bccc042d17b8b6fc4e19bc484dc666f34f0b9e22670cf245
- username-pending-reopened.png:46dca2f61983c8f8f0f10d85148824f5074846bb59635577401e2a30e93014a0
- username-pending-reopened.xml:e8660774a3236355bccc042d17b8b6fc4e19bc484dc666f34f0b9e22670cf245

# Full Android17 wallet and quote visibility failures

Run38129053893 at95d1811d9f3729353bf86204e8a235a18ff5661d completes
13 of15 username cases in91.855s. Two failures: the same unscoped wallet-address
selector matches two Compose nodes at446; quoted original text is not displayed
at243 after scrolling to its author. The original wallet PNG/XML display the
expected public address; the second Compose match location remains unknown.

Root independently views both original PNGs and the separate XMLs. Quote XML
places the verified name at[16,608][129,624] at the lower edge of the scroll
viewport[16,308][304,624]; quoted text is absent from that window XML. The bitmap
and XML differ in visible name detail, so neither is claimed to be one atomic
snapshot. Runtime had passed the exact verified-name display assertion before
failing the exact quoted-text display assertion. This is insufficient evidence
for a production rendering defect or a particular asynchronous timing cause.

The bounded fixture change scrolls to the exact quoted text before the unchanged
display assertion, then back to the verified name and asserts its visibility
again before capture. The anonymous quote-author, signed lookup, same-wallet,
no-transaction and peer-health assertions remain. Wallet acceptance uses the
named public address field with exact equality and visibility. No production
source or timeouts change. Combined fixture compilation PASS1m23s; fresh full runtime remains pending.

The entire authenticated retirement-marker corruption/repair/new-request case
passes individually. Earlier native stages pass, but this run stops before the
new Registry process stage and later wallet/update/public-feed acceptance.
These failed results do not establish full acceptance or live registration.

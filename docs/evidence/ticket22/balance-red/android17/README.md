# Balance and receiving tracer: Android 17 RED

Run [38184572679](https://github.com/robelene112/redsquare-android/actions/runs/38184572679),
source `5c173f5aa738a4c11d0ce6c3505fd7417d1c39ad`.
Core/source/debug/test assembly succeeded; one native case failed in 8.688s
because the Wallet contained no "Balance and receive" action. Root inspected
this original PNG and separately observed XML: both show the existing wallet,
export/import/recovery controls and no balance action. This is a behavioral RED,
not a build failure or payment test. No funds were sent.

The subsequent source uses "Balance from saito.davidengelen.nl" to identify the
balance observation separately from the underlying feed's existing Server
label. The receiving identity, literal 7.5 amount and actual clipboard assertions
remain. No native GREEN or ticket completion is inferred from implementation.

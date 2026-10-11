# Profile new-edit focused GREEN

Source c78ebeb. Android10 run38107420462: editor7/36.816s, publisher5/26.992s
(four passes/one API30+ assumption), media2/18.153s, new-edit1/11.001s.
Android17 run38107422060: editor7/83.711s, publisher5/33.546s,
media2/21.458s, new-edit1/12.216s, all15 pass.
Source38107407359 passes core49/debug/test/release/lint.

Root individually viewed all twelve original PNGs and parsed corresponding XML.
Cancel preserves frozen candidate; confirm starts editable newer signed baseline;
unsent new bio survives fresh Activity with stale reads; explicit second signing
preserves newest archive/extensions/media, exceeds retained timestamp and never
rebroadcasts the retired candidate. Both actual second frames retained with
independent pinned WASM/Elliptic and A/B server/browser receiver PASS reports.
Images are unavailable because the synthetic HTTPS media URLs intentionally have
no real image service. This does not establish image download, DOM, physical
performance, live deployment or chain inclusion. Cold Activity is not process death.

Bounded source review finds one remaining preview recovery issue on both axes:
load() does not derive pending full signed candidate preview after dropping the
private baseline. Editable fallback also must merge newer public fields. The
present tracer retains equal old/new media, so this GREEN does not prove those
cases. One dedicated native tracer follows before remedy. Full#18 acceptance and
actual process-death proof remain pending.

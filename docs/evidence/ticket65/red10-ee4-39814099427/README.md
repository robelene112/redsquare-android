# Search keyboard RED on Android10

Run39814099427 at ee4708716505468571a88920ffc93f4964c5ed48 finished FAILURE.
The original nine SearchPreviewTest methods passed. The additive
SearchEntryPreviewTest failed at explicit IME submission because the actual
search field declared ImeAction.Default. Its preceding real-keyboard visibility
and zero-post/quote-request checks passed. No product change was present.

The native screenshot was inspected: the keyboard was open, but its captured
input frame did not yet display the entered text. The instrumentation semantics
at the failed action contained the exact query. Later captures require visible
text before retaining the screenshot; this RED does not claim input-rendering
acceptance. Original logs, PNG/XML and archive hashes are retained here. Raw
artifacts remain outside Git under search65-red10-ee4-39814099427.

The earlier run39813595141 failed compilation only and is not native RED.
This packet is a failing baseline, never ticket or full-suite acceptance.

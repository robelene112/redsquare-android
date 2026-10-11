# Search paging GREEN, Android 17

Focused run [38135039545](https://github.com/robelene112/redsquare-android/actions/runs/38135039545), source `54e806f18f1a425da50ec58684327bdcdd300df5`.
Two native tests PASS in 40.598 seconds. Hosted source/core/debug/test assembly also pass. This focused scope does not run release assembly, lint, the full suite, public integration or physical-device checks.

The runtime verifies reply search/navigation/recreation, partial-result retention, successful-stream read counts through recreation and retry, exact older cursor paging, exhausted-stream stopping, empty versus error copy, unchanged wallet/client identity and zero submitted transactions.

Original raw log and three PNG/XML pairs are retained unchanged. Root individually viewed the partial-result, older-result and recreated-reply images and inspected their separate XML observations. Bitmap and XML capture are separate snapshots. Runtime assertions, rather than text-child XML enabled flags, establish action state, exact request counts and earlier-result retention.

No 1,000-row limit runtime, durable search cache, people search, full ticket acceptance or Pixel performance claim. Those remain pending.

# Full Android17 stopped during oversized image fixture setup

Run38180252201 at709b58d68da1b773050b33e4e459343500fa9c96 passes source/
core/release/lint, corrected browse/posting, queue/social/quote/author and
preceding image runtime/process checks. Image-preparation5cases/4PASS/1FAIL
13.502s atImagePreparationTest127: reopening a previously published one-byte
image URI for the oversized fixture raises FileNotFoundException, No item at
content://media/external/images/media/42. The composer and rejection checks
for that case are unreached. No in-scope screenshot/XML exists for this setup
failure. Reporting and later full scopes are unreached.

Source has a published-placeholder/reopen interval and reuses filenames after
deleting an owned fixture. Provider scanning/reclassification is a supported
hypothesis, not a reproduced cause or app defect. Fixture correction follows
[Android's pending write/publish sequence](https://developer.android.com/training/data-storage/shared/media#toggle-pending),
uses a UUID name and one complete write, then publishes. A valid PNG prefix
plus32MiB padding and one byte has exact positive stat-size checked before
selection. Missing uses a valid PNG followed by exact explicit deletion.
Malformed remains malformed without claiming a particular decoder branch.

All owned URI allocation moves inside the existing primary-preserving cleanup
boundary. Ordinary format/pixel/message bounds and rejection/prior-draft/no-send
assertions remain unchanged. Product source is unchanged. Source review and
focused runtime are next; the ongoing full10 run remains independent.

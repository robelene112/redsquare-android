# Different-contact receive regression, Android 17

Source `22b07ed5359e5a019890179220f1dfb73a419147`, [run 38363647971](https://github.com/robelene112/redsquare-android/actions/runs/38363647971). Thirteen cases complete in 90.368s: twelve pass and the different-contact case fails at PrivateChatPreviewTest.kt:260 waiting for positive receive ACK. The raw instrumentation command exits zero despite its reported assertion failure; the runner's OK-result gate correctly rejects this run. The 180-second class budget is not exhausted.

Root parsed all 14 XML captures and viewed the original history-first-failure.png. It shows the existing contact and independently encrypted available message, while the next-contact message is absent. The positive ACK timeout, combined with source rejection of a different counterparty, establishes the receive regression; the image alone does not establish packet delivery. Shared capture filenames belong to later cases and cannot be attributed to this failure. Activity recreation is not process death. Public unsafe fixture identities only; no production message or wallet write.

Original bytes, raw runner, exit status, source metadata and core report are retained with SHA256SUMS.

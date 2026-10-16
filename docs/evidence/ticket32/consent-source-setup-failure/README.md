# Documentation push runner setup failure

Run 38964130150 at `ec17fc0dfa306cc68b5aa1cc47ef0ab4a6fd92ef` failed before the build. Android SDK installation reports “Error reading Zip content from a SeekableByteChannel” while installing the Android Emulator. The build and both runtime steps were skipped. This is not an application test result. Exact underlying download/archive cause is unproved. No unchanged retry was dispatched.

The earlier actual passing source and native runs at `495fb72c` remain separately recorded under `social-consent-green` and `social-consent-regression`; production and test source are unchanged between that source and this failed documentation push.

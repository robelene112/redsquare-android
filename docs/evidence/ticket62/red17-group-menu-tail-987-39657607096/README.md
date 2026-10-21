# Android17 cold group action diagnostic at987

Exact9875bf29ae7c4fd77144187990040fa6d6dee5e0, run39657607096: terminal FAILURE. The original single GroupActions method exits124 under its60second bound, without JUnit completion. Later classes are unreached. Two original members retain progress and operator timing; no successful native capture was completed in this run.

The coalesced actual Copy and Reply lifetimes each complete once. Progress reaches first-original-verified114340 and cached-copy-verified122812 after fixture-start69300, but not the next native capture. Copy's menu spans90066to93009 and Reply93130to96441. This demonstrates removal of duplicate menu lifetimes without proving the cold fixture fits60seconds; unrelated startup, signing, input and restoration variation remains. No app crash or capture-specific cause is established. External originals: `/mnt/storage/redsquare-android-artifacts/conversation62-timing-group-ui17-987-39657607096/android-native-proof`.

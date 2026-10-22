# Search entry observation failure on Android17

Run39817090310, exact source4ee3121a3c22272af7cb483426b9fcca3f361002, finished FAILURE.
All nine original Search methods and the delayed-response clear/recreation case
passed. The keyboard-submit case failed before submission while the native
capture waited for the entered text. The original10second capture bound remains.

The correction explicitly observes the Compose editor text before crossing into
UiAutomator observation. This tests the pending-render hypothesis; only a fresh
execution can establish the correction. These failed runs are not acceptance.
Raw artifacts remain external under search65-entry17-4ee-39817090310.

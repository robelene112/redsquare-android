# Google Play review distribution

Official Google Play documentation checked on 2026-10-08:

- The internal testing track distributes builds through Google Play to selected
  testers using an opt-in link. It supports early testing before the app is
  fully configured or publicly searchable. This can support the owner's
  phone-based review workflow without repeated uninstall/reinstall cycles.
- Internal testing and internal app sharing are different features. Internal
  app sharing requires enabling an extra Play Store setting; the internal test
  track is suitable for ordinary Play-based review updates.
- App updates can be installed automatically when the user has enabled automatic
  updates. Do not promise immediate delivery for every uploaded build.
- The closed-test requirement for qualifying new personal developer accounts
  concerns production access. It does not require recruiting twelve testers
  before beginning the owner's internal development review.
- Play Console account setup, app creation, stable package identity, signing,
  tester access and publishing automation still need implementation or user
  configuration. No account, app listing or release was created in this check.

Sources:

- [Set up testing tracks](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
- [Internal app sharing](https://support.google.com/googleplay/android-developer/answer/9844679?hl=en)
- [App updates](https://support.google.com/googleplay/android-developer/answer/9859350?hl=en)
- [Personal-account production testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

The owner delegated channel selection to whichever is easiest for iteration.
The initial plan is signed APK review builds at a stable link, with in-app update
notices. Play internal testing remains available after account/app setup.

## Direct APK iteration and later Play compatibility

Further official Android documentation checked on 2026-10-08:

- A website can serve release APKs. On modern Android, the user grants install
  permission to the specific source, such as the downloading browser. Ordinary
  review updates must not be described as silent automatic installations.
- Compatible updates require consistent package identity, signing and increasing
  versions. Plan the app signing key before the initial installation; an upload
  key is not interchangeable with the actual app signing key.
- Play App Signing supports supplying an existing app signing key. This enables
  planning consistent signing for direct APK and Play distribution. Verify the
  transition in practice; do not promise it while package/signing setup is absent.

Sources:

- [Alternative distribution](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
- [Signing and update compatibility](https://developer.android.com/studio/publish/app-signing)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)

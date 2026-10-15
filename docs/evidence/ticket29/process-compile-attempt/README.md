# Process fixture compile failure, no device acceptance

Run38703034326 exact3a9649443a98397248874b7f0ab77dfbc70c6d5f FAILED
compileDebugAndroidTestKotlin before any native test. The new process fixture
referenced ControlledFeedPeer methods on its started MockWebServer instead.
Correction56b3c17e retains the peer separately and uses the server only for its
port/lifetime. Both bounded correction reviews report zero findings. Production,
acceptance assertions and individual deadlines are unchanged. No native process
or eight-case runtime acceptance is inferred from this failed run.

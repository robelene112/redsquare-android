# Null keychain compatibility correction

Reviewer identified imported keys:null compatibility. Initial new test mistyped
the shared-secret API and failed compilation26s; initial-test-typo.txt is retained
separately, not behavioral RED. Corrected test produced actual RED13cases/1failure
in33s at contact discovery. Minimum treats null as empty consistently for discovery,
shared-secret fallback and adding a first portable contact. Original export remains
unchanged. Shared group predicate extraction resolves labelled duplicate heuristic.
Full core84 GREEN results retained; APK build result recorded separately.

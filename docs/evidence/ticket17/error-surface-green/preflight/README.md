# Refusal-recovery source preflight

Corrected sourcec9b114a868cace2c75527c523eac939c53f30bc5, reviewbasec0f2da7e.
Standards0/Spec0 after fixing the retained AndroidView's Retry identity and
requiring the fresh provider surface to be actually attached/visible. For101/150,
the existing browser remains visible until retry/close; Retry is outside its
surface. Other failed states retain the previous disposal/fallback behavior.
`key(browser)` ensures replacement attaches a new actual view. The refusal copy
states unavailable playback without inventing the provider's reason.

The controlled tracer first failed exactly at the missing explanation; see the
adjacent error-surface-red packet. Its stronger GREEN oracle adds visible bounds
and native copy, plus visible fourth-response content after Retry. Those extra
assertions were added after the original RED, which remains unchanged.

The first b36ba387 local build was deliberately retired before editing compiled
source after the Retry finding: owned exec46456 exit143, not PASS. Root identified
the specific container by workspace/Gradle args before stopping it. Corrected
exec75378 passed3m31s: coreUP-TO-DATE, debug and instrumentation APKs assembled.
These local results are compilation/cache checks, not native or public playback.
SourceCI39004102110 SUCCESS and controlled SDK runs39004102308/39004103787
SUCCESS at this same commit are retained separately.
Beta24 is unchanged. No provider identity, header, login, proxy or node changes.

# Quote fixture and timeout diagnostics review

Supplemental base16ac9b0, application source remains3132404. Quote run38054546080
passes its first four cases, then original recovery times out after the second
raw Retry. Publication subsequently starts, but the whole180s limit expires
without further test result. No failure bitmap was captured in that run; causes
remain unestablished.

Standards:0 actionable. Original-preview Retry controls use the same positive
visible/enabled list helper. The separate process-death Quote tap also uses it.
Static publication phase markers and host PNG/bounded10s XML capture add diagnostics.

Spec:0 actionable. Composer retries, actual process kill, wallet/candidate/no-
rebroadcast and quote rendering assertions remain intact. Existing180s suite limit
and failure exit status preserved. No claim that the later hang is resolved.

# Actual profile process recovery

Source ed645ab. Android10 run38109328015 and Android17 run38109329450 pass
the four preceding profile UI classes and three host-separated recovery stages.
Source38109264540 also passes core49, debug/test/release assembly and lint.

Android10: editor7/33.591s, publishing5/24.451s (four pass, one API30+
assumption), media2/16.322s, new-edit2/24.293s. Android17: editor7/81.75s,
publishing5/31.84s, media2/21.048s, new-edit2/29.321s, all16 pass.
The combined log is the exact concatenation of the four individual logs.

The first two recovery stages deliberately kill their own app processes. Their
`Process crashed` runner results are expected interruptions, not JUnit passes.
The host verifies fresh phase checkpoints, unchanged public wallet/backend and
absence of each recorded PID. Android10 draft/candidate PIDs6030/6230;
Android17 PIDs5425/5549. Both recoveries read existing wallet/backend without
resetting either. Third-stage JUnit passes one test, 4.21s/5.913s respectively.
Capture exit status is0 on both SDKs. Exact public checkpoints and raw logs
are retained alongside six original PNG/XML observations per SDK.

Before the first death, both owned source image URIs were deleted and the latest
bio was saved by opening the real external picker. Android10 observes
com.android.documentsui; Android17 observes com.google.android.photopicker.
Cold draft asserts exact latest editable Unicode bio and prepared red avatar/
blue banner pixels, with zero broadcasts until explicit Save profile. One exact
signed complete snapshot then contains only description/image/banner, expected
JPEG dimensions8x8/12x4 and pixels. The uncertain candidate survives the second
death. Cold reconciliation observes exact server availability with zero further
broadcasts. Verified Back clearing preserves the public bio/pixels, disables
unchanged Save profile and removes Retry/Start new edit on re-entry.

Root individually viewed all twelve original PNGs and parsed their XML. Media,
picker, pending and available states agree. The bio is above the scrolled media
viewport in these captures; its recovery is established by exact runtime
EditableText assertions, not a visible-bio bitmap claim. PNG/XML are separate
observations, not atomic pairs. Checkpoints contain only public synthetic data.
No production write, arbitrary power-cut or physical Pixel performance proof.

Full cumulative source review afce661...ed645ab: Spec0 and Standards0 remaining
hard findings. A tracked capture-helper duplication heuristic is addressed by
the subsequent narrow test-only extraction; its compilation/review/full runtime
are separate checks. Final full Android10/17 regression remains pending.

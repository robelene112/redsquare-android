# Full Android10 attempt, post navigation fixture

Run38760924822 at2867694e remains FAILED. The strong keyboard class completes
four PASS27.31s, including physical Back/reopening and exactly one explicit Send.
Other completed classes are indexed separately. PostPreviewTest reports18 cases,
17PASS/1FAIL84.651s. Sole failure is unfinishedCompositionSurvivesBackendNavigationAndActivityRecreationWithoutPublishing
at175: Settings is absent immediately after editing the owner draft. Candidate
MainActivity intentionally hides global navigation while the actual IME is open.
The fixture did not dismiss the IME before this click. No failure-specific capture
exists for this case; the archive contains only other cases' earlier captures.
The exact failing window position is therefore unobserved. Later backend-switch,
recreation, no-publication and subsequent full-suite gates are not accepted.

Fixture correction dismisses through the platform IME API, waits at most10s for
visible Settings and asserts the exact unsent owner draft/no outgoing transaction
before the original click. All subsequent server-isolated draft, recreation and
no-publication assertions remain unchanged. No production or budget change.
A corrected runtime and complete suite remain required. No physical Pixel,
production node, wallet, service, chain, credential or store change is claimed.

Root inspected all four original keyboard-open PNGs from this full attempt.
All12 keyboard XMLs parsed;12PNG/12XML retained. Privacy, exact multiline draft,
photo when selected and native Send are visible above IME. These class results
remain separate from the later posting failure and do not complete the full run.

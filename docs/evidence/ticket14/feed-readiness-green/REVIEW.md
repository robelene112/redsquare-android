# Corrected Android17 feed readiness

[Run38071460383](https://github.com/robelene112/redsquare-android/actions/runs/38071460383),
sourcec73fc130310bbfdf453c5e4741de237d3fdf0f1c, focused feed-android17,
public_feed_read=false. Core39/debug/test assembly pass. All8 feed/backend
cases pass in64.029s, including unreadable-refresh warning with retained post.
Actual cold cached-feed recovery passes; no physical performance claim.

The corrected flow waits for initial responses and enabled Refresh before
mutating the controlled response, requires a visible/enabled click and a new
observed request, then asserts the exact warning and visible retained post.
This confirms the corrected test behavior; it does not prove a unique cause
of the preceding full-run failure. Production source is unchanged.

Full17 companion38071834163 at65469a7 is still pending. It includes the
subsequent stricter picker-closed helper and separately enables both public
feeds. Focused feed success alone is not final #15 acceptance.

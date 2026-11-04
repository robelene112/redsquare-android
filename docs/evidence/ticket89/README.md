# Profile identity and history separation, issue89

The real profile now separates signed identity and biography from history with
extra space and a shared-theme divider. Its name is an accessibility heading;
the own-profile edit action uses the reviewed outline treatment. The own
following count comes from actual saved follow choices. No global follower total
is invented where the existing Saito adapter supplies none. Existing identity,
Posts/Replies/Likes, follow/moderation/report, history and editor behavior remain.

Profile navigation retains its list state and displayed signed posts while a
conversation or author editor is open. Returning keeps the same post and pixel
offset. Local profile drafts remain separate from the public signed biography.

## Source and verification

Review base: `304e32a5697abc6c9a16e46d566a1bc700da0e7b`.
Last production change: `5b0b24cd2e87679769cba5f1e5b50c48c7ea8841`.
Final focused source: `b08e753fcbf3b3a3d28a6002130aefd491b1d254`.

- [Android10](https://github.com/robelene112/redsquare-android/actions/runs/41869235288)
  and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/41869242638)
  both pass all seven methods across four separate180second classes. Each checks
  24 actual own/public viewport cells:320/412dp, light/dark and100/130/200% system
  text. Signed data supplies short, empty and long biographies with distinct
  image pixels. All history tabs remain selected and actionable. Own200% cases
  use the real IME and Save draft, without replacing the signed public biography.
  The reading test physically opens a conversation and an unsent author edit,
  requiring the original post text at the same pixel offset within1px on return.
  No scrolling after return manufactures that result.
- Original full profile regressions pass on
  [Android10](https://github.com/robelene112/redsquare-android/actions/runs/41866593345)
  and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/41866599589)
  at `0de31cb05`, with identical production source:16 profile UI methods,
  four video precondition methods and two real process deaths per OS. Original
  media/import, conflict, exact signed candidate, no-rebroadcast, identity/backend
  and public pixel recovery assertions remain. Their logs and continuity reports
  are retained here.
- Local host verification passes117 core tests,16 gateway tests and Android debug
  lint with the same final production source. Final focused CI compiles the final
  instrumentation source and repeats the host tests. No test is ignored.

Both final-source review axes have zero remaining findings. The initial spec
review requested the exact reading-position tracer and the own-profile matrix;
both are implemented and pass. Final incremental reviews cover all fixture and
runner corrections. Original PNG/XML/configuration files are retained under each
OS folder. Contact sheets support inspection; full native captures remain the
source of truth. Text evidence normalizes line endings and trailing whitespace;
PNG pixels are unchanged. Solid profile-picture colours are controlled signed fixture
images, not production assets or an initials fallback.

## Failure provenance

The [before-change run](https://github.com/robelene112/redsquare-android/actions/runs/41861779920)
at `5a7e534a1` fails the required missing-divider assertion. Its instrumentation
log is retained as `before-boundary-ui.txt`. The application was unchanged from
the review base when this tracer was authored and dispatched.

GitHub's November4 Actions incident delayed status updates: some apparently
queued jobs had already executed. The workflow now optionally selects the other
standard Ubuntu22.04 x64 runner, leaving24.04 as default and preserving the Android
images and bounds. An exact optional source reference and retained source,
workflow and runner files make before-change verification explicit. The first
22.04 attempts41865714294/41865719249 stopped at host-library setup, before native
checks; selecting libraries by the actual host release corrected that setup.

Run41863900570 at `fd484f868` exposed a test that observed history tabs before
scrolling them into view. Runs41866417351/41866423111 at `0de31cb05` exposed an
old same-colour picture satisfying a new biography's readiness check, and a
merged post-card tap whose centre was below the visible viewport. Runs41867927882/
41867935701 at `927d5dbc3` exposed the alignment test counting module-navigation
tabs as profile-history tabs. Final tests wait for the exact biography and image,
match only Posts/Replies/Likes, and physically tap visible body text. All four
class outcomes are required even when an earlier class fails. No assertion,
viewport, deadline, recovery or original acceptance goal was relaxed.

These controlled native checks do not prove physical Pixel performance, public
provider delivery, global follower availability or store acceptance. They do not
replace94's final consistent-source full application/update verification. No
intermediate beta was published.

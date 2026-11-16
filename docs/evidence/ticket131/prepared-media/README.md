# First positive media frame, focused qualification

Product source: `03d80ff15ef41548b3e517e171640325db1805f5`.
The original loaded-media and retry tests are byte-identical to a594. The result
reveal retains the old inert layer while the new layer draws at0.01 opacity and
passes the existing hardware submission boundary, then starts the unchanged180ms
linear overlap. The latest target owns controls immediately. Keyed content is
retained through preparation/fading; drawable lifecycle effects remain with the
caller. Zero system motion presents only the latest result without interpolation.
Rapid revisits restore an unprepared layer's positive opacity before awaiting it.

Both source reviews pass with zero remaining findings. All four complete focused
parents succeed: loaded media10/17 runs43957716249/43957720646, retry10/17
runs43957725934/43957732330. Both independent focused evidence reviews pass with zero findings;
see `reviews.json`. This is not complete-app, signed-update or public-delivery acceptance.

Both banner and drawable recordings on both SDKs contain nine qualifying original
intermediate frames at motion1. All four motion0 recordings contain zero.
Original media delivery deadlines, final pixel/geometry assertions, motion scale
observations and recorder decoding goals remain. The retry recordings retain the
original rapid physical taps and two HTTP requests at both scales/SDKs. No fake
clock, duration/threshold change, dropped decoded frame or input retry was added.

`inventory.json` and per-run run/artifact metadata bind all original ZIPs. Each
raw file and native TAR member has an exact byte/hash index. The selected raw
instrumentation logs, original native JSON, XML and non-frame PNGs are copied
without transformation. Original MP4s and every decoded PNG remain in the exact
ZIP/TAR archives in the external integrated131 cache, indexed here, rather than
being duplicated in Git. Across the four parents:298 raw files,261 TAR members,
169 PNGs,20 XML and12 MP4s.

Earlier full17 run43939468380 remains failed and retained. Its banner recording
had only one qualifying intermediate of six original samples. The observational
probe43952067644 recorded expensive initial rendering, but neither the probe nor
these passes establishes the exact original failure cause. Probe instrumentation
was removed before this product correction and these four runs. Physical/OEM and
whole-app qualification remain separate.

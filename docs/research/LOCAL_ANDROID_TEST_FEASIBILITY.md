# Local Android test feasibility

Read-only investigation, 2026-10-09. Only this note was created. No packages,
images, containers, builds, services or test runs were started; no private
wallet/signing files or container environments were inspected.

## Recommendation

Retain hosted KVM for repeated Android UI tests and final Android 10/17
acceptance. A persistent software emulator on this production VPS is not a
supported, measured speed improvement. Available memory does not justify
running it concurrently with the existing local build. Keeping an emulator
alive could remove repeated boot work, but does not remove software execution
and rendering costs; no local timing was measured.

The existing [CI timing investigation](ANDROID_CI_TIMING.md) records focused
posting jobs at 5m07s–5m23s and full jobs at 26m23s–28m49s. Hosted runner setup
and source compilation account for part of that delay. Its proposal to build
once and run the two SDKs on separate hosted KVM runners addresses measured
repeat work; that proposal remains unimplemented and its savings unverified.

## Host and existing tooling

Read-only `nproc`, `free -m` and `/dev/kvm` existence checks found four CPUs,
7,947 MiB RAM, 3,400 MiB used, 4,546 MiB available, and 2,377 MiB of 4,095 MiB
swap used. `/dev/kvm` is absent. These are momentary observations, not peak
capacity or evidence of ongoing swap activity. [Operations](../OPERATIONS.md)
identifies this as the live netcup production VPS.

- [Build Dockerfile](../../build-support/Dockerfile) installs platform/build
  tools, but no emulator or system image.
- [Emulator Dockerfile](../../build-support/Emulator.Dockerfile) installs the
  emulator and Android 10/API29 plus Android17/API37 x86_64 images. Read-only
  `docker image ls` confirms the `redsquare-android-emulator:api29-api37` tag
  exists. Installed binary version and package contents were not verified.
- [Local build](../../scripts/android-build) permits two CPUs and 2,500 MiB
  memory. [Local emulator wrapper](../../scripts/android-emulator) permits two
  CPUs and 3 GiB, disables container networking, and persists AVD files under
  `.scratch/android-devices`. These are resource ceilings, not measured usage.
  Their combined memory ceilings exceed observed available RAM by about
  1 GiB; their CPU ceilings can consume all four host CPUs.
- [Harness](../../scripts/emulator-check) uses one guest core/1,536 MiB without
  KVM, software graphics, no snapshots, a roughly ten-minute boot deadline,
  and kills the emulator when the run finishes. Persistent AVD files therefore
  do not provide a persistent running device. Hosted KVM uses two guest
  cores/2,048 MiB. [CI](../../.github/workflows/android-proof.yml) requires KVM.

## Official limitations

Current Android documentation says `-accel off` uses unsupported, very slow
software emulation and requires `ANDROID_I_WANT_MY_TCG=yes`; without it the
emulator refuses to start. The local harness does not set that opt-in. Whether
the already installed binary enforces it remains unknown. The same document
specifies guest RAM between 1,536 and 8,192 MB, rather than total emulator host
memory. [Official command-line reference](https://developer.android.com/studio/run/emulator-commandline).

Without VM acceleration, guest machine code is translated block by block.
Linux acceleration uses KVM. [Official acceleration guide](https://developer.android.com/studio/run/emulator-acceleration).

Snapshots are unreliable with software rendering; saving/loading them is
memory intensive and can trigger swapping. They are not a dependable shortcut
for this configuration. [Official snapshot guide](https://developer.android.com/studio/run/emulator-snapshots).

## Experiment decision

Do not run a persistent-emulator experiment on this live host under the present
evidence. Production peak resource demand and emulator host-memory/CPU costs
are unknown, and unsupported execution adds a separate reliability question.
Reconsider on an isolated nonproduction machine, or after verified KVM and
dedicated capacity become available. No workflow or emulator changes are
proposed here. Existing focused hosted runs remain appropriate during
development; successful full hosted acceptance remains required for closure.

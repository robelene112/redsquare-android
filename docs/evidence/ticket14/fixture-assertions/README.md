# Image fixture assertion evidence

Public synthetic emulator data only. These are failed test checkpoints, not final
acceptance or production-defect proofs.

- `fe1be79`, Android10 run38067303565: cold candidate restores image and pending
  status, but a disabled text field lacks SetText semantics. Exact text plus
  disabled-state assertion replaces that selector. The same failure occurs on
  Android17 run38067305720. Screenshot captures the scrolled image; the text
  assertion is independently recorded in the result log.
- Same runs: backend queue image check matches two Image1of1 nodes from retained
  signed post history. Exact post-card scope preserves history and pixel checks.
- `4808761`, Android17 run38068063232: quote Add images is not displayed immediately
  after scrolling while the keyboard is open. Screenshot records the actual
  panned window. The cause is not uniquely established. `ad4a8c0` dismisses the
  keyboard and waits for displayed navigation before requiring the external
  picker intent and signed image quote.

Android10 focused4808761 passes20 completed cases and3 actual process-death
stages. Android17 passes19/20 and3 death stages. Both fullad4a8c0 acceptance runs
remain pending when this record is written. See the current verification report
for their final outcomes.

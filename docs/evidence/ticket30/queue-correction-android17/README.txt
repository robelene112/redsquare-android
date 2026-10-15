Run38737085717 exactdc28f780 FAILURE13reported/8PASS/5FAIL307.115s.
Five private cases still wait for Queued after a physical Send tap. Root cancel
PNG inspected/all5XML parsed: keyboard open, shifted/panned app, no Queued.
No evidence that these taps invoked Send; no signed original continuity inferred.
Fixturebdd07ed0 closes keyboard and positively asserts ready Send before tap,
matching existing group fixture. No timing or actual delivery assertions relaxed.

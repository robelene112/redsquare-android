FullAndroid17run38679332827 FAILED at exacte3edf1c971c25f49d5b793d98cfb2de85dd70aac.
Core/release/lint build step passed. The13queue cases report8PASS/5FAIL276.461s.
Seven private cases and group failed-save/repair/no-wire pass. All five other
group cases time out at131 waiting20s for first Queued after the initial Send tap.
Their later queue/recreate/cancel/resume/scope/removal/Archive tails never execute.
Root inspected original group-cancel failure: keyboard remains open and app
content/window geometry is shifted; no Queued observed. This supports a readiness/
coordinate hypothesis, not proof of a specific tap or app failure. No whole17 or
new app-fault conclusion is inferred. Current Android10full is still active.
Correction matches established group fixtures: close keyboard after initial text,
then require a displayed/enabled clickable Send under the existing20s bound.
Shared storage action also closes keyboard and positively observes its Send before
atomically activating the protected fault. All behavioral assertions remain.
No application change or increased deadline. Full queue checks now run immediately
after appearance, before older Chat cases, to fail early; all gates remain included.
Public unsafe fixtures only. No protected installation snapshots pulled.

# Group conversation mute control RED, Android10

Run38694777442 exact4aa36fb1d294c14375b7c2a3847941839f24bebe,
3cases/2PASS/1FAIL36.041s. Group conversation and Archive/composer are ready;
exact draft is entered, then line209 waits20s for the missing mute control.
Original PNG/XML show the correct group, privacy guidance and preserved draft.
Group dialog/save/incoming/recreation/unmute/no-wire tail is unexecuted.
Both actual private-alert/control cases pass in the same run.

Reviewed fixturee2727e09 fixes later recreation navigation and shares scoped
cleanup without changing the missing-control criterion. The minimum adds the
existing shared mute button to the selected group header and admits valid Saito
public-key group IDs alongside private conversation hashes. No group alerts or
group recreation/draft acceptance inferred. Root original group failure seen,
all5XML parsed. Public unsafe fixture, no production message, restoration intact.

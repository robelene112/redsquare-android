# Initial report save-failure GREEN

Android10 run38172025405 atb867022 passes4cases32.465s. Initial OS-save
obstruction sends no report/broadcast, displays the named save error and allows
explicit retry only after repair. The resulting signed report retains the
original identity/self-recipient/target. Other3cases pass. Root individually
viewed2affected originals and separately parsed2XMLs: full save error then
acknowledgement are visible. Both bounded source review axes0findings.

Next single test removes writing permissions at the OS filesystem seam only
after receiving the original report, retaining the public candidate bytes. Both
legacy base-file and newer temporary-file writing are blocked; exact modes are
restored in finally. It requires acknowledgement plus a truthful persistence
warning, then unknown after reopening and explicit identical retry after repair.
It is guarded to isolated .dev emulator only. No production chmod, wallet data
mutation, automatic resend, moderator/chain or full-acceptance claim.

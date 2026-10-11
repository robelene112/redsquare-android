Android10 run38174505849 at0d59cd3: ordinary7PASS50.598s,
including4a3ebee signer/active-wallet assertions. Process first phase1FAIL8.22s
at59: blocking5s receipt wait failed. Root original PNG and separate XML still
show consent dialog. Deliberate kill/pending checkpoint/cold phase were not
reached, so no process recovery claimed. Reviewed7ab3387 Compose-wait fix
predates result inspection; cause remains a timing hypothesis until corrected
runtime. Product source unchanged; receipt deadline remains5s.

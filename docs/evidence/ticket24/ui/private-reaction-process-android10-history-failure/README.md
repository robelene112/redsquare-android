# History visibility failure before reaction process checks, Android10

Run38471567896, source9111e668. Core86; history24 reports23PASS/one failure110.529s. The key-only-import case finds the retained original semantics but its direct assertIsDisplayed fails atFixture803. Root inspected the solefailure originalPNG/XML: currentownsent message is visible, while the first retained original is offscreen. No conclusion that cached content was deleted. Action and new process checks were skipped.

Subsequent1e5dddce adds explicit public-list scroll to the exact noneditable retained body, then keeps strict display/cache/key-recovery/no-extra-send assertions. Corrected build/run pending. The source of the prior visibility state is not independently proven.

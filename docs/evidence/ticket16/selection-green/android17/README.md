# Native selection and draft-animation checkpoint

Run39007775320 at40588d7091cb7b2ef2cd89d6fe3f41a8418a6b3b SUCCESS:
2cases/0failures/29.539seconds. Earlier recovery still passes. New UI flow
checks a distinct static thumbnail, selection, two literal displayed frame colors,
fresh exact original URL/query requests after selection and saved-draft/activity
recreation, removal preserving text and zero Saito writes. Localbuild13621
PASS3m36s (coreUP-TO-DATE), source reviews Standards0/Spec0. Separate
sourceCI39007775786 was pending at collection. No live provider or Pixel proof.

Thirteen originals hashed. Root viewed all four PNGs and parsed all four XMLs.
Pixel checks establish animation, but actual screenshots reveal image overdraw:
search thumbnail covers part of its title; restored animation covers lower text
field and extends below its declared image bounds. For restored image XML
bounds[12,433][308,613], square296pixel CENTER_CROP overhang is58px each side;
PNG starts at375. Therefore this checkpoint does NOT accept layout correctness.
The earlier test did not check pixels outside the image. A new boundary assertion
must fail on current rendering before its correction, preserving animation,
restoration and originalURL assertions. No cause inferred from status alone.

Issue16 stays open: clipping, retained selected credits, signed compatibility,
Android10 and live authorized provider remain. Beta24 unchanged.

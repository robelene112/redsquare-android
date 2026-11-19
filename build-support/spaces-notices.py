#!/usr/bin/env python3
"""Regenerate bundled notices from the exact Cargo metadata and upstream texts."""
import argparse
import hashlib
import json
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("metadata", type=Path, help="cargo metadata --locked --format-version1 --filter-platform x86_64-linux-android")
args = parser.parse_args()
metadata = json.loads(args.metadata.read_text())
source_dir = repo / "build-support/spaces-licenses"
sources = json.loads((source_dir / "sources.json").read_text())
for name, record in sources.items():
    assert hashlib.sha256((source_dir / name).read_bytes()).hexdigest() == record["sha256"]
texts = {}
lines = ["Cargo.lock SHA256: " + hashlib.sha256((repo / "spaces-mls/Cargo.lock").read_bytes()).hexdigest(),
         "Saito Spaces native MLS dependencies. Original upstream source is unmodified.",
         "Package-specific licenses and attribution follow. These are dependency notices.", ""]
for package in sorted(metadata["packages"], key=lambda p: (p["name"], p["version"])):
    if not package["source"]:
        continue
    root = Path(package["manifest_path"]).parent
    files = sorted(p for p in root.iterdir() if p.is_file() and p.name.upper().startswith(
        ("LICENSE", "LICENCE", "COPYING", "NOTICE", "COPYRIGHT")))
    if not files:
        name = package["name"]
        if name.startswith("openmls"):
            fallback = ["OpenMLS-MIT.txt"]
        elif name.startswith("hpke-rs"):
            fallback = ["MPL-2.0.txt", "libcrux-MIT.txt"]
        elif name.startswith("libcrux"):
            fallback = ["libcrux-LICENSE.txt", "libcrux-MIT.txt"]
        elif name.startswith("hax-"):
            fallback = ["hax-APACHE.txt"]
        elif name == "jni-sys-macros":
            fallback = ["jni-sys-APACHE.txt", "jni-sys-MIT.txt"]
        else:
            raise RuntimeError("Upstream license text required for " + name)
        files = [source_dir / filename for filename in fallback]
    lines += [package["name"] + " " + package["version"], "Declared license: " + (package["license"] or "see upstream"),
              "Authors: " + ", ".join(package["authors"]), "Repository: " + (package["repository"] or "see source"),
              "Original source: https://crates.io/api/v1/crates/" + package["name"] + "/" + package["version"] + "/download"]
    for path in files:
        text = path.read_text()
        digest = hashlib.sha256(text.encode()).hexdigest()
        texts[digest] = text
        lines.append("License/notice text: " + digest)
    lines.append("")
lines += ["License and attribution texts, deduplicated only when exactly identical", ""]
for digest, text in sorted(texts.items()):
    lines += ["TEXT " + digest, text, ""]
output = repo / "app/src/main/assets/licenses/spaces-mls.txt"
output.write_text("\n".join(lines))
print("Bundled", len(metadata["packages"]) - 1, "dependency entries and", len(texts), "distinct upstream texts")

"""Additive rendered-counter, original-retention and exact installed-input receipts.

Called by the complete-source assessor after its inherited native, process,
archive and public-read checks. Image generation is not visual approval.
"""
import base64
import hashlib
import json
from pathlib import Path
import re


def verify_installed_inputs(raw, sdk):
    raw = Path(raw)
    result = {}
    for part in ("chat", "social", "wallet"):
        receipt = raw / ("android-native-proof-" + part) / ".scratch/device-evidence" / (
            "api-29" if sdk == "10" else "api-37.0") / "built-apk-sha256.txt"
        lines = receipt.read_text().splitlines()
        assert len(lines) == 2
        values = {}
        for line in lines:
            match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
            assert match
            name = Path(match[2]).name
            assert name not in values
            values[name] = match[1]
        assert set(values) == {"app-debug.apk", "app-debug-androidTest.apk"}
        result[part] = values

    return result


def verify_counter_journeys(folder):
    folder = Path(folder)
    result = {}
    journeys = {
        "feed-0": {"before": 0, "held-1": 1, "held-2": 2, "held-3": 3,
                   "received": 3, "conversation": 3, "returned-feed": 3},
        "feed-missing": {"held-1": 1, "held-2": 2, "held-3": 3,
                         "hydrated": 7, "stale-total": 7, "next-tap": 8},
        "reply-missing": {"held-1": 1, "held-2": 2, "held-3": 3,
                          "hydrated": 7, "stale-total": 7, "reopened": 7, "next-tap": 8},
    }
    all_frames = set()
    for journey, stages in journeys.items():
        targets = set()
        for stage, expected in stages.items():
            observed = json.loads((folder / (journey + "-" + stage + "-layout.json")).read_text())
            assert observed["text"] == str(expected)
            targets.add(observed["target"])
            image = folder / (journey + "-" + stage + ".png")
            assert image.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
            if stage.startswith("held-"):
                assert observed["received"] == 0 and observed["heldHandshakes"] > 0
        assert len(targets) == 1 and re.fullmatch(r"[0-9a-f]{128}", next(iter(targets)))
        frames = sorted(folder.glob(journey + "-signed-*.base64"))
        assert len(frames) == (3 if journey == "feed-0" else 4)
        hashes = []
        for frame in frames:
            wire = base64.b64decode(frame.read_bytes(), validate=True)
            assert len(wire) > 128
            digest = hashlib.sha256(wire).hexdigest()
            assert digest not in all_frames
            all_frames.add(digest)
            hashes.append(digest)
        result[journey] = dict(target=next(iter(targets)),
                                                  stages=stages, signedFrameSha256=hashes)
    assert len(all_frames) == 11

    return result


def verify_original_retention(root):
    root = Path(root)
    movies = []
    quotes = root / "quote-evidence"
    held = json.loads((quotes / "recycled-original-observations.json").read_text())
    assert len(held) == 3 and all(row["originalVisibleWhileCacheHeld"] for row in held)
    background = json.loads((quotes / "background-original-observations.json").read_text())
    assert background["publicationCount"] == 0 and background["confirmedTargetReads"] >= 2
    motion = root / "quote-motion-evidence"
    for scale in (1, 0):
        window = json.loads((motion / ("scale-" + str(scale) + "-window.json")).read_text())
        assert window == dict(isRecomposer=True, windowMotionScale=float(scale), systemMotionScale=float(scale))
        heights, offsets = set(), set()
        for round_number in range(3):
            stem = "scale-" + str(scale) + "-round-" + str(round_number)
            observed = json.loads((motion / (stem + ".json")).read_text())
            frames = observed["decodedOriginalFrames"]
            assert observed["publicationCount"] == 0 and observed["checkpointFrames"] > 0
            assert len(frames) == observed["frameCount"] and len(frames) >= 2
            assert [row["index"] for row in frames] == list(range(len(frames)))
            assert all(a["presentationTimeUs"] < b["presentationTimeUs"] for a, b in zip(frames, frames[1:]))
            assert frames[0]["finalCheckpointPixels"] < 16
            assert any(row["finalCheckpointPixels"] >= 16 for row in frames)
            heights.add(observed["height"]); offsets.add(observed["relativeOriginalTop"])
            hashes = []
            for row in frames:
                assert Path(row["file"]).name == row["file"]
                data = (motion / row["file"]).read_bytes()
                assert data.startswith(b"\x89PNG\r\n\x1a\n")
                hashes.append(dict(file=row["file"], sha256=hashlib.sha256(data).hexdigest()))
            movie = motion / (stem + ".mp4")
            assert movie.stat().st_size > 0
            movies.append(dict(movie=movie.name, sha256=hashlib.sha256(movie.read_bytes()).hexdigest(),
                                               frames=hashes, height=observed["height"], offset=observed["relativeOriginalTop"]))
        assert len(heights) == len(offsets) == 1 and next(iter(heights)) > 0
    return movies


def verify_prerequisites(view, raw, sdk):
    root = Path(view) / "social/status-originals"
    return dict(installedInputs=verify_installed_inputs(raw, sdk),
                counterJourneys=verify_counter_journeys(root / "like-count-evidence"),
                quoteMovies=verify_original_retention(root),
                visualInspection="Separate actual-image review required; this validator does not approve images.")

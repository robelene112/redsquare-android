#!/usr/bin/env python3
"""Build the pinned MLS library without altering a system Rust/Android install."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
from urllib.request import urlopen
import zipfile

REPO = Path(__file__).resolve().parents[1]
PINS = json.loads((REPO / "build-support/spaces-toolchain.json").read_text())
TARGETS = {
    "arm64-v8a": ("aarch64-linux-android", "aarch64-linux-android"),
    "armeabi-v7a": ("armv7-linux-androideabi", "armv7a-linux-androideabi"),
    "x86": ("i686-linux-android", "i686-linux-android"),
    "x86_64": ("x86_64-linux-android", "x86_64-linux-android"),
}


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        while block := source.read(1024 * 1024):
            checksum.update(block)
    return checksum.hexdigest()


def download(cache, record):
    path = cache / Path(record["url"]).name
    if not path.exists() or digest(path) != record["sha256"]:
        temporary = path.with_suffix(path.suffix + ".partial")
        with urlopen(record["url"], timeout=180) as source, temporary.open("wb") as target:
            shutil.copyfileobj(source, target)
        if digest(temporary) != record["sha256"]:
            raise RuntimeError("Pinned compiler archive checksum differs")
        temporary.replace(path)
    return path


def rust_component(cache, record):
    marker = cache / ".components" / (Path(record["url"]).name + ".complete")
    component = record.get("component", "rust-std")
    target = record.get("target", "x86_64-unknown-linux-gnu")
    installed = cache / "rust/bin" / component if component != "rust-std" else cache / "rust/lib/rustlib" / target / "lib"
    if marker.exists() and marker.read_text() == record["sha256"] and installed.exists():
        return
    # A compiler binary alone does not mean cargo and its standard libraries
    # finished installing. Record each component only after its installer exits.
    marker.unlink(missing_ok=True)
    archive = download(cache, record)
    with tempfile.TemporaryDirectory(dir=cache, prefix="rust-install-") as temporary:
        with tarfile.open(archive) as contents:
            contents.extractall(temporary, filter="data")
        folders = list(Path(temporary).iterdir())
        assert len(folders) == 1 and (folders[0] / "install.sh").is_file()
        subprocess.run(["bash", str(folders[0] / "install.sh"),
                        "--prefix=" + str(cache / "rust"), "--disable-ldconfig"], check=True)
    if not installed.exists():
        raise RuntimeError("Rust component installer omitted its required output")
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(record["sha256"])
    archive.unlink()  # Receipt pins permit a download again; installed tools remain cached.


def prepare_tools(cache, targets):
    rustc = cache / "rust/bin/rustc"
    for record in PINS["host"]:
        rust_component(cache, record)
    version = subprocess.check_output([str(rustc), "--version"], text=True).strip()
    if not version.startswith("rustc " + PINS["rust"] + " "):
        raise RuntimeError("MLS compiler version differs from its pin")
    for target in targets:
        rust_component(cache, next(r for r in PINS["androidStd"] if r["target"] == target))
    ndk_marker = cache / ".components/android-ndk-r30.complete"
    ndk_ready = ndk_marker.exists() and ndk_marker.read_text() == PINS["ndk"]["sha256"]
    if targets and (not ndk_ready or not (cache / "android-ndk-r30/source.properties").exists()):
        ndk_marker.unlink(missing_ok=True)
        archive = download(cache, PINS["ndk"])
        with zipfile.ZipFile(archive) as contents:
            assert contents.testzip() is None
            for member in contents.infolist():
                relative = Path(member.filename)
                if relative.is_absolute() or ".." in relative.parts:
                    raise RuntimeError("Unexpected NDK archive path")
                path = cache / relative
                if member.is_dir():
                    path.mkdir(parents=True, exist_ok=True)
                    continue
                path.parent.mkdir(parents=True, exist_ok=True)
                mode = member.external_attr >> 16
                if mode & 0o170000 == 0o120000:
                    link = contents.read(member).decode()
                    if Path(link).is_absolute() or not (path.parent / link).resolve().is_relative_to(cache):
                        raise RuntimeError("Unexpected NDK link")
                    if not path.is_symlink():
                        path.symlink_to(link)
                else:
                    with contents.open(member) as source, path.open("wb") as target:
                        shutil.copyfileobj(source, target)
                    if mode:
                        path.chmod(mode & 0o777)
        ndk_marker.parent.mkdir(parents=True, exist_ok=True)
        ndk_marker.write_text(PINS["ndk"]["sha256"])
        archive.unlink()
    if targets:
        properties = (cache / "android-ndk-r30/source.properties").read_text()
        if "Pkg.Revision = " + PINS["ndk"]["revision"] not in properties:
            raise RuntimeError("MLS Android compiler revision differs from its pin")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "spaces-tools")
    parser.add_argument("--target", choices=["host", "android", *TARGETS], required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    notices = REPO / "app/src/main/assets/licenses/spaces-mls.txt"
    expected_lock = "Cargo.lock SHA256: " + digest(REPO / "spaces-mls/Cargo.lock")
    if notices.read_text().splitlines()[0] != expected_lock:
        raise RuntimeError("Regenerate the bundled native dependency notices for this Cargo.lock")
    cache = args.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    abis = list(TARGETS) if args.target == "android" else ([] if args.target == "host" else [args.target])
    with (cache / ".build.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        prepare_tools(cache, [TARGETS[abi][0] for abi in abis])
        environment = dict(os.environ, PATH=str(cache / "rust/bin") + os.pathsep + os.environ["PATH"],
                           CARGO_HOME=str(cache / "cargo"), CARGO_TARGET_DIR=str(cache / "target"), CARGO_BUILD_JOBS="2")
        command = [str(cache / "rust/bin/cargo"), "build", "--locked", "--manifest-path", str(REPO / "spaces-mls/Cargo.toml"), "--release"]
        outputs = []
        for abi in abis or ["host"]:
            current = dict(environment)
            if abi != "host":
                target, compiler = TARGETS[abi]
                ndk = cache / "android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin"
                current["CARGO_TARGET_" + target.upper().replace("-", "_") + "_LINKER"] = str(ndk / (compiler + "29-clang"))
                current["RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384"
                subprocess.run(command + ["--target", target], check=True, env=current)
                source = cache / "target" / target / "release/libsaito_spaces_mls.so"
                # Inspect the actual ELF, rather than infer page size from flags.
                headers = subprocess.check_output([str(ndk / "llvm-readelf"), "-lW", str(source)], text=True)
                loads = [line for line in headers.splitlines() if line.strip().startswith("LOAD")]
                if not loads or any(int(line.split()[-1], 16) < 16384 for line in loads):
                    raise RuntimeError("MLS native library does not have16KiB alignment")
                destination = args.output / abi / source.name
            else:
                subprocess.run(command, check=True, env=current)
                args.output.mkdir(parents=True, exist_ok=True)
                contract_log = args.output / "community-contracts.txt"
                with contract_log.open("w") as log:
                    contracts = subprocess.run([str(cache / "rust/bin/cargo"), "test", "--locked",
                        "--manifest-path", str(REPO / "spaces-mls/Cargo.toml"), "--release",
                        "--test", "community"], env=current, stdout=log, stderr=subprocess.STDOUT)
                print(contract_log.read_text(), end="", flush=True)
                contracts.check_returncode()
                source = cache / "target/release/libsaito_spaces_mls.so"
                destination = args.output / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            outputs.append(dict(abi=abi, file=str(destination.resolve()), sha256=digest(destination), bytes=destination.stat().st_size))
        inputs = [REPO / "spaces-mls/Cargo.toml", REPO / "spaces-mls/Cargo.lock", Path(__file__), REPO / "build-support/spaces-toolchain.json"]
        inputs += sorted((REPO / "spaces-mls/src").rglob("*.rs"))
        if not abis:
            inputs += sorted((REPO / "spaces-mls/tests").rglob("*.rs"))
        receipt = dict(version=1, rust=PINS["rust"], ndk=PINS["ndk"]["revision"],
                       inputs={p.relative_to(REPO).as_posix(): digest(p) for p in inputs}, outputs=outputs)
        if not abis:
            receipt["rustContracts"] = dict(file=contract_log.name, sha256=digest(contract_log), exit=contracts.returncode)
        (args.output / "build-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")


if __name__ == "__main__":
    main()

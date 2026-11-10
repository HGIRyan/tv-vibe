#!/usr/bin/env python3
"""Fetch the official Vosk model, verify pinned digest, and stage it as APK assets."""
import argparse
import hashlib
from pathlib import Path
import shutil
import tempfile
import urllib.request
import zipfile

URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
ROOT = "vosk-model-small-en-us-0.15/"
DEST = Path(__file__).resolve().parents[1] / "launcher/assistant/src/main/assets/wake-model"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="Reuse an already downloaded official model zip")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="hey-tv-model-") as temporary:
        archive = args.archive or Path(temporary) / "model.zip"
        if args.archive is None:
            with urllib.request.urlopen(URL, timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
            raise SystemExit("Model checksum mismatch; no assets changed")
        staged = Path(temporary) / "staged"
        staged.mkdir()
        with zipfile.ZipFile(archive) as bundle:
            for info in bundle.infolist():
                if not info.filename.startswith(ROOT):
                    raise SystemExit("Unexpected model archive root")
                relative = Path(info.filename[len(ROOT):])
                if relative.is_absolute() or ".." in relative.parts:
                    raise SystemExit("Unsafe model archive path")
                if info.is_dir():
                    (staged / relative).mkdir(parents=True, exist_ok=True)
                else:
                    target = staged / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with bundle.open(info) as source, target.open("wb") as output:
                        shutil.copyfileobj(source, output)
        if not (staged / "am/final.mdl").is_file():
            raise SystemExit("Model is incomplete")
        DEST.parent.mkdir(parents=True, exist_ok=True)
        if DEST.exists():
            shutil.rmtree(DEST)
        shutil.copytree(staged, DEST)
    print(f"Verified offline model staged at {DEST}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Prepare a private local Google Photos album export and optionally deploy it to the TV."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import uuid
import zipfile

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
DEST = '/sdcard/Android/data/com.gathin.tvhome/files/photos'
EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif'}


def sources(source):
    if source.is_dir():
        for path in sorted(source.rglob('*')):
            if path.is_file() and path.suffix.lower() in EXTENSIONS and not path.is_symlink():
                yield path.name, path.read_bytes()
    elif source.is_file() and source.suffix.lower() in EXTENSIONS:
        yield source.name, source.read_bytes()
    elif zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as archive:
            for item in archive.infolist():
                if not item.is_dir() and Path(item.filename).suffix.lower() in EXTENSIONS:
                    if item.file_size > 100 * 1024 * 1024:
                        raise ValueError('An image exceeds the 100 MB input limit')
                    yield Path(item.filename).name, archive.read(item)
    else:
        raise ValueError('Source must be a photo, album ZIP, or directory of photos')


def prepare(source, output):
    output.mkdir(parents=True, exist_ok=False)
    photos, seen, skipped = [], set(), 0
    for name, data in sources(source):
        if len(photos) >= 1000:
            raise ValueError('Album exceeds 1000 photos; use a smaller export')
        try:
            with Image.open(io.BytesIO(data)) as original:
                image = ImageOps.exif_transpose(original).convert('RGB')
                image.thumbnail((1920, 1080), Image.Resampling.LANCZOS)
                encoded = io.BytesIO()
                # Re-encoding with no supplied metadata strips EXIF/GPS and bakes in orientation.
                image.save(encoded, 'JPEG', quality=88, optimize=True)
            payload = encoded.getvalue()
            digest = hashlib.sha256(payload).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)
            filename = digest + '.jpg'
            (output / filename).write_bytes(payload)
            photos.append(filename)
        except (OSError, ValueError, Image.DecompressionBombError):
            skipped += 1
    if not photos:
        raise ValueError('No readable photos found; existing TV album was not changed')
    (output / 'manifest.json').write_text(json.dumps(photos))
    print(f'Prepared {len(photos)} photos; skipped {skipped} unsupported/corrupt images.')
    if skipped:
        raise ValueError('Some photos could not be converted; export them as JPEG and retry before deploying')
    return photos


def deploy(output, photos):
    if not os.environ.get('TV_HOST'):
        raise ValueError('Set TV_HOST before using --deploy')
    sys.path.insert(0, str(ROOT / 'tools'))
    import tv
    batch = 'album-' + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix='tv-photo-transfer-') as tmp:
        archive = Path(tmp) / 'album.tar'
        with tarfile.open(archive, 'w') as tar:
            for name in photos:
                tar.add(output / name, arcname=batch + '/' + name)
        remote = '/data/local/tmp/' + batch + '.tar'
        subprocess.run([str(ROOT / 'tools/venv/bin/python'), '-I',
                        str(ROOT / 'mytv/b64push-tcl.py'), str(archive), remote], check=True,
                       stdout=subprocess.DEVNULL)
        device = tv.connect(timeout=30)
        try:
            result = device.shell(f'mkdir -p {DEST} && tar -xf {remote} -C {DEST} && echo EXTRACTED', read_timeout_s=120)
            if 'EXTRACTED' not in result:
                raise RuntimeError('Photo extraction failed; current album remains active')
            # Verify every decoded photo survived transfer before atomically switching albums.
            for name in photos:
                want = hashlib.md5((output / name).read_bytes()).hexdigest()
                got = device.shell(f'md5sum {DEST}/{batch}/{name}').split()
                if not got or got[0] != want:
                    raise RuntimeError('Photo checksum mismatch; current album remains active')
            manifest = json.dumps([batch + '/' + name for name in photos])
            encoded = base64.b64encode(manifest.encode()).decode()
            result = device.shell(f"echo '{encoded}' | base64 -d > {DEST}/active.json.tmp && mv {DEST}/active.json.tmp {DEST}/active.json && echo ACTIVATED")
            if 'ACTIVATED' not in result:
                raise RuntimeError('Album activation failed')
            device.shell(f'rm -f {remote}')
            print(device.shell('am force-stop com.gathin.tvhome; am start -W -n com.gathin.tvhome/.MainActivity'))
        finally:
            device.close()
    print(f'Activated {len(photos)} photos. Previous album files remain available for rollback.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, required=True, help='New local output directory (private; do not commit)')
    parser.add_argument('--deploy', action='store_true', help='Transfer, verify, activate, and reopen Home')
    args = parser.parse_args()
    photos = prepare(args.source.expanduser(), args.output.expanduser())
    if args.deploy:
        deploy(args.output.expanduser(), photos)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError) as error:
        sys.exit(str(error))

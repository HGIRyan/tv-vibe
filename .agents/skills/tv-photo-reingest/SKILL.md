---
name: tv-photo-reingest
description: Refresh this fork's TV launcher photo backgrounds from a downloaded Google Photos album ZIP, single photo, or local photo folder when the user asks to reingest, refresh, or update the launcher photos.
---

Use the photo the user supplies, or a local export of the same Google Photos album the user uses for the TV screensaver. A single photo stays static. This is a snapshot: new Google Photos additions require another export. Do not scrape the screensaver's private storage or treat screenshots as album originals.

Work from the repo root. Ask for the export location when it is missing; do not substitute unrelated photos from Downloads. Google Photos album menu → Download all produces a usable ZIP; a supplied JPEG can be ingested directly. Photos are private: keep originals and prepared files out of Git and do not include family photos in reports unless requested.

The launcher reads `/sdcard/Android/data/com.gathin.tvhome/files/photos/active.json`, a JSON array of paths relative to `photos/`. It rotates every 30 seconds while Home is visible, with a dark overlay and crossfades. The normal background remains when no usable album exists. This does not change Google Ambient Mode or its album selection.

Use `mytv/ingest-photos.py` with Python that has Pillow. The repo's ADB client also needs `adb_shell` and `rsa`; use `tools/venv/bin/python` if these and Pillow are installed there, otherwise use the available Python with these libraries. Do not read or print `.secrets`.

Example from repo root (replace SOURCE and choose a new dated output directory):

```bash
TV_HOST=YOUR_TV_IP tools/venv/bin/python mytv/ingest-photos.py "/path/to/SOURCE.zip" --output "mytv/photo-cache/album-YYYYMMDD-HHMMSS" --deploy
```

Without `--deploy`, conversion runs locally. The helper handles ZIP entries without extracting original paths, applies EXIF orientation, removes metadata, fits to 1920×1080, deduplicates, and rejects an empty or partially unreadable export before deployment. HEIC needs a decoder; if unavailable, request JPEG exports rather than silently omitting photos. The output directory must not already exist.

Deployment uploads a checksummed TAR through the TCL-specific `mytv/b64push-tcl.py`, verifies each photo, and atomically replaces the manifest only after transfer succeeds. It reopens Home, which interrupts the current TV app; a request to reingest authorizes that refresh. Use the current TV address if it has changed. On failure leave the previous active manifest intact, report the error, and retry only after correcting its cause. Previous album directories are retained; do not delete them or source exports without a cleanup request.

The launcher must be version 2.4 or later. See SETUP.md for installation on a new device. Do not start the optional Hey TV microphone service as part of photo refresh.

After ingestion verify the selected manifest, a real Home screenshot, and a second screenshot after at least 30 seconds when there are multiple photos. Confirm readable text, six columns, Apple TV/HBO in the first row, and photo rotation. Report count and any limitations; do not claim continuous cloud sync. For rollback, restore the previous generation's file list into `active.json` atomically and restart only the launcher.

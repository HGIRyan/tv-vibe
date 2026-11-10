# TV Vibe

A personal, extensible fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) for Android TV and Google TV. The `tv-vibe` branch is the working customized launcher; `main` preserves upstream's branch. Original MIT license and attribution are retained.

## What's different

- Six columns, with YouTube, Netflix, Prime Video, Disney+, Apple TV and HBO Max in the first row. Missing apps are skipped.
- Photo backgrounds: use one picture as a static wallpaper or rotate a local album export, with a dark overlay for readable text. No Google Photos live-sync or personal pictures are included.
- Recent-app switcher: OK opens an app. A separate **Close apps…** mode retains cleanup controls.
- TCL remote mappings and a recent-app drawer on the cog button, when that remote sends `KEYCODE_NOTIFICATION`. Other remotes need their own mapping; installing on Google TV does not guarantee the same buttons.
- Optional experimental Hey TV voice-assistant source in a separate module. It is **not installed or listening** as part of launcher setup and requires your own credentials and speech-model download.

Tested on a TCL 75QM6K running Android TV 14. App switching was confirmed with the physical remote. Other devices, including Google TV dongles, need validation. Apps control whether switching resumes an existing screen or reopens their start page.

## Install on your own TV or dongle

**[Download the launcher APK](https://github.com/HGIRyan/tv-vibe/releases/tag/v2.5)**, then start with **[SETUP.md](SETUP.md)**. It covers identifying a Chromecast/Google TV/onn. device, building, installing without disabling the stock launcher, optional remote access, photos, and rollback. Classic Chromecast devices without Android/Google TV cannot run this Android launcher. Fire TV, Roku, Samsung Tizen and LG webOS are not the Google TV install path.

This fork is based on upstream launcher 2.2 plus the customizations above. Newer upstream changes have not been merged or tested here. The original project's instructions remain in [README-UPSTREAM.md](README-UPSTREAM.md).

## Extend it and send updates back

Please add your setup as an **extension**: a new theme in `themes/<your-name>/`, a device profile/notes folder in `examples/<device>/`, or an optional feature with its own default-off setting. Avoid replacing another person's defaults to suit a single device.

Code changes and bug fixes are welcome too; there is no plugin framework or rule against modifying code. Keep changes focused, preserve existing behavior for other devices, and submit pull requests to **HGIRyan/tv-vibe → tv-vibe**. See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch workflow and test expectations.

## Source and privacy

No API keys, ADB pairing keys, local device dumps, family photos, downloaded speech models, or APK signing keys are included. Store personal photos in ignored `mytv/photo-cache/`, and secrets in ignored `.secrets/`. Photo-refresh instructions for coding assistants are in [.agents/skills/tv-photo-reingest/SKILL.md](.agents/skills/tv-photo-reingest/SKILL.md).

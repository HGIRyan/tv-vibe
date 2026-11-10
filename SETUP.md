# Set up a Google TV dongle or Android TV

These steps install the launcher, not a debloat list. Remote key codes and Home selection vary by model. First identify the device; do not copy TCL-specific settings onto an unknown dongle.

## Already built: download the APK

The [v2.5 release](https://github.com/HGIRyan/tv-vibe/releases/tag/v2.5) contains `tv-vibe-2.5.apk` and its SHA-256 checksum. The APK has no personal photos, API keys, or voice service. It uses the same launcher package ID as upstream, so a differently signed existing installation may need a migration before Android accepts it.

For this route you need Android platform-tools, a successfully paired TV connection, and the matching Android/Google TV device; you do not need Java or the SDK build tools. Identify and pair the dongle as described below, then:

```bash
adb install -r tv-vibe-2.5.apk
adb shell appops set com.gathin.tvhome GET_USAGE_STATS allow
adb shell am start -W -n com.gathin.tvhome/.MainActivity
```

Continue with Home selection, remote compatibility and rollback below. The source-build route follows for making your own extensions.

## 1. Identify and connect

On the dongle: Settings → System → About. Record the model and Android/Google TV version. Enable Developer options by selecting the build number seven times, then enable the available USB/network/wireless debugging option. Some devices use Android wireless-debugging pairing instead of TCP port 5555; use the pairing method shown on that device.

On your computer, install Python 3, Git, JDK 17, Android platform-tools, and an Android SDK with platform 34 and build-tools 34.0.0. Point `JAVA_HOME` and `ANDROID_HOME` at your installations. On Windows, use Git Bash for the shell commands, or standard Android `adb` for manual installation.

```bash
git clone --branch tv-vibe https://github.com/HGIRyan/tv-vibe.git
cd tv-vibe
python3 -m venv tools/venv
tools/venv/bin/python -m pip install -r tools/requirements.txt
```

Authorize the computer on the TV. For a device exposing ADB TCP at port 5555:

```bash
export TV_HOST=192.168.1.123  # replace with YOUR TV/dongle's address
tools/venv/bin/python -I tools/tv.py --probe
```

If that does not connect, resolve device pairing first using `adb pair`/`adb connect` and the ports displayed by the device. Do not assume every Google TV dongle exposes port 5555. The Python helper defaults to 5555 and reuses an authorized local ADB key where possible; the manual `adb` path below also works after successful pairing.

## 2. Build the launcher

From the repo root:

```bash
launcher/gradlew -p launcher :app:assembleRelease --console=plain
```

Windows: use `launcher/gradlew.bat`. The APK is `launcher/app/build/outputs/apk/release/app-release.apk`. Build **only `:app`**; the optional voice module requires separate model assets. The build signs with your computer's local Android debug key. Another computer's build may have a different signature; do not uninstall an existing installation without backing up its settings/photos first.

## 3. Install, then choose Home

For the Python/TCP connection, use the portable installer:

```bash
TV_HOST="$TV_HOST" tools/install-launcher.sh
```

It saves the current Home and accessibility settings under ignored `backup/`, verifies the APK transfer, installs it, enables recent-app usage access, and opens the launcher. It leaves the stock launcher and your existing accessibility settings enabled. Choose your preferred Home app using the TV's settings or Home chooser if the firmware offers one.

For a paired platform-tools connection instead:

```bash
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
adb shell appops set com.gathin.tvhome GET_USAGE_STATS allow
adb shell am start -W -n com.gathin.tvhome/.MainActivity
```

Select the correct device with `adb -s SERIAL` if more than one is connected. Record the existing Home first:

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Where supported, make this launcher Home:

```bash
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```

Verify with the resolve-activity command again and the physical Home button. If the stock launcher still wins, stop and record the model/firmware; some builds restrict replacements. The older `launcher/build-and-install.sh` can disable recognized stock launchers, so it is **not** the default dongle setup route here.

## 4. Recent apps and remote buttons

Enable the launcher service in the TV's Accessibility settings if you want remote remapping. `DoubleHomeService` handles the recent-app drawer, and `InputKeyService` handles the Inputs button. Accessibility grants the app permission to observe window changes and handle remote keys.

The tested TCL remote uses `KEYCODE_NOTIFICATION` for the cog button and vendor codes for app buttons. A Chromecast or onn. remote may send other keys or reserve them for the system. Do not assume these TCL mappings will work: identify your remote's codes and add the matching optional mapping in a focused extension PR. Keep voice and system shortcuts working. Do not enable TCL AUTO_START app ops or audio-delay profiles on unrelated firmware.

With a working drawer shortcut: cog → left/right to an app → OK to switch. Select **Close apps…** for cleanup; Back returns from close mode to switching. Force-close behavior is firmware-dependent and has not been validated on dongles. Usage history is a recent-app list, not proof that every listed app is still running.

## 5. Your own theme and photos

Copy a theme into `themes/<your-name>/` and edit its `config.json`. Put dock packages, column count, and colors there instead of replacing shared defaults. See [themes/README.md](themes/README.md). A theme can be applied with `TV_HOST=... tools/theme.sh themes/<your-name>` on a working TCP connection.

For private local photos:

```bash
tools/venv/bin/python -m pip install -r mytv/requirements-photos.txt
TV_HOST="$TV_HOST" tools/venv/bin/python mytv/ingest-photos.py "/path/to/photo.webp" --output mytv/photo-cache/my-background --deploy
```

The source may be one image, an album ZIP, or a directory. Choose a fresh output directory each time. It corrects orientation, removes image metadata, prepares screen-sized JPEGs, verifies transfers, and switches the manifest atomically. One photo stays static; multiple photos rotate every 30 seconds. Portrait photos are center-cropped to fill the screen. Google Photos → album menu → Download all is a snapshot, not live sync. The manifest is in the launcher's external files directory under `photos/active.json`; remove or rename it and restart the launcher to return to the normal background. Do not commit your photos.

## 6. Undo

Using the original Home component recorded before installation, reselect the stock Home in system settings or run:

```bash
adb shell cmd package set-home-activity --user 0 ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY
```

Verify the physical Home button. Disable only this launcher's accessibility services in the TV settings, leaving other services alone. Uninstall with `adb uninstall com.gathin.tvhome` only if you want to erase its saved configuration and locally copied photos. The portable installer does not disable system apps, so there is no debloat list to restore.

After setup, turn off debugging when you no longer need it. Submit model-specific findings and extensions using [CONTRIBUTING.md](CONTRIBUTING.md).

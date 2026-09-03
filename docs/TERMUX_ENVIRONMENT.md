# Reproducing the Termux build environment

AppId carries the scripts needed to create and audit its phone-side Android build environment. This reproduces the environment's required capabilities and directory layout; package versions come from the configured Termux repositories and are reported after installation rather than pinned to historical package archives.

## In-app workflow

1. Enable `allow-external-apps=true` using the first-run clipboard command.
2. Grant AppId the Termux `RUN_COMMAND` permission.
3. Tap **Install / repair full environment**.
4. Keep AppId open to see the setup stages.
5. Setup automatically runs the audit; open **Setup & tools** and tap **Check dependencies now** later to refresh it on demand.

## Android platform source

The **Android platform source** controls where the required `android.jar` comes
from. You can import either a compatible `android.jar` or an Android platform ZIP
from **Setup & tools**. The imported file is validated and stored in AppId's private
storage, then copied to `Download/com.focsd.appid/` so Termux can read it through
its normal shared-storage link. It is never bundled into the APK or uploaded by
AppId. Clear the import to stop overriding an existing Termux
JAR; if no JAR is installed, setup returns to the documented default download.
Imported files are validated by the setup script before installation.

If no imported source is selected, setup uses the pinned Google Android Platform
35 archive:

`https://dl.google.com/android/repository/platform-35_r02.zip`

Its SHA-256 is verified before `android.jar` is installed. This URL and checksum
are embedded in `setup_termux.sh`; a clean reset therefore returns to this same
default source.

The **Download documented platform source** button opens this URL in the device's
browser/download manager. After the download completes, use **Import platform ZIP
or android.jar** to select it. AppId does not request Internet access or download
the file itself, so this remains compatible with F-Droid's network policy.

AppId validates an import immediately. It accepts a raw `android.jar` containing
`android/app/Activity.class` or a platform archive containing
`android-35/android.jar`; corrupt, empty, and unrelated ZIP/JAR files are rejected
before they are stored or sent to Termux. Setup repeats the validation as a second
safety check.

## Reset for reinstall/testing

**Reset AppId environment…** in Setup & tools removes only AppId's private
`~/.com.focsd.appid/` workspace (the platform JAR, builder, template, signing key,
and generated output). It does not remove shared Termux packages. Confirm the
dialog, then run **Install / repair environment** again. This is useful for
reproducing a clean setup or testing platform-source fallback behavior.

If command integration is not ready, **Copy manual environment bootstrap** copies the same embedded setup and builder as one Base64-backed shell command. Paste it into Termux to reproduce the environment manually.

To make imported platform files and generated APK copies available in shared
Downloads, tap **Grant Termux storage access** in Setup & tools. Android opens
Termux's app settings; grant its Files/Photos and videos (or storage) access,
then return to AppId and rerun setup. The setup command cannot reliably display a
permission dialog while running in the background, so this is an explicit action.

## Managed state

The setup manages these paths under `~/.com.focsd.appid/`:

```text
build_placeholder.sh
check_environment.sh
environment-version
output/
placeholder-signing.keystore
placeholder-signing.pass
template/dex/classes.dex
template/icon-generator/com/focsd/appid/icon/IconGenerator.class
```

Environment schema `5` requires the Termux packages `openjdk-21`, `aapt`, `d8`, `apksigner`, and `zip`, replacement template version `5`, launcher-icon renderer version `2`, and SHA-256 verification support for the pinned Android platform archive. Setup skips package downloads when the required commands and Android platform JAR already exist, but it always refreshes the embedded builder and repairs generated artifacts. The renderer writes its built-in pixel glyphs directly into a PNG buffer, so no system font, Fontconfig setup, or image-processing package is needed.

## Audit behavior

The checker reports installed package versions, command versions/paths, required artifacts, external-command configuration, and optional capabilities. Missing required items produce **Environment incomplete**; a complete audit produces **Environment ready**. Optional shared storage and `zipalign` do not prevent private-output APK builds.

## Progress, console, and recovery

Every operation has a random ID and persistent state. Package setup sends throttled live output, while all scripts send milestone updates. Termux returns the final stdout, stderr, exit code, and internal error through its RUN_COMMAND result `PendingIntent`. AppId retains up to 100,000 console characters and restores the operation, report, and console after activity or process recreation.

The app accepts progress only when its random token matches the current operation. Only one environment or build operation can be started at a time. After setup completes, the embedded checker refreshes dependency status automatically.

The builder Base64-encodes the small APK into Termux's official command result. AppId validates it and stores it in the private APK library. A later user-requested install opens Android's package installer through an app-owned, read-only content URI. This replaces `termux-open`, which Android can prevent from launching UI when Termux is in the background.

Android itself still controls installation-source compatibility, the RUN_COMMAND grant, shared-storage consent, and unknown-app installation consent. AppId can guide or open the relevant settings but cannot reproduce or silently grant those user-controlled permissions.

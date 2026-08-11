# AppId

A minimal Android utility that asks **Termux** to create, compile and sign tiny placeholder APKs directly on an Android phone. AppId then opens Android's package installer when requested.

Website: [focsd.com](https://focsd.com)  
Android application ID: `com.focsd.appid`

License: [MIT](LICENSE)

Privacy: [AppId does not collect or transmit personal data](PRIVACY.md)

F-Droid packaging: [release and submission guide](docs/FDROID.md)

The main screen keeps the complete replacement form visible without mixing it with
setup controls. Open the top-right menu for **Setup & tools**, **APK library**,
**Installed apps**, and **About**. Setup & tools contains Termux onboarding,
dependency status, operation progress, console logs, selected-app actions, and
Android installation settings.

It also includes a separate **Installed apps, App IDs, screen time & storage** view, adapted from the sibling AppIdViewer project. That view searches installed packages, optionally includes system apps, sorts by app name, App ID, today's screen time, or occupied space, copies visible IDs, opens Android App info on long-press, and displays today's per-app foreground time and occupied space when Usage Access is granted.

The **Built APK library** keeps AppId's latest successful APK for each package ID. Library entries survive restarts and can be installed again, copied by App ID, deleted individually, or deleted together. Removing a library entry does not remove a separate APK previously copied into Termux Downloads.

The generated placeholder is intentionally small:

- one native Android `Activity`
- no AndroidX
- no Compose
- no third-party libraries
- no Internet permission
- neutral background
- generated launcher icon using the selected app's first letter
- selected app, personal reason, and replacement action
- small `⋮` menu with **About** and **Close**
- package ID taken from the selected installed app
- one reusable signing key for all placeholders created by this installation

## Important: replacing an existing app

Android does **not** allow an APK signed by one certificate to update an installed APK with the same package ID but a different certificate.

That means a placeholder cannot silently overwrite Spotify, Instagram, YouTube, etc. AppId uses the normal Android workflow:

1. choose the original app from the dropdown;
2. enter why you are removing it and what you will do instead;
3. tap **CREATE** to build the replacement using the original package ID;
4. uninstall the original app through Android's normal uninstall confirmation;
5. install the placeholder from the APK library;
6. later, to restore the original app, uninstall the placeholder and reinstall the original.

Uninstalling the original may remove its local app data. System/preinstalled apps may not be fully removable on an unmodified device.

## Architecture

```text
AppId Android app
        |
        | com.termux.RUN_COMMAND
        v
Termux RunCommandService
        |
        +-- setup_termux.sh
        |     +-- pkg install openjdk-21 aapt d8 apksigner zip
        |     +-- installs ~/.com.focsd.appid/build_placeholder.sh
        |     +-- precompiles reusable TemplateActivity -> classes.dex
        |     +-- compiles the reusable Java icon renderer
        |
        +-- build_placeholder.sh
              +-- generate AndroidManifest.xml
              +-- render title initial -> launcher PNG
              +-- aapt package
              +-- reuse classes.dex
              +-- zip / optional zipalign
              +-- apksigner
              +-- copy APK to Downloads/com.focsd.appid
              +-- optional Base64 APK result -> AppId
                                                |
                                                +-- Android package installer
```

The placeholder Java Activity and launcher-icon renderer are compiled once. Subsequent placeholders generate a small icon, then package and sign the APK, which keeps each build small and quick.

## First run on the phone

### 1. Install and open Termux

Install Termux from F-Droid or the official Termux GitHub releases, then open it once so its environment is initialized. The Google Play build does not currently expose the `RUN_COMMAND` permission and service required by AppId.

If switching from the Google Play build, back up important Termux files first. Android may require uninstalling it because builds from different sources use different signing keys.

### 2. Enable external commands in Termux

AppId has a **Copy first-run Termux command** button. Paste that command into Termux:

```sh
mkdir -p ~/.termux; touch ~/.termux/termux.properties; grep -q '^allow-external-apps=true$' ~/.termux/termux.properties || printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
```

Then fully close/reopen Termux.

AppId sends setup and APK builds as Termux background commands. **Display over other apps is not required** because AppId does not start interactive Termux terminal sessions.

### 3. Grant RUN_COMMAND permission

Android Settings -> Apps -> AppId -> Permissions / Additional permissions -> allow **Run commands in Termux environment**.

The exact label/location varies by Android vendor.

### 4. Tap `Install / repair full environment`

The first setup installs:

```text
openjdk-21
aapt
d8
apksigner
zip
```

The **Install / repair environment** action in **Setup & tools** is idempotent: it embeds the current builder and checker, installs missing Termux packages, downloads Android SDK Platform 35 with a pinned SHA-256 verification, prepares the reusable template and icon renderer, creates signing material, records environment schema version `5`, and automatically runs the dependency audit. If RUN_COMMAND is not usable yet, **Copy manual environment bootstrap** copies the same complete setup as a pasteable Termux command.

For reproducible package installation, setup backs up the existing main `sources.list` as `sources.list.com.focsd.appid-backup`, selects Termux's primary package repository, and uses bounded download retries. If the primary repository fails, setup automatically retries against the official-listed Warsaw mirror.

Use **Check dependencies now** at any time for a versioned report covering:

- Termux packages: `openjdk-21`, `aapt`, `d8`, `apksigner`, and `zip`;
- every required command used by the build scripts;
- `android.jar`, the installed builder, template DEX, launcher-icon renderer, signing key/password, and environment schema;
- `allow-external-apps=true`, shared Downloads access, `zipalign`, and `termux-setup-storage`.

Termux may also ask for storage access. If granted, APKs are copied to:

```text
Download/com.focsd.appid/
```

Otherwise they remain under:

```text
~/.com.focsd.appid/output/
```

### 5. Allow AppId to install unknown apps

AppId includes a shortcut to its own Android **Install unknown apps** page. This is required only when installing a generated APK from the library. Termux never opens the installer, so Termux does not need this permission.

## Building a placeholder

Developers can exercise the complete placeholder pipeline on macOS before launching the Android app by running `./scripts/build_placeholder_macos.sh`. See [Build and run](docs/BUILD_AND_RUN.md#run-the-placeholder-creator-on-macos-first).

## Building AppId for F-Droid

The ordinary `assembleRelease` task produces an unsigned APK for F-Droid to sign:

```sh
./gradlew clean testDebugUnitTest lintRelease assembleRelease
```

Publisher-signed upstream builds use the separately guarded
`assemblePublisherRelease` task and external signing environment variables. See the
[F-Droid guide](docs/FDROID.md) and [release guide](docs/RELEASING.md).

## Installed apps, screen time and storage

Open **Installed apps** from AppId's top-right menu. The separate screen supports:

- search by app name, App ID/package name, or UID;
- ascending sort by app name or App ID, and descending sort by today's screen time or occupied space;
- optional system-app inclusion;
- tap to copy one App ID and **Copy visible IDs** for the filtered list;
- long-press to open Android's App info page;
- today's foreground time and occupied space for each package.

Android requires the user to grant AppId **Usage access** before other apps' foreground time and storage statistics are available. The screen links to the correct system setting and refreshes when you return. Screen time is Android's per-package `totalTimeInForeground` for the current local day. Occupied space is Android's app bytes plus data bytes; data bytes already include cache, so cache is not counted twice. These are system-reported values, and no usage or storage data leaves the device.

Example values:

```text
Replace: Instagram
Why:     I want to finish writing my book.
Instead: Read 2 pages
```

The example reason and action are input hints rather than entered text. They disappear while typing and reappear whenever the field is empty.
Installed AppId placeholders also appear in the dropdown with a `★` prefix, so they
can be rebuilt with a new reason or replacement action.

Tap:

- **CREATE** — creates and signs the replacement APK and adds it to the APK library.

Every successful build is also transferred into AppId's private **Built APK library**. Rebuilding an existing package ID replaces its previous library APK with the newest build. The library distinguishes an installed AppId placeholder from a real app using the same package ID and offers Android-confirmed install and uninstall actions. After a placeholder is successfully installed and you return to AppId, it deletes its private installer APK and cache copy but keeps the installed placeholder visible. Uninstalling that placeholder removes the entry when no saved installer remains.

APK builds remain locked until the latest dependency audit reports `READY`, preventing a missing or incomplete Termux environment from being invoked as though the builder were installed.

The progress panel reports validation, launcher-icon generation, manifest packaging, compiled-code insertion, alignment, signing, copying, and the final APK path. Environment setup reports package downloads and template preparation in the same panel. Progress and console state are persisted, so reopening AppId restores the latest operation. The app prevents starting a second operation while one is active.

**Stop current Termux operation** sends `TERM` and then `KILL` only to the process tree whose command line identifies it as an AppId setup or build. It does not stop unrelated Termux shells or commands.

The selectable **Termux console** has its own touch-scrolling area. It shows live throttled package-manager output and stage messages. When a command ends, AppId receives Termux's official result bundle and appends stdout, stderr, exit code, and internal Termux errors. Termux may truncate very large command results to its documented result-size limit.

Console events are also appended to private daily files named `yyyy-MM-dd.log` under the app's `files/logs` directory. **Copy today's saved log** copies the complete daily history. Clearing the on-screen console does not remove a saved daily log, and starting a new operation does not overwrite it.

In Termux's result bundle, internal error value `-1` means there was no Termux service error. AppId displays this as `termuxInternalError=none`. Version `0.4.1` also rejects exit code `0` when setup ends before its final dependency audit, so an incomplete setup cannot be presented as successful.

Termux returns every generated APK to AppId instead of using `termux-open`. AppId validates it and saves it in the private APK library. When the user later chooses **Install**, AppId exposes only that file through a temporary read-only content URI and opens Android's confirmed package installer. A missing, invalid, or oversized transfer is reported as a build failure.

If the original package is still installed, installation will normally fail because the signatures differ. Use **Uninstall target…** first if you deliberately want the placeholder to occupy that package ID.

## Generated APK design

The generated app has no app-specific resources or external dependencies. Its manifest stores:

- selected app title, reason, and replacement action as Base64 metadata;
- six-digit background color as metadata.

All placeholders reuse the same precompiled `TemplateActivity` bytecode and a locally generated signing key. At build time, the first Unicode letter or digit in the selected app title is uppercased and drawn into a rounded 192×192 PNG. The replacement screen explains why the app was removed and emphasizes the chosen alternative action in uppercase.

Generated placeholders currently declare:

```text
minSdkVersion    23
targetSdkVersion 34
```

## Signing key

The first Termux setup generates:

```text
~/.com.focsd.appid/placeholder-signing.keystore
~/.com.focsd.appid/placeholder-signing.pass
```

Back these up if you care about updating an already-installed placeholder without uninstalling it first. Rebuilding the same package ID with the same key and a higher generated version code can act as an update to a previous AppId placeholder.

Do not publish this generic local signing setup as a production signing scheme.

## Project structure

```text
AppIdCreator/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/focsd/appid/MainActivity.java
│       └── assets/
│           ├── setup_termux.sh
│           ├── check_environment.sh
│           └── build_placeholder.sh
├── termux/
│   ├── setup_termux.sh
│   ├── check_environment.sh
│   └── build_placeholder.sh
├── docs/
├── build.gradle
├── settings.gradle
└── README.md
```

## Building AppId itself

Open the project in Android Studio and run the `app` configuration.

For a complete command-line build, device installation, launch, and verification record, see [Build and run AppId](docs/BUILD_AND_RUN.md).

Command-line build, once the Gradle wrapper is present/configured:

```sh
./gradlew assembleDebug
```

Result:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Why Termux RUN_COMMAND needs setup

Termux intentionally requires both:

- the calling Android app to request `com.termux.permission.RUN_COMMAND`; and
- `allow-external-apps=true` in Termux properties.

This project uses the documented `com.termux.RUN_COMMAND` service and passes commands as argument arrays instead of constructing shell commands from the selected app, reason, or action fields.

## References

- Termux RUN_COMMAND Intent documentation: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- Termux packages: https://github.com/termux/termux-packages
- Android package signing overview: https://developer.android.com/studio/publish/app-signing
- Android 15 minimum installable target SDK behavior: https://developer.android.com/about/versions/15/behavior-changes-all

## Scope of this version

This is an MVP focused specifically on lightweight habit-friction placeholders. It does not:

- bypass Android uninstall/install confirmations;
- disable Device Admin or system apps;
- replace a differently signed installed app in-place;
- include a full IDE;
- compile arbitrary Android Studio projects;
- use root.

The Termux build engine is deliberately much smaller than Gradle/Android Studio because the generated placeholder template does not need them.

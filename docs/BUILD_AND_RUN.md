# Build and run AppId

This document records the path from the current source tree to a running debug APK.

## Development quality gates

The project pins AGP 8.9.2 to its supported Gradle 8.11.1 wrapper and verifies the
wrapper distribution checksum. Run the same local checks as CI with:

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./scripts/check_termux_scripts.sh
shellcheck app/src/main/assets/*.sh scripts/*.sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

`app/src/main/assets/` is the canonical Termux-script source. After editing one of
those scripts, run `./scripts/sync_termux_scripts.sh`; CI rejects drift in the
human-readable `termux/` copies. Lint treats new warnings as errors, excluding only
the documented English-only programmatic UI text and the deliberate API 35 target.
Publisher-signed builds additionally require environment-backed signing
credentials; unsigned F-Droid release builds do not. See
[RELEASING.md](RELEASING.md).

## Changes in version 0.11.0

The source tree is prepared for F-Droid: `assembleRelease` produces the unsigned APK
expected by the F-Droid build service, publisher signing uses a separate guarded
task, Android Build Tools 35.0.0 are pinned, and CI verifies both debug and release
variants on JDK 17. Upstream Fastlane metadata now supplies the title, descriptions,
icon, feature graphic, and version-code changelog. Privacy, permission rationale,
public-repository, tag, and fdroiddata submission steps are documented separately in
[FDROID.md](FDROID.md).

## Changes in version 0.10.1

The development environment is now reproducible and continuously checked: Gradle
8.11.1 is pinned with its distribution checksum, Termux platform downloads use a
pinned SHA-256 checksum, the environment schema is version 4, canonical embedded
scripts are synchronized into their readable copies, and CI runs Bash syntax,
ShellCheck, unit tests, warning-strict Java compilation, Android lint, and the debug
APK build. Publisher release tasks refuse to run without explicit external signing
credentials. Pure input/formatting logic has unit coverage, Android backup excludes
private state, and compatibility-only deprecated API calls are narrowly isolated.

## Changes in version 0.10.0

The Android application ID and Java namespace are now `com.focsd.appid`, with
sources under `app/src/main/java/com/focsd/appid/`. This installs alongside the
legacy `tools.appidcreator` build; the migration does not update, uninstall, or
delete that older app. The new package uses its own Termux workspace at
`~/.com.focsd.appid/`, its own callback actions, provider authority, generated-app
branding marker, signing material, and Downloads folder. The legacy Termux state at
`~/.appidcreator/` is deliberately untouched. About now identifies the package and
links to `https://focsd.com`.

## Changes in version 0.9.0

The built-APK library now tracks saved installer files separately from installed
package state. It identifies generated placeholders through manifest metadata,
retains installed placeholders in the library after their private installer APK is
deleted, labels a conflicting installed package as a real app, and offers Android's
confirmed install and uninstall flows. After Android confirms a placeholder install
and the user returns to AppId, it automatically removes its private installer and
cache copies. Existing
placeholders remain detectable through their earlier title/color metadata.

AppId also has new generated full-color launcher artwork, while retaining a vector
monochrome resource for Android themed icons.

## Changes in version 0.8.1

The installed-app viewer now supports largest-first sorting by today's foreground
screen time and occupied storage. Storage is read on a background worker through
Android's `StorageStatsManager`; the displayed total is app bytes plus data bytes
(which already include cache). Starting a refresh cancels any queued or active
inventory scan before scheduling the replacement.

## Changes in version 0.8.0

Creator defaults are now true empty-field hints: they disappear naturally while editing, reappear when empty, and are used as defaults at build time. Every successful Termux build now transfers its APK to a persistent app-private library. The **Built APKs** activity lists the newest build for each package ID and provides install, copy-ID, individual-delete, and delete-all actions.

## Changes in version 0.7.2

Both portrait activities now apply system-bar and display-cutout `WindowInsets` to their root containers. Content starts below the top status/caption area and ends above gesture or three-button navigation controls. Keyboard resize behavior is also explicit for searchable/editable screens.

## Changes in version 0.7.1

The user-facing application name is now **AppId**. The existing application ID `tools.appidcreator`, private Termux directory `~/.appidcreator`, and generated-APK output paths remain unchanged so upgrades retain permissions, logs, signing keys, and build artifacts.

## Changes in version 0.7.0

The sibling AppIdViewer inventory is integrated as a separate **Installed apps** activity. It adds search, system-app filtering, copy/app-info actions, sorting by app name or App ID, and today's per-package foreground time. Package inventory uses `QUERY_ALL_PACKAGES`; screen time uses `UsageStatsManager` and remains unavailable until the user grants the special Usage Access setting.

## Changes in version 0.6.1

The launcher-icon renderer no longer uses AWT system fonts, because Termux OpenJDK can lack a usable Fontconfig configuration. A built-in 5×7 glyph set now renders Latin initials and digits directly into the PNG pixel buffer. Renderer version `2` is audited explicitly, and APK builds reject stale environment reports from an older schema or renderer.

## Changes in version 0.6.0

Generated placeholders now receive a launcher PNG derived from the first letter or digit in their title and the selected color. The existing Termux Java runtime compiles and runs the renderer, so there is no additional package dependency. AppId itself now supplies legacy, round, adaptive, and Android 13 monochrome launcher resources.

Version 0.5.1 changed the install handoff to return the APK from Termux to AppId and launch Android's package installer from the app. This required `REQUEST_INSTALL_PACKAGES`, a private read-only `ApkFileProvider`, APK transfer/validation in `CommandResultService`, a preflight link to AppId's unknown-source setting, and failure handling for missing or truncated results. Builds execute the current embedded builder script, preventing an older Termux-side script from restoring the broken `termux-open` behavior.

## Host requirements

The local machine does need:

- Android Studio's bundled JDK (Java 17 or newer is compatible with this project);
- the Android SDK referenced by `local.properties`;
- an Android device with USB debugging enabled and the computer authorized.

On this machine, `java` is not available on the default shell `PATH`, so the build command explicitly selects Android Studio's bundled runtime. This is a local environment setting and is not committed to the project.

## Build

From the repository root:

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

The generated APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Run the placeholder creator on macOS first

The host preflight runs the same embedded builder used by Termux, including template compilation, launcher-icon generation, Android resource packaging, DEX insertion, alignment, signing, and signature verification:

```sh
./scripts/build_placeholder_macos.sh
```

Optional positional arguments are title, package ID, six-digit color, reason, and replacement action:

```sh
./scripts/build_placeholder_macos.sh "Instagram" com.focsd.appid.preview F2F2F2 \
  "I want to finish writing my book." "Read 2 pages"
```

The preview APK is written under:

```text
artifacts/com.focsd.appid-placeholder/
```

Host-only signing material and reusable compiled artifacts stay under `build/com.focsd.appid-placeholder/`. This preflight also confirms the APK has a launcher icon and remains within the 60,000-byte Termux result handoff limit. It does not install the preview package on Android.

## Install and launch

Check that ADB sees an authorized device:

```sh
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

Install (or replace an existing debug installation) and launch AppId:

```sh
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -W -n com.focsd.appid/.MainActivity
```

Use `-s SERIAL` after `adb` in both commands when more than one device is connected.

## Verified result

On 2026-08-11, version 0.10.1 was built with `assembleDebug`, installed alongside
the legacy build on the connected `A142P` device (`000881487000711`), and launched
successfully. Android reported:

```text
package:     com.focsd.appid
activity:    com.focsd.appid/.MainActivity
versionCode: 27
versionName: 0.10.1
minSdk:      26
targetSdk:   35
launch:      cold, status ok
```

Version 0.11.0 subsequently passed unit tests, debug and release lint, debug and
unsigned-release assembly entirely offline. Two clean unsigned release builds were
byte-identical. The device disconnected before the optional 0.11.0 smoke install;
this does not affect the completed host release checks.

## App first-run requirements

Building and launching AppId itself does not require Termux. Its APK-generation features do. Before using **CREATE** on the phone, install a compatible F-Droid or official GitHub build and follow the Termux setup in the main [README](../README.md#first-run-on-the-phone), including enabling external apps, granting the Run Command permission, and installing the builder. The current Google Play build does not expose the required integration.

AppId checks for both `com.termux.permission.RUN_COMMAND` and `com.termux.app.RunCommandService`. If either is absent, it labels RUN_COMMAND support unavailable and links to the official Termux installation guide instead of opening a permission page that cannot grant the missing permission.

Setup and placeholder builds use `RUN_COMMAND_BACKGROUND=true`. They do not create interactive terminal sessions and therefore do not require Termux's **Display over other apps** permission.

Each operation receives a random progress token. The Termux scripts send package-scoped stage updates back to the running AppId screen, and the app ignores updates that do not match the active token. The progress panel ends with either **APK ready**, **Setup complete**, or **Failed**.

The creator and setup interfaces are persistent sibling views in `MainActivity`.
The fixed creator view retains the selected app and typed reason/action while the
scrollable **Setup & tools** view owns environment controls, detailed progress,
console logs, target-app actions, and Android settings. Overflow-menu actions open
Setup & tools, the APK library, installed-app inventory, and About.

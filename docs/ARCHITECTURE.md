# Architecture notes

## Design goal

Make the generated placeholder APK as close to “one screen + one menu” as practical, while allowing the creator app to run entirely on the Android device.

## Why the placeholder builder does not use Gradle

For this specific template Gradle is unnecessary overhead. The Termux pipeline is:

1. `javac` compiles the reusable Activity once.
2. `d8` converts it to `classes.dex` once.
3. A small reusable Java renderer creates a launcher PNG from the selected app's title initial.
4. Each new placeholder gets a generated manifest.
5. `aapt` packages the manifest and icon.
6. `classes.dex` is inserted into the APK.
7. `zipalign` is used when available.
8. `apksigner` signs the APK.

The selected app title, reason, replacement action, and color live in manifest metadata, so changing them does not require recompiling Java.

## Trust boundary

AppId can request Termux to run commands only after the user explicitly enables external apps and grants the custom RUN_COMMAND permission. The builder accepts structured arguments and validates the package ID and color.

AppId deliberately does not request root or accessibility privileges.

## Android UI and operation state

`MainActivity` is the launcher/creator destination and `SetupActivity` is a
separate back-stack destination. Both use the package-private `AppIdActivity`
controller for the Termux bridge and shared menu behavior. Setup does not create
the replacement form or scan installed applications; the creator passes the
selected package explicitly when opening Setup.

Long-running command state is persisted in `OperationStore`, so progress survives
activity recreation and moving between destinations. An unresolved Android
installer flow settles to “APK saved” when the user returns, while a later package
install broadcast can still promote it to “APK installed.” New library items use
monotonic latest/seen sequence values, allowing the menu badge to survive process
recreation and clear only after the library has loaded.

The generated APK returns in the bounded Termux command result. AppId validates its ZIP signature and writes it to the private APK library. If the user later requests installation, AppId grants Android's installer temporary read-only access through `ApkFileProvider`. This keeps installer activity launching in AppId's visible UID instead of relying on a background `termux-open` request.

## Replacement model

The intended habit-building sequence is:

```text
original installed
      |
      | user selects the app and enters a reason/action
      v
placeholder built
      |
      | user confirms uninstall of original
      v
original removed
      |
      | Android package installer
      v
placeholder occupies same package ID
```

Restore is the reverse: uninstall placeholder, reinstall original.

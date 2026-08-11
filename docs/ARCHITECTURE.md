# Architecture notes

## Design goal

Make the generated placeholder APK as close to “one screen + one menu” as practical, while allowing the creator app to run entirely on the Android device.

## Why the placeholder builder does not use Gradle

For this specific template Gradle is unnecessary overhead. The Termux pipeline is:

1. `javac` compiles the reusable Activity once.
2. `d8` converts it to `classes.dex` once.
3. A small reusable Java renderer creates a launcher PNG from the title initial and selected color.
4. Each new placeholder gets a generated manifest.
5. `aapt` packages the manifest and icon.
6. `classes.dex` is inserted into the APK.
7. `zipalign` is used when available.
8. `apksigner` signs the APK.

The title and color live in manifest metadata so changing either does not require recompiling Java.

## Trust boundary

AppId can request Termux to run commands only after the user explicitly enables external apps and grants the custom RUN_COMMAND permission. The builder accepts structured arguments and validates the package ID and color.

AppId deliberately does not request root or accessibility privileges.

For **Build & install**, the generated APK returns in the bounded Termux command result. AppId validates its ZIP signature, writes it to private cache, and grants Android's installer temporary read-only access through `ApkFileProvider`. This keeps installer activity launching in AppId's visible UID instead of relying on a background `termux-open` request.

## Replacement model

The intended habit-building sequence is:

```text
original installed
      |
      | user chooses package ID
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

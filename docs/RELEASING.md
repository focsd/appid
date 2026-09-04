# Releasing AppId

Debug builds use Android's local debug key and are not publication artifacts.
`assembleRelease` intentionally produces an unsigned APK so F-Droid can build and
sign it. For an upstream release signed by FOCSD, supply all four signing variables
and use the guarded publisher task:

```sh
export FOCSD_APPID_KEYSTORE="/absolute/path/to/appid-release.jks"
export FOCSD_APPID_KEY_ALIAS="appid"
export FOCSD_APPID_STORE_PASSWORD="..."
export FOCSD_APPID_KEY_PASSWORD="..."
./gradlew clean lintRelease testDebugUnitTest assemblePublisherRelease
```

Never place the keystore or passwords in this repository. The `.gitignore` excludes
common signing-file extensions, but secrets should still live in a password manager
or CI secret store. Back up the release key securely: losing it prevents normal
updates to an already distributed application.

The current AppId publisher certificate SHA-256 is
`8c54543c74d42f5cc96b24027c30d89f7e38f8595f4056c8976b253fdfa603a8`.
Publisher APK assets must be named `AppId-v<versionName>.apk`; this stable naming is
used by fdroiddata's `Binaries` URL for reproducible-build verification. Never
replace a published APK with a build signed by another key.

Before publication, confirm the version code is greater than the last release,
review the lint report, verify the APK signature, install the release APK on a test
device, and exercise Termux setup, build, library install/uninstall, Usage Access,
and unknown-source permission flows.

F-Droid releases use `./gradlew assembleRelease` without signing variables. The
result is `app/build/outputs/apk/release/app-release-unsigned.apk`; F-Droid applies
its repository signing key after rebuilding the tagged public source.

For normal F-Droid releases, use the reproducible preparation, double-build,
Docker/F-Droid, metadata, and guarded tagging scripts documented in
[FDROID.md](FDROID.md). The
verification script explicitly removes all four publisher-signing variables from
the Gradle process so an unsigned F-Droid candidate cannot accidentally inherit a
developer's signing environment.

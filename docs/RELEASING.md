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

Before publication, confirm the version code is greater than the last release,
review the lint report, verify the APK signature, install the release APK on a test
device, and exercise Termux setup, build, library install/uninstall, Usage Access,
and unknown-source permission flows.

F-Droid releases use `./gradlew assembleRelease` without signing variables. The
result is `app/build/outputs/apk/release/app-release-unsigned.apk`; F-Droid applies
its repository signing key after rebuilding the tagged public source.

# F-Droid release and submission

AppId is structured for the official F-Droid build service. The production APK uses
only Android framework APIs, contains no tracking or advertising SDK, and has no
Internet permission. The project is MIT-licensed. Store copy and changelogs live in
`fastlane/metadata/android/en-US/`.

## Permission and policy notes

- `com.termux.permission.RUN_COMMAND` is used only after a user starts a setup,
  audit, or build operation. Termux is a separate free-software application.
- `REQUEST_INSTALL_PACKAGES` opens Android's confirmed package-installer flow for a
  user-selected generated APK.
- `QUERY_ALL_PACKAGES` powers the installed-app/App ID inventory.
- `PACKAGE_USAGE_STATS` is a special setting granted explicitly by the user and is
  used only for local screen-time and storage display.
- AppId itself has no Internet permission. Its user-started Termux setup downloads
  free-software packages and a SHA-256-pinned Android platform archive.
- The generated APK is executable content, but it is created only after an explicit
  user build request and is never downloaded or installed silently.

See `PRIVACY.md` for the user-facing data-handling statement.

## Validation status

On 2026-08-11, the release candidate was validated in Docker with fdroidserver
2.4.2 (upstream commit `6af4c421`), OpenJDK 21, Gradle 8.11.1, Android platform
35, and build-tools 35.0.0. `fdroid readmeta`, `fdroid rewritemeta`, and
`fdroid lint` completed without findings. The F-Droid source scanner completed
without findings, the APK-level scanner exited cleanly, and
`fdroid build --test com.focsd.appid:28` produced and accepted the unsigned APK.
The validation APK's SHA-256 was
`ba8757a18a02d18d40724c2df69e59ea04b6c678141afbb44371a665a1a9ff1b`.
Release VCS metadata is disabled so documentation-only commits do not change the
APK bytes.

The validation used a local tagged source snapshot in place of the future public
HTTPS repository. Repeat the checks against the published tag before submitting
to fdroiddata.

## Upstream release checklist

1. Publish this complete source tree in a public Git repository.
2. Set the repository's origin URL and replace `REPOSITORY_URL` in the fdroiddata
   template below with its HTTPS clone URL.
3. Run the full release gate from a clean checkout:

   ```sh
   ./scripts/check_termux_scripts.sh
   bash -n app/src/main/assets/*.sh scripts/*.sh termux/*.sh
   shellcheck app/src/main/assets/*.sh scripts/*.sh termux/*.sh
   ./gradlew clean testDebugUnitTest lintDebug lintRelease assembleRelease
   ```

4. Confirm that the unsigned APK is at
   `app/build/outputs/apk/release/app-release-unsigned.apk`.
5. Commit the release, then create and push an annotated tag matching versionName:

   ```sh
   git tag -a v0.11.0 -m "AppId 0.11.0"
   git push origin main v0.11.0
   ```

Do not tag a dirty tree or tag before the public repository contains this release.
F-Droid checks out the tag and signs its own rebuilt APK. Publisher signing secrets
must never be added to either repository.

## fdroiddata candidate

After the public URL and tag exist, use this as the starting point for
`metadata/com.focsd.appid.yml` in an fdroiddata merge request:

```yaml
Categories:
  - Development
License: MIT
AuthorName: FOCSD
AuthorWebSite: https://focsd.com
WebSite: https://focsd.com
SourceCode: REPOSITORY_URL
IssueTracker: REPOSITORY_URL/issues

RepoType: git
Repo: REPOSITORY_URL

Builds:
  - versionName: 0.11.0
    versionCode: 28
    commit: v0.11.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.11.0
CurrentVersionCode: 28
```

Run `fdroid readmeta`, `fdroid rewritemeta com.focsd.appid`,
`fdroid lint com.focsd.appid`, and `fdroid build com.focsd.appid` in the official
fdroidserver environment before submitting the metadata merge request.

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
  free-software packages and, when no user-provided platform source is configured,
  a SHA-256-pinned Android platform archive. Setup & tools can import a compatible
  `android.jar` or platform ZIP instead; AppId validates it, keeps a private copy,
  and places a user-selected copy in `Download/com.focsd.appid/` for Termux setup.
- The optional **Download documented platform source** action delegates the pinned
  URL to the user's browser/download manager; AppId performs no network request.
- The generated APK is executable content, but it is created only after an explicit
  user build request and is never downloaded or installed silently.

See `PRIVACY.md` for the user-facing data-handling statement.

## Validation status

AppId 0.12.0 (30) is the current submission candidate. The release gate below
must be run from its final committed source and again from the published tag.

### Publication progress — 2026-09-04

- [x] Release commit `7c6a6bb4e8e1bf2cf2cf3379038d5243ffac31ec` is on `main`.
- [x] Annotated tag `v0.12.0` is published on `origin` at that commit.
- [x] Local tag gate passed: tests, debug/release lint, script checks, unsigned
  APK checks, and two identical builds.
- [x] Local unsigned APK SHA-256: `6477ae9c48feb90f841c9461dc9f0966f4df9baf1ec3c77771c27c7b89c7effc`.
- [ ] Docker/fdroidserver gate: currently blocked because the pinned Debian
  image provides OpenJDK 21, while the tagged Gradle configuration requires a
  Java 17 runtime.
- [ ] Submit `metadata/com.focsd.appid.yml` to fdroiddata after the Docker gate
  passes against the exact public tag.

On 2026-08-11, AppId 0.11.1 (29) was validated in Docker with fdroidserver
2.4.2 (upstream commit `6af4c421`), OpenJDK 21, Gradle 8.11.1, Android platform
35, and build-tools 35.0.0. `fdroid readmeta`, `fdroid rewritemeta`, and
`fdroid lint` completed without findings. The F-Droid source scanner completed
without findings, the APK-level scanner exited cleanly, and
`fdroid build --test com.focsd.appid:29` produced and accepted the unsigned APK.
The validation APK's SHA-256 was
`b347fd01eb230e7aeb41d4386e383a99f573caf40298c5d008599023c6cb5d0c`.
Release VCS metadata is disabled so documentation-only commits do not change the
APK bytes.

The canonical public source repository is
`https://github.com/focsd/appid.git`. The release gate must be repeated against
the published tag before submitting to fdroiddata.

## Reproducible next-release workflow

The checked-in release scripts read `versionName` and `versionCode` directly from
`app/build.gradle`; version numbers are never duplicated in shell configuration.
Start from a clean `main` branch and prepare the next version with a reviewed,
plain-text changelog:

```sh
./scripts/prepare_fdroid_release.sh 0.12.1 31 /path/to/0.12.1.txt
git diff --check
git diff
git add app/build.gradle fastlane/metadata/android/en-US/changelogs/31.txt
git commit -m "Release AppId 0.12.0"
```

Run the complete gate. It checks script parity and syntax, runs ShellCheck, unit
tests and both lint variants, forces publisher signing variables off, builds the
unsigned APK twice from clean outputs, and requires identical SHA-256 results:

```sh
./scripts/verify_fdroid_release.sh
```

The verified APK, checksum, source-provenance record, and ready-to-copy
`artifacts/com.focsd.appid.yml` fdroiddata candidate are written to the ignored
`artifacts/` directory. The gate rejects both JAR-style signing files and APK
signing-block signatures in the unsigned candidate. Tagging rejects an artifact
built from a dirty tree or a different commit. Preview and then create/publish the
annotated tag:

```sh
./scripts/tag_fdroid_release.sh
./scripts/tag_fdroid_release.sh --create
./scripts/tag_fdroid_release.sh --push
```

`--push` is the only release-script action that changes the remote repository.
After publishing, repeat the gate from the exact public tag rather than the working
tree:

```sh
git fetch origin tag v0.12.0
./scripts/verify_fdroid_release.sh --ref v0.12.0
./scripts/verify_fdroid_docker.sh --ref v0.12.0
```

The Docker gate builds a reusable image from the SHA-256-pinned official F-Droid
buildserver base, installs the pinned Android Platform/Build Tools 35 toolchain,
runs `fdroid readmeta`, rewrite and lint, then performs the real
`fdroid build --test` against the public tag. It requires the Docker APK hash to
equal the locally double-built APK hash. Use `--rebuild-image` when deliberately
refreshing the derived environment; `FDROID_RELEASE_IMAGE` can select a reviewed
replacement image.

The `AutoUpdateMode: Version` configuration lets F-Droid detect the new `v0.12.0`
tag. If maintainers request a manual metadata update, render a schema-correct full
file or just the new build/current-version block with:

```sh
./scripts/render_fdroid_metadata.sh
./scripts/render_fdroid_metadata.sh --build-block
```

Do not tag a dirty tree. F-Droid checks out the tag and signs its own rebuilt APK;
publisher signing secrets must never be added to either repository.

## fdroiddata candidate

Use this as the starting point for
`metadata/com.focsd.appid.yml` in an fdroiddata merge request:

```yaml
Categories:
  - Development
License: MIT
AuthorName: FOCSD
AuthorWebSite: https://focsd.com
WebSite: https://focsd.com
SourceCode: https://github.com/focsd/appid
IssueTracker: https://github.com/focsd/appid/issues
Changelog: https://github.com/focsd/appid/releases

AutoName: AppId

RepoType: git
Repo: https://github.com/focsd/appid.git
Binaries: https://github.com/focsd/appid/releases/download/v%v/AppId-v%v.apk

Builds:
  - versionName: 0.12.0
    versionCode: 30
    commit: REPLACE_WITH_THE_FULL_V0.12.0_RELEASE_COMMIT
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: be1a53e94b9ccc0dbea1e4343aacf34169de1c14ccd77037d8783029a286dbbc

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.12.0
CurrentVersionCode: 30
```

Run `fdroid readmeta`, `fdroid rewritemeta com.focsd.appid`,
`fdroid lint com.focsd.appid`, and `fdroid build com.focsd.appid` in the official
fdroidserver environment before submitting the metadata merge request.

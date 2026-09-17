# AppId Google Play release readiness

Task created from the release-preparation patterns used by `android-switchlog`,
`android-moodlogger`, and `android-quarry`.

## Product and policy materials

- [ ] Confirm the final public app name, application ID, category, and support email.
- [x] Keep the store listing accurate and free of medical, addiction-treatment,
      or guaranteed-wellbeing claims.
- [x] Draft a host-ready, non-geofenced HTML privacy policy.
- [x] Make the same privacy policy available from inside the app.
- [x] Make the MPL-2.0 license and source notices available inside the app.
- [x] Use Material Components/AppCompat for the application shell while retaining
      API 24 compatibility and the existing action-bar navigation.
- [x] Add an explicit Play permission-rationale draft for package visibility,
      Usage Access, APK installation, uninstall confirmation, and Termux commands.
- [ ] Complete Play Console Data Safety, App access, content rating, target audience,
      account deletion, and permissions declarations.
- [ ] Verify that the listing, privacy policy, in-app notices, and uploaded artifact
      describe the same behavior.

## Store assets and listing

- [x] Validate the 512×512 Play icon as a full-square, opaque, sRGB PNG without a
      baked Play mask or shadow.
- [x] Validate the 1024×500 feature graphic.
- [ ] Capture current light and dark phone screenshots on API 34+.
- [ ] Capture tablet screenshots or explicitly decide not to distribute to tablets.
- [x] Prepare short description, full description, reviewer notes, and release notes.

## Build and signing

- [x] Target API 36 and support API 24+.
- [x] Keep publisher signing material outside version control.
- [ ] Generate a secure Google Play upload key and enroll in Play App Signing.
- [ ] Build a signed AAB with the externally configured upload key.
- [ ] Verify the AAB signature, package name, version code, debuggable flag, and
      release permissions before upload.
- [ ] Confirm the version code is greater than every uploaded artifact.
- [ ] Tag the exact clean commit used for the uploaded AAB.

## Verification

- [x] Run `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug bundleRelease`.
- [x] Run `git diff --check` and the Termux parity checker after asset changes.
- [ ] Test the signed release on API 24 and API 35/36 devices.
- [ ] Check light/dark mode, large fonts, TalkBack labels, rotation, edge-to-edge,
      keyboard behavior, and all destructive confirmations.
- [ ] Test Termux setup, environment audit, placeholder build, APK install, and
      generated-APK library flows.
- [ ] Upload to Play internal testing and resolve automated pre-launch findings.

## External blockers

These require the Play Console account owner and cannot be completed by repository
changes alone: developer verification, legal identity, support email, public policy
hosting, signing-key enrollment, Data Safety and IARC answers, target audience,
countries/pricing, tablet distribution, internal testing, and final review.

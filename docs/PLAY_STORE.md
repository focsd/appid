# Google Play Store submission packet

This document is the source-of-truth draft for the Google Play listing and
Play Console declarations. Replace the privacy-policy URL with the verified
public HTTPS URL after hosting [`privacy-policy.html`](privacy-policy.html).

## Product details

- App name: **AppId**
- Application ID: `com.focsd.appid`
- Category: Productivity
- Default language: English (United States)
- Target audience: publisher decision required; do not select children unless
  the product, policy, and listing are deliberately designed for children.
- Privacy policy URL: **[PUBLIC PRIVACY POLICY URL]**
- Support contact: **Use the verified developer contact published on the listing**

## Short description

Build tiny, intentional placeholder apps on Android with Termux.

## Full description

AppId helps you create a small placeholder for an installed app you have chosen
to remove. Choose the app, record why you are removing it, and write what you
will do instead. AppId asks Termux to build the placeholder APK, then uses
Android's normal installer confirmation flow when you choose to install it.

Features:

- Create a personal replacement reminder with your reason and next action
- Generate a launcher icon from the selected app's name
- See setup progress, dependency checks, and Termux build logs
- Keep a local library of generated APKs for later installation or cleanup
- Browse installed applications and application IDs
- Optionally view locally processed screen-time and storage information after
  granting Android Usage Access
- No account, ads, analytics, tracking SDK, or Internet permission in AppId

AppId does not silently uninstall or install applications. Android's package
signature and installer confirmation screens remain in control. APK-building
features require a compatible F-Droid or official GitHub build of Termux.

AppId is a productivity and intentional-use tool, not a medical device and not
a treatment, diagnosis, crisis-monitoring, or emergency service.

## Reviewer notes / app access

All functionality is available without a login, subscription, account, or
special reviewer credentials. Install and open the app to see the main creator.
The Installed apps viewer works without Termux. APK-building features require
the separately installed compatible Termux app and the user-granted Run
commands permission; the setup screen explains this dependency and provides
the Termux installation links.

To review the main flow without creating an APK, select an installed app and
inspect the reason/action form. To inspect generated APK management, open
**APK library**. To inspect privacy and licensing information, open the top-right
menu and select **Privacy policy** or **License**.

App access declaration: AppId has no login, account, subscription, age gate, or
restricted reviewer content. Every screen is available immediately after
installation. Termux is an optional, separately installed companion used only
when the reviewer wants to test environment setup or APK generation; the main
installed-app inventory and replacement form remain usable without it.

## Data Safety draft

- Data collected by AppId: **None**
- Data shared by AppId: **None**
- Account creation: **No**
- Advertising or analytics: **No**
- Data deletion: users can delete generated APKs/logs, reset the AppId Termux
  workspace, clear app storage, or uninstall AppId.

Usage statistics and installed-app information are processed only on the device
after explicit user action. Termux's separately installed package downloads are
outside AppId's own network permission and must be described consistently with
the final uploaded artifact and Play Console form.

## Permission declarations

- `QUERY_ALL_PACKAGES` — core inventory functionality. After the user opens
  Installed apps, AppId reads the installed package list so the user can choose
  the app to replace and see its application ID. The inventory is not uploaded
  or used for advertising. A narrow package-query declaration cannot support
  this user-selected “show any installed app” workflow because the target is
  not known in advance. Complete the Play Console package-visibility declaration
  with this user-facing purpose and confirm that the selected policy category
  permits it.
- `PACKAGE_USAGE_STATS` — optional local diagnostics. Only after the user grants
  Usage Access, AppId reads Android's locally available screen-time and occupied
  storage values for the Installed apps view. The values help the user compare
  habits and storage impact; they are not needed to create a placeholder and
  never leave the device. Explain that the user can deny this access and retain
  all core replacement features.
- `REQUEST_INSTALL_PACKAGES` — user-selected placeholder installation. When the
  user taps install for an APK that AppId generated, AppId starts Android's
  package-installer confirmation flow. AppId does not silently install packages,
  install third-party downloads, or install in the background. Confirm current
  Google Play eligibility for this restricted permission before declaring it.
- `REQUEST_DELETE_PACKAGES` — user-selected cleanup. When the user chooses to
  remove a generated placeholder from APK library or Installed apps, AppId starts
  Android's uninstall confirmation flow. It does not silently remove apps or
  delete the original app that the user is replacing.
- `com.termux.permission.RUN_COMMAND` — optional build integration. When the
  user presses a setup, dependency-check, or build action, AppId sends that
  explicit command to the separately installed Termux app. Termux performs the
  build locally and AppId shows the returned status/log; AppId cannot execute
  commands without the user's action and the Termux permission. Denying it
  leaves the non-Termux screens available.

These permissions do not mean AppId collects the associated information. The
Data Safety answers describe data leaving the device, while the permission
declarations explain why Android access is needed.

## Assets

- 512×512 icon: `fastlane/metadata/android/en-US/images/icon.png`
- 1024×500 feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Phone screenshots (API 34, light and dark):
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`
  (`1-home.png`, `2-setup.png`, `3-home-dark.png`, `4-setup-dark.png`)

Capture fresh screenshots from the release candidate, including light and dark
mode, before upload. Test or explicitly restrict tablet distribution.

## External steps still required

- Host the HTML policy at a public, non-geofenced HTTPS URL.
- Complete the target-audience, IARC content-rating, Data Safety, App access,
  countries/pricing, and developer-verification forms.
- Configure Google Play App Signing and an upload key outside Git.
- Build and verify a signed API 36-targeting AAB with a new version code.
- Upload to internal testing, review the pre-launch report, and complete any
  testing requirement attached to the developer account.

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

- `QUERY_ALL_PACKAGES`: core functionality is the user-requested installed-app
  and application-ID inventory. The declaration must explain why a narrower
  package-query list cannot support this feature.
- `PACKAGE_USAGE_STATS`: optional, user-granted access used only to show local
  screen-time and occupied-storage information in Installed apps.
- `REQUEST_INSTALL_PACKAGES`: core functionality is installing a generated
  placeholder APK through Android's user-confirmed package installer. Confirm
  eligibility under the current Play restricted-permission policy before upload;
  the Play Console owner must make the final declaration.
- `REQUEST_DELETE_PACKAGES`: used only to open Android's user-confirmed
  uninstall flow for a generated placeholder.
- `com.termux.permission.RUN_COMMAND`: used only to send explicit user-requested
  setup/check/build commands to the separately installed Termux app.

These permissions do not mean AppId collects the associated information. The
Data Safety answers describe data leaving the device, while the permission
declarations explain why Android access is needed.

## Assets

- 512×512 icon: `fastlane/metadata/android/en-US/images/icon.png`
- 1024×500 feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Phone screenshots: `fastlane/metadata/android/en-US/images/phoneScreenshots/`

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

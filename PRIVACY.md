# AppId privacy policy

**Effective date: September 17, 2026**

AppId does not collect, transmit, sell, or share personal data. It contains no
advertising, analytics, telemetry, tracking SDK, account system, or cloud
synchronization.

## Information processed on the device

AppId reads the installed-application inventory so you can choose an app to
replace. If you explicitly grant Android Usage Access, AppId can display
device-reported screen-time and storage information. This information is processed
locally and is not sent to the developer or any third party.

Build logs, generated APKs, preferences, and imported Android platform sources are
stored locally in AppId's private storage. Optional copies of generated APKs or
platform files in shared Downloads remain under your control.

## Termux and downloads

When you explicitly request setup, dependency checking, or a placeholder build,
AppId sends a command to the separately installed Termux application. Termux may
download open-source build packages and a checksum-pinned Android platform archive
from the repositories documented in the source code. AppId itself has no Internet
permission and does not operate a network service.

## Installation and deletion

AppId asks Android to install or uninstall an APK only after an explicit user
action; Android displays its normal confirmation screen. You can delete generated
APKs and logs from the app, reset AppId's private Termux workspace, clear the app's
storage in Android settings, or uninstall AppId. Files copied separately into
Termux or shared Downloads are not deleted by clearing AppId.

## Contact and policy changes

Android and Termux are separate services governed by their own policies. We will
update this policy before releasing a version whose data practices change.
Questions can be sent through [focsd.com](https://focsd.com).

The host-ready HTML copy is [`docs/privacy-policy.html`](docs/privacy-policy.html).

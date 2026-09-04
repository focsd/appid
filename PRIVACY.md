# AppId privacy policy

AppId does not collect, transmit, sell, or share personal data. It contains no
advertising, analytics, telemetry, tracking SDKs, or Internet permission.

AppId can read the installed-application inventory. If the user explicitly grants
Android Usage Access, it can also display device-reported screen time and storage
usage. This information is processed locally and is not transmitted off the device.

AppId sends commands to the separately installed Termux application only when the
user requests an environment setup, dependency check, or placeholder build. Setup
downloads open-source Termux packages and a checksum-pinned Android platform archive
from the repositories described in the source code. Before setup starts, AppId shows
what Termux may download and requires explicit confirmation. AppId can ask Android
to install or uninstall an APK only after an explicit user action; Android displays
its normal confirmation screen.

Build logs and generated APKs are stored locally in AppId's private storage. The
user can view, copy, or delete them from the app. Optional copies in the shared
Downloads folder remain under the user's control.

Questions about this policy can be directed through https://focsd.com.

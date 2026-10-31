# Etar Calendar
Etar (from Arabic:  `إِيتَار`)  is an open source material designed calendar made for everyone!

[<img src="metadata/en_fdroid.png" height="60" alt="Get it on F-Droid">](https://f-droid.org/packages/ws.xsoh.etar/)[<img src="metadata/en_google_play.png" height="60" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=ws.xsoh.etar)

> **Release builds are a different app.** Releases from this repository are package `org.lineageos.etar`, signed with the release key. Debug builds are `org.lineageos.etar.debug`. Moving from a debug build to a release build means uninstalling the debug build first, which deletes its data. The earlier Release 1.0 (`ws.xsoh.etar.debug`) is a separate package and can stay installed.

> **Note:** the store links above point to upstream Etar (`ws.xsoh.etar`). This repository's releases use `org.lineageos.etar`, so they cannot update or replace that app.

![Etar Calendar](metadata/animation.gif)

## Why?
Well, I wanted a simple, material designed and state of the art open source calendar that anyone can make better.

## Special thanks

The application is an enhanced version of AOSP Calendar. Without the help of
[Free Software for Android](https://github.com/Free-Software-for-Android/Standalone-Calendar) team, 
this app would be just a dream. So thanks to them!

## Features
- Month view.
- Week, day and agenda view.
- Uses Android calendar storage to show all synchronized calendars.
- Material Design.
- Dark and light theme.
- Support offline calendar.
- Agenda widget.
- Anniversary and Countdown event types.
- Support sharing calendars via ICS and the ability to import/export from SD card.
- Multilingual UI.

## How to use Etar
Store your calendar on the phone only:
  - Create an offline calendar.

Sync your calendar to a server:
  - A cloud-synched calendar could be a google calendar, but you can also use
  any other public Caldav-server or even host your own (which would be the
  only way to keep full control over your data and still have ONE calendar
  usable from different devices.) To sync such a calendar to some server you
  need yet another app, e. g. DAVx5. That’s necessary because a Caldav client
  isn't included in Etar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Etar.
You can even configure in Etar which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Etar requires
- READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE  
->import and export ics calendar files  
- READ_CONTACTS(optional)  
  Is queried the first time an appointment is created and can be rejected. But then search and location suggestions no longer work.
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events

### Known issues with the ICS import feature

Etar can import ICS files, for instance, received in invitation emails.
The import feature is unstable and has a list of known bugs.
Refer to https://github.com/Etar-Group/Etar-Calendar/pull/653 as a reference to those bugs.
Use it with caution, especially, if your calendar provider automatically sends out invitation emails.

## Contribute
### Translations
Interested in helping to translate Etar? Contribute here: https://hosted.weblate.org/projects/etar-calendar/strings/

##### Google Play app description:
You can update/add your own language and all artwork files [here](metadata)

### Build instructions
Install and extract Android SDK command line tools.
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
gradle :app:assembleDebug
```
Debug builds are `org.lineageos.etar.debug`, signed with the local debug key. A debug build from another machine may not install over yours.

### Build and release
Release APKs are built by the GitHub workflow described in [docs/WORKFLOW.md](docs/WORKFLOW.md). Local builds need none of this.

- **Secrets** (repository settings): `KEYSTORE_BASE64` (base64 of the keystore file), `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`.
- **Keystore**, created once by the owner:
  ```
  keytool -genkeypair -v -keystore release.jks -alias gkui-release -keyalg RSA -keysize 4096 -validity 10000
  base64 -w0 release.jks
  ```
  Keep the file and its passwords safe. Losing them means a new key, and existing installs can no longer be upgraded.
- **`release-signer.sha256`** at the repository root holds the SHA-256 of the release certificate (64 hex characters). The release script refuses any APK signed by another key. The owner writes it once, from the value the first run prints.
- **versionCode** is `git rev-list --count HEAD`, the commit count. It only grows, so each release is newer than the last. This needs a full clone (`fetch-depth: 0`). **versionName** is `yyyyMMdd.<versionCode>`; the `VERSION_NAME` variable overrides it.
- **Local builds are unaffected.** `gradle :app:assembleDebug` needs no secret. `assembleRelease` without the four variables is signed with the debug key and prints a warning; do not distribute that APK. CI refuses to build release without the release key.
- **Release output:** package `org.lineageos.etar`, not debuggable, files `GKUICalendar-<versionName>.apk`, its `.sha256`, and `build-info.json`.

## License

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

Licensed under the GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
Except where otherwise noted.

Google Play and the Google Play logo are trademarks of Google Inc.

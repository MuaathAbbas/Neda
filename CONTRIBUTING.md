# Contributing

## Build and test

JDK 17 or newer, and Android SDK 36.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

A release build is signed only when `keystore.properties` exists. Do not commit that file, `*.jks`, `*.keystore`, or `local.properties`.

## Strings

Every user-visible string goes in both `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ar/strings.xml`. Arabic is Modern Standard Arabic, short and natural. Layouts use `start` and `end`, not `left` and `right`. Check `values-night` so dark mode stays readable.

## Code

Keep the existing layers. The UI does not calculate prayer times and does not play audio. Do not change alarm playback to the media stream, and do not replace exact alarm clocks for prayer or clock alarms with inexact alarms. Do not add analytics, ads, accounts, or network calls beyond the existing weather request. Do not add Google Play Services or Firebase.

Logcat tag: `Adhan`.

Add or update unit tests for changes in `domain/`, `prayer/`, `scheduler/`, `store/`, and `calendar/`.

## Pull request checklist

- Debug build, unit tests, and lint pass.
- New strings exist in English and Arabic.
- No secrets in the diff.
- One phase of work stays on its own branch.

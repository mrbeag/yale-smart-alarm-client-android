# Home Alarm for Android Auto

A private, family-oriented Android and Android Auto controller for Yale Smart Alarm systems.

The UI has exactly three mode controls: **Away**, **Home**, and **Disarmed**. A requested mode remains in a visible **Switching** state until a fresh status read confirms it. The previous confirmed mode stays highlighted during that transition.

The phone UI follows Android's system light or dark theme automatically. There is no separate in-app theme setting.

## Safety model

- Debug and release builds use the same real Yale login and alarm-control workflow.
- The last successful Yale email, password, area, and refresh token are encrypted with Android Keystore and excluded from backup and device transfer.
- Android Auto never accepts or displays credentials.
- Away, Home, and Disarmed each issue their request with one tap.
- A successful command response is not treated as proof that the alarm changed state.

## Build prerequisites

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- An Android API 36 emulator or physical Android phone

The Yale OAuth Basic client credential is deliberately not committed. Supply it through the `YALE_BASIC_AUTH` Gradle property or environment variable. Without it, Yale login is disabled.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./gradlew test lint assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a connector-enabled local build, set `YALE_BASIC_AUTH` only in the shell running Gradle. Do not commit it to this repository. Each family member enters their Yale account details on their phone. The last successful login and resulting refresh token are encrypted by Android Keystore so later logins can reuse them.

The API 36 Google APIs emulator can verify the phone app and Car App templates. A full Desktop Head Unit projection requires the current Android Auto app with developer mode and **Start head unit server** enabled, so use a Play-enabled emulator or the target Samsung phone for that final check.

This project is unofficial and is not affiliated with or endorsed by Yale or ASSA ABLOY.

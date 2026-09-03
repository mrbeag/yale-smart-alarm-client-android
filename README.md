# Home Alarm for Android, Wear OS, and Android Auto

A private, family-oriented Android, Wear OS, and Android Auto controller for Yale Smart Alarm systems.

The UI has exactly three mode controls: **Away**, **Home**, and **Disarmed**. A requested mode remains in a visible **Switching** state until a fresh status read confirms it. The previous confirmed mode stays highlighted during that transition.

The phone UI follows Android's system light or dark theme automatically. There is no separate in-app theme setting.

The Wear OS app is a non-standalone companion. It sends status and mode requests to the paired phone over Google's Wear Data Layer; only the phone talks to Yale. Opening either UI refreshes the alarm state and retries one transient read failure after one second.

The Wear OS complication displays the current mode on compatible watch-face slots and opens the full mode controls when tapped. If the phone cannot be reached, it labels the locally cached mode as the last confirmed state rather than presenting it as current.

## Safety model

- Debug and release builds use the same real Yale login and alarm-control workflow.
- The last successful Yale email, password, area, and refresh token are encrypted with Android Keystore and excluded from backup and device transfer.
- Wear OS and Android Auto never accept, store, or display Yale credentials.
- Away, Home, and Disarmed each issue their request with one tap.
- A successful command response is not treated as proof that the alarm changed state.

## Build prerequisites

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- An Android API 36 emulator or physical Android phone
- A Wear OS API 30+ emulator or watch for the companion app

The Yale OAuth Basic client credential is deliberately not committed. Supply it through the `YALE_BASIC_AUTH` Gradle property or environment variable. Without it, Yale login is disabled.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./gradlew test lint assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Install `wear/build/outputs/apk/debug/wear-debug.apk` on a paired Wear OS device. The phone and watch builds deliberately use the same application ID and signing identity so the Wear Data Layer will accept their messages.

For a connector-enabled local build, set `YALE_BASIC_AUTH` only in the shell running Gradle. Do not commit it to this repository. Each family member enters their Yale account details on their phone. The last successful login and resulting refresh token are encrypted by Android Keystore so later logins can reuse them.

The API 36 Google APIs emulator can verify the phone app and Car App templates. A full Desktop Head Unit projection requires the current Android Auto app with developer mode and **Start head unit server** enabled, so use a Play-enabled emulator or the target Samsung phone for that final check. End-to-end watch testing requires a paired phone/watch environment with Google Play services on both devices.

This project is unofficial and is not affiliated with or endorsed by Yale or ASSA ABLOY.

# FindMyPhone

Press SELECT on a Garmin Forerunner 170 and your Android phone rings (max alarm volume + vibration).
Press SELECT again, tap **Stop** on the phone notification, or wait for the timeout to stop it.

- `WatchApp/` – Connect IQ (Monkey C) watch app.
- `AndroidApp/` – Android companion app (Kotlin/Compose). A foreground service listens for the watch
  through [`ciq-companion-app-sdk`](https://github.com/garmin/connectiq-android-sdk) and Garmin Connect Mobile.
- `connectiq-sdk-manager/` – local copy of Garmin's SDK manager (git-ignored).

## Message protocol
Dictionaries, defined in `WatchApp/source/Protocol.mc` and `AndroidApp/.../Protocol.kt` (keep in sync):

| Direction     | Payload                                  |
|---------------|------------------------------------------|
| watch → phone | `{"cmd": "ring"}` / `{"cmd": "stop"}`    |
| phone → watch | `{"state": "ringing"}` / `{"state": "stopped"}` |

The watch app id (`WatchApp/manifest.xml`) must match `WATCH_APP_ID` in `AndroidApp/.../Constants.kt`.

## Build

**Watch** (needs the Connect IQ SDK installed via `connectiq-sdk-manager/sdkmanager.sh`):

```
cd WatchApp
./run.sh            # build + run in the simulator (device: fr170)
```

Sideload to the watch by copying `WatchApp/bin/FindMyPhone.prg` to `GARMIN/Apps/` over USB (MTP).
Watch builds are signed with `WatchApp/developer_key.der`, which is **git-ignored**.
**Back this key up somewhere safe** – a different key makes future builds a different app.
Create one with:

```
openssl genrsa -out developer_key.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
```

**Android**:

```
cd AndroidApp
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Requires `local.properties` with `sdk.dir=...` and Garmin Connect Mobile on the phone.

## Notes
- On Arch the SDK manager and simulator need `libjxl.so.0.11`; `connectiq-sdk-manager/jxl-compat/` symlinks it
  to the installed 0.12 and `sdkmanager.sh` / `run.sh` put it on `LD_LIBRARY_PATH`.
- Ringing uses the alarm stream, so it plays through normal Do Not Disturb but not through "Total silence".
- Uncaught errors are saved and shown in a dialog the next time the Android app opens (no logcat needed).

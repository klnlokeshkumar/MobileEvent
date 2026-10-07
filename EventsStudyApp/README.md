# Events — Study Hall Automation v2

For Redmi Note 11 / HyperOS.

## Included
- Event 1: screen OFF for 1 minute -> media volume 0. The automation service stays alive in the background.
- Study Mode manual ON/OFF.
- Study-hall Wi-Fi automation: connect to the saved Wi-Fi -> Study Mode ON; disconnect or Wi-Fi off -> Study Mode OFF. Manual OFF while connected creates an override that lasts until Wi-Fi disconnects.
- Study Mode options: media mute, ringer vibrate mode, notification/DND suppression (requires Notification Policy Access), and selected-app anti-distraction (requires Accessibility access).
- Study-hall statistics: session start/end time, duration, Wi-Fi-triggered time, and total time.
- Battery alerts: threshold 50–100%; at/above threshold, 2-second alarm every 2 minutes while charging until unplugged; every 5 minutes when not charging.
- Home-screen countdown widgets. Each widget copy can be configured to a different reminder. Default: SSC CGL — 28 Oct 2026. Add more entries such as Maths course with its own completion date.

## Android / HyperOS limitations
- Wi-Fi SSID access can require Location permission and Location services to be ON.
- Anti-distraction needs the user to enable Events under Accessibility. This is how Android allows an app to detect the foreground app.
- Notification suppression needs Notification Policy Access. Android/HyperOS ultimately controls exact lock-screen privacy behavior; a normal app cannot silently override every lock-screen setting.
- HyperOS may still restrict background services unless Autostart and Battery = No restrictions are enabled.

## Build
Open in Android Studio, let Gradle sync, then Build -> Rebuild Project. The project uses AGP 8.6.1, Kotlin 2.0.21, Gradle 8.7, and JVM 17 for both Java and Kotlin.


## Study-hall Wi-Fi privacy update
On Android 13 and newer, the app uses the Nearby Wi-Fi Devices runtime permission to identify the saved study-hall Wi-Fi. It does not request or require ACCESS_FINE_LOCATION on those versions, so the phone's Location switch can remain OFF. On Android 12 and older, Android requires location permission for Wi-Fi SSID access.


## Background / Recents behavior (v4)
The automation is a foreground service with `START_STICKY` and `stopWithTask=false`, so swiping Events away from the Recents screen does not request the service to stop. It also receives screen, Wi-Fi and battery events while the app UI is closed.
Do not use Android/HyperOS "Force stop" for the app: force-stop is a different operation that intentionally stops background execution until the app is opened again.
On Xiaomi/HyperOS, enable Autostart and set Battery to No restrictions if available for best reliability. Keeping the app locked in Recents is optional extra protection, not a requirement for normal foreground-service behavior.


## v5 build fix
Fixed Android resource linking for the AccessibilityService description by moving the text into res/values/strings.xml and referencing it with @string/accessibility_service_description.


## Wi-Fi SSID detection note (v6)
Android's `NEARBY_WIFI_DEVICES` permission does not by itself make the connected SSID available. The Android WifiInfo documentation states that SSID is a location-sensitive field and can be returned as UNKNOWN_SSID when the required access is not available. Therefore this version requests `ACCESS_FINE_LOCATION` and checks that Location is enabled when automatic study-hall SSID detection is used. This is an Android platform requirement for reliable SSID-based detection, not an app choice.


## v7: Location-free Study Hall Wi-Fi detection
This version does not read the Wi-Fi SSID/BSSID. When you tap "Use current Wi-Fi as study hall", it saves a fingerprint based on the active Wi-Fi network's local IPv4/subnet, default gateway and DNS configuration. Later it compares the active network fingerprint in the foreground service.
Because it does not read SSID/BSSID, the phone Location switch can remain OFF.
Limitation: this fingerprint is not the Wi-Fi name and can change if the study-hall router changes its network configuration. Two networks with identical local configuration could theoretically match.


## v8: Kotlin daemon stability
This version sets `kotlin.compiler.execution.strategy=in-process` so Kotlin compilation does not depend on the Kotlin compile daemon staying alive during startup. Java/Kotlin JVM target remains 17.


## v9: WifiIdentity Kotlin nullability fix
Fixed nullable access to `Route.gateway` by using a safe call (`it.gateway?.hostAddress`). This resolves the Kotlin compiler error about a nullable `InetAddress`.


## v10 stability update
- Study Mode and Wi-Fi setup actions are guarded so a system/API exception is shown as a Toast instead of terminating the app process.
- Study-hall Wi-Fi identification no longer reads SSID/BSSID and no longer requests Location or Nearby Wi-Fi permission.
- The Wi-Fi fingerprint uses the subnet portion of the local IPv4 address plus the default gateway and DNS configuration, reducing false changes when DHCP gives the phone a new host IP.
- Foreground automation remains `START_STICKY` and `stopWithTask=false`.


## v11: MainActivity syntax fix
The malformed multiline Kotlin strings in `refreshUi()` were replaced with valid escaped newline strings (`\n`). This fixes the parser errors reported around lines 62–67.


## v12: Study Mode feature fixes
- Silent/vibrate is applied immediately when Study Mode is active.
- Notification suppression uses DND priority mode with calls and alarms allowed, while suppressing the notification list/lock-screen view, status-bar, peeking, badges and other visual interruptions where supported.
- Anti-distraction is more reliable when the Accessibility service is enabled; it listens for window state/windows/content changes and returns to Home when a selected app opens.
- The UI shows whether DND access and Accessibility are enabled.
- The selected study-hall Wi-Fi fingerprint remains saved until the user explicitly saves a new network.


## v13: MainActivity + DND compile fix
MainActivity was rewritten cleanly to remove malformed Kotlin expressions that caused the large cascade of unresolved-reference/parser errors. The invalid `PRIORITY_SENDERS_NONE` reference was replaced with a supported policy sender value. Kotlin is compiled in-process and Java/Kotlin target remains JVM 17.


## v15: Foreground notification compile fix
Fixed the AutomationService foreground startup to use the shared AutomationNotifier. Its notification builder is now accessible to the service.


## v16: Silent Mode fix
Study Mode now changes only the system ringer to `RINGER_MODE_SILENT`; vibration is not managed separately. Android requires Notification Policy Access for ringer-mode changes that affect Do Not Disturb, so the app provides an explicit one-time "ALLOW SILENT MODE ACCESS" button and also opens that settings page after the study-hall Wi-Fi is saved when access is missing. While connected, the background service re-applies Silent Mode periodically.
When the saved Wi-Fi disconnects or changes, the exact previous media volume and ringer mode captured at activation are restored.


## v17: Notification Policy Access manifest fix
Added `android.permission.ACCESS_NOTIFICATION_POLICY` to the app manifest. This is the permission that allows the app to appear in Android's Do Not Disturb / Notification Policy Access app list. Once the user grants it, Study Mode can request Silent Mode through `AudioManager.RINGER_MODE_SILENT`.


## v18: Recents swipe protection
The foreground automation service already uses `START_STICKY` and `android:stopWithTask="false"`. v18 additionally schedules a lightweight restart when the user swipes Events away from the Recents screen. This is specifically for a normal Recents swipe; Android/Xiaomi can still stop an app after an explicit Force Stop, and HyperOS battery/autostart restrictions can still prevent background execution.

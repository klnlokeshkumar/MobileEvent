# Events Study Hall Automation

Version 26.0

## Battery reminder audio
- Battery reminders now use bundled audio files instead of relying on Text-to-Speech.
- High reminder: "Energised, Enough of charging" while the charger is actually connected and the battery is at/above the highest threshold.
- Low reminder: "I am thirsty, Please connect the charger" while the charger is disconnected and the battery is below the lowest threshold.
- Audio is routed with ALARM usage so it remains audible even when Study Mode has set media volume to zero.
- The high reminder stops as soon as the charger is disconnected, including the case where Android reports a previous FULL status.

## Existing behavior retained
- Stable Study Hall Wi-Fi detection and transient-loss grace period.
- Media volume is set to zero only when a genuinely new Study Mode session starts; it is not repeatedly reset while connected.
- Silent Mode is rechecked every 30 minutes while Study Mode remains active.
- BootReceiver and automatic boot startup remain enabled.
- Events remains resilient to being swiped from Recents.
- Battery thresholds and reminder intervals remain user-configurable.

Use the same permanent release signing key for every future update. Do not commit the keystore or passwords to source control.

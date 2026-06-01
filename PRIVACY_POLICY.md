# Privacy Policy

**Effective Date:** 2026-06-01

Mlue is designed with a fundamental commitment to user privacy, simplicity, and offline-first reliability. We believe that your habits and personal data belong solely to you.

## 1. Data Collection and Storage
**All data is stored locally.** 
Mlue does not collect, transmit, sync, or sell your personal information, habit data, or usage statistics to any external servers or third parties. Your habits, goals, and journal entries live exclusively in an SQLite database (Room) on your personal device.

## 2. Telemetry and Analytics
We do **not** use heavy third-party telemetry software (such as Firebase Crashlytics) to monitor your behavior.
To ensure the app remains stable, we rely on **Android Vitals**, a native Google Play Console service. Android Vitals only provides anonymized crash reports and performance metrics if you have explicitly opted into sharing diagnostics with Google through your device settings.

## 3. Permissions
Mlue requests the following permissions to function:
- **Notifications & Alarms (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`)**: Used exclusively to deliver timely habit reminders. Reminders are scheduled and triggered entirely on-device using Android's native AlarmManager.
- **Activity Recognition (`ACTIVITY_RECOGNITION`)**: Used optionally to detect daily step counts natively through the local device sensors, keeping your health data offline.

## 4. Backups
Because Mlue operates entirely offline, it relies on Android's native Auto Backup feature. If you have Google Drive backups enabled on your device, your encrypted app data (including your habits) is securely backed up to your personal Google Drive account by Android, independent of our servers. We do not have access to these backups.

## 5. Changes to this Policy
We may update this Privacy Policy as we add new features (such as opt-in syncing or data export). Any changes will be reflected in the app and its version notes.

## 6. Contact
If you have any questions or feedback during our Closed Beta, please reach out via our official feedback channels.

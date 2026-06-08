# Changelog

All notable changes to Mlue will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-06-08

### Added
- **Legal Center**: Added native, offline-first viewers for Privacy Policy, Terms of Use, and Open Source Licenses.
- **Version Info**: Added explicit version information screen displaying the offline-first philosophy.

### Changed
- **Release Promotion**: Promoted the app from Beta to Version 1.0.0 Production.
- **Snackbar Physics**: Fixed snackbar clearance to correctly avoid overlapping the bottom dock.
- **Visual Polish**: Improved the "Create Goal" CTA visual contrast for accessibility.
- **Reminders**: Improved notification scheduling diagnostics for Doze reliability.

### Removed
- **Permission Cleanup**: Stripped the unused `ACTIVITY_RECOGNITION` permission for flawless Play Store privacy compliance.
## [0.9.0-beta1] - 2026-06-01

### Added
- **Closed Beta Infrastructure**: Enabled R8/ProGuard minification, retaining critical reflection models for DataStore, Room, and Compose.
- **Smart Notification Suppression**: Reminders now silently swallow if the user has already completed the habit for the day, preventing notification spam.
- **Data Protection**: Enabled safe deletion confirmations on the Home Screen.
- **Crash Observability**: Integrated Android Vitals as the primary crash diagnostics tool, opting against heavy third-party telemetry to preserve offline-first privacy.

### Changed
- **Permissions Restraint**: Removed `USE_EXACT_ALARM` to comply with strict Play Store guidelines for non-alarm apps. Reminders now fallback gracefully to standard Doze-aware alarm boundaries or prompt user manually on Android 14+.
- **Version Bump**: Bumped to `versionCode 3`, `versionName 0.9.0-beta1`.

### Removed
- **Unused Assets**: Cleaned up debug artifacts, placeholder strings, and redundant permission requests.

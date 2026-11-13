# Preview validation

Validated on 13 November 2026 for v0.3.0.

## Automated checks

- `testDebugUnitTest`: **16 passed**, zero failures/errors.
  - overlapping sleep intervals, missing versus zero health values, manual overrides;
  - monotonic reading timer, 20-minute cap, reboot handling;
  - alcohol calculation, preservation of progress after a lapse;
  - JSON round-trip and atomic reading-session state transition;
  - migration from 0.1 with preserved history, meditation/reminder round-trip, and reminder times across DST changes;
  - finishing and undoing books, dated book-history serialization, duplicate-finish prevention, unchanged active timers/reading totals, and loading 0.2 records without a book-history field.
- `connectedDebugAndroidTest`: **8 passed** on Android 16 / API 36, ARM64 Pixel 6 emulator.
  - onboarding gate and all five primary navigation destinations;
  - active timer across activity recreation, saving exactly once;
  - choosing/changing books, cancelling/confirming completion, undoing completion, and selecting the next book while the active timer and reading totals survive recreation;
  - manual sleep and private check-ins persist across activity recreation;
  - encrypted vault contents, successful decryption and deletion;
  - meditation logging, recreation and clearing;
  - reminder receiver posts neutral text with secret lock-screen visibility, and disabling cancels it;
  - notification meditation route remains gated by app lock and does not automatically log practice.
- `lintDebug`: **0 errors, 8 warnings**. Warnings concern newer dependency versions, target SDK level, and a suggested Kotlin URI extension; none are suppressed by a lint baseline.
- `assembleRelease`: successful optimized, resource-shrunk personal preview.
- `apksigner verify`: APK signature verifies (v2 signing).
- Packaged manifest inspected: read-only sleep/steps, local notifications and boot reception; **no Internet permission**.
- Update APK signing certificate matches the published 0.2 preview, allowing in-place installation.

## Visual and device checks

- Installed the published 0.2 APK in a fresh Android 16 / API 36 Pixel 6 emulator, chose a book and paused a reading session, then installed 0.3 over it with no uninstall. The selected book, onboarding state and exact paused timer value survived the update.
- Visually inspected the 0.3 release Today and Read screens. Today has no connection/sync buttons or restedness question; both book actions fit above the unchanged timer.
- Updated Today and Reading previews in `dist/previews/` from the actual 0.3 release app with local test state. The Welcome preview is unchanged.
- Previously confirmed the system Health Connect consent screen lists only **read Sleep** and **read Steps** on Android 15. The permission set and sync implementation are unchanged in 0.3.

The reminder receiver was invoked in the emulator test; a full overnight wait for inexact alarm delivery was not performed.

## Remaining real-device checks

- Reminder delivery under the phone's battery restrictions, reboot and timezone changes.
- Actual wristband → Google Fit → Health Connect → Steady 90 sleep/step delivery.
- Revoked/partial permissions on the user's phone; imported-source selection when multiple real sources are present.
- Device credential lock with the user's configured PIN/password.
- Other Android versions, unusually large accessibility font settings, manufacturer-specific Health Connect behavior, and daylight-saving travel scenarios.

The preview uses foreground health refresh, not background sync. No tests establish clinical effectiveness, diagnostic accuracy or treatment efficacy.

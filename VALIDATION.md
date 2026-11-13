# Preview validation

Validated on 3 November 2026.

## Automated checks

- `testDebugUnitTest`: **14 passed**, zero failures/errors.
  - overlapping sleep intervals, missing versus zero health values, manual overrides;
  - monotonic reading timer, 20-minute cap, reboot handling;
  - alcohol calculation, preservation of progress after a lapse;
  - JSON round-trip and atomic reading-session state transition;
  - migration from 0.1 with preserved history, meditation/reminder round-trip, and reminder times across DST changes.
- `connectedDebugAndroidTest`: **7 passed** on Android 15 / API 35, ARM64 Pixel 6 emulator.
  - onboarding gate and all five primary navigation destinations;
  - active timer across activity recreation, saving exactly once;
  - manual sleep and private check-ins persist across activity recreation;
  - encrypted vault contents, successful decryption and deletion;
  - meditation logging, recreation and clearing;
  - reminder receiver posts neutral text with secret lock-screen visibility, and disabling cancels it;
  - notification meditation route remains gated by app lock and does not automatically log practice.
- `lintDebug`: **0 errors, 8 warnings**. Warnings concern newer dependency versions, target SDK level, and a suggested Kotlin URI extension; none are suppressed by a lint baseline.
- `assembleRelease`: successful optimized, resource-shrunk personal preview.
- `apksigner verify`: APK signature verifies (v2 signing).
- Packaged manifest inspected: read-only sleep/steps, local notifications and boot reception; **no Internet permission**.
- Update APK signing certificate matches the 0.1 preview, allowing in-place installation.

## Visual and device checks

- Installed and launched the compressed release APK on the Android 15 emulator.
- Confirmed the system Health Connect consent screen lists only **read Sleep** and **read Steps**.
- Visually inspected onboarding, dashboard and reading screen; adjusted the reading ring to keep the primary button higher on the screen.
- Previews in `dist/previews/` show the actual app with empty/local test state, not a web mockup.

The reminder receiver was invoked in the emulator test; a full overnight wait for inexact alarm delivery was not performed.

## Remaining real-device checks

- Reminder delivery under the phone's battery restrictions, reboot and timezone changes.
- Actual wristband → Google Fit → Health Connect → Steady 90 sleep/step delivery.
- Revoked/partial permissions on the user's phone; imported-source selection when multiple real sources are present.
- Device credential lock with the user's configured PIN/password.
- Other Android versions, unusually large accessibility font settings, manufacturer-specific Health Connect behavior, and daylight-saving travel scenarios.

The preview uses foreground health refresh, not background sync. No tests establish clinical effectiveness, diagnostic accuracy or treatment efficacy.

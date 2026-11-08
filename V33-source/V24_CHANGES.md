# Reps100 V24 (working source)

Based on the previously updated V23 source.

- Replaced the top-left Community icon with a three-person symbol enclosed in a ring, styled white on the existing blue circle.
- Replaced the top-right Settings target-like icon with an eight-tooth gear symbol, styled white on the existing blue circle.
- Preserved existing 43dp icon circles and 58dp header buttons with the same dark navy tile background as the menu.
- Community and Settings remain only in the header, not duplicated in the lower grid.
- Set Android versionCode=24 and versionName=24.0; applicationId remains com.reps100.app.
- Updated V23 wording in two placeholder screens to V24.

This is a source-only work-in-progress; APK build and device testing have not been performed.
Signing keystore is intentionally excluded.

## Workout timing amendment (working source)
- Added pre-workout overview with routine name, rep/set configuration, exercise order and potential calories.
- Workout set button now cycles Start exercise → Stop exercise → Next (or Workout Complete on final set).
- Added visible elapsed set timer beside the rep count; sets no longer start automatically on navigation.
- Captures measured lifting seconds separately from rest/transition intervals between sets.
- Includes measured rest/transition time at recovery intensity in completed-workout net calories and saves `restDurationsSec`, `restDurationSec` and `restCalories` in the workout record.
- Existing exercise order (exercise 1..N for each set round) is retained.
- Not yet compiled or tested on an Android device.

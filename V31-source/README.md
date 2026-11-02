# Reps100 v2

Offline-first native Android fitness app. Every selected exercise is performed for exactly 100 reps, distributed across a configurable number of sets.

## v2 features
- Reliable Android/system back navigation to the previous screen.
- Current-date rolling 7-day Completed Routines calendar.
- Calendar can page backwards to the app's package install date; pre-install dates are disabled.
- Completed routine records with date/time, duration, exercises, weights and estimated calories.
- Daily calorie bars and day-by-day workout details.
- Current streak and best streak on the home page.
- Personal profile: biological sex, age, height, weight and waist.
- Editable weight per exercise in Create Routine, with kg/lb selection.
- Sets & Reps Manager with sliders and validation for exactly 100 reps.
- Core category and rep-based exercise filtering. Timed/static exercises such as planks and carries are excluded from the 100-rep library.
- Exercise detail layout supports start/extended and contracted/midpoint image panels. Existing generated assets are used as the current fallback until exercise-specific pairs are populated.
- Offline local persistence via SharedPreferences; no account or network is required.

## Build
Run `./upgrade_v2.sh` if the app source has not yet been generated, then:

`./gradlew assembleRelease --no-daemon`

APK: `app/build/outputs/apk/release/app-release.apk`

## Security
The release keystore is intentionally not included in distributed source packages. Keep signing keys outside Git and outside source ZIPs.

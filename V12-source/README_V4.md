# Reps100 V4

Reps100 V4 is a native Android, offline-first 100-rep workout app.

## V4 changes

- Android 15/system-bar safe layouts using dp-based sizing and window insets.
- Centralised full-screen page layout and scrolling on long screens.
- Main menu grid with Create Routine, Sets & Reps, Routines, Exercises, Completed and Profile.
- Sets & Reps Manager on the main menu with 1–10 sets and min/max rep sliders; every exercise still totals exactly 100 reps.
- Routine creation supports per-exercise weight and kg/lb units.
- Choosing Add Exercise and then adding an exercise automatically returns to the routine being edited.
- Exercise filters include Bodyweight, Weightlifting, Chest, Back, Legs, Shoulders, Arms and Core.
- Timed/static exercises and Tempo/Pause duplicates are hidden from new routine selection.
- Exercise detail/workout screens support `reps_photo_<photo>_start` and `reps_photo_<photo>_mid` image assets, with existing photos used as fallback until the full two-image library is populated.
- Personal Profile includes Biological Sex, Age, Weight, Height and Waist with Metric/Imperial switching (cm/kg or ft+in/lb).
- Body measurement illustrations show full-height measurement and horizontal waist measurement.
- Estimated calories are recorded per exercise and per completed routine.
- Completed Routines includes current dates, a rolling 7-day calorie chart, history back to installation date, duration, exercise weights and per-exercise calorie breakdown.
- Current streak and record streak appear on the main screen.
- Workout completion displays the estimated total calories burned.
- Saved routines and completed records can be deleted.

## Build in Codespaces

Make sure `local.properties` points to your Android SDK, for example:

```properties
sdk.dir=/home/codespace/android-sdk
```

Then build:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean assembleRelease --no-daemon
```

The unsigned APK is normally created at:

`app/build/outputs/apk/release/app-release-unsigned.apk`

Sign it with your existing Reps100 release keystore. Do not commit the keystore to GitHub.

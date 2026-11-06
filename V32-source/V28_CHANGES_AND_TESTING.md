# Reps100 V28 test source

Based on Reps100-V27-Matte-3D-Buttons-Source.zip.

## Implemented
- Version code 28 / version name 28.0, package unchanged.
- Nine-button, three-column home menu: six existing functions, plus Daily 100, Challenges and Achievements.
- Three new satin-blue metal-ring button symbols with press animations.
- Daily 100: multiple exercises each day; save individual sets of 1–remaining repetitions; automatic completion only at 100; persists locally by date.
- Searchable exercise picker drawing on the existing exercise library.
- Challenges: daily double/triple, weekly five/ten, first completion.
- Achievements: milestones for completed 100s and active days.
- No wellbeing tracking added; Profile and Health Connect integration retained.

## Test before installation
1. Build with Java 21 and Android SDK 36: `./gradlew assembleRelease`.
2. Sign with the SAME existing V27 keystore to preserve installed app data.
3. Verify package `com.reps100.app`, versionCode 28.
4. Check all nine home buttons and original pages.
5. Add two Daily 100 exercises, record partial sets, leave/reopen the page and confirm persistence.
6. Finish 100 reps and confirm Challenges and Achievements progress; confirm 99 reps does not complete.
7. Confirm old routines and completed history remain intact after upgrade.

## Deliberately not included yet
- Scheduled notification/reminder permissions and background scheduling.
- Garmin Connect direct sync (requires a separate supported integration design).
- Wellbeing questionnaires or health scores.
- Direct Health Connect export of Daily 100 sets (not yet mapped to workout sessions).

This archive is source code, not a signed APK. Compilation and on-device QA must be performed in Codespaces; this environment does not have the Android SDK.

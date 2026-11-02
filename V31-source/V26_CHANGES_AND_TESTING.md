# Reps100 V26 — hybrid calorie estimator and workout validation

Based on supplied Reps100-V25-source-Health-Connect-Fix.zip.

## Changes
- Version code 26, version name 26.0.
- Set-by-set plausibility validation based on conservative exercise-specific minimum repetition times.
- An implausibly fast set triggers a dialog asking whether the session was a test.
- Test and unverified sessions are stored in SharedPreferences key `flaggedWorkoutRecords`, not the genuine `records` array. They do not count toward normal history/statistics or Health Connect exports. Their top-level `calories` is set to zero; the calculated estimate is retained in `estimatedCaloriesBeforeVerification` for diagnostics.
- Verified sessions use a 75% measured-time MET / 25% repetition-assumed-time MET blend. This is a provisional heuristic, not a validated biomechanical per-rep model; both branches use existing load and range-of-motion adjustments.
- Existing V25 historical records are left unchanged.

## Known limitations / review before public release
- The 75/25 blend and conservative timing limits require calibration with real workout observations.
- The model does not yet calculate mechanical work independently from MET values or provide a catalog of measured per-rep energy constants. Do not describe it as an absolute calorie floor.
- Flagged sessions are preserved in private app data but have no dedicated history/review screen yet.
- There is no public-release enforcement switch yet. This is the agreed development-stage warning behavior.
- No Android build was run in the artifact creation environment; compile and test in Codespaces.

## Suggested tests
1. Realistic 100-rep sets should save normally and optionally export to Health Connect.
2. A 100-rep push-up set stopped after 5 seconds should show the test/unverified dialog.
3. Selecting Test should not increase history, streaks or Health Connect records.
4. Selecting No should retain an unverified record separately and not export.
5. Selecting Review should return to workout UI without saving.
6. Compare V25 vs V26 estimated kcal for several realistic recorded workouts.

## Planning calorie estimate update
- The routine creator and library cards now call the same 75/25 hybrid estimation engine as saved workouts.
- Estimates use each exercise's default rep pace, MET, bodyweight, resistance and ROM adjustments, with a quick/slow pace range.
- Planning assumes 100 reps per exercise and 30–90 seconds of rest between sets, matching the current routine structure.
- Draft routine resistance values are passed to the planning estimator.
- The displayed range is a planning scenario, not a scientifically established floor or guarantee.
- Compile and check routine creation/library previews and completed-workout summaries in Codespaces.

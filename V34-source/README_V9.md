# Reps100 V9

V9 is based on the supplied V8 source and the supplied definitive exercise-card ZIP.

## Exercise catalogue
- 128 unique exercise cards are included after de-duplicating the duplicate Bird Dog card and preferring the later accepted Bird Dog asset.
- Previous generic `reps_photo_*` exercise imagery has been removed.
- Exercise detail and workout screens use the supplied generated cards as the definitive visual/instruction source.
- The old Beginner label is not displayed.
- Each exercise detail includes a subtle primary-muscle anatomy overlay; placement alternates between the upper corners to reduce obstruction.
- `EXERCISE_CARD_CATALOG.tsv` records the source image used for every exercise.

## Personal Profile
- New launcher icon installed from the supplied REPS 100 icon.
- Waist-to-hip risk rows are painted as solid row colours.
- BMI reference graphics now use aspect-ratio-preserving fit rather than stretched drawing.
- Measurement fields are narrowed and shown beside the sex-specific supplied measurement image.
- Male/Female measurement image switches with Biological sex.
- Measurement guide appears before Your results.

## Routine / workout interaction
- Create Routine exercise cards have a hold-and-drag reorder handle.
- Workout has a smaller Back control beside Next and returns to the previous exercise/set step.
- Roulette-style click audio is used for buttons and throttled list scrolling in Exercises and Routines.

## Health / Garmin integration status
Android Health Connect can represent strength-training exercise sessions and calories, but production sync requires the Health Connect permission/onboarding flow and store declarations. Garmin Connect's public Activity API is for receiving Garmin-recorded activities; Garmin's public push APIs cover structured workouts/training plans rather than uploading a third-party completed activity directly. Automatic Garmin completed-activity sync therefore requires an approved Garmin integration/backend and cannot be truthfully implemented as a local-only Android source change.

The existing local Completed Routines history remains authoritative in this source package. Health Connect/Garmin production account integration should be completed once the app's distribution identity and developer credentials are available.

## V10 health integration additions
- Version 10.0 / versionCode 9.
- Health Connect write integration for completed strength-training sessions and total calories burned.
- User-controlled Health Connect permission request in Personal Profile > Health integrations.
- Completed routine title and exercise/repetition summary are attached to the Health Connect exercise session notes.
- Garmin Connect integration point is documented in-app but intentionally disabled until Garmin Developer Program approval and permitted API credentials are supplied. No fake Garmin sync is implemented.
- Google Play release still requires the developer's Health Apps declaration and privacy-policy disclosures.

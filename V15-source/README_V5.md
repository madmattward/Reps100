# Reps100 V5 redesign

This source package updates V4 with the requested blue theme, smaller/gapped controls, profile labels that remain visible, generated photorealistic male/female profile guide images, improved exercise search normalization/aliases, expanded bodyweight/core exercise coverage, bodyweight load estimates, more detailed exercise guidance, and a linked 0–1200 kcal history chart with tiered colours and year display.

## Bodyweight load estimates
The app records an *estimated effective bodyweight load* for bodyweight movements. These are approximations for workout logging, not scale measurements or medical/biomechanical assessments. For a standard push-up the app uses about 72% of body mass, based on published measurements of roughly 69% at the top and 75% at the bottom. Other bodyweight factors are practical approximations and vary with technique, limb lengths, support points, and range of motion.

## Build
Use Java 21 and Android SDK 35, then run:

    ./gradlew clean assembleRelease --no-daemon

Do not place the release keystore in this source archive or commit it to Git.

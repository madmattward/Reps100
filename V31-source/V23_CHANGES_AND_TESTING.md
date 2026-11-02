# Reps100 V23 additions

- Community and Settings header buttons, new screens, and informational placeholders (no registration, cloud sync or notifications implemented).
- Wake Up Workout seeded into saved routines once (only known exercise names), respecting existing routines and deletion.
- Planning estimates for time and potential net exercise calories on routine editor and saved routine cards. Uses 100 repetitions per exercise, 1–3 seconds/rep, and an explicit provisional 30–90 second rest per set transition because this source does not expose a configurable rest-period setting. Estimate uses existing MET categorisation and current body weight. It is approximate, not a clinical measure.
- Prior V23 button styles and reorder code retained.

## Build status
Not compiled in this environment: Gradle wrapper attempts to download its distribution, and internet access is unavailable. Build and test in Codespaces before installation. Check navigation, icon layout, first-launch routine seeding, persistence after deletion, routine estimates, reorder, and swipe-to-delete.

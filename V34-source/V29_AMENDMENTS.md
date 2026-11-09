# Reps100 V29 — amendment 1

- Daily 100, Challenges and Achievements menu buttons now use a deeper dimensional blue face, a silver ring and crisp white symbols consistent with the original six menu buttons.
- Existing press-down animation, click sound, haptics, destinations and nine-card grid are unchanged.
- This is the V29 working source based on the latest V28 source archive.
- Build not verified in this environment; compile and test in Android Codespaces.

Amendment 7: Completed Daily 100 exercises appear in Completed Workouts on their original dates, with rep and set counts and confirmed deletion. No invented calories.

Amendment 8: Daily 100 exercise cards show rep-only estimated calories, updated from total recorded reps. Completed Workouts Daily 100 entries display the same estimate. The calculation uses the existing MET engine and profile weight, with no assumed rest; routine workout estimates are unchanged. Historical values recalculate if profile weight changes.
\nAmendment 9: When Daily 100 reaches 100 reps, snapshot estimated kcal into the exercise record (completedKcal), with completion timestamp. Completed Workouts displays the snapshot rather than recalculating against future profile changes. Earlier completed records without snapshots use legacy calculation until migrated; no retroactive historical weight can be recovered.\n
Amendment 10: Updated the Daily 100 introductory and footer descriptions as requested. No changes to workout logic.

Amendment 11: Preserve Create Routine scroll position during swipe-delete and drag-reorder; restore viewport before first redraw to prevent top-of-page flash.

Amendment 12: Daily 100 drag/drop and swipe/delete now refresh the existing ScrollView in place, restoring scroll before first draw. Unchanged drops avoid refresh. Set recording and exercise adding preserve position too.

Amendment 13: Corrected Trap Bar Shrugs (Weightlifting, Barbell, Back, Shoulders), Tricep Kickback (Weightlifting, Dumbbell, Arms), and Standing Cable Crunches (Weightlifting, Cable, Core) in the exercise catalogue and display metadata.

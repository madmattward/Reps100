# V34 Standard Fitness Achievements enhancement

This source variant retains package/version 34 and the existing Centurian theme.

- Standard Challenges: adaptive weekly workout and repetition targets based on the previous week; Easy, Balanced, Ambitious difficulty settings. Targets are frozen per week and difficulty setting. Personal-best weekly challenge.
- Standard Achievements: futuristic blue/metallic programmatic badges with Bronze, Silver, Gold and Platinum tiers; lifetime repetitions, completed workouts, training days and exercise variety; best weekly repetitions display.
- Counts verified completed routine records and completed Daily100 exercises. Unverified routines are excluded.
- New Daily100 completions are archived in a separate local ledger so they remain counted after Daily100's one-month detail-history retention. The ledger is synced with currently available Daily100 history on viewing the Standard pages.
- Existing Centurian theme and main menu layout are unchanged.

Important: A previously expired Daily100 entry cannot be reconstructed if no completed record remains. Deleting a Daily100 exercise after it was completed does not currently retract an archived achievement entry. The badge designs are programmatically drawn placeholders (not custom illustration assets), and unlock timestamps are not yet tracked per tier. Build and on-device regression testing are required before distribution.

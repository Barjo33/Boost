---
name: hr-glucose-leadlag-null-2026-07-06
description: "HR→glucose-rise lead is NULL (37k paired cycles, 6 users, ~1,900 rise onsets, de-confounded). Only real coupling: HR↑ leads BG↓ by ~10min (exercise, p=0.003) — wrong direction for meal detection. Sedentary rise onsets show HR DIP, no cephalic lift. Hypo tachycardia positive-control passes (weak on DB 15-min-smoothed hr_avg, strong +13.6bpm on instantaneous NS hrBpmLatest) — the smoothing blunts transients. HR is NOT a meal-signal input as currently sensed."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**HR↔glucose lead-lag (2026-07-06, TimescaleDB-wide per Tim's DB-first directive).** 37,141 paired 5-min cycles across 6 users (tim 10.6k incl. Garmin era from March, F 13.5k, A 9.2k, C/D/H smaller); ~1,942 rise onsets.

- **Correlation**: raw BG↔HR +0.18 is a CIRCADIAN ARTIFACT (both peak midday); residualized = 0 to weakly negative every user (pooled −0.08). The 9-day small-sample "sedentary +0.19 / asleep +0.38" did NOT replicate — noise. (DB-first directive vindicated immediately.)
- **Lead-lag**: single significant CCF feature — |r|=0.063 at lag −10 min, NEGATIVE, p=0.003 (circular permutation): HR rises ~10 min before BG FALLS = the exercise/uptake coupling, stronger in active cycles, redundant with steps. **No positive HR-leads-rise signal at any horizon ±90 min.**
- **Event-locked**: sedentary rise onsets (n=1,098) show HR DIPPING −1.5 to −2 bpm — no cephalic/anticipatory lift; the faint pre-rise lift in pooled data is entirely the active/movement subset (symmetric = confound). Highs >180: flat.
- **Positive control (hypo tachycardia)**: passes — weakly (+1.4–1.7 bpm) on the DB's 15-min-smoothed hr_avg, strongly (+6 to +13.6 bpm, p=0.014, small n) on instantaneous NS hrBpmLatest → **the DB's 15-min HR smoothing erases transients**; any future HR-transient analysis needs instantaneous/1-min HR (consider wiring hrBpmLatest into the extractor).
- **Verdict**: HR does NOT substitute for the retired meal-time learner and earns no place in the meal-signal score as currently sensed; its only algorithmic value is movement confirmation (already covered by steps) and possibly hypo corroboration via UNSMOOTHED HR (untested at power). What would decide properly: instantaneous HR + true ingestion timestamps (CGM onsets lag eating 10–20 min, smearing any cephalic blip), more symptomatic-hypo events.

See [[early-dosing-audit-2026-07-03]] (meal-time learner retirement), [[feedback-prefer-timescaledb]].

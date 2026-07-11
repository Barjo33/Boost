---
name: resume-point-2026-07-11
description: "⭐ RESUME POINT (2026-07-11) — READ FIRST. UKF CGM smoothing DONE + shipped: v4 UnscentedKalmanFilter (RTS + adaptive R) with a grafted IOB-gated compression-low damper, on Boost-V7-shadow + cherry-picked/pushed to V6-experimental + dev. Reproducible benchmark + public 'retire exponential' report (PDF on Drive); G7/One+ = primary real cohort. Overnight compression-low confirmed. Aggression→confirm mechanism nailed. APK on Drive."
metadata:
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**WHERE WE ARE (2026-07-11).** Prior: [[resume-point-2026-07-09]]. Big smoothing session.

**UKF CGM SMOOTHING — DONE & SHIPPED.** Full record: [[ukf-adaptive-smoothing-2026-07-10]].
- Investigated the tsunami UKF → ported to 3.4 → then REPLACED with the more advanced **v4 `UnscentedKalmanFilterPlugin`** (forward filter + **RTS** backward pass + adaptive R; from the local `~/StudioProjects/AndroidAPS-v4-port` `boost-v4-port` branch). Selectable smoothing option, OFF by default.
- **Grafted IOB-gated compression-low damping onto v4** (commit `5eb31b9365`): v4 tracked compression lows down (its "chi-squared" check is DIAGNOSTIC-ONLY — logs/counts, no rejection; and it has no IOB). Fix = raw-baseline drop trigger (>30 mg/dL fall) + `IOB<2U` gate + soft R=900 (zero-lag Q suppressed) + 15-min cap; fail-safe (disabled when IOB unavailable). Log line: `UKF: Compression-suspect low`. 9/9 unit tests.
- **SHIPPED to 3 branches** (all compile + Dagger-valid + 9/9 tests): `Boost-V7-shadow` (origin — 15 local commits UNPUSHED, Tim's call), **`Boost-V6-experimental` + `dev` PUSHED** (fast-forward; `5bfa12f717` / `e2b8f46cc7`). APK **`Boost-V7shadow-UKF-v4-compression-2026-07-11.apk`** on [user-Drive-account] Drive.
- **Reproducible benchmark** `backtesting/scripts/2026-07-ukf-smoothing/repeatable/benchmark.py` (numpy-only, seeded, `--db --sensor G7`) + **public report** `ARGUMENT_retire-exponential-adopt-ukf.md` (PDF `Boost_UKF_vs_Exponential_2026-07-10.pdf` on Drive) = argue **retire exponential, adopt UKF**. Written in Tim's plain measured register.

**KEY TRAPS/LESSONS (now in CLAUDE.md):** (1) the phase2 CGM export (`oref_phase2_sites_v2`) INTERLEAVES upload streams → MUST 5-min dedup or it's a sawtooth; (2) **one-step-predict-next-RAW is a noise-chasing lens** (favours raw on a clean sensor like G7) — measure smoothing by noise-removal + lag + ground-truth, not one-step; (3) never `git add -A` while a subagent shares the repo (swept its files into my commit); (4) a backgrounded `gradle | tail` reports `tail`'s exit not gradle's → nearly shipped a stale APK; verify the new class is in the dex; (5) local `dev` was 29 behind origin → sync before cherry-pick/push; (6) recurring stale-KSP artifact (`ActivitiesModule…`) — a clean re-run clears it.

**OVERNIGHT 07-10→11:** severe-looking TBR (nadir 39, ~100 min <54) = **compression, Tim-confirmed** (near-zero IOB, Boost IDLE throughout, jagged multi-dip + data gaps). The UKF WAS running but didn't help → this is what drove the compression-damping graft. HR 58% present / worn 46% (strap-fit; new strap ~Wed 07-15 to retest density). Sleep state still not in the extractor. Extractor now current to 07-11 08:32.

**AGGRESSION→CONFIRM (code finding):** raising Boost aggression makes CONFIRMED shots BOTH bigger (`mealActionMultiplier` = base×aggression, CONFIRMED only) AND earlier (the OBSERVING→CONFIRMED **dose-adequacy gate**: `prospectiveConfirmShot = budget×1.8×aggression×velocity` must beat a ~fixed floor, so higher aggression clears it sooner). BUT the agreed fast-carb-crash driver is **late+large** (the big shot's ~3h SMB action outlasts a self-clearing fast carb → lands into the fall); the eager-confirm context was the DISCRIMINATOR not the cause. So aggression's dominant harm on fast carbs is SIZE, and the fix stays V7 (sizes down). [[fastcarb-confirm-crash-2026-07-10]].

**STILL OPEN:** V7-shadow's 15 commits unpushed (Tim left local); golden-vector Kotlin↔Python parity test (before quoting UKF absolute numbers); strap-fit density re-test ~Wed 07-15; the public git author on this repo is the [user-Drive-account] Gmail (Tim's config, FYI). Methodology + traps: [[feedback-anonymize-before-github]], [[feedback-backtest-protocol]].

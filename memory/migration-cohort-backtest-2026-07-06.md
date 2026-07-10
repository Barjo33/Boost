---
name: migration-cohort-backtest-2026-07-06
description: "4-agent cohort backtest of the auto-config migration (b2c0705e5e) across 7 AAPS users: mechanism VALIDATED (A/C/F rescued, D correctly tightened −26% @2.5:1 protective) but 5 amendments required before cohort rollout — historical-factory-defaults BLOCKER (C/D freeze risk), cumulative max(5,conf) clamp collapse, cumulative-from-resolved, n≥10 bolus guard, TBR>4% raise-guard (B case). Amendments implemented same day."
metadata: 
  node_type: memory
  type: project
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Migration cohort backtest (2026-07-06, 4 parallel agents, users tim/A/B/C/D/E/F/H).** Per-user upshot:
- tim: only ccap 0.5→0.40 (TDD/40 on U200 pump-units), −0.02 U/day, neutral.
- A: textbook rescue (0.5→1.21, 2.5→6.8, cum→6.8): +3.4–4.5 U/day at 2.1% pre-low vs 3.4% base. BUT conf 6.8 from n=4 boluses; cum==conf.
- B: CAUTION — deliberately reverted 0.6→factory on 07-04; migration would set ccap 1.54 (3× his choice); added insulin 44.7% pre-low vs 32.8% base (TBR70 4.26%). THE raise-guard case.
- C: old-era factory 0.25/1.0 → 1.20/4.00, +44% shadow insulin. Aggr flips 1.0/0.92 on the 4% TBR boundary by window.
- D (hypo-heaviest, 11.7% TBR70): tightens hard — HC 2.0 (clamped from raw 3.5), aggr 0.85, FCC OFF, cum 2.9 → net −26%, removals 2.5:1 protective:costly. Safety half validated.
- E: ccap→0.62 raises confirm floor → blocks most of his tiny confirms (budgets ~0.2U) — "confirm phase mostly off"; cum tightening vacuous. Exposed cumulative-from-DERIVED-not-resolved incoherence (kept conf 2.0, cum sized from derived 4.65).
- F: main change conf 2.5→6.0 (clips ~1/day today, 0/7 unclipped events pre-low) — ship. Set ccap 0.8 manually 07-05.
- H (user H): near no-op — he self-escalated ccap 1.2→**1.8** on 07-06 morning (above formula 1.26). Sole effect cum 10→6: on V1-like flow binds ~1/5-6 days, up to 3.5U/episode held; the max(5,conf) CLAMP causes 6/8 suppressions (unclamped 8.5 → only 2).

**Five amendments (implemented same day, follow-on commit after b2c0705e5e):** (1) BLOCKER historical factory defaults — old builds shipped ccap 0.25/conf 1.0/cum 6.0 (strings-era 1.5); value-vs-CURRENT-factory test would freeze C/D at tightest-ever values ("stuck-users surviving the stuck-user fix"); fix = per-key sets of ALL historical defaults (verified from git history) + per-knob classification logging. (2) cumulative clamp → clamp(conf+2×ccap, 1.0, 10.0) (kills single-confirm-exhausts-hour). (3) cumulative computed from RESOLVED (kept-or-derived) caps. (4) confirmedCap manual-bolus p90 term needs n≥10 else p95-SMB only. (5) TBR raise-guard (Tim approved): dose-cap RAISES not applied when TBR70>4% — notify-suggest instead; lowerings/tightenings always apply. +cosmetic: ccap rationale string now says max(routine SMB, TDD/40).

**Field notes**: user H actively self-escalating (1.8 > formula) — formula may run conservative for announced-meal users; his RECOVERING-during-rise re-engage revisit is pending his 7-day window.

**07-06 AFTERNOON STATE**: Amendments `fe9d8a1a13` (27 tests) + **versioned re-migration `131923247e`** (31 tests; AUTO_CONFIG_SCHEMA_VERSION=2 rescues installs stranded by the un-amended promoted APK — b2c0705e5e-era resolution flags carried NO outcome detail, so v2 re-audits every resolved knob) + **ML renorm parity fix** (1.25→1.2299: weights sum 1.07 not 1.0; found by the TRIO AUDIT — Trio had deliberately corrected it; simulator updated in step; ~1.6% score over-scale during ML outages, marginally-earlier confirms exactly when ML missing). = 3 commits LOCAL on experimental, UNPUSHED. **TRIO PORT COMPLETE + PUSHED** (tim2000s/Trio Boost-in-Trio-v0.1 @ 3e9aeeaac): audit showed Trio was ALREADY synced through the 07-02 batch; ported early-confirm/fast-path-retune/post-rescue-cap/per-knob-autoconfig; 279 swift tests; xcodebuild gotcha: `-sdk iphonesimulator` now breaks watch targets — build WITHOUT -sdk. Trio follow-up port (amendments+re-migration scaffold) IN FLIGHT. **RESOLVED (07-06 eve): everything pushed + released.** Android experimental == dev == `69f4a928fc` (amendments fe9d8a1a13 + re-migration 131923247e + ML renorm 69f4a928fc). Trio pushed through `fccae0df6` (follow-up port: Trio had its OWN 3-day factory-era window 06-23→26 [ccap 0.25, conf 1.0]; cumulative 6.0 is TUNED on Trio not factory; schema-version scaffold at v2 matching Android; 292 tests). **Definitive APK `Boost-V6-experimental-autoconfigv2+renorm-2026-07-06.apk` on Drive — safe for ALL cohort users incl. C/D; the hazard-bearing promoted-2026-07-06.apk was DELETED from Drive.** Tim's cumulative cap = 5.0 self-set 07-05 (never bound on 07-06; at 2.5 both confirms would have been clipped). **Weekly DB job**: launchd com.boost.weekly-backfill (Sun 06:00, missed-run-on-wake) → hardened backfill_all.sh; being extended with stage-2 oref-cohort (28 live-probe sites → oref_live table, sites_all.json registry) — verification run in progress.

See [[user-h-diagnosis-2026-07-05]], [[recovering-highs-smb-rejected-2026-07-03]] (harm-pricing discipline), [[tools-rework-2026-07-03]] (promotion state).

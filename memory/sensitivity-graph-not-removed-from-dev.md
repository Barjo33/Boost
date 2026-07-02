---
name: sensitivity-graph-not-removed-from-dev
description: "Boost overview: there are TWO overview fragments. The v2 UI (BoostOverviewV2Fragment) ALREADY shows a steps/HR 3rd graph (sensitivity replaced). The sensitivity ratio graph survives only in the legacy v1 BoostOverviewFragment."
metadata: 
  node_type: memory
  type: project
  originSessionId: d9e18601-4d49-42cd-9289-f1b24cf4e999
---

**Two Boost overview fragments, chosen in `OverviewPlugin.kt` (~L157-158) by preference:**
- `BooleanKey.OverviewUseBoostOverviewV2` → **`BoostOverviewV2Fragment`** = the **v2 UI** (BgBobble, stat pills, inline time-range buttons). **Experimental-line ONLY — does NOT exist on the shadow line.**
- `BooleanKey.OverviewUseBoostOverview` → **`BoostOverviewFragment`** = the legacy **v1** Boost overview.

**The v2 UI 3rd graph is ALREADY steps/HR (sensitivity replaced).** In `BoostOverviewV2Fragment.kt` (~L853): comment *"Activity graph (steps + heart rate) — replaces the old sensitivity preview"*. It's **data-driven** — plots whichever of steps/HR has data (`heartRateGraphSeries`/`stepsCountGraphSeries`, populated unconditionally by `PrepareTreatmentsDataWorker`), so it does NOT depend on the chart-menu HR/STEPS toggles (which default OFF). Label = "Heart rate · Steps"/"Heart rate"/"Steps". The **legacy `v2SensitivityGraph*` view IDs are repurposed, not renamed** — so the "sensitivity" name in the XML/binding is cosmetic, not a sensitivity graph. So Tim's memory ("removed sensitivity, replaced with HR/Steps on the v2 UI") is CORRECT.

**Where a real sensitivity ratio graph still lives:** ONLY the legacy v1 `BoostOverviewFragment.kt` (~L902-1030) — computes deviation ratio from bucketed BG + IOB (fixed by commits `a13fd4598f`/`527aecb882`/`861a7da470`/`5a87583de5`; `b0910c5bc2` added its separate hrSteps graph). If Tim never runs the v1 overview (`OverviewUseBoostOverview`), this is moot. To purge it there: remove the block in `BoostOverviewFragment.kt` + the `sensitivity_graph*` views in `boost_overview_fragment.xml` (NOT the `v2_*` ones).

**Earlier mistake (2026-07-02):** I first reported "experimental still shows the sensitivity graph / it was never removed" — that was because I read the v1 `BoostOverviewFragment`, not the v2 fragment Tim actually meant. The v2 UI was already correct. See [[boost-v6-experimental-state-2026-06-27]].

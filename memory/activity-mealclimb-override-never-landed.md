---
name: activity-mealclimb-override-never-landed
description: Design 9 meal-climb override for Boost activity mode was never implemented in any repo/branch
metadata: 
  node_type: memory
  type: project
  originSessionId: 3ff7c4c5-b663-45b9-a69f-bedcec1e0c9e
---

As of 2026-06-13, the "meal-climb override" (Design 9 in `boost_v4_5_design_sketch.md`) that exits Boost activity mode when a meal climb is in progress was **never coded** — neither in Boost-AAPS-core (all 12 branches incl. master/dev/Boost-V5/V4.4.2) nor in AndroidAPS (Boost-ML-Beta and all local branches). No source file contains `mealClimb`/`climbOverride`; no commit landed it.

Tim believed it was deployed ("haven't seen the issue since I raised it"). It is not. The activity logic in `OpenAPSBoostV3MLG3Plugin.kt` (and V1 `OpenAPSBoost`, V2) still has unguarded `isActive = recentSteps5Min > activitySteps5` and still raises target via `activityBgTarget = 150.0` with no early-exit.

**Why:** The original 2026-05-06 incident (activity target=150 persisting up to 60 min after walking stops, suppressing insulinReq by 50-70% into meal climbs) is unfixed at the activity layer. The reason Tim stopped seeing it is most likely that he moved to **V5**, whose slow-meal/sustainedRise dosing compensates *downstream* — masking, not fixing, the upstream target=150 suppression. Reappears if falling back to V1/V2/V3MLG3.

**How to apply:** Boost-AAPS-core (`/Users/timstreet/StudioProjects/Boost-AAPS-core`) is the source of truth that gets mirrored into AndroidAPS Boost-ML-Beta. Land any activity-layer fix there first, then mirror. Proposed override: `delta >= 5.0 && shortAvgDelta >= 3.0` ANDed into the `isActive` negation. See [[boost_v4_port_complete]].

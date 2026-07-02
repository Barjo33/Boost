---
name: ting-metric-definition
description: "TING = Tim's glucose metric: Time In Normo-glycaemia, 3.5–7.8 mmol/L (63–140 mg/dL). Report alongside TIR."
metadata: 
  node_type: memory
  type: reference
  originSessionId: e3138941-0dfd-4662-b6e9-831ed2e3863a
---

**TING = Time In Normo-glycaemia = % of CGM readings in 3.5–7.8 mmol/L (63–140 mg/dL).**

Tim's preferred tight-control metric, reported **alongside TIR** (Time-in-Range, 3.9–10.0 mmol/L / 70–180 mg/dL). Note TING's *lower* bound (3.5 mmol) is slightly below TIR's (3.9) and its *upper* bound (7.8) is the tight-control ceiling — so it's a "good glucose" band, not the same as the standard TITR (70–140). When Tim asks for "TIR/TING", compute both: TIR 70–180 and TING 63–140 mg/dL.

NS source for his data: `https://nstest3.crabdance.com`, read token at `/Users/timstreet/Nightscout_Work/.ns_token` (units mg/dl, BST utcOffset=60). Used for the V1→V6 outcome analysis (2026-06-29).

**Tim's COMPRESSION-LOW definition (for excluding artifacts from TBR):** an *overnight* (23:30–07:00 local) sensor dropout (>15 min gap) bracketed by low shoulders (<75 mg/dL) that recovers to ≥90 mg/dL within 30 min. Remove the bracketing <75 readings. (Impact is small in his data — 9 readings in Apr, 7 in late-Jun — i.e. his lows are mostly real.)

**Engine timeline (verified from NS `suggested.reason` markers, 2026-06-29) — critical for any "which engine" analysis:**
- **Pure V1** (oref/UAM tiers, no ML): ~20 Mar → ~1 May. Marker: "UAM Boost 1/2 … Enhanced oref1 triggered", NO ML.
- **V4.4.x (G3/ML line)**: ~2 May → ~10/11 Jun. Marker: "G3 pre-UAM uncertainty hold: T5/6/7/8 suppressed" (the ML gate fires only on uncertain cycles, so appears in ~5–15% of records — its mere presence over a stretch = V4.4.x running).
- **V6** active: from ~11–12 Jun, but **STABLE only from 19 Jun** (12–18 Jun = bad-settings shakedown, exclude). Marker: `V5-ACTIVE drove SMB X.XU (V1 would=Y.YU, state=OBSERVING/CONFIRMED/COMMITTED)`. NOTE this exact string changed in the 27-Jun build (became "V6 suppressed (…)" etc.) — so absence of "V5-ACTIVE" after 27 Jun is NOT a reversion. So the valid V1→V6 comparison = **V1 (April) vs V6 (19–29 Jun)**; do NOT use May/early-June as "V1" (it's V4.4.x).

**V1→V6 result (compression-removed, the Discord summary):** same mean (~6.7→6.9 mmol), TIR 83→87%, TING 70→71%, time<3.9 8.0→3.1% (halved), severe <3.0 2.2→0.1% (near-eliminated, into the <1% target V1 missed), CV 38→32. Festival 19–22 ran higher targets (5.5–6.5) by design (more time-high, zero severe lows); non-festival 23–29 matched targets 4.8–5.3.

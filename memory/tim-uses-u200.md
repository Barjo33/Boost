---
name: tim-uses-u200
description: "Tim runs U200 insulin in the Dana-I (confirmed 2026-07-04), settings scaled appropriately; U200 era ≥ 2026-05-03 so ALL 2026 analysis windows are uniformly pump-units. His 'U' figures = 2× insulin mass — fine vs his own history/V1-would, skewed for cross-user absolute-U comparisons."
metadata: 
  node_type: memory
  type: user
  originSessionId: db82de70-d40e-4e73-9c47-395352be1ee8
---

**Tim uses U200 insulin (Dana-I pump), confirmed 2026-07-04.** ISF/CR/basal are all set in pump-units appropriately (his statement), so the engine arithmetic is self-consistent; DynISF self-scales via pump-unit TDD.

- U200 era: since **at least 2026-05-03** (first "New U200 basal" profile switch in NS; earlier history not checked). All boost_decisions telemetry in the 2026 analysis windows is uniformly U200 pump-units — no unit-era mixing; auto-config caps derived from U200-era history are correctly scaled by construction.
- **His "U" numbers = 2× insulin mass.** Same-user comparisons (vs his own V1-would, longitudinal) are unaffected. Cross-user ABSOLUTE-unit comparisons (e.g. "Tim doses X U/day vs user B") understate his mass 2× — flag when doing cohort absolute-U tables.
- Profile switches like "New U200 basal - 16-2u per day lower targets" (2026-07-03 17:12 BST) are tweaks within the U200 family, not concentration changes.

Context: surfaced during the [[postrescue-mealstate-cap-2026-07-04]] forensic (the 2.7 pump-unit SMB = ~5.4 U100-equivalent mass).

---
name: feedback-tbr-4pct-cutoff
description: "Tim's rule (2026-07-06): kill-switches and safety cutoffs key on the CONSENSUS thresholds — TBR<70 > 4% (and TBR<54 > 1%) — not relative changes like 'doubles from baseline'. A user at 1.3% doubling to 2.6% is still fine; reverting there is a false trip."
metadata:
  type: feedback
---

**Kill-switches use absolute consensus TBR thresholds, not relative deltas.** (Tim, 2026-07-06: "when thinking about kill switches, remember 4% is the cutoff for excessive TBR.")

**Why:** consensus targets are TBR<70 ≤ 4% and TBR<54 ≤ 1%. A relative criterion ("revert if TBR doubles") trips inside clinically acceptable territory for low-baseline users (1.27% → 2.5% is fine) and fails to trip for high-baseline users. The auto-config rules already key on 4%/1% (HypoCaution formula, aggression 0.92 rule, TBR raise-guard) — kill-switches must use the same lines for coherence.

**How to apply:** any post-change re-measure spec: revert/alert when TBR<70 > 4% or TBR<54 > 1% (or when a hypo-prone trigger fires: <54 > 1.5% / <70 > 6% = escalate immediately). Trend context is fine to REPORT (direction matters), but the ACTION line is the consensus threshold. Applied retroactively to user A's (user A) cap-raise kill-switch: revert at >4%, not at 2× his 1.27% baseline.

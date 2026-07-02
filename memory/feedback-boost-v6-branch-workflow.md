---
name: feedback-boost-v6-branch-workflow
description: "Workflow rule (Tim, 2026-06-30) — make ALL Boost-V6 changes on Boost-V6-experimental FIRST, then push experimental→dev so the two stay an identical base. Don't push straight to dev."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 673865fa-1e92-4c73-8f69-e6668c7d4f48
---

**Tim's rule (2026-06-30):** for the Boost-V6 fork (`tim2000s/Boost-in-AAPS_3.4`, remote `origin`):

1. **`Boost-V6-experimental` is the working/upstream branch.** Make every change there first, commit it, then push.
2. **`dev` is downstream** — push `Boost-V6-experimental` → `dev` so the two are an **identical base** at all times. Never commit/push straight to `dev` (that's what caused the divergence we had to reconcile).
3. **Keep them in lockstep** so it's easy to track what's going on (one source of truth, no "this is on dev but not experimental" surprises).

**Why:** I broke this earlier in the session by pushing the V6-simulator work straight to `dev`, so `dev` got ahead of `experimental` (2 commits) — confusing. Reconciled 2026-06-30 by fast-forwarding `experimental` up to `dev`; **both now == `6264e34f98`**.

**How to apply:**
- New change → branch/commit on `Boost-V6-experimental` → `git push origin Boost-V6-experimental` → then `git push origin Boost-V6-experimental:dev` (ff dev to match). Verify `git rev-parse origin/dev origin/Boost-V6-experimental` are equal.
- For changes that need shadow-testing first, keep them on a side branch until validated, THEN cherry-pick onto `experimental` and push to `dev` per the rule. **DONE 2026-07-02:** phone-anchored step-baseline fix (`47a5f049df` watch-swap calibration + `d29dfc5de5` HC phone-sensor backfill) cherry-picked from `Boost-V6-activity-source` onto `experimental` (clean), built OK, pushed → experimental+dev now at `26feb32237`. Experimental HAS the phone-anchored stepbridge as of 07-02. `Boost-V6-activity-source` (d29dfc5de5) may still hold other unmerged multi-source/HR-resolver commits.
- Pipeline beyond dev is unchanged: `experimental → dev → master` (master = Tim's primary). Publish remote is `origin` → github.com/tim2000s/Boost-in-AAPS_3.4.git — NOT the `boost-aaps-core` remote (wrong repo). See [[boost-v6-experimental-state-2026-06-27]].
- ⚠️ Building APKs: don't build the `Boost-V6-activity-source*` line directly — it lacks the overview steps+HR graph; cherry-pick onto `experimental` and build from there (see [[boost-activity-source-abstraction-plan]]).

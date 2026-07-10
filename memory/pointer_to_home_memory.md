---
name: Boost / AndroidAPS memory location
description: All AndroidAPS and Boost-plugin memories live in the home-directory project memory, not here
type: reference
originSessionId: 4ad81ae7-3097-4538-933d-51ff60da7a74
---
When working in `/Users/timstreet/StudioProjects/AndroidAPS` (this repo), all relevant memories live in:

`/Users/timstreet/.claude/projects/-Users-timstreet/memory/`

Key files there:
- `MEMORY.md` — index
- `boost_v3_architecture.md` — V3 plugin architecture (formula, tiers, features)
- `boost_v2_investigation_status.md` — Tim's [self-NS-site] single-user analysis + NS read token location
- `boost_v3ml_production_validation.md` — V3ML on-device model production check (Apr 28 2026)
- `project_v4_migration_plan.md` — Boost 3.4.2.1 → AAPS v4 phased plan
- `project_session_apr2026_day{1,2,3,4}.md` — Daily session records
- `session_apr2026_day9_10.md` — oref v6 + V3ML build session
- `oref_methodology.md` — has a Boost-specific section on silent-Boost detection, field-coverage, paired comparisons
- `project_sms_debug.md` — SMS Communicator debugging

NS read token for `[self-NS-site]` is at `/Users/timstreet/Nightscout_Work/.ns_token`.

V3ML branch lives in a separate repo: `/Users/timstreet/StudioProjects/Boost-AAPS-core/` on `Boost-V3ML-Testing`.

**Why:** The home-dir project memory was created first when work started in `~`. The AndroidAPS-specific memory dir was empty until this pointer was added on 2026-04-28.

**How to apply:** Read `/Users/timstreet/.claude/projects/-Users-timstreet/memory/MEMORY.md` for the full index when working on this repo.

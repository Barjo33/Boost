#!/usr/bin/env python3
"""Do the activity, sleep and HR states flip safely?

Backs the 2026-07-31 fix that made sleep-in and INACTIVE mutually exclusive, and checks the
wider layering for unsafe transitions. Everything here is measured from logged telemetry; the
fix is not yet in any of this data, so the counts show the EXPOSURE the fix removes.

Four questions:

  A. How often did the profile raise coincide with sleep? This is the reported defect. The raise
     lowers ISF, lifts basal and scales the SMB tiers, so a coincidence with sleep is insulin
     added to a sleeping user.
  B. Do the states flap? Rapid oscillation is unsafe regardless of direction, because the dose
     path sees a different profile every few minutes.
  C. Does the raise coincide with low or falling glucose? Left unguarded by choice, since a
     genuinely inactive user should still get it, but worth knowing the size of.
  D. Is the HR layer reachable? The July elevated-HR suppression is gated on a preference that
     ships false, so measure how often HR data was present at all.
"""
import os, sys, json, numpy as np, psycopg2

DSN = "dbname=oref host=127.0.0.1 port=5432"
SINCE = "2026-06-15"
HERE = os.path.dirname(os.path.abspath(__file__))
ASLEEP = ("SLEEPING", "PRE_SLEEP")

q = """
select user_id, ts_utc, sleep_state, boost_profile_switch, hr_avg, hr_zone, hrr_pct,
       steps_60m, steps_5m, sug_bg_arg, sug_iob
from (select user_id, ts_utc, sleep_state, boost_profile_switch, hr_avg, hr_zone, hrr_pct,
             steps_60m, steps_5m, null::double precision as sug_bg_arg, sug_iob
      from boost_decisions where ts_utc >= %s) t
order by user_id, ts_utc
"""
# bg lives in the reason text on some variants; pull it from boost_cgm instead
with psycopg2.connect(DSN) as c, c.cursor() as cur:
    cur.execute("""select user_id, ts_utc, sleep_state, boost_profile_switch, hr_avg, hr_zone,
                          steps_60m, sug_iob
                   from boost_decisions where ts_utc >= %s order by user_id, ts_utc""", (SINCE,))
    rows = cur.fetchall()
    cur.execute("""select user_id, ts_utc, cgm_mgdl from boost_cgm
                   where ts_utc >= %s and cgm_mgdl is not null order by user_id, ts_utc""", (SINCE,))
    cgm = cur.fetchall()

import collections, datetime as dt
by_user = collections.defaultdict(list)
for r in rows: by_user[r[0]].append(r)
cgm_by = collections.defaultdict(list)
for u, t, v in cgm: cgm_by[u].append((t, float(v)))

def bg_at(u, t, tol_min=6):
    arr = cgm_by.get(u)
    if not arr: return None
    ts = [x[0] for x in arr]
    i = np.searchsorted(ts, t)
    best = None
    for j in (i-1, i):
        if 0 <= j < len(arr):
            d = abs((arr[j][0]-t).total_seconds())/60.0
            if d <= tol_min and (best is None or d < best[0]): best = (d, arr[j][1])
    return best[1] if best else None

print("A. PROFILE RAISE DURING SLEEP  (the reported defect, measured)\n")
print(f"  {'user':>5s} {'cycles':>8s} {'raised':>8s} {'raised%':>8s} {'RAISED WHILE ASLEEP':>20s} "
      f"{'% of raised':>12s} {'% of sleep':>11s}")
out = {}
for u, rs in sorted(by_user.items()):
    with_state = [r for r in rs if r[2] and r[3] is not None]
    if len(with_state) < 500: continue
    raised = [r for r in with_state if r[3] > 100]
    asleep = [r for r in with_state if r[2] in ASLEEP]
    both = [r for r in raised if r[2] in ASLEEP]
    out[u] = dict(cycles=len(with_state), raised=len(raised), asleep=len(asleep), both=len(both))
    print(f"  {u:>5s} {len(with_state):8,d} {len(raised):8,d} {100*len(raised)/len(with_state):7.1f}% "
          f"{len(both):20,d} {100*len(both)/max(len(raised),1):11.1f}% "
          f"{100*len(both)/max(len(asleep),1):10.1f}%")
tot_both = sum(v["both"] for v in out.values()); tot_raised = sum(v["raised"] for v in out.values())
tot_sleep = sum(v["asleep"] for v in out.values())
print(f"\n  cohort: {tot_both:,} of {tot_raised:,} raised cycles were during sleep "
      f"({100*tot_both/max(tot_raised,1):.1f}%), i.e. {100*tot_both/max(tot_sleep,1):.1f}% of all "
      f"sleeping cycles ran on a raised profile.")

print("\nB. DO THE STATES FLAP?  (transitions per day; dwell time between changes)\n")
print(f"  {'user':>5s} {'sleep flips/d':>14s} {'med dwell':>10s} {'profile flips/d':>16s} "
      f"{'med dwell':>10s} {'zone flips/d':>13s}")
for u, rs in sorted(by_user.items()):
    if u not in out: continue
    days = (rs[-1][1]-rs[0][1]).total_seconds()/86400.0
    def flips(idx):
        seq = [(r[1], r[idx]) for r in rs if r[idx] is not None]
        ch = [(seq[i][0]-seq[i-1][0]).total_seconds()/60.0
              for i in range(1, len(seq)) if seq[i][1] != seq[i-1][1]]
        return len(ch)/days, (float(np.median(ch)) if ch else float("nan"))
    sf, sd = flips(2); pf, pd_ = flips(3); zf, _ = flips(5)
    out[u].update(sleep_flips_day=sf, sleep_dwell_med=sd, prof_flips_day=pf, prof_dwell_med=pd_,
                  zone_flips_day=zf)
    print(f"  {u:>5s} {sf:14.1f} {sd:9.0f}m {pf:16.1f} {pd_:9.0f}m {zf:13.1f}")

print("\nC. PROFILE RAISE AT LOW OR FALLING GLUCOSE  (left unguarded by design; size of it)\n")
print(f"  {'user':>5s} {'raised w/ BG':>13s} {'BG<80':>8s} {'BG<70':>8s} {'median BG':>10s}")
for u, rs in sorted(by_user.items()):
    if u not in out: continue
    raised = [r for r in rs if r[2] and r[3] and r[3] > 100]
    bgs = [b for r in raised if (b := bg_at(u, r[1])) is not None]
    if len(bgs) < 50: continue
    lo80 = sum(1 for b in bgs if b < 80); lo70 = sum(1 for b in bgs if b < 70)
    out[u].update(raised_with_bg=len(bgs), raised_bg_lt80=lo80, raised_bg_lt70=lo70,
                  raised_bg_med=float(np.median(bgs)))
    print(f"  {u:>5s} {len(bgs):13,d} {lo80:8,d} {lo70:8,d} {np.median(bgs):10.0f}")

print("\nD. IS THE HR LAYER REACHABLE?  (July suppression needs HR present AND the pref on)\n")
print(f"  {'user':>5s} {'cycles':>8s} {'with HR':>9s} {'HR%':>7s} {'zone>=3':>9s} "
      f"{'raised & no HR':>15s}")
for u, rs in sorted(by_user.items()):
    if u not in out: continue
    n = len(rs); hr = sum(1 for r in rs if r[4] is not None)
    z3 = sum(1 for r in rs if r[5] and any(d in str(r[5]) for d in ("3", "4", "5")))
    raised_nohr = sum(1 for r in rs if r[3] and r[3] > 100 and r[4] is None)
    out[u].update(hr_cycles=hr, hr_pct=100*hr/n, zone3plus=z3, raised_without_hr=raised_nohr)
    print(f"  {u:>5s} {n:8,d} {hr:9,d} {100*hr/n:6.1f}% {z3:9,d} {raised_nohr:15,d}")

json.dump(out, open(os.path.join(HERE, "state_safety.json"), "w"), indent=1, default=float)
print(f"\n-> state_safety.json")
print("\nPROVISIONAL — logged telemetry, pre-fix. Counts are the exposure the fix removes.")

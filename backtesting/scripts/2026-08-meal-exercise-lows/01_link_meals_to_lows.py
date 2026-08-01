#!/usr/bin/env python3
"""Link each low back to the meal that preceded it, and characterise the window between.

Question: given that a meal has happened, do exercise indicators observed BETWEEN the meal and
the low distinguish the meals that end low from those that do not? The register already has
"recent activity is a per-user leading indicator of a forward hypo"; this asks the harder,
discriminative form, which is what an anticipation layer would actually need.

Every meal is classified by what follows it within MAX_GAP_H, and the same exercise summary is
computed over the window either way, so the two groups are described on the same basis.
"""
import sys, os, json, numpy as np, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mel_lib as M

HERE = os.path.dirname(os.path.abspath(__file__))
out_rows, per_user = [], {}
print("01. MEALS, THE LOWS THAT FOLLOW, AND THE WINDOW BETWEEN\n")
print(f"  {'user':>5s} {'meals':>6s} {'->low':>6s} {'rate':>6s} {'->severe':>9s} "
      f"{'med gap h':>10s} {'lows w/o meal':>14s}")
for u in M.users():
    ts, bg = M.load_cgm(u); nom = M.nominal_min(ts)
    if nom > 6: nom = 5.0
    ctx = M.load_context(u)
    ml = M.meals(ts, bg, nom)
    lo = M.lows(ts, bg, nom)
    sev = M.lows(ts, bg, nom, thr=M.SEVERE_MGDL)
    sev_t = set(ts[i] for i, _ in sev)
    low_t = [(ts[i], v) for i, v in lo]
    used = set()
    n_low = n_sev = 0
    gaps = []
    for (mi, pk) in ml:
        t_meal = ts[mi]
        nxt = [(t, v) for t, v in low_t if t > t_meal and
               (t - t_meal).total_seconds()/3600.0 <= M.MAX_GAP_H]
        ended_low = bool(nxt)
        t_end = nxt[0][0] if ended_low else None
        if ended_low:
            used.add(t_end); n_low += 1
            gaps.append((t_end - t_meal).total_seconds()/3600.0)
            if t_end in sev_t: n_sev += 1
        # window: meal -> low, or a matched-length window when no low followed
        t_stop = t_end if ended_low else t_meal + __import__("datetime").timedelta(hours=2.5)
        w = M.window_context(ctx, t_meal, t_stop)
        if w:
            w.update(user=u, t=str(t_meal), ended_low=ended_low,
                     nadir=(nxt[0][1] if ended_low else None),
                     gap_h=((t_end - t_meal).total_seconds()/3600.0 if ended_low else None),
                     peak_rise=float(bg[pk] - bg[mi]), bg_at_meal=float(bg[mi]))
            out_rows.append(w)
    orphan = sum(1 for t, _ in low_t if t not in used)
    per_user[u] = dict(meals=len(ml), to_low=n_low, to_severe=n_sev, lows=len(lo),
                       orphan_lows=orphan,
                       rate=100*n_low/max(len(ml), 1),
                       med_gap_h=float(np.median(gaps)) if gaps else None)
    print(f"  {u:>5s} {len(ml):6d} {n_low:6d} {100*n_low/max(len(ml),1):5.1f}% {n_sev:9d} "
          f"{(np.median(gaps) if gaps else float('nan')):10.1f} {orphan:14d}")

tm = sum(v["meals"] for v in per_user.values()); tl = sum(v["to_low"] for v in per_user.values())
to = sum(v["orphan_lows"] for v in per_user.values()); ta = sum(v["lows"] for v in per_user.values())
print(f"\n  cohort: {tm:,} meals, {tl:,} ended in a low within {M.MAX_GAP_H:.0f}h "
      f"({100*tl/tm:.1f}%)")
print(f"  of {ta:,} sustained lows, {to:,} ({100*to/max(ta,1):.0f}%) had NO meal in the preceding "
      f"{M.MAX_GAP_H:.0f}h — those are a different pathway and are excluded from the comparison")
json.dump(dict(per_user=per_user, rows=out_rows),
          open(os.path.join(HERE, "meals_lows.json"), "w"), indent=1, default=str)
print(f"\n  {len(out_rows):,} meal windows with context -> meals_lows.json")

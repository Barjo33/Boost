#!/usr/bin/env python3
"""Do exercise indicators in the post-meal window separate the meals that end low?

Two framings, and the distinction matters:

  DESCRIPTIVE   the whole meal-to-low window. Says what was present, but the window ends AT the
                low, so anything correlated with the descent itself shows up. Cannot support a
                prediction claim.
  PREDICTIVE    only the first EARLY_MIN minutes after the meal, before any descent is visible.
                This is the version an anticipation or back-out layer could act on.

The register records the activity-to-hypo relationship as per-user rather than cross-user, so
both pooled and per-user discrimination are reported. Uncertainty is a bootstrap over meals.
"""
import sys, os, json, numpy as np, collections, datetime as dt
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mel_lib as M
from sklearn.metrics import roc_auc_score

EARLY_MIN = 75
HERE = os.path.dirname(os.path.abspath(__file__))
rng = np.random.default_rng(20260801)

# rebuild windows in both framings
rows = []
for u in M.users():
    ts, bg = M.load_cgm(u); nom = M.nominal_min(ts)
    if nom > 6: nom = 5.0
    ctx = M.load_context(u)
    lo = M.lows(ts, bg, nom)
    low_t = [(ts[i], v) for i, v in lo]
    for (mi, pk) in M.meals(ts, bg, nom):
        t_meal = ts[mi]
        nxt = [(t, v) for t, v in low_t if t > t_meal and
               (t-t_meal).total_seconds()/3600.0 <= M.MAX_GAP_H]
        ended = bool(nxt)
        t_end = nxt[0][0] if ended else t_meal + dt.timedelta(hours=2.5)
        full = M.window_context(ctx, t_meal, t_end)
        early = M.window_context(ctx, t_meal, t_meal + dt.timedelta(minutes=EARLY_MIN))
        if full and early:
            rows.append(dict(user=u, ended_low=ended, full=full, early=early))

print(f"02. EXERCISE SIGNATURE IN THE POST-MEAL WINDOW\n")
print(f"  {len(rows):,} meals with context; {sum(r['ended_low'] for r in rows):,} ended low "
      f"({100*sum(r['ended_low'] for r in rows)/len(rows):.1f}%)\n")

FEATS = [("steps5_max", "peak 5-min steps"), ("steps30_max", "peak 30-min steps"),
         ("steps60_max", "peak 60-min steps"), ("hr_max", "peak HR"),
         ("hrr_max", "peak HR reserve %"), ("load_ratio", "activity-load ratio"),
         ("iob_mean", "mean IOB"), ("iob_min", "min IOB"), ("cob_max", "peak COB")]

def auc_ci(y, x, nboot=800):
    m = np.isfinite(x)
    y2, x2 = y[m], x[m]
    if len(np.unique(y2)) < 2 or len(y2) < 60: return None
    pt = roc_auc_score(y2, x2)
    bs = []
    for _ in range(nboot):
        idx = rng.integers(0, len(y2), len(y2))
        if len(np.unique(y2[idx])) < 2: continue
        bs.append(roc_auc_score(y2[idx], x2[idx]))
    return pt, float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5)), int(len(y2))

for tag in ("full", "early"):
    label = ("DESCRIPTIVE — whole meal-to-low window" if tag == "full"
             else f"PREDICTIVE — first {EARLY_MIN} min after the meal only")
    print(f"  {label}")
    print(f"    {'indicator':>22s} {'n':>6s} {'AUC (95% CI)':>26s} {'verdict':>14s}")
    y = np.array([1.0 if r["ended_low"] else 0.0 for r in rows])
    res = {}
    for key, name in FEATS:
        x = np.array([r[tag].get(key) if r[tag].get(key) is not None else np.nan for r in rows], float)
        a = auc_ci(y, x)
        if not a: 
            print(f"    {name:>22s} {'':>6s} {'too few':>26s}"); continue
        pt, lo_, hi_, n = a
        sep = "separates" if (lo_ > 0.5 or hi_ < 0.5) else "not distinguishable"
        res[key] = dict(auc=pt, lo=lo_, hi=hi_, n=n, separates=bool(lo_ > 0.5 or hi_ < 0.5))
        print(f"    {name:>22s} {n:6d} {pt:8.3f} [{lo_:.3f}, {hi_:.3f}] {sep:>21s}")
    print()

# per-user, on the predictive framing only
print(f"  PER-USER (predictive framing, peak 30-min steps)")
print(f"    {'user':>5s} {'meals':>6s} {'low%':>6s} {'AUC':>20s}")
per = {}
for u in sorted(set(r["user"] for r in rows)):
    rs = [r for r in rows if r["user"] == u]
    y = np.array([1.0 if r["ended_low"] else 0.0 for r in rs])
    x = np.array([r["early"].get("steps30_max") if r["early"].get("steps30_max") is not None else np.nan
                  for r in rs], float)
    a = auc_ci(y, x, nboot=400)
    if not a: print(f"    {u:>5s} {len(rs):6d} {'':>6s} {'too few':>20s}"); continue
    pt, lo_, hi_, n = a
    per[u] = dict(auc=pt, lo=lo_, hi=hi_, n=n)
    print(f"    {u:>5s} {n:6d} {100*y.mean():5.1f}% {pt:6.3f} [{lo_:.3f}, {hi_:.3f}]")
sig = [u for u, v in per.items() if v["lo"] > 0.5 or v["hi"] < 0.5]
print(f"\n  {len(sig)} of {len(per)} users show a distinguishable per-user effect: {sig or 'none'}")
json.dump(dict(per_user=per), open(os.path.join(HERE, "signature.json"), "w"), indent=1)
print("\nPROVISIONAL — observational; a meal that ends low differs in more than exercise.")

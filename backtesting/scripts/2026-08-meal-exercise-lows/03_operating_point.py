#!/usr/bin/env python3
"""Is the post-meal step signal usable, and at what threshold?

AUC says a signal exists; it does not say whether acting on it is sensible. This reports the
precision and recall of a simple rule — "peak 30-min steps in the 75 min after a meal exceeds T"
— against the base rate, which is what an anticipation or back-out layer would live or die by.
"""
import sys, os, json, numpy as np, datetime as dt, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mel_lib as M

EARLY_MIN = 75
rows = []
for u in M.users():
    ts, bg = M.load_cgm(u); nom = M.nominal_min(ts)
    if nom > 6: nom = 5.0
    ctx = M.load_context(u)
    low_t = [(ts[i], v) for i, v in M.lows(ts, bg, nom)]
    for (mi, pk) in M.meals(ts, bg, nom):
        t0 = ts[mi]
        nxt = [(t, v) for t, v in low_t if t > t0 and (t-t0).total_seconds()/3600.0 <= M.MAX_GAP_H]
        w = M.window_context(ctx, t0, t0 + dt.timedelta(minutes=EARLY_MIN))
        if w and w.get("steps30_max") is not None:
            rows.append(dict(user=u, low=bool(nxt), s=w["steps30_max"],
                             nadir=(nxt[0][1] if nxt else None)))
y = np.array([1.0 if r["low"] else 0.0 for r in rows])
x = np.array([r["s"] for r in rows], float)
base = y.mean()
print(f"03. OPERATING POINT\n\n  {len(rows):,} meals, base rate {100*base:.1f}% end in a low\n")
print(f"  {'threshold':>10s} {'fires':>7s} {'% meals':>8s} {'precision':>10s} {'lift':>6s} "
      f"{'recall':>7s} {'lows missed':>12s}")
best = None
for T in (100, 200, 300, 400, 500, 750, 1000, 1500):
    f = x >= T
    if f.sum() < 30: continue
    prec = y[f].mean(); rec = y[f].sum()/y.sum()
    print(f"  {T:10d} {int(f.sum()):7d} {100*f.mean():7.1f}% {100*prec:9.1f}% "
          f"{prec/base:6.2f} {100*rec:6.1f}% {int(y.sum()-y[f].sum()):12d}")
    if best is None or prec/base > best[1]: best = (T, prec/base, prec, rec)
print(f"\n  best lift {best[1]:.2f}x at >= {best[0]} steps: precision {100*best[2]:.1f}% "
      f"against a {100*base:.1f}% base, catching {100*best[3]:.0f}% of the lows")

# does it get worse lows, or just more of them?
print(f"\n  severity: nadir of the lows caught vs missed, at the best threshold")
f = x >= best[0]
caught = [r["nadir"] for r, k in zip(rows, f) if k and r["nadir"] is not None]
missed = [r["nadir"] for r, k in zip(rows, f) if not k and r["nadir"] is not None]
if caught and missed:
    print(f"    caught n={len(caught):4d} median nadir {np.median(caught):.0f} mg/dL")
    print(f"    missed n={len(missed):4d} median nadir {np.median(missed):.0f} mg/dL")
print("\nPROVISIONAL — observational. The rule identifies meals followed by activity; whether")
print("acting on it prevents the low is a separate question this cannot answer.")

#!/usr/bin/env python3
"""Compare the two replay arms.

Two views of the dose are reported and they answer different questions.

  Raw sum of every cycle's recommendation. The one-minute arm runs five times as often, so if
  the engine recommends a similar dose per cycle this figure will be roughly five times
  larger. That is not what a pump would deliver, but it shows what the engine asks for.

  Sum after enforcing a minimum interval between microboluses, which is what the delivery
  layer imposes in practice. This is the closer estimate of delivered insulin and is the fair
  comparison between arms.
"""
import numpy as np, pandas as pd, os, json
D = os.path.dirname(os.path.abspath(__file__))
a = pd.read_csv(os.path.join(D, "out_1min.csv"))
b = pd.read_csv(os.path.join(D, "out_5min.csv"))
days = (a.ts.max() - a.ts.min())/86_400_000
print(f"Replay window {days:.1f} days.  1-min arm {len(a):,} cycles, 5-min arm {len(b):,} cycles\n")

def limited_total(df, min_gap_min=5.0):
    """Deliver a dose only if at least min_gap_min has passed since the last one."""
    tot, last = 0.0, -1e18
    for t, d in zip(df.ts.to_numpy(), df.finalDose.to_numpy()):
        if d <= 0: continue
        if (t - last)/60_000.0 >= min_gap_min:
            tot += d; last = t
    return tot

res = {}
print(f"  {'measure':<44s} {'1-min':>12s} {'5-min':>12s} {'ratio':>8s}")
def row(name, va, vb, fmt="{:.3f}"):
    r = va/vb if vb else float('nan')
    res[name] = dict(one=float(va), five=float(vb), ratio=float(r))
    print(f"  {name:<44s} {fmt.format(va):>12s} {fmt.format(vb):>12s} {r:8.2f}")

row("cycles per day", len(a)/days, len(b)/days, "{:.0f}")
row("cycles recommending a dose (%)", 100*(a.finalDose>0).mean(), 100*(b.finalDose>0).mean(), "{:.2f}")
row("raw sum of recommendations, U/day", a.finalDose.sum()/days, b.finalDose.sum()/days)
row("with a 5-minute minimum interval, U/day", limited_total(a)/days, limited_total(b)/days)
row("mean dose when dosing, U", a.loc[a.finalDose>0,'finalDose'].mean(),
    b.loc[b.finalDose>0,'finalDose'].mean(), "{:.4f}")
row("max single dose, U", a.finalDose.max(), b.finalDose.max(), "{:.3f}")
row("primer bolus total, U/day", a.primerBolusU.sum()/days, b.primerBolusU.sum()/days)

print(f"\n  Meal state occupancy, per cent of cycles")
states = sorted(set(a.state) | set(b.state))
print(f"  {'state':<14s} {'1-min':>8s} {'5-min':>8s}")
occ = {}
for s in states:
    pa, pb = 100*(a.state==s).mean(), 100*(b.state==s).mean()
    occ[s] = dict(one=float(pa), five=float(pb))
    print(f"  {s:<14s} {pa:8.2f} {pb:8.2f}")
res["state_occupancy"] = occ

print(f"\n  Inputs the engine saw")
for col in ("delta", "shortAvgDelta", "deltaAccl", "baseInsulinReq"):
    print(f"  {col:<20s} 1-min mean {a[col].mean():8.3f} SD {a[col].std():7.3f}   "
          f"5-min mean {b[col].mean():8.3f} SD {b[col].std():7.3f}")
    res[f"input_{col}"] = dict(one_mean=float(a[col].mean()), one_sd=float(a[col].std()),
                               five_mean=float(b[col].mean()), five_sd=float(b[col].std()))

# align arms at the 5-minute instants both saw, to compare like with like
m = a[a.ts.isin(set(b.ts))].set_index("ts")
n = b.set_index("ts")
common = m.index.intersection(n.index)
if len(common) > 500:
    print(f"\n  Same instants, both arms ({len(common):,} cycles)")
    for col in ("delta", "shortAvgDelta", "deltaAccl", "finalDose"):
        d = m.loc[common, col] - n.loc[common, col]
        print(f"  {col:<16s} mean difference {d.mean():+8.4f}  SD {d.std():7.4f}  "
              f"identical in {100*np.mean(np.abs(d)<1e-9):5.1f}% of cycles")
        res[f"paired_{col}"] = dict(mean_diff=float(d.mean()), sd=float(d.std()),
                                    pct_identical=float(100*np.mean(np.abs(d)<1e-9)))
    agree = (m.loc[common,"state"] == n.loc[common,"state"]).mean()
    res["state_agreement_pct"] = float(100*agree)
    print(f"  meal state agrees in {100*agree:.1f}% of shared cycles")
with open(os.path.join(D, "comparison.json"), "w") as f: json.dump(res, f, indent=1)
print(f"\n-> comparison.json")

# sensitivity of the delivered total to the minimum interval the delivery layer enforces
print("\n  Sensitivity to the minimum interval between microboluses")
print(f"  {'min interval':>13s} {'1-min U/day':>12s} {'5-min U/day':>12s} {'ratio':>8s}")
sens = {}
for gap in (0, 1, 3, 5, 10, 15):
    ta, tb = limited_total(a, gap)/days, limited_total(b, gap)/days
    sens[str(gap)] = dict(one=float(ta), five=float(tb), ratio=float(ta/tb) if tb else None)
    print(f"  {gap:12d}m {ta:12.3f} {tb:12.3f} {ta/tb:8.2f}")
res["interval_sensitivity"] = sens
with open(os.path.join(D, "comparison.json"), "w") as f: json.dump(res, f, indent=1)

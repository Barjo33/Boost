#!/usr/bin/env python3
"""How sensor cadence affects oref dosing.

oref splits its output in two, and the split is the whole story. The temp basal is a RATE in
units per hour, so it is cadence-neutral by construction: asking for it five times as often
does not deliver five times as much. The microbolus is a per-cycle amount and is not.

Insulin is therefore accounted properly rather than by summing recommendations: the temp basal
contributes rate multiplied by the time until it is superseded, and the microbolus contributes
its face value when oref's own SMBInterval permits it.
"""
import numpy as np, pandas as pd, os, json
D = os.path.dirname(os.path.abspath(__file__))
a = pd.read_csv(os.path.join(D, "out_oref_1min.csv"))
b = pd.read_csv(os.path.join(D, "out_oref_5min.csv"))
days = (a.ts.max()-a.ts.min())/86_400_000
BASAL = 0.6

def basal_units(df):
    """Temp basal delivered: each rate holds until the next decision supersedes it."""
    t = df.ts.to_numpy()/60_000.0
    r = df.rate.to_numpy().astype(float)
    dur = np.diff(np.append(t, t[-1] + (t[-1]-t[-2] if len(t) > 1 else 5)))
    r = np.where(r < 0, BASAL, r)          # -1 means no temp requested, so basal runs
    return float(np.sum(r*dur/60.0))

print(f"window {days:.1f} days.  1-min {len(a):,} cycles, 5-min {len(b):,} cycles\n")
res = {}
print(f"  {'measure':<44s} {'1-min':>11s} {'5-min':>11s} {'ratio':>8s}")
def row(k, va, vb, f="{:.3f}"):
    res[k] = dict(one=float(va), five=float(vb), ratio=float(va/vb) if vb else None)
    print(f"  {k:<44s} {f.format(va):>11s} {f.format(vb):>11s} "
          f"{(va/vb if vb else float('nan')):8.3f}")

row("temp basal delivered, U/day", basal_units(a)/days, basal_units(b)/days)
row("microboluses issued per day", (a.units>0).sum()/days, (b.units>0).sum()/days, "{:.1f}")
row("microbolus insulin, U/day", a.units.sum()/days, b.units.sum()/days)
row("total insulin, U/day", (basal_units(a)+a.units.sum())/days,
    (basal_units(b)+b.units.sum())/days)
row("mean temp basal rate, U/h", a.loc[a.rate>=0,'rate'].mean(), b.loc[b.rate>=0,'rate'].mean())
row("cycles requesting a temp, %", 100*(a.rate>=0).mean(), 100*(b.rate>=0).mean(), "{:.1f}")
row("mean eventualBG", a.eventualBG.mean(), b.eventualBG.mean(), "{:.1f}")

common = np.intersect1d(a.ts, b.ts)
ma = a.set_index("ts").loc[common]; mb = b.set_index("ts").loc[common]
print(f"\n  At the {len(common):,} instants both arms evaluated:")
for col in ("rate", "units", "eventualBG", "delta"):
    d = ma[col].to_numpy() - mb[col].to_numpy()
    ident = 100*np.mean(np.abs(d) < 1e-9)
    res[f"paired_{col}"] = dict(mean=float(d.mean()), sd=float(d.std()), identical_pct=float(ident))
    print(f"    {col:<12s} mean difference {d.mean():+9.4f}  SD {d.std():8.4f}  "
          f"identical in {ident:5.1f}% of cycles")
json.dump(res, open(os.path.join(D, "oref_comparison.json"), "w"), indent=1)
print("\n-> oref_comparison.json")

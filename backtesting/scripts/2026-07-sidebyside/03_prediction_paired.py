#!/usr/bin/env python3
"""Does either arm predict a COMMON future better?

The target is the consensus of the sensors not used as predictors, so no arm is scored against
its own quirks. Each arm is given the same look-back in minutes. Skill is normalised by the
standard deviation of the target. The same-cadence pair supplies the null: two sensors of the
same model will differ a little by chance, and a cross-cadence difference must exceed that.
"""
import sys, os, numpy as np, pandas as pd
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sbs_lib as S
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import GroupKFold
from sklearn.preprocessing import StandardScaler

HORIZONS = [15, 30, 60]
path = sys.argv[1] if len(sys.argv) > 1 else \
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture_sidebyside.csv")
print(f"03. PREDICTING A COMMON TARGET\n  input: {os.path.basename(path)}\n")
df = S.drop_warmup(S.load_csv(path))
sens = S.sensors(df); ids = list(sens)
grid = S.common_grid(df, step_min=1)
days = np.array([d.date() for d in pd.to_datetime(grid, unit="ms", utc=True)])
Vg = {sid: S.on_grid(df, sid, grid, max_gap_min=cad*1.6) for sid, cad in sens.items()}

def feats(v, cad):
    """Same look-back in minutes for every arm; the faster arm holds more samples."""
    k = lambda mins: max(int(round(mins/cad)), 1)*int(round(cad))
    cols = [v]
    for back in (5, 10, 15, 30, 45):
        s = np.full(len(v), np.nan); s[back:] = v[back:] - v[:-back]; cols.append(s)
    for win in (15, 30, 45):
        x = np.arange(win+1, dtype=float)
        w = (x - x.mean())/((x - x.mean())**2).sum()*5.0
        s = np.full(len(v), np.nan)
        conv = np.convolve(np.nan_to_num(v, nan=np.nanmean(v)), w[::-1], mode="valid")
        s[win:] = conv[:len(v)-win]
        s[~np.isfinite(v)] = np.nan
        cols.append(s)
    return np.column_stack(cols)

out = {}
for sid, cad in sens.items():
    tgt_src = [s for s in ids if s != sid]
    cons = np.nanmean(np.vstack([Vg[s] for s in tgt_src]), axis=0)
    X = feats(Vg[sid], cad)
    out[sid] = dict(cadence=float(cad), horizons={})
    for H in HORIZONS:
        y = np.full(len(grid), np.nan); y[:-H] = cons[H:]
        m = np.isfinite(X).all(1) & np.isfinite(y)
        if m.sum() < 1000: continue
        Xm, ym, g = X[m], y[m], days[m]
        p = np.zeros(len(ym))
        for tr, te in GroupKFold(n_splits=5).split(Xm, ym, groups=g):
            sc = StandardScaler().fit(Xm[tr])
            p[te] = LinearRegression().fit(sc.transform(Xm[tr]), ym[tr]).predict(sc.transform(Xm[te]))
        nr = lambda s: float(np.sqrt(np.mean((p[s]-ym[s])**2))/ym[s].std())
        pt = nr(np.arange(len(ym))); lo, hi = S.paired_day_bootstrap(nr, g, 600)
        out[sid]["horizons"][str(H)] = dict(nrmse=pt, lo=lo, hi=hi, n=int(m.sum()))
for H in HORIZONS:
    print(f"  horizon {H} min")
    for sid in ids:
        r = out[sid]["horizons"].get(str(H))
        if r: print(f"    {sid} ({sens[sid]:.0f} min)  nRMSE {r['nrmse']:.4f} "
                    f"[{r['lo']:.4f}, {r['hi']:.4f}]")
    a = [out[s]["horizons"][str(H)]["nrmse"] for s in ids
         if sens[s] == 1 and str(H) in out[s]["horizons"]]
    b = [out[s]["horizons"][str(H)]["nrmse"] for s in ids
         if sens[s] == 5 and str(H) in out[s]["horizons"]]
    if len(a) == 2 and len(b) == 2:
        null = max(abs(a[0]-a[1]), abs(b[0]-b[1]))
        eff = abs(np.mean(a) - np.mean(b))
        print(f"    within-cadence spread (the null): {null:.4f}; "
              f"cross-cadence difference: {eff:.4f} -> "
              f"{'inside the null' if eff <= null else 'outside the null'}")
        out.setdefault("verdict", {})[str(H)] = dict(null=float(null), effect=float(eff),
                                                     exceeds=bool(eff > null))
S.save("03_prediction_paired.json", out)

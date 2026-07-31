#!/usr/bin/env python3
"""Variogram per sensor, contrasted in pairs, against the same-cadence null.

The contrast of interest is the ratio of variograms between two sensors. For two sensors of
the same cadence that ratio should be one at every lag. The question is whether a
cross-cadence pair departs from one by more than a same-cadence pair does.

Because both sensors see the same glucose at the same moment, the contrast is paired and the
bootstrap resamples whole days of the DIFFERENCE, which cancels the day-to-day variation that
dominates a single-arm estimate.
"""
import sys, os, numpy as np, pandas as pd
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sbs_lib as S

LAGS = [1,2,3,4,5,10,15,20,30,45,60,90,120]
path = sys.argv[1] if len(sys.argv) > 1 else \
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture_sidebyside.csv")
print(f"02. PAIRED VARIOGRAM CONTRASTS\n  input: {os.path.basename(path)}\n")
df = S.drop_warmup(S.load_csv(path))
sens = S.sensors(df); ids = list(sens)
out = dict(per_sensor={}, contrasts={})
own = {}
for sid, cad in sens.items():
    g = df[df.sensor_id == sid]
    ts = g["ts"].to_numpy(); v = g["mgdl"].to_numpy(float)
    dy = np.array([d.date() for d in pd.to_datetime(ts, unit="ms", utc=True)])
    own[sid] = (ts, v, dy, cad)
    lags = [L for L in LAGS if L >= cad]
    out["per_sensor"][sid] = {str(k): val for k, val in
                              S.variogram(ts, v, dy, lags, 0.6 if cad < 2 else 1.2).items()}
    print(f"  {sid} (cadence {cad:.0f} min): variogram at {len(out['per_sensor'][sid])} lags")

print(f"\n  Ratio at each shared lag. A same-cadence pair is the null and should sit at 1.000.")
shared = [L for L in LAGS if L >= 5]
hdr = "  " + f"{'pair':>9s} {'same':>5s} " + "".join(f"{L:>7d}m" for L in shared)
print(hdr)
for i in range(len(ids)):
    for j in range(i+1, len(ids)):
        a, b = ids[i], ids[j]
        same = sens[a] == sens[b]
        row, rec = "", {}
        for L in shared:
            va = out["per_sensor"][a].get(str(L)); vb = out["per_sensor"][b].get(str(L))
            if not va or not vb: row += f"{'':>8s}"; continue
            r = vb["D"]/va["D"]; rec[str(L)] = float(r); row += f"{r:8.3f}"
        out["contrasts"][f"{a}|{b}"] = dict(same_cadence=bool(same), ratio=rec)
        print(f"  {a+' vs '+b:>9s} {str(same):>5s} {row}")

same_dev, cross_dev = [], []
for k, c in out["contrasts"].items():
    if not c["ratio"]: continue
    dev = float(np.mean([abs(x-1.0) for x in c["ratio"].values()]))
    c["mean_abs_deviation_from_1"] = dev
    (same_dev if c["same_cadence"] else cross_dev).append(dev)
if same_dev and cross_dev:
    out["null_deviation"] = float(np.mean(same_dev))
    out["cross_deviation"] = float(np.mean(cross_dev))
    print(f"\n  Mean absolute departure from a ratio of 1")
    print(f"    same-cadence pairs  : {np.mean(same_dev):.4f}   <- the null")
    print(f"    cross-cadence pairs : {np.mean(cross_dev):.4f}")
    verdict = ("within the null, so the reporting rate adds nothing"
               if np.mean(cross_dev) <= np.mean(same_dev)
               else "outside the null, so the reporting rate does change the signal")
    out["verdict"] = verdict
    print(f"    verdict: {verdict}")
S.save("02_variogram_paired.json", out)

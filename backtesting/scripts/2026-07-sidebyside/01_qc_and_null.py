#!/usr/bin/env python3
"""Quality control, and the empirical null that the whole study rests on.

The same-cadence pair is the reference. Two sensors of identical model worn at the same time
differ by unit-to-unit variation, by site, and by their own noise. Any difference attributed
to the reporting rate must exceed that.

Reported here:
  coverage and cadence stability per sensor
  pairwise bias and its drift, which must be removed before comparing anything
  the variance of the difference between same-cadence sensors, which is twice the per-sensor
  noise variance because true glucose cancels
"""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sbs_lib as S

path = sys.argv[1] if len(sys.argv) > 1 else \
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture_sidebyside.csv")
print(f"01. QUALITY CONTROL AND EMPIRICAL NULL\n  input: {os.path.basename(path)}\n")
df = S.drop_warmup(S.load_csv(path))
sens = S.sensors(df)
grid = S.common_grid(df, step_min=1)
days = np.array([d.date() for d in
                 __import__("pandas").to_datetime(grid, unit="ms", utc=True)])
print(f"  {'sensor':>7s} {'cadence':>8s} {'readings':>9s} {'days':>6s} {'median gap':>11s} "
      f"{'on cadence':>11s} {'mean':>7s}")
V, out = {}, dict(sensors={}, pairs={})
for sid, cad in sens.items():
    g = df[df.sensor_id == sid]
    gaps = np.diff(g["ts"].to_numpy())/60_000.0
    V[sid] = S.on_grid(df, sid, grid, max_gap_min=cad*1.6)
    o = dict(cadence=float(cad), n=int(len(g)), days=float((g.ts.max()-g.ts.min())/86_400_000),
             median_gap=float(np.median(gaps)),
             pct_on_cadence=float(100*np.mean(np.abs(gaps-cad) < 0.3*cad)),
             mean=float(g.mgdl.mean()), grid_coverage=float(100*np.mean(np.isfinite(V[sid]))))
    out["sensors"][sid] = o
    print(f"  {sid:>7s} {cad:8.0f} {o['n']:9,d} {o['days']:6.1f} {o['median_gap']:11.2f} "
          f"{o['pct_on_cadence']:10.1f}% {o['mean']:7.1f}")

print(f"\n  Pairwise agreement, compared only where both sensors have just reported")
print(f"  {'pair':>9s} {'same cadence':>13s} {'n':>7s} {'bias':>8s} {'drift/day':>10s} "
       f"{'SD of diff':>11s} {'implied noise SD':>17s}")
ids = list(sens)
for i in range(len(ids)):
    for j in range(i+1, len(ids)):
        a, b = ids[i], ids[j]
        same = sens[a] == sens[b]
        # Compare only where BOTH sensors have just reported. Otherwise a 5-minute arm is
        # being judged on a held value and we measure the zero-order hold, not the sensor.
        slow = max(sens[a], sens[b])
        fresh = (((grid - grid[0])//60_000) % int(round(slow))) == 0
        m = np.isfinite(V[a]) & np.isfinite(V[b]) & fresh
        if m.sum() < 500: continue
        d = V[a][m] - V[b][m]
        t = (grid[m]-grid[m][0])/86_400_000.0
        slope = float(np.polyfit(t, d, 1)[0])
        resid = d - np.polyval(np.polyfit(t, d, 1), t)
        rec = dict(same_cadence=bool(same), n=int(m.sum()), bias=float(d.mean()),
                   drift_per_day=slope, sd_diff=float(resid.std()),
                   implied_noise_sd=float(resid.std()/np.sqrt(2)) if same else None)
        out["pairs"][f"{a}|{b}"] = rec
        print(f"  {a+' vs '+b:>9s} {str(same):>13s} {m.sum():7,d} {d.mean():8.2f} "
              f"{slope:10.3f} {resid.std():11.2f} "
              f"{(rec['implied_noise_sd'] if same else float('nan')):17.2f}")

same_sd = [r["sd_diff"] for r in out["pairs"].values() if r["same_cadence"]]
cross_sd = [r["sd_diff"] for r in out["pairs"].values() if not r["same_cadence"]]
if same_sd and cross_sd:
    out["null_sd_same_cadence"] = float(np.mean(same_sd))
    out["sd_cross_cadence"] = float(np.mean(cross_sd))
    print(f"\n  Mean SD of difference, same cadence  : {np.mean(same_sd):.2f} mg/dl  <- the null")
    print(f"  Mean SD of difference, cross cadence : {np.mean(cross_sd):.2f} mg/dl")
    print(f"  Excess attributable to cadence       : "
          f"{np.sqrt(max(np.mean(cross_sd)**2 - np.mean(same_sd)**2, 0)):.2f} mg/dl")
    print("  A cross-cadence SD no larger than the same-cadence SD means the reporting rate")
    print("  contributes nothing beyond ordinary unit-to-unit variation.")
    # what a consumer experiences, including staleness of the slower arm
    print("\n  For contrast, the same comparison at every minute, which includes the")
    print("  staleness of the slower arm rather than isolating the sensor:")
    for i in range(len(ids)):
        for j in range(i+1, len(ids)):
            a, b = ids[i], ids[j]
            if sens[a] == sens[b]: continue
            m2 = np.isfinite(V[a]) & np.isfinite(V[b])
            if m2.sum() < 500: continue
            d2 = V[a][m2] - V[b][m2]
            t2 = (grid[m2]-grid[m2][0])/86_400_000.0
            r2 = d2 - np.polyval(np.polyfit(t2, d2, 1), t2)
            out["pairs"][f"{a}|{b}"]["sd_diff_all_minutes"] = float(r2.std())
            print(f"    {a} vs {b}: SD {r2.std():.2f} mg/dl")
S.save("01_qc_and_null.json", out)

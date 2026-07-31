#!/usr/bin/env python3
"""Synthetic four-sensor fixture, so the pipeline can be tested before real data exists.

The truth is a real glucose trace taken from the database. Each simulated sensor applies its
own low-pass filter, an offset, a slow drift and AR(1) noise, then reports on its own grid.
This is a test harness for the code, not evidence about anything.
"""
import sys, os, numpy as np, pandas as pd, psycopg2, datetime as dt
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

DSN = "dbname=oref host=127.0.0.1 port=5432"
rng = np.random.default_rng(7)
with psycopg2.connect(DSN) as c, c.cursor() as cur:
    cur.execute("select extract(epoch from ts_utc)*1000, cgm_mgdl from boost_cgm "
                "where user_id='I' and cgm_mgdl is not null "
                "and ts_utc >= '2026-06-13' and ts_utc < '2026-06-28' order by ts_utc")
    r = cur.fetchall()
ts = np.array([int(x[0]) for x in r], np.int64)
truth = np.array([float(x[1]) for x in r], float)
print(f"truth trace: {len(ts):,} points over {(ts[-1]-ts[0])/86_400_000:.1f} days")

def simulate(tau_min, offset, drift_per_day, noise_sd, ar, cadence, seed):
    g = np.random.default_rng(seed)
    a = np.exp(-1.0/max(tau_min, 1e-6))
    y = np.empty_like(truth); y[0] = truth[0]
    for i in range(1, len(truth)):                 # first-order lag
        y[i] = a*y[i-1] + (1-a)*truth[i]
    e = np.zeros(len(truth))
    for i in range(1, len(truth)):
        e[i] = ar*e[i-1] + g.normal(0, noise_sd*np.sqrt(1-ar**2))
    days = (ts - ts[0])/86_400_000.0
    v = y + offset + drift_per_day*days + e
    step = int(round(cadence))
    idx = np.arange(0, len(ts), step)
    return ts[idx], np.round(v[idx])

specs = [("A1", 1, dict(tau_min=3.0, offset=+2.0, drift_per_day=0.3, noise_sd=2.0, ar=0.85, seed=1)),
         ("A2", 1, dict(tau_min=3.0, offset=-3.0, drift_per_day=-0.2, noise_sd=2.2, ar=0.85, seed=2)),
         ("B1", 5, dict(tau_min=6.0, offset=+1.0, drift_per_day=0.1, noise_sd=2.6, ar=0.55, seed=3)),
         ("B2", 5, dict(tau_min=6.0, offset=-2.0, drift_per_day=-0.4, noise_sd=2.4, ar=0.55, seed=4))]
rows = []
for sid, cad, kw in specs:
    t, v = simulate(cadence=cad, **kw)
    rows.append(pd.DataFrame(dict(sensor_id=sid, cadence_min=cad, ts_utc=t, mgdl=v, session=1)))
df = pd.concat(rows)
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture_sidebyside.csv")
df.to_csv(out, index=False)
print(f"wrote {out}: {len(df):,} rows, {df.sensor_id.nunique()} sensors")
print(df.groupby(["sensor_id","cadence_min"]).size().to_string())

#!/usr/bin/env python3
"""Extract the replay input: 1-minute glucose with the recorded IOB trajectory aligned to it.

Both arms of the replay read this same file, so IOB at a given wall-clock instant is identical
whichever cadence is being run. Only the glucose sampling differs.
"""
import numpy as np, psycopg2, datetime as dt, os
DSN = "dbname=oref host=127.0.0.1 port=5432"
START, END = '2026-07-21', '2026-07-31'
with psycopg2.connect(DSN) as c, c.cursor() as cur:
    cur.execute("select extract(epoch from ts_utc)*1000, cgm_mgdl from boost_cgm "
                "where user_id='I' and cgm_mgdl is not null and ts_utc>=%s and ts_utc<%s "
                "order by ts_utc", (START, END))
    g = cur.fetchall()
    cur.execute("select extract(epoch from ts_utc)*1000, sug_iob from boost_decisions "
                "where user_id='I' and sug_iob is not null and ts_utc>=%s and ts_utc<%s "
                "order by ts_utc", (START, END))
    d = cur.fetchall()
ts = np.array([int(x[0]) for x in g], np.int64); bg = np.array([float(x[1]) for x in g])
its = np.array([int(x[0]) for x in d], np.int64); iob = np.array([float(x[1]) for x in d])
# last recorded IOB at or before each glucose sample, stale beyond 20 min -> 0
j = np.searchsorted(its, ts, side="right") - 1
v = np.zeros(len(ts))
ok = j >= 0
v[ok] = iob[j[ok]]
stale = np.full(len(ts), np.inf); stale[ok] = (ts[ok] - its[j[ok]])/60_000.0
v[stale > 20] = 0.0
keep = np.isfinite(bg)
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "replay_input.csv")
with open(out, "w") as f:
    f.write("ts,bg,iob\n")
    for t, b, i_ in zip(ts[keep], bg[keep], v[keep]):
        f.write(f"{t},{b},{i_:.4f}\n")
gaps = np.diff(ts)/60_000.0
print(f"{keep.sum():,} samples, {(ts[-1]-ts[0])/86_400_000:.1f} days, "
      f"median gap {np.median(gaps):.2f} min")
print(f"IOB coverage within 20 min of a decision: {100*np.mean(stale <= 20):.1f}%, "
      f"median IOB {np.median(v[stale<=20]):.2f} U")
print(f"BG mean {bg[keep].mean():.1f}, TIR {100*np.mean((bg[keep]>=70)&(bg[keep]<=180)):.1f}%")
print(f"-> {out}")

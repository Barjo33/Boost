"""Machinery for a side-by-side multi-sensor study.

DESIGN. Four sensors worn simultaneously, two reporting at one minute and two at five. The
second sensor of each cadence is what makes the study work: a pair of sensors differing only
by unit gives the empirical null distribution for every contrast. Any one-minute versus
five-minute difference must exceed the difference between two sensors of the same cadence
before it can be attributed to the reporting rate.

The design also supplies two things a single-sensor study cannot:

  A common target. Prediction and event definitions use a consensus built from all four
  sensors, so no arm is scored against its own idiosyncrasies.

  Direct noise decomposition. With simultaneous duplicates, the variance of the difference
  between two sensors of the same model is twice the sensor noise variance, with the true
  glucose cancelling. No assumption about the noise process is required.

ANALYSIS IS PAIRED throughout. Both arms observe the same glucose on the same person at the
same moment, so day-level variation is common and cancels in the difference. Bootstrap the
difference over whole days, never the two arms separately.

INPUT FORMAT. A CSV with columns: sensor_id, cadence_min, ts_utc (ISO8601 or epoch ms), mgdl.
One row per reading. Optionally a `session` column so sensor changes can be excluded around
warm-up.
"""
import os, json, numpy as np, pandas as pd, datetime as dt

RESULTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
WARMUP_HOURS = 12          # excluded after any session start
SEED = 20260731

def load_csv(path):
    df = pd.read_csv(path)
    if np.issubdtype(df["ts_utc"].dtype, np.number):
        df["ts"] = df["ts_utc"].astype("int64")
    else:
        df["ts"] = (pd.to_datetime(df["ts_utc"], utc=True).astype("int64")//10**6)
    df = df.dropna(subset=["mgdl"]).sort_values(["sensor_id", "ts"]).reset_index(drop=True)
    df["day"] = pd.to_datetime(df["ts"], unit="ms", utc=True).dt.date
    return df

def drop_warmup(df):
    if "session" not in df.columns: return df
    keep = []
    for (sid, sess), g in df.groupby(["sensor_id", "session"]):
        t0 = g["ts"].min()
        keep.append(g[g["ts"] >= t0 + WARMUP_HOURS*3600*1000])
    return pd.concat(keep).sort_values(["sensor_id", "ts"]).reset_index(drop=True)

def sensors(df):
    return (df.groupby("sensor_id")["cadence_min"].first().to_dict())

def common_grid(df, step_min=1):
    """A regular grid spanning the overlap of all sensors."""
    lo = df.groupby("sensor_id")["ts"].min().max()
    hi = df.groupby("sensor_id")["ts"].max().min()
    return np.arange(lo, hi + 1, step_min*60_000, dtype=np.int64)

def on_grid(df, sid, grid, max_gap_min):
    """Last reported value at or before each grid point, NaN if staler than max_gap."""
    g = df[df["sensor_id"] == sid]
    ts = g["ts"].to_numpy(); v = g["mgdl"].to_numpy(float)
    j = np.searchsorted(ts, grid, side="right") - 1
    out = np.full(len(grid), np.nan)
    ok = j >= 0
    out[ok] = v[j[ok]]
    stale = np.full(len(grid), np.inf)
    stale[ok] = (grid[ok] - ts[j[ok]])/60_000.0
    out[stale > max_gap_min] = np.nan
    return out

def consensus(df, grid, exclude=None):
    """Mean of the sensors held out of the arm under test, as a common target."""
    cols = []
    for sid, cad in sensors(df).items():
        if exclude and sid in exclude: continue
        cols.append(on_grid(df, sid, grid, max_gap_min=cad*1.6))
    if not cols: return np.full(len(grid), np.nan)
    M = np.vstack(cols)
    return np.nanmean(M, axis=0)

def paired_day_bootstrap(diff_fn, days, n_boot=2000, seed=SEED):
    """Bootstrap a PAIRED contrast over whole days. diff_fn takes an index array."""
    rng = np.random.default_rng(seed)
    du = np.unique(days); idx = {d: np.nonzero(days == d)[0] for d in du}
    bs = []
    for _ in range(n_boot):
        pick = rng.choice(du, size=len(du), replace=True)
        sel = np.concatenate([idx[d] for d in pick])
        try:
            v = diff_fn(sel)
            if np.isfinite(v): bs.append(float(v))
        except Exception:
            pass
    if not bs: return (float("nan"), float("nan"))
    return float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5))

def variogram(ts, v, days, lags, tol_min, n_boot=600):
    out = {}
    for L in lags:
        j = np.searchsorted(ts, ts + int(L*60_000))
        ok = (j < len(ts))
        i_ = np.nonzero(ok)[0]; j_ = j[ok]
        keep = np.abs((ts[j_]-ts[i_])/60_000.0 - L) <= tol_min
        a, b = i_[keep], j_[keep]
        good = np.isfinite(v[a]) & np.isfinite(v[b])
        a, b = a[good], b[good]
        if len(a) < 200: continue
        sq = (v[b]-v[a])**2; dy = days[a]
        pt = float(sq.mean())
        lo, hi = paired_day_bootstrap(lambda s: sq[s].mean(), dy, n_boot)
        out[L] = dict(D=pt, lo=lo, hi=hi, n=int(len(sq)))
    return out

def save(name, obj):
    os.makedirs(RESULTS, exist_ok=True)
    p = os.path.join(RESULTS, name)
    with open(p, "w") as f: json.dump(obj, f, indent=1, default=float)
    print(f"  -> {p}")

def read(name):
    with open(os.path.join(RESULTS, name)) as f: return json.load(f)

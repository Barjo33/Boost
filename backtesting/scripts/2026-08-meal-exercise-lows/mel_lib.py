"""Shared machinery: meal episodes, the lows that follow them, and the window between.

The register already records that recent activity is a per-user leading indicator of a forward
hypo, and that the post-meal-exercise crash is a carb-counterweight problem rather than a dosing
one. Neither settles the question posed here, which is DISCRIMINATIVE: given that a meal has
happened, do exercise indicators observed BETWEEN the meal and the low tell that meal apart from
one that ends safely? That is what an anticipation or back-out layer would need.

Definitions, chosen to work for every user rather than only those running V6:

  MEAL       a rise of >= RISE_MGDL within CLIMB_WINDOW minutes from a local trough. Purely
             glucose-based, so it does not depend on carbs being announced or on the meal state
             machine, which only exists for some users.
  LOW        glucose below LOW_MGDL for at least MIN_LOW_MIN, to exclude single-sample dips.
  ONSET      the first sample below the threshold.
  WINDOW     meal onset to low onset, capped at MAX_GAP_H. For meals with no low, a matched
             window of the same median length, so the comparison is like for like.
"""
import numpy as np, psycopg2, datetime as dt, collections

DSN = "dbname=oref host=127.0.0.1 port=5432"
SINCE = "2026-05-01"
RISE_MGDL, CLIMB_WINDOW, TROUGH_LOOKBACK = 40.0, 90, 20
LOW_MGDL, SEVERE_MGDL, MIN_LOW_MIN = 70.0, 54.0, 15
MAX_GAP_H = 6.0

def users(min_rows=20000):
    with psycopg2.connect(DSN) as c, c.cursor() as cur:
        cur.execute("select user_id, count(*) from boost_cgm where cgm_mgdl is not null "
                    "and ts_utc>=%s group by 1 having count(*)>%s order by 1", (SINCE, min_rows))
        return [r[0] for r in cur.fetchall()]

def load_cgm(u):
    with psycopg2.connect(DSN) as c, c.cursor() as cur:
        cur.execute("select ts_utc, cgm_mgdl from boost_cgm where user_id=%s and cgm_mgdl is not null "
                    "and ts_utc>=%s order by ts_utc", (u, SINCE))
        r = cur.fetchall()
    return np.array([x[0] for x in r]), np.array([float(x[1]) for x in r])

def load_context(u):
    """Per-cycle exercise and dosing context, on its own (coarser, irregular) time base."""
    with psycopg2.connect(DSN) as c, c.cursor() as cur:
        cur.execute("""select ts_utc, steps_5m, steps_15m, steps_30m, steps_60m,
                              hr_avg, hrr_pct, hr_zone, sug_iob, sug_cob,
                              boost_activity_load_ratio, boost_activity_load_intraday_ratio
                       from boost_decisions where user_id=%s and ts_utc>=%s order by ts_utc""",
                    (u, SINCE))
        return cur.fetchall()

def nominal_min(ts):
    return float(np.median(np.diff(ts).astype("timedelta64[s]").astype(float))/60.0)

def meals(ts, bg, nom):
    """Climb episodes: (onset_index, peak_index)."""
    n = len(ts)
    kw = max(int(round(CLIMB_WINDOW/nom)), 2); kb = max(int(round(TROUGH_LOOKBACK/nom)), 1)
    out, i = [], kb
    while i < n-2:
        j = min(i+kw, n-1)
        if (ts[j]-ts[i]).total_seconds()/60.0 > CLIMB_WINDOW*1.4: i += 1; continue
        seg = bg[i:j+1]
        if seg.max()-bg[i] < RISE_MGDL: i += 1; continue
        if bg[i] > bg[max(i-kb,0):i+1].min()+5.0: i += 1; continue
        pk = i+int(np.argmax(seg)); out.append((i, pk)); i = pk+1
    return out

def lows(ts, bg, nom, thr=LOW_MGDL):
    """Sustained excursions below thr: list of (onset_index, nadir_value)."""
    need = max(int(round(MIN_LOW_MIN/nom)), 1)
    below = bg < thr
    out, i = [], 0
    n = len(bg)
    while i < n:
        if not below[i]: i += 1; continue
        j = i
        while j < n and below[j]: j += 1
        if (ts[j-1]-ts[i]).total_seconds()/60.0 >= MIN_LOW_MIN - 1e-9 or (j-i) >= need:
            out.append((i, float(bg[i:j].min())))
        i = j
    return out

def window_context(ctx, t0, t1):
    """Exercise indicators observed strictly between two instants."""
    rows = [r for r in ctx if t0 <= r[0] <= t1]
    if not rows: return None
    def col(k, cast=float):
        v = [cast(r[k]) for r in rows if r[k] is not None]
        return v or None
    s5, s15, s30, s60 = (col(1), col(2), col(3), col(4))
    hr, hrr = col(5), col(6)
    zones = [str(r[7]) for r in rows if r[7]]
    iob, cob = col(8), col(9)
    load, intra = col(10), col(11)
    return dict(
        n=len(rows),
        steps5_max=max(s5) if s5 else None, steps5_sum=sum(s5) if s5 else None,
        steps30_max=max(s30) if s30 else None, steps60_max=max(s60) if s60 else None,
        hr_max=max(hr) if hr else None, hr_mean=float(np.mean(hr)) if hr else None,
        hrr_max=max(hrr) if hrr else None,
        zone_high=sum(1 for z in zones if any(d in z for d in ("3","4","5"))),
        zone_n=len(zones),
        iob_mean=float(np.mean(iob)) if iob else None,
        iob_min=min(iob) if iob else None,
        cob_max=max(cob) if cob else None,
        load_ratio=float(np.mean(load)) if load else None,
        intraday_ratio=float(np.mean(intra)) if intra else None)

#!/usr/bin/env python3
"""Auto-config PERIODIC RE-DERIVATION replay (2026-08-03).

QUESTION. Auto-config derives the V5/V6 knobs once, from the user's own trailing
history. Should it re-run on a schedule, so the settings track the person as they
drift? Before writing any of that, this replay walks the *existing* history and
asks what a periodic re-derivation would actually have done.

THE HAZARD BEING TESTED (the reason this isn't obviously free). Two of the derived
knobs read the delivered-SMB distribution:

    confirmedCap = max(p90 manual bolus, p95 SMB)      committedCap = max(p75 SMB, TDD/40)

On day one those SMBs come from the *previous* algorithm — exogenous. After
migration they are Boost's own SMBs, and Boost's SMBs are clipped by the very caps
being derived. p95(delivered) <= cap by construction, so a naive re-run can only pull
the cap down or leave it: a monotone ratchet with no restoring force. The anchors
that could stop it are the manual-bolus p90 (unclipped, but needs n>=10 and vanishes
for a no-announce user) and TDD/40 (which itself falls as the caps tighten).

WHAT THIS MEASURES (all on real telemetry; no counterfactual BG is claimed):
  1. CENSORING   — how much of the delivered-SMB distribution actually sits at the
                   operative cap (the precondition for a ratchet). Measured against
                   the per-cycle logged caps, not an assumed one.
  2. RATCHET     — the knob trajectory under repeated re-derivation, per user, with
                   the V1->V6 regime change marked (pre-migration windows read the
                   previous algorithm's doses and are NOT self-referential).
  3. BIAS PER STEP — delivered-sourced vs desired-sourced (pre-cap) caps on the same
                   window, where the dose-chain telemetry exists to reconstruct the
                   uncapped shot (budget x actionMult x velocityFactor, logged from
                   2026-07-10). This is the size of one ratchet step.
  4. CHURN       — changes per user per 6 months and the share reversed within two
                   windows: the metric that killed the online cap-stepper at 43%.
  5. A/A NULL    — split-half re-derivation inside a single window. Any knob change
                   between two halves of the SAME window is sampling noise, not
                   drift. Without this there is no baseline to judge (4) against.
  6. HARM PRICE  — for cap LOWERINGS (the ratchet's direction), the insulin removed,
                   priced against observed lows: share of removed insulin delivered
                   within 3 h of a real <70 (protective) vs not (costly).

Usage:  python3 redrive_replay.py [--window 28] [--step 28] [--out REPORT.md]
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import sys

import numpy as np
import pandas as pd
import psycopg2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import boost_autoconfig as ac  # noqa: E402

DSN = "dbname=oref host=127.0.0.1 port=5432"
USERS = ["tim", "A", "B", "C", "D", "E", "F", "H"]     # G: no V6 telemetry; I: <2 weeks
CLIP_TOL = 0.98            # delivered >= CLIP_TOL * operative cap counts as the cap binding
LOW_MGDL = 70.0
LOW_WINDOW_H = 3.0
RNG = np.random.default_rng(20260803)


# ── loading ─────────────────────────────────────────────────────────────────────
def load():
    conn = psycopg2.connect(DSN)
    # bolus_type is the uploaded BS.Type. 266 records (user C, 2026-03-03..03-11, an older
    # uploader) carry no type at all: unclassifiable as manual-vs-SMB, so they are dropped
    # rather than guessed. They fall outside every derived window, so this changes nothing
    # in the current run — it stops a future re-run inheriting a silent mis-classification.
    tre = pd.read_sql("""
        SELECT user_id, ts_utc, insulin, is_smb, bolus_type
        FROM boost_treatments
        WHERE insulin > 0 AND bolus_type IS NOT NULL AND user_id = ANY(%s)""",
                      conn, params=(USERS,))
    cgm = pd.read_sql("""
        SELECT user_id, ts_utc, cgm_mgdl FROM boost_cgm
        WHERE cgm_mgdl > 1 AND user_id = ANY(%s)""", conn, params=(USERS,))
    dec = pd.read_sql("""
        SELECT DISTINCT ON (user_id, floor(ts_epoch/300.0))
          user_id, ts_utc, ts_epoch, tdd, boostv5_state AS state, boostv5_active AS active,
          boostv5_finaldose AS fd, boostv5_committedcap AS ccap, boostv5_confirmedcap AS fcap,
          boostv5_budget AS budget, boostv5_actionmult AS amult,
          boostv5_velocityfactor AS vf
        FROM boost_decisions WHERE user_id = ANY(%s)
        ORDER BY user_id, floor(ts_epoch/300.0), ts_epoch DESC""", conn, params=(USERS,))
    conn.close()
    for d in (tre, cgm, dec):
        d["ts"] = pd.to_datetime(d.ts_utc, utc=True)
    # uncapped desired shot: the raw pre-state-cap dose (commit a78bf7bb95's own chain)
    dec["desired"] = dec.budget * dec.amult * dec.vf
    return tre, cgm, dec


def daily_tdd(dec_u):
    """Per-day TDD from the engine's own rolling TDD field (median within the day)."""
    d = dec_u[dec_u.tdd > 0]
    if d.empty:
        return pd.Series(dtype=float)
    return d.groupby(d.ts.dt.date).tdd.median()


# ── profile construction ────────────────────────────────────────────────────────
def build_profile(t0, t1, tre_u, cgm_u, dec_u, smb_source="delivered"):
    """A V1Profile for the window [t0, t1), exactly as the plugin would gather it."""
    tw = tre_u[(tre_u.ts >= t0) & (tre_u.ts < t1)]
    cw = cgm_u[(cgm_u.ts >= t0) & (cgm_u.ts < t1)]
    dw = dec_u[(dec_u.ts >= t0) & (dec_u.ts < t1)]
    if cw.empty:
        return None, {}

    if smb_source == "delivered":
        smb = tw[tw.is_smb].insulin.tolist()
    elif smb_source == "desired_dosed":
        # The uncapped shot, restricted to cycles that ACTUALLY delivered. This is the
        # like-for-like comparator: the same events as `delivered`, un-clipped. Taking
        # every positive raw shot instead would fold in cycles the brakes/gates zeroed,
        # which were never doses at all.
        smb = dw.desired[(dw.desired > 0) & (dw.fd > 0)].dropna().tolist()
    else:                                     # "desired_all" — every positive raw shot
        smb = dw.desired[dw.desired > 0].dropna().tolist()
    manual = tw[~tw.is_smb].insulin.tolist()

    td = daily_tdd(dw)
    n = len(cw)
    bg = cw.cgm_mgdl.values
    prof = ac.V1Profile(
        daysWithData=int(len(td)), bgReadingCount=int(n),
        tddMedianU=float(np.median(td)) if len(td) else 0.0,
        manualBolusesU=manual, smbAmountsU=smb,
        tbrBelow70Pct=100.0 * float((bg < 70).sum()) / n,
        timeBelow54Pct=100.0 * float((bg < 54).sum()) / n,
        meanGlucoseMgdl=float(bg.mean()))
    meta = dict(n_smb=len(smb), n_manual=len(manual), n_cgm=n, n_days=len(td),
                v6_share=float(dw.active.fillna(False).mean()) if len(dw) else np.nan,
                op_ccap=float(dw.ccap.median()) if dw.ccap.notna().any() else np.nan,
                op_fcap=float(dw.fcap.median()) if dw.fcap.notna().any() else np.nan)
    return prof, meta


def windows(cgm_u, width_d, step_d):
    t0 = cgm_u.ts.min().floor("D")
    end = cgm_u.ts.max()
    out = []
    while t0 + pd.Timedelta(days=width_d) <= end:
        out.append((t0, t0 + pd.Timedelta(days=width_d)))
        t0 = t0 + pd.Timedelta(days=step_d)
    return out


# ── low-within-3h lookup (exact, O(1) per query after an O(n log n) build) ──────
class LowLookup:
    """Was there a CGM value < LOW_MGDL in (t, t+3h]?  Sparse-table range-min."""

    def __init__(self, cgm_u):
        self.t = cgm_u.ts.values.astype("datetime64[ns]").astype("int64")
        v = cgm_u.cgm_mgdl.values.astype(float)
        n = len(v)
        self.k = max(1, int(np.floor(np.log2(n))) + 1) if n else 1
        self.tab = [v]
        j = 1
        while (1 << j) <= n:
            prev = self.tab[-1]
            self.tab.append(np.minimum(prev[: n - (1 << j) + 1], prev[(1 << (j - 1)):]))
            j += 1
        self.n = n
        self.span = int(LOW_WINDOW_H * 3600 * 1e9)

    def _rmin(self, lo, hi):                     # min over [lo, hi)
        if hi <= lo or self.n == 0:
            return np.inf
        j = int(np.floor(np.log2(hi - lo)))
        return min(self.tab[j][lo], self.tab[j][hi - (1 << j)])

    def any_low(self, ts_ns):
        lo = np.searchsorted(self.t, ts_ns, "right")
        hi = np.searchsorted(self.t, ts_ns + self.span, "right")
        return np.array([self._rmin(a, b) < LOW_MGDL for a, b in zip(lo, hi)])


# ── 1. censoring: is the delivered distribution actually clipped? ───────────────
def censoring(dec_u, tre_u):
    """Clip rate against the cap that actually governs each state's shot."""
    d = dec_u[(dec_u.fd > 0) & dec_u.active.fillna(False)].copy()
    if d.empty:
        return {}
    d["opcap"] = np.where(d.state.eq("CONFIRMED"), d.fcap, d.ccap)
    dd = d[d.opcap.notna()]
    smb = tre_u[tre_u.is_smb & (tre_u.ts >= d.ts.min())].insulin.tolist()
    com = dd[dd.state.eq("COMMITTED")]
    con = dd[dd.state.eq("CONFIRMED")]
    return dict(n_dosed=len(dd),
                clip_all=float((dd.fd >= CLIP_TOL * dd.opcap).mean()),
                clip_committed=float((com.fd >= CLIP_TOL * com.ccap).mean()) if len(com) else np.nan,
                clip_confirmed=float((con.fd >= CLIP_TOL * con.fcap).mean()) if len(con) else np.nan,
                p75_smb=ac.percentile(smb, 75.0), p95_smb=ac.percentile(smb, 95.0),
                op_ccap=float(dd.ccap.median()), op_fcap=float(dd.fcap.median()))


# ── 2/4. trajectory + churn ─────────────────────────────────────────────────────
KNOBS = ("aggression", "hypoCaution", "confirmedCap", "committedCap", "cumulative60", "primerCap")


def which_terms(prof):
    """Which term is actually BINDING each cap — the anchor question."""
    p75 = ac.percentile(prof.smbAmountsU, 75.0)
    p95 = ac.percentile(prof.smbAmountsU, 95.0)
    mp90 = (ac.percentile(prof.manualBolusesU, 90.0)
            if len(prof.manualBolusesU) >= ac.MIN_MANUAL_BOLUS_SAMPLES else 0.0)
    tdd40 = prof.tddMedianU / 40.0
    if max(p95, mp90) < 1.5:
        fterm = "floor(1.5)"
    else:
        fterm = "manual_p90" if mp90 > p95 else "smb_p95"
    cterm = "tdd/40" if tdd40 > p75 else ("smb_p75" if p75 > 0.25 else "floor(0.25)")
    return fterm, cterm, p75, p95, mp90, tdd40


def trajectory(u, tre_u, cgm_u, dec_u, width, step, smb_source="delivered"):
    rows = []
    for (t0, t1) in windows(cgm_u, width, step):
        prof, meta = build_profile(t0, t1, tre_u, cgm_u, dec_u, smb_source)
        if prof is None:
            continue
        s = ac.compute(prof)
        r = dict(user=u, t0=t0, t1=t1, derived=s is not None, **meta)
        if s:
            r.update(s.knobs())
            fterm, cterm, p75, p95, mp90, tdd40 = which_terms(prof)
            r.update(tbr70=prof.tbrBelow70Pct, sev54=prof.timeBelow54Pct, tdd=prof.tddMedianU,
                     fcap_term=fterm, ccap_term=cterm,
                     p75=p75, p95=p95, manual_p90=mp90, tdd40=tdd40)
        rows.append(r)
    return pd.DataFrame(rows)


# ── 5. A/A null — day-block bootstrap AT THE ANALYSIS WINDOW WIDTH ─────────────
def bootstrap_band(t0, t1, tre_u, cgm_u, dec_u, n_boot=300):
    """Sampling distribution of each derived knob, holding the person fixed.

    Days are the resampling unit (CGM within a day is heavily autocorrelated, so an
    iid bootstrap over readings would badly understate the spread). Same window width
    as the trajectory, so the band is directly comparable to a between-window change.
    """
    tw = tre_u[(tre_u.ts >= t0) & (tre_u.ts < t1)]
    cw = cgm_u[(cgm_u.ts >= t0) & (cgm_u.ts < t1)]
    dw = dec_u[(dec_u.ts >= t0) & (dec_u.ts < t1)]
    if cw.empty:
        return None
    cg_by_day = {d: g.cgm_mgdl.values for d, g in cw.groupby(cw.ts.dt.date)}
    smb_by_day, man_by_day = {}, {}
    for d, g in tw.groupby(tw.ts.dt.date):
        smb_by_day[d] = g[g.is_smb].insulin.values
        man_by_day[d] = g[~g.is_smb].insulin.values
    tdd_by_day = daily_tdd(dw).to_dict()
    days = sorted(cg_by_day)
    if len(days) < 7:
        return None

    out = {k: [] for k in KNOBS}
    for _ in range(n_boot):
        pick = RNG.choice(len(days), len(days), replace=True)
        sel = [days[i] for i in pick]
        bg = np.concatenate([cg_by_day[d] for d in sel])
        smb = np.concatenate([smb_by_day.get(d, np.empty(0)) for d in sel]).tolist()
        man = np.concatenate([man_by_day.get(d, np.empty(0)) for d in sel]).tolist()
        tdds = [tdd_by_day[d] for d in sel if d in tdd_by_day]
        p = ac.V1Profile(
            daysWithData=max(len(tdds), ac.MIN_DAYS), bgReadingCount=len(bg),
            tddMedianU=float(np.median(tdds)) if tdds else 0.0,
            manualBolusesU=man, smbAmountsU=smb,
            tbrBelow70Pct=100.0 * float((bg < 70).sum()) / len(bg),
            timeBelow54Pct=100.0 * float((bg < 54).sum()) / len(bg),
            meanGlucoseMgdl=float(bg.mean()))
        s = ac.compute(p)
        if s:
            for k, v in s.knobs().items():
                if k in out:
                    out[k].append(v)
    return {k: (np.percentile(v, 2.5), np.percentile(v, 97.5)) if len(v) > 10 else (np.nan, np.nan)
            for k, v in out.items()}


def material_churn(g, bands, knob):
    """Changes that exceed the same window's own sampling band (a real move, not noise)."""
    g = g[g.derived].sort_values("t0").reset_index(drop=True)
    b = bands[bands.user == g.user.iloc[0]].set_index("t0")
    n_change = n_material = 0
    for i in range(1, len(g)):
        d = g[knob][i] - g[knob][i - 1]
        if abs(d) <= 1e-9:
            continue
        n_change += 1
        t = g.t0[i]
        if t in b.index and np.isfinite(b.loc[t, f"{knob}_lo"]):
            half = (b.loc[t, f"{knob}_hi"] - b.loc[t, f"{knob}_lo"]) / 2.0
            if abs(d) > half:
                n_material += 1
    return dict(user=g.user.iloc[0], n_changes=n_change, n_material=n_material,
                material_share=n_material / n_change if n_change else np.nan)


def churn_stats(traj, knob):
    """Changes, direction split, reversals within two windows, net drift."""
    v = traj[traj.derived][knob].dropna().values
    if len(v) < 2:
        return {}
    d = np.diff(v)
    changed = np.flatnonzero(np.abs(d) > 1e-9)
    dirs = np.sign(d[changed])
    rev = 0
    for i in range(len(changed) - 1):
        # a reversal: next change is opposite in sign and lands within two windows
        if dirs[i + 1] != dirs[i] and (changed[i + 1] - changed[i]) <= 2:
            rev += 1
    return dict(n_windows=len(v), n_changes=len(changed),
                down=int((dirs < 0).sum()), up=int((dirs > 0).sum()),
                reversals=rev,
                revert_rate=rev / len(changed) if len(changed) else np.nan,
                first=v[0], last=v[-1], net=v[-1] - v[0],
                monotone_down=float((dirs < 0).mean()) if len(dirs) else np.nan)


# ── 5. A/A null: split-half re-derivation inside one window ─────────────────────
def aa_null(u, tre_u, cgm_u, dec_u, width):
    out = []
    for (t0, t1) in windows(cgm_u, width, width):
        mid = t0 + (t1 - t0) / 2
        # each half is scaled up to the full window's data requirement, so the
        # MIN_DAYS/MIN_BG gates don't reject halves for being half-length
        pa, _ = build_profile(t0, mid, tre_u, cgm_u, dec_u)
        pb, _ = build_profile(mid, t1, tre_u, cgm_u, dec_u)
        if pa is None or pb is None:
            continue
        for p in (pa, pb):
            p.daysWithData *= 2
            p.bgReadingCount *= 2
        sa, sb = ac.compute(pa), ac.compute(pb)
        if not sa or not sb:
            continue
        row = dict(user=u, t0=t0)
        for k in KNOBS:
            row[k] = sb.knobs()[k] - sa.knobs()[k]
        out.append(row)
    return pd.DataFrame(out)


# ── 6. harm pricing of cap lowerings ───────────────────────────────────────────
def price_lowering(dec_u, low, t0, t1, cap_old, cap_new):
    """Insulin a LOWER committedCap would have removed, priced against observed lows."""
    d = dec_u[(dec_u.ts >= t0) & (dec_u.ts < t1) & (dec_u.fd > 0) & dec_u.active.fillna(False)]
    if d.empty or cap_new >= cap_old:
        return None
    removed = np.clip(d.fd.values - cap_new, 0, None) - np.clip(d.fd.values - cap_old, 0, None)
    keep = removed > 1e-9
    if not keep.any():
        return None
    rem = removed[keep]
    pre_low = low.any_low(d.ts.values[keep].astype("datetime64[ns]").astype("int64"))
    return dict(u_removed=float(rem.sum()), n_cycles=int(keep.sum()),
                protective_share=float(rem[pre_low].sum() / rem.sum()))


def baseline_prelow_share(dec_u, low):
    """Share of ALL delivered insulin that lands within 3 h of a real low (the baseline)."""
    d = dec_u[(dec_u.fd > 0) & dec_u.active.fillna(False)]
    if d.empty:
        return np.nan
    pre = low.any_low(d.ts.values.astype("datetime64[ns]").astype("int64"))
    return float(d.fd.values[pre].sum() / d.fd.values.sum())


def lb_pad(s, w=13):
    return s.ljust(w)


def md(df, dp=3):
    """Minimal markdown table (avoids a hard dependency on tabulate)."""
    def cell(x):
        if isinstance(x, float):
            return "" if not np.isfinite(x) else f"{round(x, dp):g}"
        if isinstance(x, pd.Timestamp):
            return f"{x:%Y-%m-%d}"
        return str(x)

    cols = [str(c) for c in df.columns]
    head = "| " + " | ".join(cols) + " |"
    rule = "| " + " | ".join("---" for _ in cols) + " |"
    body = ["| " + " | ".join(cell(v) for v in row) + " |"
            for row in df.itertuples(index=False, name=None)]
    return "\n".join([head, rule] + body)


def boot_ci(vals, n=5000):
    v = np.asarray([x for x in vals if np.isfinite(x)], float)
    if len(v) < 2:
        return (np.nan, np.nan)
    b = RNG.choice(v, (n, len(v)), replace=True).mean(1)
    return tuple(np.percentile(b, [2.5, 97.5]))


# ── main ────────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--window", type=int, default=28)
    ap.add_argument("--step", type=int, default=28)
    ap.add_argument("--out", default="REDRIVE_REPORT.md")
    ap.add_argument("--users", help="comma-separated subset (default: the whole cohort)")
    ap.add_argument("--boot", type=int, default=300, help="day-block bootstrap draws per window")
    a = ap.parse_args()

    global USERS
    if a.users:
        USERS = a.users.split(",")
    tre, cgm, dec = load()
    trajs, aas, cens, prices, bands = [], [], [], [], []

    for u in USERS:
        tre_u = tre[tre.user_id == u].sort_values("ts")
        cgm_u = cgm[cgm.user_id == u].sort_values("ts")
        dec_u = dec[dec.user_id == u].sort_values("ts")
        if cgm_u.empty or tre_u.empty:
            print(f"[{u}] skipped (no data)")
            continue
        t = trajectory(u, tre_u, cgm_u, dec_u, a.window, a.step)
        trajs.append(t)
        aas.append(aa_null(u, tre_u, cgm_u, dec_u, a.window))
        for _, w in t[t.derived].iterrows():                 # sampling band per window
            bd = bootstrap_band(w.t0, w.t1, tre_u, cgm_u, dec_u, a.boot)
            if bd:
                bands.append(dict(user=u, t0=w.t0,
                                  **{f"{k}_{s}": v[i] for k, v in bd.items()
                                     for i, s in enumerate(("lo", "hi"))}))
        c = censoring(dec_u, tre_u)
        if c:
            cens.append(dict(user=u, **c))
        # price every committedCap lowering the trajectory produces
        low = LowLookup(cgm_u)
        base = baseline_prelow_share(dec_u, low)
        td = t[t.derived].reset_index(drop=True)
        for i in range(1, len(td)):
            old, new = td.committedCap[i - 1], td.committedCap[i]
            if new < old - 1e-9:
                p = price_lowering(dec_u, low, td.t0[i], td.t1[i], old, new)
                if p:
                    prices.append(dict(user=u, t0=td.t0[i], old=old, new=new,
                                       baseline=base, **p))
        print(f"[{u}] {len(t)} windows, {int(t.derived.sum())} derived")

    traj = pd.concat(trajs, ignore_index=True)
    aa = pd.concat(aas, ignore_index=True) if aas else pd.DataFrame()
    here = os.path.dirname(os.path.abspath(__file__))
    traj.to_csv(os.path.join(here, f"trajectory_w{a.window}.csv"), index=False)
    if bands:
        pd.DataFrame(bands).to_csv(os.path.join(here, f"bands_w{a.window}.csv"), index=False)

    # ── desired-vs-delivered bias, on windows where the dose chain is logged ────
    bias = []
    for u in USERS:
        tre_u = tre[tre.user_id == u].sort_values("ts")
        cgm_u = cgm[cgm.user_id == u].sort_values("ts")
        dec_u = dec[dec.user_id == u].sort_values("ts")
        d_vf = dec_u[dec_u.desired.notna()]
        if len(d_vf) < 500:
            continue
        t1 = d_vf.ts.max()
        t0 = max(d_vf.ts.min(), t1 - pd.Timedelta(days=a.window))
        pa, _ = build_profile(t0, t1, tre_u, cgm_u, dec_u, "delivered")
        pb, _ = build_profile(t0, t1, tre_u, cgm_u, dec_u, "desired_dosed")
        if pa is None or pb is None:
            continue
        for p in (pa, pb):                       # short window: waive the data gates
            p.daysWithData = max(p.daysWithData, ac.MIN_DAYS)
            p.bgReadingCount = max(p.bgReadingCount, ac.MIN_BG_READINGS)
        sa, sb = ac.compute(pa), ac.compute(pb)
        if sa and sb:
            bias.append(dict(user=u, days=(t1 - t0).days,
                             n_delivered=len(pa.smbAmountsU), n_desired=len(pb.smbAmountsU),
                             p75_delivered=ac.percentile(pa.smbAmountsU, 75.0),
                             p75_desired=ac.percentile(pb.smbAmountsU, 75.0),
                             p95_delivered=ac.percentile(pa.smbAmountsU, 95.0),
                             p95_desired=ac.percentile(pb.smbAmountsU, 95.0),
                             tdd40=pa.tddMedianU / 40.0,
                             ccap_delivered=sa.committedCapU, ccap_desired=sb.committedCapU,
                             fcap_delivered=sa.confirmedCapU, fcap_desired=sb.confirmedCapU))
    bias = pd.DataFrame(bias)

    write_report(a, traj, aa, pd.DataFrame(cens), bias, pd.DataFrame(prices), pd.DataFrame(bands))
    print(f"wrote {a.out}")


def write_report(a, traj, aa, cens, bias, prices, bands):
    L = []
    P = L.append
    P(f"# Auto-config periodic re-derivation — replay report\n")
    P(f"Generated {dt.datetime.now(dt.UTC):%Y-%m-%d %H:%M UTC} · "
      f"window {a.window}d, step {a.step}d · users {', '.join(sorted(traj.user.unique()))}\n")
    P("Data: local TimescaleDB (`boost_decisions`, `boost_cgm`, `boost_treatments`), refreshed to t=now. "
      "Knobs derived by `boost_autoconfig.py`, a verbatim port of `BoostV5AutoConfig.compute()` "
      "(selftest-checked, including Kotlin's half-up rounding).\n")

    P("\n## 1. Censoring — is the delivered-SMB distribution actually clipped?\n")
    if len(cens):
        P(md(cens))
    P("\n`at_committed_cap` = share of delivered SMBs sitting at (>=98% of) the operative "
      "committedCap logged for that cycle. This is the precondition for the ratchet: the "
      "derivation's p75/p95 inputs are censored exactly to the extent this is non-zero.\n")

    P("\n## 2. Trajectory under repeated re-derivation\n")
    for knob in KNOBS:
        rows = []
        for u, g in traj.groupby("user"):
            s = churn_stats(g.sort_values("t0"), knob)
            if s:
                rows.append(dict(user=u, **s))
        if not rows:
            continue
        df = pd.DataFrame(rows)
        P(f"\n### {knob}\n")
        P(md(df))
        lo, hi = boot_ci(df.net.values)
        P(f"\nPooled net drift (first→last window): mean {df.net.mean():+.3f}, "
          f"bootstrap 95% CI [{lo:+.3f}, {hi:+.3f}] — "
          f"{'distinguishable from zero' if (lo > 0 or hi < 0) else 'overlaps zero'}.\n")

    P("\n## 2b. Which term is actually binding each cap\n")
    tt = traj[traj.derived]
    if "ccap_term" in tt:
        P(md(pd.DataFrame([
            dict(cap="committedCap = max(smb_p75, TDD/40)",
                 **{k: f"{v} ({v / len(tt):.0%})"
                    for k, v in tt.ccap_term.value_counts().items()}),
            dict(cap="confirmedCap = max(manual_p90, smb_p95)",
                 **{k: f"{v} ({v / len(tt):.0%})"
                    for k, v in tt.fcap_term.value_counts().items()}),
        ]).fillna("")))
        P("\nThe ratchet can only bite through a term that reads Boost's own clipped output. "
          "Where TDD/40 or the 1.5 U floor binds instead, the derived cap is anchored to something "
          "the caps do not censor.\n")

    P("\n## 3. One ratchet step: delivered-sourced vs desired-sourced caps\n")
    if len(bias):
        P(md(bias))
        for lbl, d in (("committedCap", bias.ccap_desired - bias.ccap_delivered),
                       ("confirmedCap", bias.fcap_desired - bias.fcap_delivered),
                       ("p95(SMB)", bias.p95_desired - bias.p95_delivered)):
            lo, hi = boot_ci(d.values)
            P(f"\n{lb_pad(lbl)} from the uncapped desired shot minus from delivered SMBs: "
              f"mean {d.mean():+.3f}, 95% CI [{lo:+.3f}, {hi:+.3f}].")
        P("\n\n`desired` = budget x actionMult x velocityFactor on cycles that ACTUALLY dosed — the "
          "same events as `delivered`, un-clipped. Windows are short (the dose-chain fields were only "
          "added 2026-07-10), so `days`/`n` are the honest sample here.\n")
    else:
        P("_No window carried the dose-chain telemetry (velocityFactor) in sufficient volume._\n")

    P("\n## 3b. Material change rate — changes that beat their own sampling band\n")
    if len(bands):
        rows = []
        for knob in KNOBS:
            per = [material_churn(g, bands, knob) for _, g in traj.groupby("user")
                   if g.derived.sum() >= 2]
            per = [r for r in per if r["n_changes"]]
            if not per:
                continue
            df = pd.DataFrame(per)
            lo, hi = boot_ci(df.material_share.values)
            rows.append(dict(knob=knob, users=len(df), changes=int(df.n_changes.sum()),
                             material=int(df.n_material.sum()),
                             material_share=df.n_material.sum() / df.n_changes.sum(),
                             ci_lo=lo, ci_hi=hi))
        P(md(pd.DataFrame(rows)))
        P("\nThe band is a day-block bootstrap (days are the resampling unit) over the SAME window, "
          "so it isolates sampling noise from real drift. A change inside the band is a number the "
          "same fortnight could have produced by chance.\n")

    P("\n## 4. A/A null — split-half re-derivation inside one window (secondary)\n")
    if len(aa):
        rows = []
        for k in KNOBS:
            v = aa[k].dropna().values
            lo, hi = boot_ci(np.abs(v))
            rows.append(dict(knob=k, n=len(v), mean_abs_delta=np.abs(v).mean(),
                             ci_lo=lo, ci_hi=hi,
                             pct_changed=float((np.abs(v) > 1e-9).mean())))
        P(md(pd.DataFrame(rows)))
        P("\nBoth halves come from the same window, so every non-zero delta here is sampling noise. "
          "NOTE the halves are HALF the analysis width, so this overstates the noise of a full "
          f"{a.window}-day derivation — §3b's bootstrap is the like-for-like null. At a 28-day "
          "analysis width the halves are 14 days, i.e. exactly the production LOOKBACK_DAYS, so this "
          "table doubles as the noise floor of the shipping one-shot derivation.\n")

    P("\n## 4b. Drift vs noise — is there anything to track?\n")
    if len(bands):
        rows = []
        for knob in KNOBS:
            per = []
            for u, g in traj.groupby("user"):
                g = g[g.derived].sort_values("t0")
                if len(g) < 2:
                    continue
                b = bands[bands.user == u]
                half = ((b[f"{knob}_hi"] - b[f"{knob}_lo"]) / 2.0).mean()
                drift = abs(g[knob].iloc[-1] - g[knob].iloc[0])
                rng = g[knob].max() - g[knob].min()
                per.append(dict(user=u, drift=drift, span=rng, noise_half=half,
                                ratio=drift / half if half > 0 else np.nan))
            df = pd.DataFrame(per)
            lo, hi = boot_ci(df.ratio.values)
            rows.append(dict(knob=knob, mean_drift=df.drift.mean(),
                             mean_span=df.span.mean(), mean_noise_half=df.noise_half.mean(),
                             drift_over_noise=df.ratio.mean(), ci_lo=lo, ci_hi=hi,
                             users_gt1=int((df.ratio > 1).sum()), n=len(df)))
        P(md(pd.DataFrame(rows)))
        P("\n`drift` = |last window − first window| (~5 months apart); `noise_half` = half-width of "
          "that knob's day-block bootstrap band. Ratio > 1 means the movement over five months is "
          "bigger than the noise of measuring it once — i.e. there is real drift for a re-derivation "
          "to track. Ratio < 1 means a re-run would mostly be chasing its own sampling error.\n")

    P("\n## 5. Harm pricing of committedCap lowerings\n")
    if len(prices):
        P(md(prices))
        d = prices.protective_share - prices.baseline
        lo, hi = boot_ci(d.values)
        P(f"\nRemoved insulin's pre-low share minus the user's baseline pre-low share: "
          f"mean {d.mean():+.3f}, 95% CI [{lo:+.3f}, {hi:+.3f}]. Positive = the removed "
          f"insulin was more often followed by a real low than the user's average unit, "
          f"i.e. the lowering was protective.\n")
    else:
        P("_No committedCap lowering with dosed cycles in the following window._\n")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), a.out)
    open(out, "w").write("\n".join(L))


if __name__ == "__main__":
    main()

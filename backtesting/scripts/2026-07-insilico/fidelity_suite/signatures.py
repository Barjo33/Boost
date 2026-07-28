#!/usr/bin/env python3
"""Fidelity signatures. Each computes the SAME statistic on the real cohort (with a
bootstrap CI) and on the simulator cohort, then returns a divergence verdict.

A signature returns a dict:
  name, category, real, real_ci, sim, sim_ci, metric, verdict, note
verdict is PASS (sim reproduces real within tolerance) or FAIL (sim diverges), or
STRUCTURAL (the mechanism is absent from the model by construction — see fidelity_test.py).
"""
import numpy as np
from scipy.stats import ks_2samp, wasserstein_distance
import common as C


# ---- helpers ---------------------------------------------------------------
def _within_band_outcome_sd(ts, bg, lo, hi, horizon_s=1800, tol_s=240):
    """SD of BG(t+30min) - BG(t) for samples with BG(t) in [lo,hi). CGM-only, so it is
    computable identically on real and sim cohorts. Wide SD = outcome unpredictable."""
    ts = np.asarray(ts, float); bg = np.asarray(bg, float)
    j = np.searchsorted(ts, ts + horizon_s)
    j = np.clip(j, 0, len(ts) - 1)
    good = np.abs(ts[j] - (ts + horizon_s)) <= tol_s
    inband = (bg >= lo) & (bg < hi) & good
    d = bg[j][inband] - bg[inband]
    return d


# ---- S1: marginal variability (CV) -----------------------------------------
def s1_cv(real, sim):
    real_cv = [C.cv(bg) for _, bg in real.values()]
    sim_cv = [C.cv(cgm) for cgm in sim.values()]
    rp, rlo, rhi = C.boot_ci(real_cv, np.median, seed=1)
    sp, slo, shi = C.boot_ci(sim_cv, np.median, seed=2)
    # PASS if the sim median CV lands inside the real cohort's CI
    verdict = "PASS" if rlo <= sp <= rhi else "FAIL"
    return dict(name="Glucose variability (CV%)", category="distribution",
                real=rp, real_ci=(rlo, rhi), sim=sp, sim_ci=(slo, shi),
                metric=f"median CV real {rp:.0f}% vs sim {sp:.0f}%",
                verdict=verdict,
                note="CV is the standard glucose-variability index; the sim runs smoother.")


# ---- S2: short-horizon delta tails (unannounced-meal spikes) ----------------
def _deltas_from_series(ts, bg, lo=240, hi=360):
    dt, dbg = np.diff(ts), np.diff(bg)
    return dbg[(dt >= lo) & (dt <= hi)]


def s2_delta_tails(real, sim):
    rd = np.concatenate([_deltas_from_series(ts, bg) for ts, bg in real.values()])
    sd = np.concatenate([C.sim_deltas_5min(cgm) for cgm in sim.values()])
    r_tail = 100 * np.mean(rd > 10)      # P(rise > 10 mg/dL per 5 min)
    s_tail = 100 * np.mean(sd > 10)
    r_sd, s_sd = rd.std(), sd.std()
    ks = ks_2samp(rd, sd).statistic
    verdict = "FAIL" if (r_tail / max(s_tail, 1e-9) > 1.5 or ks > 0.1) else "PASS"
    return dict(name="Short-horizon delta tails (5 min)", category="dynamics",
                real=r_tail, real_ci=None, sim=s_tail, sim_ci=None,
                metric=f"P(rise>10): real {r_tail:.1f}% vs sim {s_tail:.1f}%; "
                       f"SD {r_sd:.1f} vs {s_sd:.1f}; KS {ks:.2f}",
                verdict=verdict,
                note="Fat positive tails are unannounced-meal onsets the sim never sees.")


# ---- S3: autocorrelation / smoothness --------------------------------------
def s3_acf(real, sim):
    lags = [6, 12]   # 30 and 60 min at 5-min cadence
    r = np.mean([C.acf(bg, lags) for _, bg in real.values()], axis=0)
    s = np.mean([C.acf(C.sim_5min(cgm), lags) for cgm in sim.values()], axis=0)
    gap = float(np.max(np.abs(r - s)))
    verdict = "FAIL" if gap > 0.15 else "PASS"
    return dict(name="Autocorrelation (30/60 min)", category="dynamics",
                real=tuple(round(x, 2) for x in r), real_ci=None,
                sim=tuple(round(x, 2) for x in s), sim_ci=None,
                metric=f"ACF@30/60 real {r[0]:.2f}/{r[1]:.2f} vs sim {s[0]:.2f}/{s[1]:.2f}",
                verdict=verdict,
                note="How fast the glucose curve decorrelates; a proxy for smoothness.")


# ---- S4: outcome unpredictability (efficacy determinism) --------------------
def s4_outcome_sd(real, sim):
    """Within a BG band, SD of where you are 30 min later. Real is wide (efficacy and
    absorption vary); sim is narrow (deterministic dynamics + sensor noise only)."""
    LO, HI = 180, 240
    r = np.concatenate([_within_band_outcome_sd(ts, bg, LO, HI)
                        for ts, bg in (real[u] for u in real)])
    # sim CGM is 1-min; build (t,bg) at 1-min then evaluate
    sim_ds = []
    for cgm in sim.values():
        b5 = C.sim_5min(cgm)
        sim_ds.append(_within_band_outcome_sd(C.sim_ts_5min(cgm), b5, LO, HI))
    s = np.concatenate(sim_ds)
    rp, rlo, rhi = C.boot_ci(r, np.std, seed=3)
    sp, slo, shi = C.boot_ci(s, np.std, seed=4)
    ratio = rp / max(sp, 1e-9)
    verdict = "FAIL" if ratio > 1.4 else "PASS"
    return dict(name="Outcome unpredictability (BG 180-240, +30 min)", category="efficacy",
                real=rp, real_ci=(rlo, rhi), sim=sp, sim_ci=(slo, shi),
                metric=f"outcome SD real {rp:.0f} vs sim {sp:.0f} mg/dL  (x{ratio:.1f})",
                verdict=verdict,
                note="Real next-30-min outcome from a stuck-high band is far more spread "
                     "than the sim's. See fidelity_test.py Probe B: sim glucodynamic "
                     "variance across identical repeats is exactly 0.")


# ---- S5: non-stationarity / insulin-sensitivity drift ----------------------
def s5_drift(real, sim):
    """Real weekly-median insulin sensitivity drifts over the year; the sim's patient
    parameters are fixed, so its sensitivity drift is structurally zero."""
    drifts = []
    for u in real:
        with C.conn() as c, c.cursor() as cur:
            cur.execute(
                "SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY variable_sens) "
                "FROM boost_decisions WHERE user_id=%s AND variable_sens IS NOT NULL "
                "GROUP BY date_trunc('week', ts_utc) HAVING count(*) > 200", (u,))
            wk = np.array([r[0] for r in cur.fetchall()], float)
        if len(wk) >= 6:
            drifts.append(100 * wk.std() / wk.mean())   # CV of weekly-median sensitivity
    rp, rlo, rhi = C.boot_ci(drifts, np.median, seed=5)
    return dict(name="Insulin-sensitivity drift (weekly, %CV)", category="non-stationarity",
                real=rp, real_ci=(rlo, rhi), sim=0.0, sim_ci=(0.0, 0.0),
                metric=f"weekly-sensitivity CV real {rp:.0f}% vs sim 0% (fixed params)",
                verdict="STRUCTURAL",
                note="The virtual patient's parameters do not change over time; real "
                     "insulin sensitivity drifts week to week. The sim is stationary.")


# ---- S6: exercise counterweight (structural, from Probe A) ------------------
def s6_exercise(real, sim):
    return dict(name="Post-meal-exercise counterweight", category="exercise",
                real="crash rate falls with IOB (32/20/17% by tertile)", real_ci=None,
                sim="not representable", sim_ci=None,
                metric="model input is (CHO, insulin); no exercise term in the ODE",
                verdict="STRUCTURAL",
                note="See fidelity_test.py Probe A and the mechanism report. The "
                     "insulin-independent exercise drain has no input path in the model.")


SIGNATURES = [s1_cv, s2_delta_tails, s3_acf, s4_outcome_sd, s5_drift, s6_exercise]

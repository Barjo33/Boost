#!/usr/bin/env python3
"""How much can a 15-day side-by-side study actually resolve?

Before running a study it is worth knowing which of its endpoints it can settle. We take the
existing records, draw 15-day windows from them, and measure how wide the confidence interval
on each endpoint becomes at that duration. Comparing against the interval obtained from the
full record shows what is lost.

Endpoints fall into two classes with very different appetites for data. Signal endpoints such
as the variogram consume samples, of which 15 days supplies plenty. Event endpoints such as
meal onset consume events, of which 15 days supplies few.
"""
import sys, os, numpy as np
sys.path.insert(0, "/Users/timstreet/StudioProjects/Boost-AAPS-core/backtesting/scripts/2026-07-meal-anticipation")
sys.path.insert(0, "/Users/timstreet/StudioProjects/Boost-AAPS-core/backtesting/scripts/2026-07-cgm-cadence")
import anticip_lib as A
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold
from sklearn.metrics import roc_auc_score
from sklearn.preprocessing import StandardScaler

WINDOW_DAYS = 15
N_WINDOWS = 12
RNG = np.random.default_rng(20260731)

def windows(day, n=N_WINDOWS, span=WINDOW_DAYS):
    du = np.array(sorted(set(day)))
    out = []
    if len(du) < span + 2: return out
    starts = np.linspace(0, len(du)-span-1, n).astype(int)
    for s in starts:
        out.append(set(du[s:s+span].tolist()))
    return out

print(f"00. POWER OF A {WINDOW_DAYS}-DAY SIDE-BY-SIDE STUDY\n")
report = {}
for u in ["tim", "I"]:
    d = A.load_user(u)
    ts, bg, day, tod = d["ts"], d["bg"], d["day"], d["tod"]
    nom = A.nominal_interval(ts); nom = 5.0 if nom > 3.0 else nom
    n = len(ts)
    print(f"  {u} (nominal {nom:.0f} min, {len(set(day))} days, {n:,} readings)")

    # ---- signal endpoint: variogram at 30 min
    def vario_ci(sel_days):
        m = np.isin(day, list(sel_days))
        t2, b2, d2 = ts[m], bg[m], day[m]
        j = np.searchsorted(t2, t2 + 30*60_000)
        ok = j < len(t2); i_ = np.nonzero(ok)[0]; j_ = j[ok]
        keep = np.abs((t2[j_]-t2[i_])/60_000.0 - 30) <= (0.6 if nom < 2 else 1.2)
        sq = (b2[j_[keep]]-b2[i_[keep]])**2; dy = d2[i_[keep]]
        if len(sq) < 200: return None
        pt = float(sq.mean())
        lo, hi = A.day_bootstrap(lambda s: sq[s].mean(), dy, 300)
        return pt, (hi-lo)/pt
    full = vario_ci(set(day))
    ws = [vario_ci(w) for w in windows(day)]
    ws = [x for x in ws if x]
    if full and ws:
        print(f"    variogram D(30 min): full record CI width {100*full[1]:.1f}% of the estimate; "
              f"{WINDOW_DAYS}-day windows median {100*np.median([x[1] for x in ws]):.1f}%")
        report.setdefault(u, {})["vario30_ci_pct_full"] = 100*full[1]
        report[u]["vario30_ci_pct_15d"] = float(100*np.median([x[1] for x in ws]))

    # ---- event endpoint: meal onset AUC
    G = A.glucose_features(ts, bg, nom)
    tcyc = np.column_stack([np.sin(2*np.pi*tod/24), np.cos(2*np.pi*tod/24),
                            np.sin(4*np.pi*tod/24), np.cos(4*np.pi*tod/24)])
    X = np.column_stack([G, tcyc])
    sl15 = A.causal_slope(ts, bg, 15, nom)
    eps = A.climb_episodes(ts, bg, nom)
    onset_ts = np.array([ts[a] for a, _ in eps], np.int64)
    lab = np.zeros(n)
    for t0 in onset_ts:
        lo = np.searchsorted(ts, t0-15*60_000, side="left"); hi = np.searchsorted(ts, t0, side="right")
        lab[lo:hi] = 1.0
    quiet = np.isfinite(sl15) & (np.abs(sl15) < 2.0)
    def onset_ci(sel_days):
        m = np.isfinite(X).all(1) & quiet & np.isin(day, list(sel_days))
        y = lab[m]; g = day[m]
        if m.sum() < 800 or y.sum() < 25 or len(np.unique(y)) < 2: return None
        p = np.full(len(y), np.nan)
        ns = min(5, len(set(g)))
        for tr, te in GroupKFold(n_splits=ns).split(X[m], y, groups=g):
            if len(np.unique(y[tr])) < 2: continue
            sc = StandardScaler().fit(X[m][tr])
            p[te] = LogisticRegression(max_iter=2000).fit(sc.transform(X[m][tr]), y[tr]) \
                    .predict_proba(sc.transform(X[m][te]))[:, 1]
        ok = np.isfinite(p)
        if ok.sum() < 300 or len(np.unique(y[ok])) < 2: return None
        f = lambda s: float(roc_auc_score(y[s], p[s])) if len(np.unique(y[s])) > 1 else np.nan
        pt = f(np.nonzero(ok)[0]); lo, hi = A.day_bootstrap(f, g[ok], 300)
        return pt, hi-lo, int(y.sum())
    fullo = onset_ci(set(day))
    wso = [onset_ci(w) for w in windows(day)]
    wso = [x for x in wso if x]
    n_climb_15 = len(eps)*WINDOW_DAYS/max(len(set(day)), 1)
    if fullo and wso:
        w15 = float(np.median([x[1] for x in wso]))
        print(f"    meal onset AUC:      full record CI width {fullo[1]:.3f}; "
              f"{WINDOW_DAYS}-day windows median {w15:.3f}")
        print(f"    expected climbs in {WINDOW_DAYS} days: {n_climb_15:.0f}")
        print(f"    smallest AUC difference two such studies could separate: "
              f"about {w15:.2f}")
        report.setdefault(u, {}).update(onset_ci_full=fullo[1], onset_ci_15d=w15,
                                        climbs_15d=float(n_climb_15))
    print()
print("  Reading: the variogram is a sample-hungry endpoint and is well determined in 15 days.")
print("  Meal onset is an event-hungry endpoint and is not.")
A.RESULTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
A.save("00_power_check.json", report)

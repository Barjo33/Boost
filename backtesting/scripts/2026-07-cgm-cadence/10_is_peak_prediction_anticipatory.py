#!/usr/bin/env python3
"""Is the peak-prediction result of script 07 anticipation, or merely detection?

Script 07 reported AUC near 0.90 for "will this climb peak within 10 minutes". That is a
suspiciously strong result for a task earlier work found to be hard, and there is an obvious
way for it to be trivial: as a climb approaches its peak the rate of change flattens, and
flattening is directly observable. A model reading current slope would then score well without
anticipating anything.

Three checks:

  1. Does a slope-only model match the full model? If so the extra features add nothing and the
     task is being solved by reading the current rate.
  2. What is the current rate at the true positives? If glucose has already stopped rising, the
     model is reporting the present rather than the future.
  3. Does skill survive if points where the rise has already stalled are excluded? This is the
     honest version of the question: while glucose is still climbing properly, can the peak be
     seen coming?
"""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cadence_lib as L
from sklearn.metrics import roc_auc_score

RISE_MGDL, CLIMB_WINDOW, TROUGH_LOOKBACK, H = 40.0, 90, 20, 10

def climbs(ts, bg, nominal):
    n = len(ts); k_w = max(int(round(CLIMB_WINDOW/nominal)), 2)
    k_b = max(int(round(TROUGH_LOOKBACK/nominal)), 1)
    eps, i = [], k_b
    while i < n-2:
        j = min(i+k_w, n-1)
        if (ts[j]-ts[i])/60_000.0 > CLIMB_WINDOW*1.4: i += 1; continue
        seg = bg[i:j+1]
        if seg.max()-bg[i] < RISE_MGDL: i += 1; continue
        if bg[i] > bg[max(i-k_b,0):i+1].min()+5.0: i += 1; continue
        pk = i+int(np.argmax(seg)); eps.append((i, pk)); i = pk+1
    return eps

print("10. IS THE PEAK RESULT ANTICIPATION OR DETECTION?\n")
E = L.load_eras(); out = {}
for k, e in E.items():
    ts, bg, day, nom = e["ts"], e["bg"], e["day"], e["nominal"]
    n = len(ts)
    X, _ = L.build_features(ts, bg, nom)
    sl15 = L.causal_slope(ts, bg, 15, nom)
    eps = climbs(ts, bg, nom)
    rising = np.zeros(n, bool); lab = np.full(n, np.nan)
    for a, pk in eps:
        rising[a:pk] = True
        lab[a:pk] = 0.0
        lo = np.searchsorted(ts, ts[pk]-H*60_000, side="left")
        lab[max(lo, a):pk] = 1.0
    base_m = np.isfinite(X).all(1) & np.isfinite(lab) & rising & np.isfinite(sl15)
    res = {}
    def score(mask, feats, tag):
        y = lab[mask]; g = day[mask]
        if len(np.unique(y)) < 2 or y.sum() < 30: return None
        p = L.cv_classify(feats[mask], y, g)
        ok = np.isfinite(p); idx = np.nonzero(ok)[0]
        f = lambda s: float(roc_auc_score(y[s], p[s])) if len(np.unique(y[s])) > 1 else np.nan
        auc = f(idx); lo_, hi_ = L.day_bootstrap(f, g[ok], 300)
        return dict(tag=tag, n=int(len(y)), base=float(y.mean()), auc=auc, lo=lo_, hi=hi_)
    slope_only = np.column_stack([sl15])
    r_full = score(base_m, X, "full feature set")
    r_slope = score(base_m, slope_only, "15-minute slope only")
    print(f"  {e['label']}")
    for r in (r_full, r_slope):
        if r: print(f"    {r['tag']:<26s} n={r['n']:6,d} base {100*r['base']:5.2f}%  "
                    f"AUC {L.ci_str(r['auc'], r['lo'], r['hi'], 4)}")
    # 2. what is the rate at the positives?
    pos = base_m & (lab == 1.0); neg = base_m & (lab == 0.0)
    sp, sn = sl15[pos], sl15[neg]
    print(f"    15-min slope at positives : median {np.median(sp):5.2f} mg/dL per 5 min "
          f"(p25 {np.percentile(sp,25):.2f}, p75 {np.percentile(sp,75):.2f})")
    print(f"    15-min slope at negatives : median {np.median(sn):5.2f} "
          f"(p25 {np.percentile(sn,25):.2f}, p75 {np.percentile(sn,75):.2f})")
    print(f"    positives already flat or falling (slope <= 0): {100*np.mean(sp <= 0):.1f}%")
    # 3. restrict to points still climbing properly
    still = base_m & (sl15 >= 2.0)
    r_still = score(still, X, "full set, slope >= 2 only")
    if r_still:
        print(f"    {r_still['tag']:<26s} n={r_still['n']:6,d} base {100*r_still['base']:5.2f}%  "
              f"AUC {L.ci_str(r_still['auc'], r_still['lo'], r_still['hi'], 4)}")
    out[k] = dict(label=e["label"], full=r_full, slope_only=r_slope, still_climbing=r_still,
                  pos_slope_median=float(np.median(sp)), neg_slope_median=float(np.median(sn)),
                  pos_pct_flat_or_falling=float(100*np.mean(sp <= 0)))
    print()
L.save("10_peak_anticipation_check.json", out)

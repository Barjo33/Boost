#!/usr/bin/env python3
"""Closed-loop comparison: the same glucose record, looped at one minute and at five.

Arm A runs the engine every minute on the one-minute record. Arm B runs it every five minutes
on every fifth sample of the SAME record. Both drive the real Kotlin engine through the
per-cycle server, so nothing about the controller is reimplemented.

The loop is closed. Each arm's doses lower its own glucose through an insulin action curve and
raise its own insulin on board, and both feed back into the next decision. That is the part
the open-loop replay could not do, and it is what allows the brakes to damp a runaway.

WHAT IS AND IS NOT CLAIMED. The counterfactual is defined BETWEEN THE ARMS. Each arm's
trajectory is the real trace less the effect of that arm's own doses, so the difference A minus
B is the quantity the linear model supports and is what is reported. The absolute level of
either arm is not a claim about what would have happened to the person, because the real trace
already contains whatever insulin was actually delivered.

EPISODIC BY CONSTRUCTION. A linear insulin perturbation has no counter-regulation in it, so a
deviation applied continuously accumulates without bound: run over days it drives the
counterfactual to implausible levels and the result is an artefact of the model rather than a
finding about cadence. The replay is therefore cut into independent episodes of one insulin
duration, each starting from the real trace with no carried deviation. That confines the
extrapolation to a horizon over which a linear response is defensible.

A validity check reports any episode whose counterfactual leaves a plausible glucose range;
those episodes are excluded rather than quietly averaged in.
"""
import sys, os, json, subprocess, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from insulin import Insulin

D = os.path.dirname(os.path.abspath(__file__))
ISF, TARGET, MAX_IOB = 137.0, 100.0, 7.0
SMB_MIN_GAP = float(os.environ.get("SMB_MIN_GAP", "3"))   # ApsMaxSmbFrequency ships at 3
JAVA = "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"

rows = open(os.path.join(D, "..", "2026-07-v6-cadence-replay", "replay_input.csv")).read().splitlines()[1:]
ts = np.array([int(float(r.split(",")[0])) for r in rows], np.int64)
bg_real = np.array([float(r.split(",")[1]) for r in rows])
iob_real = np.array([float(r.split(",")[2]) for r in rows])
tmin = (ts - ts[0])/60_000.0
n = len(ts)
print(f"record: {n:,} one-minute samples, {(ts[-1]-ts[0])/86_400_000:.1f} days, "
      f"BG mean {bg_real.mean():.1f}\n")
ins = Insulin()

class Arm:
    def __init__(self, name, stride):
        self.name, self.stride = name, stride
        self.p = subprocess.Popen([JAVA, "-jar", os.path.join(D, "engine_server.jar")],
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
        self.dose_t, self.dose_u = [], []
        self.bg = bg_real.copy()
        self.last_smb = -1e9
        self.log = []
        self.invalid = set()
    def effect(self, i):
        """mg/dl already removed, and units still on board, from this arm's own doses."""
        if not self.dose_t: return 0.0, 0.0
        dt = tmin[i] - np.array(self.dose_t)
        u = np.array(self.dose_u)
        return float(ISF*np.sum(u*ins.action_fraction(dt))), float(np.sum(u*ins.iob_fraction(dt)))
    def ask(self, payload):
        self.p.stdin.write(json.dumps(payload) + "\n"); self.p.stdin.flush()
        return json.loads(self.p.stdout.readline())
    def close(self):
        try: self.p.stdin.close(); self.p.wait(timeout=5)
        except Exception: self.p.kill()

def deltas(bg_arm, i, stride):
    """DeltaCalculator windows, on this arm's own counterfactual glucose at its own cadence."""
    last, short, lng = [], [], []
    j = i - stride
    while j >= 0:
        mago = tmin[i] - tmin[j]
        if mago > 42.5: break
        av = (bg_arm[i] - bg_arm[j])/mago*5.0
        if 2.5 <= mago <= 7.5: last.append(av)
        if 2.5 <= mago <= 17.5: short.append(av)
        if 17.5 <= mago <= 42.5: lng.append(av)
        j -= stride
    s = float(np.mean(short)) if short else 0.0
    d = float(np.mean(last)) if last else s
    return d, s, (float(np.mean(lng)) if lng else 0.0)

EPISODE_MIN = int(os.environ.get("EPISODE_MIN", "360"))     # one insulin duration
BG_FLOOR, BG_CEIL = 40.0, 400.0

arms = [Arm("1-min", 1), Arm("5-min", 5)]
episodes = []
start = 45
while start + EPISODE_MIN < n:
    episodes.append((start, min(start + EPISODE_MIN, n)))
    start += EPISODE_MIN
print(f"  {len(episodes)} independent episodes of {EPISODE_MIN} min\n")

for arm in arms:
    warm = 45
    for (e0, e1) in episodes:
        arm.dose_t, arm.dose_u = [], []      # no deviation carried across episodes
        arm.last_smb = -1e9
        arm.ask(dict(reset=True))
        for i in range(e0, e1):
            if (i - e0) % arm.stride: continue
            removed, extra_iob = arm.effect(i)
            arm.bg[i] = bg_real[i] - removed
            d, s, l = deltas(arm.bg, i, arm.stride)
            accl = 100.0*(d - s)/max(abs(s), 2.0)
            iob = iob_real[i] + extra_iob
            cum30 = s*6.0
            ev = arm.bg[i] + cum30 - iob*ISF
            lo = max(0, i - 45)
            req = max(0.0, (ev - TARGET)/ISF)
            r = arm.ask(dict(bg=arm.bg[i], delta=d, shortAvgDelta=s, deltaAccl=accl,
                         eventualBg=ev, targetBg=TARGET,
                             maxDelta=float(arm.bg[lo:i+1].max() - arm.bg[i]),
                             minGuardBg=float(arm.bg[lo:i+1].min()),
                             recentLowBg=float(arm.bg[lo:i+1].min()),
                             deltaHistory=f"{l};{s};{d}", iob=iob, maxIob=MAX_IOB,
                             baseInsulinReq=req, cumulativeRise30min=cum30,
                             hour=int((ts[i]//3600000) % 24), nowMs=int(ts[i])))
            dose = float(r["finalDose"])
            if dose > 0 and (tmin[i] - arm.last_smb) >= SMB_MIN_GAP:
                arm.dose_t.append(tmin[i]); arm.dose_u.append(dose); arm.last_smb = tmin[i]
            else:
                dose = 0.0
            arm.log.append((tmin[i], arm.bg[i], iob, dose, r["state"], e0))
            if not (BG_FLOOR <= arm.bg[i] <= BG_CEIL): arm.invalid.add(e0)
    arm.close()
    # dose_t/dose_u are cleared per episode, so count from the log rather than from them
    nz = [r for r in arm.log if r[3] > 0]
    print(f"  {arm.name}: {len(arm.log):,} cycles across all episodes, "
          f"{len(nz)} microboluses, {sum(r[3] for r in nz):.2f} U")


# ---------------------------------------------------------------- reporting
A, B = arms
bad = A.invalid | B.invalid
good = [e0 for (e0, _) in episodes if e0 not in bad]
print(f"\n  {len(good)} of {len(episodes)} episodes stayed inside a plausible glucose range; "
      f"{len(bad)} excluded")
hours = len(good)*EPISODE_MIN/60.0
res = dict(smb_min_gap=SMB_MIN_GAP, episode_min=EPISODE_MIN,
           episodes_total=len(episodes), episodes_used=len(good))
if not good:
    print("  nothing valid to report"); json.dump(res, open(os.path.join(D,"closed_loop.json"),"w"), indent=1); sys.exit(0)

def arm_rows(arm):
    return [r for r in arm.log if r[5] in set(good)]
def doses(arm):
    return [r[3] for r in arm_rows(arm) if r[3] > 0]
la, lb = arm_rows(A), arm_rows(B)
da, db = doses(A), doses(B)
print(f"\n  {'measure':<40s} {'1-min':>10s} {'5-min':>10s} {'difference':>12s}")
def row(k, va, vb, f="{:.3f}"):
    res[k] = dict(one=float(va), five=float(vb), diff=float(va-vb))
    print(f"  {k:<40s} {f.format(va):>10s} {f.format(vb):>10s} {f.format(va-vb):>12s}")
row("insulin delivered, U per 24 h", sum(da)/hours*24, sum(db)/hours*24)
row("microboluses per 24 h", len(da)/hours*24, len(db)/hours*24, "{:.1f}")
row("mean microbolus, U", np.mean(da) if da else 0.0, np.mean(db) if db else 0.0, "{:.4f}")
row("cycles per 24 h", len(la)/hours*24, len(lb)/hours*24, "{:.0f}")
row("cycles that dosed, %", 100*len(da)/max(len(la),1), 100*len(db)/max(len(lb),1), "{:.2f}")
row("mean counterfactual BG, mg/dl", np.mean([r[1] for r in la]), np.mean([r[1] for r in lb]), "{:.1f}")
row("mean IOB, U", np.mean([r[2] for r in la]), np.mean([r[2] for r in lb]), "{:.3f}")

ta = {r[0]: r[1] for r in la}; tb = {r[0]: r[1] for r in lb}
common = sorted(set(ta) & set(tb))
if common:
    gap = np.array([ta[t] - tb[t] for t in common])
    res["shared"] = dict(n=len(common), mean=float(gap.mean()),
                         p95=float(np.percentile(np.abs(gap), 95)), mx=float(np.abs(gap).max()))
    print(f"\n  At the {len(common):,} instants both arms evaluated, the counterfactual glucose")
    print(f"  differs by {gap.mean():+.2f} mg/dl on average, 95th percentile of the absolute gap "
          f"{np.percentile(np.abs(gap),95):.1f}, largest {np.abs(gap).max():.1f}")

per_ep = []
for e0 in good:
    ua = sum(r[3] for r in A.log if r[5] == e0)
    ub = sum(r[3] for r in B.log if r[5] == e0)
    per_ep.append((ua, ub))
pa = np.array([x[0] for x in per_ep]); pb = np.array([x[1] for x in per_ep])
res["per_episode"] = dict(one_mean=float(pa.mean()), five_mean=float(pb.mean()),
                          one_gt_five=int((pa > pb).sum()), equal=int((pa == pb).sum()),
                          five_gt_one=int((pb > pa).sum()), n=len(per_ep))
print(f"\n  Per episode, the one-minute arm gave more insulin in {(pa>pb).sum()} of {len(per_ep)}, "
      f"the same in {(pa==pb).sum()}, less in {(pb>pa).sum()}")
boot = np.array([np.mean(np.random.default_rng(k).choice(pa-pb, len(pa))) for k in range(2000)])
res["diff_ci"] = [float(np.percentile(boot, 2.5)), float(np.percentile(boot, 97.5))]
print(f"  mean difference per episode {np.mean(pa-pb):+.3f} U "
      f"[{np.percentile(boot,2.5):+.3f}, {np.percentile(boot,97.5):+.3f}]")
tot = sum(db)
pert = abs(sum(da)-tot)/max(tot, 1e-9)
res["perturbation_pct"] = float(100*pert)
print(f"\n  Perturbation: the arms differ by {100*pert:.1f}% of the slower arm's total insulin.")
print("  A linear insulin response is defensible over one episode; read direction over magnitude.")
json.dump(res, open(os.path.join(D, "closed_loop.json"), "w"), indent=1)

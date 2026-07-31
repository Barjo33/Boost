#!/usr/bin/env python3
"""Fidelity gate for the cadence question.

A closed-loop cadence study is only worth running if the simulated sensor produces a signal
with realistic roughness. If the simulator's glucose is smoother than reality, one-minute
sampling will see less structure in the simulation than it would in life, and any conclusion
about cadence would be an artefact of the simulator.

The test compares the variogram of the simulated feed against the real record measured
earlier: exponent about 1.35 over 5 to 20 minutes, and about 1.29 over 20 to 60.
"""
import sys, os, numpy as np, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sensor import Sensor, variogram, loglog_slope
import warnings; warnings.filterwarnings("ignore")
from simglucose.simulation.env import T1DSimEnv
from simglucose.patient.t1dpatient import T1DPatient
from simglucose.sensor.cgm import CGMSensor
from simglucose.actuator.pump import InsulinPump
from simglucose.simulation.scenario import CustomScenario
from datetime import datetime, timedelta

REAL = dict(slope_5_20=1.35, slope_20_60=1.29, D1=4.44, D5=47.5, D30=556.3)
DAYS = 5
PATIENT = "adult#001"

start = datetime(2026, 1, 1, 0, 0, 0)
meals = []
for d in range(DAYS):
    for h, g in ((7, 45), (12, 60), (19, 70)):
        meals.append((d*24 + h, g))
scen = CustomScenario(start_time=start, scenario=meals)
patient = T1DPatient.withName(PATIENT)
pump = InsulinPump.withName("Insulet")
env = T1DSimEnv(patient, CGMSensor.withName("Dexcom", seed=1), pump, scen)
env.reset()

basal = patient._params.u2ss * patient._params.BW / 6000.0
true_bg, tmin = [], []
from simglucose.controller.base import Action
for k in range(DAYS*24*60):
    obs, _, _, info = env.step(Action(basal=basal, bolus=0.0))
    true_bg.append(float(env.patient.observation.Gsub))
    tmin.append(float(k))
true_bg = np.array(true_bg); tmin = np.array(tmin)
print(f"simulated truth: {len(true_bg):,} minutes, mean {true_bg.mean():.1f} mg/dl, "
      f"SD {true_bg.std():.1f}, range {true_bg.min():.0f} to {true_bg.max():.0f}")

LAGS = [1,2,3,4,5,10,15,20,30,45,60,90,120]
out = {"real_reference": REAL, "arms": {}}
print(f"\n  {'configuration':<38s} {'D(1)':>7s} {'D(5)':>8s} {'D(30)':>9s} "
      f"{'slope 5-20':>11s} {'slope 20-60':>12s}")
for label, kw in (
    ("1-min, no added noise",       dict(interval_min=1, noise_sd=0.0)),
    ("1-min, calibrated noise",     dict(interval_min=1, noise_sd=1.0)),
    ("5-min, calibrated noise",     dict(interval_min=5, noise_sd=1.0)),
):
    s = Sensor(seed=3, **kw)
    ts, vals = [], []
    for k, g in enumerate(true_bg):
        r = s.step(g)
        if r is not None: ts.append(tmin[k]); vals.append(r)
    vg = variogram(ts, vals, [L for L in LAGS if L >= kw["interval_min"]])
    s520 = loglog_slope(vg, 5, 20); s2060 = loglog_slope(vg, 20, 60)
    out["arms"][label] = dict(vario={str(k): v for k, v in vg.items()},
                              slope_5_20=s520, slope_20_60=s2060, n=len(vals))
    print(f"  {label:<38s} {vg.get(1, float('nan')):7.2f} {vg.get(5, float('nan')):8.2f} "
          f"{vg.get(30, float('nan')):9.1f} "
          f"{(s520 if s520 else float('nan')):11.2f} {(s2060 if s2060 else float('nan')):12.2f}")
print(f"  {'REAL RECORD (user I, 1-min)':<38s} {REAL['D1']:7.2f} {REAL['D5']:8.2f} "
      f"{REAL['D30']:9.1f} {REAL['slope_5_20']:11.2f} {REAL['slope_20_60']:12.2f}")

a = out["arms"]["1-min, calibrated noise"]
verdict = []
if a["slope_5_20"] and abs(a["slope_5_20"] - REAL["slope_5_20"]) > 0.25:
    verdict.append(f"roughness over 5-20 min is {a['slope_5_20']:.2f} against {REAL['slope_5_20']:.2f} in reality")
d30 = a["vario"].get("30")
if d30 and (d30 < REAL["D30"]/3 or d30 > REAL["D30"]*3):
    verdict.append(f"D(30) is {d30:.0f} against {REAL['D30']:.0f} in reality")
out["verdict"] = verdict or ["within tolerance on roughness and scale"]
print("\n  Gate:")
for v in out["verdict"]: print(f"    {v}")
json.dump(out, open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                 "sensor_fidelity.json"), "w"), indent=1)

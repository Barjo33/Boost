#!/usr/bin/env python3
"""Generate the report from the JSON written by 01-05. No figure is typed by hand.

House style enforced by 07_style_check.py: no em-dashes, no bold, plain British prose.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cadence_lib as L

P  = L.read("01_profile.json")
V  = L.read("02_variogram.json")
F  = L.read("03_forecast.json")
Ev = L.read("04_events.json")
Dl = L.read("05_reporting_delay.json")
rs = V["ratio_summary"]; c = P["comparability"]
out = []; w = out.append

w("# Is one-minute CGM data more useful than five-minute data?")
w("")
w("*Generated from `backtesting/scripts/2026-07-cgm-cadence/`. Every figure is read from the")
w("JSON written by scripts 01 to 05.*")
w("")
w("## The short answer")
w("")
w("No, for accuracy. The two records carry the same information about glucose, and every")
w("accuracy measure tested comes out the same at both cadences.")
w("")
w(f"Yes, by about {Dl['mean_difference']:.1f} minutes, for timing. A five-minute sensor reports a")
w("threshold crossing later than a one-minute sensor does, by roughly the amount the sample")
w("spacing implies. That is a scheduling difference and it requires no additional information.")
w("")
w("## 1. What was compared")
w("")
w(f"One person wore a five-minute sensor from {P['e5']['start']} to {P['e5']['end']}, then a")
w(f"one-minute sensor from {P['e1']['start']} to {P['e1']['end']}. The two records are compared")
w("as they were recorded. Nothing is decimated, interpolated or simulated.")
w("")
w("This matters because the usual way to ask this question is to take a one-minute record and")
w("discard four samples in five. That measures what a consumer loses by reading a fast sensor")
w("slowly. It does not measure how a fast sensor and a slow sensor differ, because a slow sensor")
w("filters internally before it reports.")
w("")
w("| | Five-minute era | One-minute era |")
w("|---|---|---|")
rows = [("Days with data", "n_days", "{:d}"), ("Readings", "n", "{:,}"),
        ("Median gap (min)", "median_gap", "{:.2f}"), ("Samples on cadence", "pct_on_cadence", "{:.1f}%"),
        ("Coverage of the period", "coverage_pct", "{:.1f}%"), ("Mean glucose (mg/dL)", "mean", "{:.1f}"),
        ("SD (mg/dL)", "sd", "{:.1f}"), ("CV", "cv", "{:.1f}%"),
        ("Time in range 70 to 180", "tir", "{:.1f}%"), ("Time below 70", "tbr70", "{:.2f}%"),
        ("Time below 54", "tbr54", "{:.2f}%"), ("Time above 180", "tar180", "{:.2f}%"),
        ("Time above 250", "tar250", "{:.2f}%")]
for lab, key, fmt in rows:
    w(f"| {lab} | {fmt.format(P['e5'][key])} | {fmt.format(P['e1'][key])} |")
w("")
w("The two periods are not matched. Control was worse during the later one: the squared ratio of")
w(f"coefficients of variation is {c['cv_ratio_squared']:.3f}, with {c['tbr70_ratio']:.2f} times as much")
w(f"time below 70 and {c['tar180_ratio']:.2f} times as much above 180.")
w("")
w("Glycaemic variability is a property of the person and the period rather than of the sensor, so")
w("it cannot be allowed to decide the comparison. Every measure below is either scale-free or")
w("divided by that era's own base rate. Where a measure is not, the point is not relied upon.")
w("")
w("## 2. Method")
w("")
w("The main tool is the variogram, D(tau) = E[(x(t+tau) - x(t))^2], which is the mean squared")
w("change over a lag of tau minutes.")
w("")
w("It suits this question for two reasons. It is expressed in time rather than in samples, so a")
w("five-minute record and a one-minute record can be placed on the same axis without resampling")
w("either. It also separates noise from signal by construction: if a sensor adds independent")
w("measurement noise of variance s^2 then every difference contains two independent noise draws,")
w("so D is raised by 2s^2 at every lag, including the shortest. Real signal structure vanishes as")
w("tau approaches zero, because glucose is continuous. A noise floor therefore appears as a")
w("flattening of D at small lag, and its height gives the noise variance directly.")
w("")
w("The log-log slope of D describes the character of the record independently of how large its")
w("excursions were. A slope of 2 indicates a smooth differentiable signal and a slope of 0")
w("indicates white noise.")
w("")
w("Prediction is modelled at each era's own native cadence and validated out of sample with")
w("GroupKFold over whole days. Both cadences are given the same look-back in minutes; the faster")
w("record simply holds five times as many samples inside it. Intervals throughout are 95 per cent")
w("block bootstraps that resample whole days, which respects the autocorrelation of glucose.")
w("")
w("## 3. Do the records differ by anything other than volatility?")
w("")
w("If the periods differ only in how volatile they were, the variogram of one will be a constant")
w("multiple of the other at every lag. If the sensors differ, the ratio will bend at short lag,")
w("since that is the only place their behaviour can diverge.")
w("")
w("| Lag | Five-minute era D | One-minute era D | Ratio |")
w("|---|---|---|---|")
for r in V["ratio"]:
    a = V["vario"]["e5"][str(r["lag"])]; b = V["vario"]["e1"][str(r["lag"])]
    w(f"| {r['lag']} min | {a['D']:.1f} [{a['lo']:.1f}, {a['hi']:.1f}] | "
      f"{b['D']:.1f} [{b['lo']:.1f}, {b['hi']:.1f}] | {r['ratio']:.3f} |")
w("")
w(f"The ratio averages {rs['mean']:.3f} over lags from 5 to 120 minutes, a twenty-four-fold range,")
w(f"with a total spread of {rs['spread_pct']:.1f} per cent of its mean. It does not trend and it does")
w("not bend at the short end.")
w("")
w("The two records are therefore the same signal scaled by a single number. For reference, the")
w(f"squared ratio of coefficients of variation is {c['cv_ratio_squared']:.3f}, so most of the scale")
w("factor is accounted for by the change in control.")
w("")
w("## 4. Is either sensor noisier?")
w("")
w("| Lag | One-minute era D (mg/dL^2) | Share of the floor implied by a 3.19 mg/dL noise SD |")
w("|---|---|---|")
for lg in [1,2,3,4,5,10]:
    k = f"e1_D{lg}"
    if k not in V["noise"]: continue
    d = V["noise"][k]
    w(f"| {lg} min | {d['D']:.2f} [{d['lo']:.2f}, {d['hi']:.2f}] | {100*d['D']/V['noise']['lit_floor']:.0f}% |")
w("")
w(f"D falls smoothly to {V['vario']['e1']['1']['D']:.2f} mg/dL^2 at a one-minute lag and shows no")
w("sign of levelling off. Neither record has a noise floor to measure.")
w("")
w("The comparison worth making is with the published error models. Vettoretti and colleagues fit a")
w(f"measurement-noise standard deviation of 3.19 mg/dL to a factory-calibrated sensor, which would")
w(f"hold D at {V['noise']['lit_floor']:.1f} mg/dL^2 at every lag. The measured value at one minute is")
w(f"{V['noise']['pct_of_lit_floor_at_1min']:.0f} per cent of that. Treated as white noise it would")
w(f"correspond to a standard deviation of {V['noise']['implied_white_sd_at_1min']:.2f} mg/dL.")
w("")
w("The reading is that neither sensor reports raw transducer output. Both filter before the value")
w("leaves the device, and it is the filtering rather than the reporting interval that governs how")
w("clean the series looks. Section 3 already shows the point directly: there is no lag at which the")
w("faster record sits proportionally higher than the slower one.")
w("")
w("## 5. Does the faster sensor resolve anything below five minutes?")
w("")
w("| Record | Lag band | Log-log slope of D |")
w("|---|---|---|")
for k, lab in (("e5", "Five-minute era"), ("e1", "One-minute era")):
    for band, s in V["slopes"][k].items():
        if not isinstance(s, dict): continue
        w(f"| {lab} | {band.replace('-', ' to ')} min | {s['slope']:.2f} [{s['lo']:.2f}, {s['hi']:.2f}] |")
w("")
w("In the two bands the sensors share, the slopes agree to two decimal places and the intervals")
w("overlap. The records have the same roughness at every timescale both can see.")
w("")
sub = V["slopes"]["e1"]["1-5"]; above = V["slopes"]["e1"]["5-20"]
w(f"Below five minutes, where only the faster sensor reaches, the slope is {sub['slope']:.2f}")
w(f"[{sub['lo']:.2f}, {sub['hi']:.2f}]. That interval contains the {above['slope']:.2f} measured just")
w("above it, so the same power law continues from one minute to sixty with no break. The extra")
w("samples trace the curve more finely; they do not open a new regime.")
w("")
w("## 6. Does prediction improve?")
w("")
w("This is the only category of use where a faster cadence could plausibly be more accurate rather")
w("than merely earlier. Reading the current value, raising an alarm on it, and computing")
w("retrospective statistics all depend either on the newest sample or on an average over thousands")
w("of them.")
w("")
w("### 6.1 Forecasting a future glucose value")
w("")
w("Error is divided by the standard deviation of the target, so 1.0 means no better than")
w("predicting the mean and the difference in volatility between the periods cannot drive the")
w("comparison.")
w("")
w("| Horizon | Five-minute era | One-minute era | Intervals | Nominally better |")
w("|---|---|---|---|---|")
for H, cm in F["comparison"].items():
    a = F["e5"]["horizons"][H]; b = F["e1"]["horizons"][H]
    w(f"| +{H} min | {a['model']:.3f} [{a['model_lo']:.3f}, {a['model_hi']:.3f}] | "
      f"{b['model']:.3f} [{b['model_lo']:.3f}, {b['model_hi']:.3f}] | "
      f"{'overlap' if cm['overlap'] else 'separated'} | {cm['nominally_better']} |")
w("")
w("The intervals overlap at every horizon and the nominal winner changes from one horizon to the")
w("next, so there is no advantage to detect in either direction.")
w("")
w("### 6.2 Predicting lows and highs")
w("")
w("Base rates differ substantially between the periods, so lift is the figure to compare. Lift is")
w("the precision within the top decile of predicted risk, divided by that era's own base rate. AUC")
w("is shown alongside, with the caveat that it is sensitive to prevalence.")
w("")
for task in ["low <70", "low <54", "high >180", "high >250"]:
    w(f"#### {task.replace('<', 'below ').replace('>', 'above ')}")
    w("")
    w("| Horizon | Era | Base rate | AUC | Lift |")
    w("|---|---|---|---|---|")
    for H in ["15","20","30","45","60"]:
        row = Ev["tasks"][task].get(H)
        if not row: continue
        for k, lab in (("e5", "5-min"), ("e1", "1-min")):
            r = row.get(k)
            if not r: continue
            if r.get("too_rare"):
                w(f"| {H} min | {lab} | {100*r['base']:.2f}% | too rare to model | |")
            else:
                w(f"| {H} min | {lab} | {100*r['base']:.2f}% | "
                  f"{r['auc']:.4f} [{r['auc_lo']:.4f}, {r['auc_hi']:.4f}] | "
                  f"{r['lift']:.2f}x [{r['lift_lo']:.2f}, {r['lift_hi']:.2f}] |")
    w("")
w("### 6.3 The direction of the difference reverses")
w("")
w("If one-minute sampling carried more predictive information it would help whichever way glucose")
w("was moving. It does not.")
w("")
w("| Task | AUC gap, one-minute minus five-minute | Behaviour with horizon |")
w("|---|---|---|")
for task in ["low <70", "low <54", "high >180"]:
    gaps = []
    for H in ["15","20","30","45","60"]:
        r = Ev["tasks"][task].get(H, {})
        cmp_ = r.get("compare") if isinstance(r, dict) else None
        if cmp_: gaps.append(f"{H}m {cmp_['auc_gap']:+.4f}")
    tr = Ev["tasks"][task].get("auc_gap_trend", "")
    if gaps: w(f"| {task.replace('<', 'below ').replace('>', 'above ')} | {', '.join(gaps)} | {tr} |")
w("")
w("On lows the one-minute era scores higher. On highs above 180 it scores lower, and the deficit")
w("widens with horizon. A sampling interval cannot help in one direction and hinder in the other.")
w("")
w("A genuine cadence benefit would also be largest at the shortest horizon, where recent detail")
w("matters most, and would fade as the horizon lengthened. Neither task behaves that way. What")
w("these differences track is how difficult each period was to predict.")
w("")
w("## 7. What cadence does change")
w("")
w("A threshold is crossed at some instant between two reported samples. Locating that instant by")
w("interpolation and measuring the wait until the next sample the sensor actually reported gives")
w("the delay directly, from the real records.")
w("")
w("| Crossing | Five-minute era | One-minute era | Difference |")
w("|---|---|---|---|")
labels = {"70.0": "falling below 70", "54.0": "falling below 54",
          "180.0": "rising above 180", "250.0": "rising above 250"}
def cell(d):
    return "too few crossings" if d.get("too_few") else \
           f"{d['mean']:.2f} [{d['mean_lo']:.2f}, {d['mean_hi']:.2f}] min, n={d['n']}"
for thr, lab in labels.items():
    a = Dl["e5"]["thresholds"].get(thr); b = Dl["e1"]["thresholds"].get(thr)
    if not a or not b: continue
    diff = "" if (a.get("too_few") or b.get("too_few")) else f"+{a['mean']-b['mean']:.2f} min"
    w(f"| {lab} | {cell(a)} | {cell(b)} | {diff} |")
w("")
w(f"The mean difference is {Dl['mean_difference']:.2f} minutes, against an arithmetic expectation of")
w("2.00 minutes from the sample spacing alone. This is the whole of what the faster feed delivers,")
w("and it is a matter of scheduling rather than of information.")
w("")
w("Whether two minutes is worth having depends on what consumes it. An alarm can use it in full,")
w("as can a person who is able to act at once. It is small against the onset of rapid-acting")
w("insulin, which is on the order of fifteen minutes.")
w("")
w("## 8. Limitations")
w("")
w("The comparison rests on one person. It is observational and between eras, so the sensor")
w("hardware changed at the boundary and so did glycaemic control. The analysis is built to be")
w("robust to the latter, since every measure used here is scale-free, but a single subject cannot")
w("show that the result generalises across people or devices.")
w("")
w("The sensor makes and models are not recorded in the data available. The noise conclusion")
w("concerns the reported series rather than the raw transducer signal behind it, which the")
w("published error models address and which is not available here.")
w("")
w("Some tasks were too rare to model in one era or the other. They are marked as such rather than")
w("being forced.")
w("")
w("No outcome data is analysed. None is needed for the question asked, which is what the two")
w("records contain.")
w("")
w("## Reproducing")
w("")
w("```")
w("./run_all.sh")
w("")
w("01_profile.py          coverage, cadence stability, glycaemic distribution")
w("02_variogram.py        ratio across shared lags, noise floor, log-log slopes")
w("03_forecast.py         normalised forecast error by horizon")
w("04_events.py           event prediction, AUC and base-rate lift")
w("05_reporting_delay.py  delay from an interpolated crossing to the next reported sample")
w("06_report.py           regenerates this document from results/*.json")
w("07_style_check.py      house-style gate on the generated document")
w("```")
w("")
w("Provisional. One subject, observational between-era comparison.")

def rewrap(lines, width=92):
    """Even paragraph wrapping. Tables, headings, code and list items pass through."""
    import textwrap
    res, buf, in_code = [], [], False
    def flush():
        if buf:
            res.extend(textwrap.wrap(" ".join(buf), width=width,
                                     break_long_words=False, break_on_hyphens=False))
            buf.clear()
    for l in lines:
        if l.strip().startswith("```"):
            flush(); in_code = not in_code; res.append(l); continue
        if in_code or not l.strip() or l.strip().startswith(("|", "#", "-", "*")):
            flush(); res.append(l); continue
        buf.append(l.strip())
    flush()
    return res

dest = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                     "..", "..", "reports", "2026-07_cgm_cadence_report.md"))
with open(dest, "w") as f: f.write("\n".join(rewrap(out)) + "\n")
print(f"report written: {dest} ({len(out)} lines)")

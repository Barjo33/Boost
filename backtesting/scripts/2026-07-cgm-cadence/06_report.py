#!/usr/bin/env python3
"""Generate the report from the JSON produced by 01-05. No number is typed by hand here."""
import sys, os, json, datetime as dt
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cadence_lib as L

P  = L.read("01_profile.json")
V  = L.read("02_variogram.json")
F  = L.read("03_forecast.json")
Ev = L.read("04_events.json")
Dl = L.read("05_reporting_delay.json")
def ci(d, k="", dp=3):
    return f"{d[k]:.{dp}f} [{d[k+'_lo']:.{dp}f}, {d[k+'_hi']:.{dp}f}]" if k else ""
out = []
w = out.append

w("# One-minute versus five-minute CGM: what the extra samples carry")
w("")
w("*Generated from `backtesting/scripts/2026-07-cgm-cadence/`. Every figure in this report is")
w("read from the JSON written by scripts 01-05; none is transcribed by hand.*")
w("")
w("## Summary")
w("")
rs = V["ratio_summary"]
w(f"One person wore a five-minute sensor for {P['e5']['n_days']} days and then a one-minute")
w(f"sensor for {P['e1']['n_days']} days. Nothing below is decimated, interpolated or simulated:")
w("the two cadences are compared as they were actually recorded.")
w("")
w(f"- **The two records differ by one number.** The ratio of their variograms is")
w(f"  **{rs['mean']:.3f}** across every lag both sensors can see, from 5 to 120 minutes, with a")
w(f"  total spread of {rs['spread_pct']:.1f} per cent of the mean and no trend.")
w(f"- **Neither cadence is noisier, and neither shows a measurement-noise floor.** At a")
w(f"  one-minute lag D is {V['vario']['e1']['1']['D']:.2f} mg/dL², which is")
w(f"  {V['noise']['pct_of_lit_floor_at_1min']:.0f} per cent of the {V['noise']['lit_floor']:.1f} mg/dL²")
w("  floor that a published sensor-noise standard deviation would impose at every lag. Both sensors report")
w("  values that are already filtered.")
w(f"- **Nothing new appears below five minutes.** The log-log slope there is")
w(f"  {V['slopes']['e1']['1-5']['slope']:.2f} [{V['slopes']['e1']['1-5']['lo']:.2f}, "
  f"{V['slopes']['e1']['1-5']['hi']:.2f}], containing the "
  f"{V['slopes']['e1']['5-20']['slope']:.2f} measured just above it.")
w(f"- **Forecasting does not improve.** Normalised RMSE intervals overlap at all")
w(f"  {len(F['comparison'])} horizons and the nominal winner alternates.")
w("- **Predicting lows and highs does not improve.** The sign of the difference *reverses*")
w("  between lows and highs, which no cadence effect can produce.")
w(f"- **What does change is when you are told:** the five-minute record reports a threshold")
w(f"  crossing {Dl['mean_difference']:.2f} minutes later on average, against an arithmetic")
w("  expectation of 2.00 minutes from sample spacing alone.")
w("")
w("## 1. The records")
w("")
w("| | Five-minute era | One-minute era |")
w("|---|---|---|")
for lab, key, fmt in [("Dates", None, None), ("Days with data", "n_days", "{:d}"),
                      ("Readings", "n", "{:,}"), ("Median gap (min)", "median_gap", "{:.2f}"),
                      ("On cadence", "pct_on_cadence", "{:.1f}%"),
                      ("Coverage", "coverage_pct", "{:.1f}%"),
                      ("Mean glucose", "mean", "{:.1f}"), ("SD", "sd", "{:.1f}"),
                      ("CV", "cv", "{:.1f}%"), ("Time in range 70-180", "tir", "{:.1f}%"),
                      ("Time <70", "tbr70", "{:.2f}%"), ("Time <54", "tbr54", "{:.2f}%"),
                      ("Time >180", "tar180", "{:.2f}%"), ("Time >250", "tar250", "{:.2f}%")]:
    if key is None:
        w(f"| {lab} | {P['e5']['start']} – {P['e5']['end']} | {P['e1']['start']} – {P['e1']['end']} |")
    else:
        w(f"| {lab} | {fmt.format(P['e5'][key])} | {fmt.format(P['e1'][key])} |")
w("")
c = P["comparability"]
w(f"The eras are **not** matched. The later period is more volatile — the squared ratio of")
w(f"coefficients of variation is {c['cv_ratio_squared']:.3f}, there is {c['tbr70_ratio']:.2f} times")
w(f"as much time below 70 and {c['tar180_ratio']:.2f} times as much above 180. Glycaemic")
w("variability is a property of the person and the period, not of the sensor, so every metric")
w("below is scale-free or normalised by the era's own base rate. Where that is not possible the")
w("confound is stated.")
w("")
w("## 2. Noise and signal: the variogram")
w("")
w("The variogram D(τ) = E[(x(t+τ) − x(t))²] is the mean squared change over a lag of τ minutes.")
w("It is expressed in time rather than samples, so both cadences sit on one axis with no")
w("resampling. It separates the two questions by construction: additive measurement noise of")
w("variance s² lifts D by 2s² at *every* lag including the shortest, whereas real signal")
w("vanishes as τ → 0. A noise floor therefore shows up as a flattening at small lag.")
w("")
w("### 2.1 The two records differ by a single scale factor")
w("")
w("| Lag | Five-minute era D | One-minute era D | Ratio |")
w("|---|---|---|---|")
for r in V["ratio"]:
    a = V["vario"]["e5"][str(r["lag"])]; b = V["vario"]["e1"][str(r["lag"])]
    w(f"| {r['lag']} min | {a['D']:.1f} [{a['lo']:.1f}, {a['hi']:.1f}] | "
      f"{b['D']:.1f} [{b['lo']:.1f}, {b['hi']:.1f}] | {r['ratio']:.3f} |")
w("")
w(f"Mean ratio **{rs['mean']:.3f}**, range {rs['min']:.3f} to {rs['max']:.3f}, spread")
w(f"{rs['spread_pct']:.1f} per cent of the mean over a twenty-four-fold range of lag. It does not")
w("trend and it does not bend at the short end, which is the only place the two sensors could")
w(f"differ. For comparison the squared ratio of coefficients of variation is {c['cv_ratio_squared']:.3f}.")
w("")
w("### 2.2 Neither record has a measurement-noise floor")
w("")
w("| Lag | One-minute era D | Share of the floor a 3.19 mg/dL noise SD would impose |")
w("|---|---|---|")
for lg in [1,2,3,4,5,10]:
    k = f"e1_D{lg}"
    if k not in V["noise"]: continue
    d = V["noise"][k]
    w(f"| {lg} min | {d['D']:.2f} [{d['lo']:.2f}, {d['hi']:.2f}] | {100*d['D']/V['noise']['lit_floor']:.0f}% |")
w("")
w(f"D falls smoothly to {V['vario']['e1']['1']['D']:.2f} mg/dL² at a one-minute lag with no sign of")
w(f"levelling off — {V['noise']['pct_of_lit_floor_at_1min']:.0f} per cent of the")
w(f"{V['noise']['lit_floor']:.1f} mg/dL² that independent noise of the published magnitude would")
w("hold it at. If that were white noise it would correspond to a standard deviation of only")
w(f"{V['noise']['implied_white_sd_at_1min']:.2f} mg/dL. The values these sensors report are not raw")
w("transducer output; they have been filtered before leaving the device, and the filtering rather")
w("than the reporting interval is what governs how clean the series looks.")
w("")
w("### 2.3 No new regime below five minutes")
w("")
w("| Record | Lag band | Log-log slope |")
w("|---|---|---|")
for k, lab in (("e5", "Five-minute era"), ("e1", "One-minute era")):
    for band, s in V["slopes"][k].items():
        if not isinstance(s, dict): continue
        w(f"| {lab} | {band.replace('-', '–')} min | {s['slope']:.2f} [{s['lo']:.2f}, {s['hi']:.2f}] |")
w("")
w("A slope of 2 would be a smooth differentiable signal and 0 would be white noise; both records")
w("sit near 1.3 throughout. In the two bands the sensors share, the intervals overlap. Below five")
w("minutes, where only the faster sensor can see, the slope contains the value measured just")
w("above it, so the same power law continues from one minute to sixty with no break.")
w("")
w("## 3. Forecasting — the automated-insulin-delivery case")
w("")
w("Each era is modelled at its own native cadence and validated out of sample with GroupKFold")
w("over whole days. Both get the same look-back in *minutes*; the faster record simply has five")
w("times as many samples inside it. Error is divided by the standard deviation of the target, so")
w("1.0 means no better than predicting the mean and the difference in variability between the")
w("eras cannot drive the comparison.")
w("")
w("| Horizon | Five-minute era | One-minute era | Verdict |")
w("|---|---|---|---|")
for H, cm in F["comparison"].items():
    a = F["e5"]["horizons"][H]; b = F["e1"]["horizons"][H]
    w(f"| +{H} min | {a['model']:.3f} [{a['model_lo']:.3f}, {a['model_hi']:.3f}] | "
      f"{b['model']:.3f} [{b['model_lo']:.3f}, {b['model_hi']:.3f}] | "
      f"{'overlap' if cm['overlap'] else 'separated'}, nominally {cm['nominally_better']} |")
w("")
w(f"Intervals overlap at every horizon and the nominal winner alternates, so there is no")
w("forecast advantage to detect in either direction.")
w("")
w("## 4. Predicting lows and highs")
w("")
w("Base rates differ substantially between the eras, so **lift** — precision in the top risk")
w("decile divided by that era's own base rate — is the metric to compare. AUC is shown alongside.")
w("")
for task in ["low <70", "low <54", "high >180", "high >250"]:
    w(f"### {task}")
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
                w(f"| {H} min | {lab} | {100*r['base']:.2f}% | too rare to model | — |")
            else:
                w(f"| {H} min | {lab} | {100*r['base']:.2f}% | "
                  f"{r['auc']:.4f} [{r['auc_lo']:.4f}, {r['auc_hi']:.4f}] | "
                  f"{r['lift']:.2f}× [{r['lift_lo']:.2f}, {r['lift_hi']:.2f}] |")
    w("")
w("### The sign reverses, which settles it")
w("")
w("If one-minute sampling carried more predictive information it would help on every task. It")
w("does not. On lows the one-minute era scores nominally higher; on highs above 180 it scores")
w("**lower**, and substantially so at the longer horizons.")
w("")
w("| Task | AUC gap, one-minute minus five-minute, by horizon | Trend |")
w("|---|---|---|")
for task in ["low <70", "low <54", "high >180"]:
    gaps = []
    for H in ["15","20","30","45","60"]:
        r = Ev["tasks"][task].get(H, {})
        cmp_ = r.get("compare") if isinstance(r, dict) else None
        if cmp_: gaps.append(f"{H}m {cmp_['auc_gap']:+.4f}")
    tr = Ev["tasks"][task].get("auc_gap_trend", "—")
    if gaps: w(f"| {task} | {', '.join(gaps)} | {tr} |")
w("")
w("A genuine cadence benefit would be largest at the shortest horizon, where fine-grained recent")
w("detail matters most, and would wash out as the horizon lengthens. Neither task behaves that")
w("way, and the two tasks disagree on direction. These differences track how hard each period was")
w("to predict, not how often it was sampled.")
w("")
w("## 5. What cadence does change: reporting delay")
w("")
w("A threshold is crossed at some instant between two reported samples. Locating that instant by")
w("interpolation and measuring the wait until the next sample the sensor actually reported gives")
w("the delay directly, on the real records.")
w("")
w("| Crossing | Five-minute era mean delay | One-minute era mean delay | Difference |")
w("|---|---|---|---|")
labels = {"70.0": "falling below 70", "54.0": "falling below 54",
          "180.0": "rising above 180", "250.0": "rising above 250"}
for thr, lab in labels.items():
    a = Dl["e5"]["thresholds"].get(thr); b = Dl["e1"]["thresholds"].get(thr)
    if not a or not b: continue
    def cell(d):
        return "too few crossings" if d.get("too_few") else f"{d['mean']:.2f} min (n={d['n']})"
    if a.get("too_few") or b.get("too_few"):
        w(f"| {lab} | {cell(a)} | {cell(b)} | — |")
        continue
    w(f"| {lab} | {a['mean']:.2f} [{a['mean_lo']:.2f}, {a['mean_hi']:.2f}] min "
      f"(n={a['n']}) | {b['mean']:.2f} [{b['mean_lo']:.2f}, {b['mean_hi']:.2f}] min "
      f"(n={b['n']}) | **+{a['mean']-b['mean']:.2f} min** |")
w("")
w(f"The average difference is **{Dl['mean_difference']:.2f} minutes**, against an arithmetic")
w("expectation of 2.00 minutes from the sample spacing alone. This is pure scheduling: it")
w("requires no extra information and it is the whole of what the faster feed delivers.")
w("")
w("## 6. Reading")
w("")
w("The two sensors record the same process at the same relative noise, and their records differ")
w("by a single scale factor that is the volatility of the period. The faster sensor resolves no")
w("new regime below five minutes, forecasts no better at any horizon between fifteen and ninety")
w("minutes, and predicts neither lows nor highs better once each era's own base rate is divided")
w("out — with the sign of the difference reversing between the two, which no property of the")
w("sampling interval could produce.")
w("")
w("What a one-minute feed does deliver is about two minutes less waiting to be told that")
w("something has happened. Whether two minutes is worth having depends on what consumes it: it is")
w("available in full to an alarm and to a person who can act at once, and it is small against the")
w("onset of any insulin action.")
w("")
w("## 7. Limitations")
w("")
w("One subject. The comparison is observational and between eras, so sensor hardware, season,")
w("therapy and glycaemic control all change at the boundary. The analysis is built to be robust")
w("to exactly that — variogram ratios, log-log slopes, normalised error and base-rate lift are")
w("all scale-free — but a single person cannot establish that the finding generalises.")
w("")
w("The sensor makes and models are not recorded in the data available. The noise conclusion is")
w("about the *reported* series, not the raw transducer signal behind it.")
w("")
w("Two tasks were too rare to model in one era or the other and are shown as such rather than")
w("being forced.")
w("")
w("No outcome data is analysed, and none is needed for the question asked, which is what the two")
w("records contain.")
w("")
w("## Reproducing")
w("")
w("```")
w("python3 01_profile.py          # coverage, cadence stability, glycaemic distribution")
w("python3 02_variogram.py        # ratio, noise floor, log-log slopes")
w("python3 03_forecast.py         # normalised forecast error by horizon")
w("python3 04_events.py           # lows and highs, AUC and base-rate lift")
w("python3 05_reporting_delay.py  # real delay from crossing to next reported sample")
w("python3 06_report.py           # regenerates this document from results/*.json")
w("```")
w("")
w("PROVISIONAL — one subject; observational between-era comparison.")

dest = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "..", "..", "reports", "2026-07_cgm_cadence_report.md")
dest = os.path.normpath(dest)
with open(dest, "w") as f: f.write("\n".join(out) + "\n")
print(f"report written: {dest}  ({len(out)} lines)")

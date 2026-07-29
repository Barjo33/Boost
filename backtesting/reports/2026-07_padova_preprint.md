# The UVA/Padova simulator versus real-world data: the case for a new approach to closed-loop testing

*[Author name(s) and affiliation to be added. Prepared from an anonymised multi-cohort
analysis; all code and data-processing are open and reproducible.]*

## Abstract

**Background.** The UVA/Padova type 1 diabetes simulator has been accepted since 2008 as
a substitute for animal trials in the pre-clinical testing of automated insulin delivery
(AID). It is the de facto gate through which control algorithms pass on their way to
clinical evaluation. Its fidelity to real-world glucose data therefore has direct
consequences for which systems are judged safe, yet that fidelity has rarely been
measured against real-world distributions at scale.

**Methods.** We compared the community-standard open implementation of the simulator
(simglucose, the 2008 model, all thirty virtual personae across three age classes)
against four independent real-world AID datasets totalling approximately 192 users and,
for many, more than a year of five-minute continuous glucose data: a fully closed loop
(Boost), an iAPS/Trio cohort, the OpenAPS/oref0 cohort from the OpenAPS Data Commons, and
a pre-dynamic-ISF AndroidAPS cohort. Eleven glucose-dynamics statistics were computed
identically on both sides, aggregated per user then pooled with bootstrap confidence
intervals. For each statistic we asked not only whether the adult personae matched, but
whether any persona class reproduced it.

**Results.** The four real datasets, built by different communities and worn by different
people, converged closely on every statistic, defining a real-world envelope. The
simulator reproduced short-horizon autocorrelation for all persona classes, and reached
real-world glucose variability only through its child personae. Five of eleven statistics
were reproduced by no persona at any age: the fat tail of unannounced-meal glucose rises,
the speed and overshoot of hypoglycaemia recovery, sensor compression artefacts,
high-frequency sensor noise, and week-to-week insulin-sensitivity drift (zero in the
model by construction). Implementing the two S2013 refinements that could plausibly help,
time-varying insulin sensitivity and glucagon counter-regulation, closed only the
variability gap, overshot the diurnal amplitude, narrowed the hypoglycaemia-recovery gap
without closing it, and left the remaining structural gaps unchanged.

**Conclusions.** In-silico testing on the community-standard platform exercises a smooth,
announced-meal, stationary, clean-sensor regime and is close to blind to the unannounced
meals, variable insulin efficacy, sensor artefact and sensitivity drift that dominate
real-world safety. We argue this is not a reason to distrust simulation but a reason to
change how it is validated and used: an empirical fidelity benchmark that any simulator
should report against, and a move towards data-calibrated, hybrid and replay-based
simulation anchored in the large real-world corpora that now exist.

**Keywords:** type 1 diabetes, automated insulin delivery, artificial pancreas, in-silico
testing, UVA/Padova simulator, model validation, regulatory science.

## 1. Introduction

In 2008 the US Food and Drug Administration accepted the University of Virginia and
University of Padova type 1 diabetes simulator as a substitute for animal trials in the
pre-clinical testing of artificial-pancreas control algorithms [1]. The decision was
consequential. In the years since, essentially no animal experiments have been run to
design an AID controller, and the major commercial systems now in clinical use, among
them the Medtronic 670G and 780G, Tandem Control-IQ, Insulet Omnipod 5, CamAPS FX and
Diabeloop, were developed and screened with in-silico testing on this platform on their
way to patients [5].

The simulator therefore sits at a decision point in the field. A controller that looks
safe in silico earns the right to a clinical trial; a controller that looks dangerous in
silico may never be built. That leverage makes a specific, testable question important:
how faithfully does the simulator reproduce the glucose data of the people it stands in
for? The question is not whether the underlying model is a good description of metabolic
physiology, which is well established, but whether the particular statistics that decide
whether a controller is safe, the variability, the excursions, the behaviour of lows, the
sensor, come out looking like real-world data.

That question is rarely asked at scale, for an understandable reason: until recently there
was no large, multi-system corpus of real-world closed-loop data to ask it against. The
open-source AID movement has changed this. Communities running OpenAPS, AndroidAPS, Trio
and related systems have accumulated, and in the case of the OpenAPS Data Commons openly
shared, years of continuous glucose and pump data from real people living real lives on
closed loops [6, and OpenAPS Data Commons]. This makes it possible to hold the simulator
against not one real dataset but several independent ones at once, which turns out to be
the crux of a credible comparison.

We report such a comparison. Our contribution is threefold: a demonstration that
independent real-world AID datasets converge tightly and so define a meaningful target;
a systematic, per-persona measurement of where the community-standard simulator matches
that target and where it does not; and an argument, following directly from the pattern
of the failures, that the field should measure simulator fidelity explicitly and move
towards data-calibrated simulation.

## 2. The simulator and what "tested in silico" means

The UVA/Padova simulator is built on a validated model of glucose and insulin dynamics
whose meal subsystem derives from tracer studies of glucose turnover [7]. Its inputs are
carbohydrate and insulin; it outputs plasma and sensor glucose. The full commercial
distribution carries three hundred in-silico subjects across three age classes. The
freely distributed academic version, and the open-source simglucose package that
reimplements the 2008 model [6], carry the canonical thirty: ten adults, ten adolescents
and ten children.

This distinction matters for our scope. The version we test, simglucose, is the 2008
model, and it is the tool the open AID community actually develops and evaluates against,
in reinforcement-learning research and in the algorithm work of the OpenAPS, AndroidAPS
and Trio projects whose real-world data we compare it to. Its fidelity is therefore not
an academic question but a direct description of the world these algorithms are optimised
in before they reach people.

The simulator has been refined since 2008. The S2013 version [2] added intraday and
interday variability of insulin sensitivity based on clinical data, a model of the dawn
phenomenon, glucagon kinetics and secretion, and improved glucose kinetics in
hypoglycaemia; it was accepted by the FDA in 2013 and later extended from single-meal to
single-day scenarios [3] and validated against clinical-trial traces [4]. Separate
extensions have added physical-activity effects to the model family. We return in Section
6 to what these refinements do and do not close; for now the point is only that the
community-standard, freely available tool is the 2008 model, and that is what we measure.

## 3. Methods

The whole comparison rests on one principle: every statistic is computed the identical
way on real data and on the simulator, with the same definitions, thresholds, cadence and
aggregation. Nothing is applied to one side and not the other. The pipeline is open and
re-runnable.

### 3.1 Data

The real cohorts are anonymised AID users held in a local research database, each a
different system built by a different community:

- **Boost** (9 users), a fully closed loop with no meal announcement.
- **Trio** (29 users), the iAPS and Trio lineage.
- **OpenAPS** (110 users), the oref0 lineage from the OpenAPS Data Commons, several with
  multiple years of continuous data.
- **AndroidAPS classic** (44 users), AndroidAPS predating dynamic insulin sensitivity.

All provide continuous glucose at a five-minute cadence. A user is included only with at
least 500 CGM points. No trace is smoothed, trimmed or cleaned beyond dropping null
readings, so the real sensor noise and artefacts are preserved.

The simulator cohort is all thirty UVA/Padova personae run through simglucose for
twenty-one days each. Meals are randomised per day in time and size and announced to the
controller (a basal-bolus controller doses on the scenario carbohydrate using each
patient's own ratios), because the simulator has no working unannounced-meal controller.
Meal sizes are scaled by body weight (reference 70 kg, clipped to 0.5 to 1.15 times) so
that a child is not fed an adult's dinner. The simulator's sensor runs at a three-minute
cadence; each trace is resampled onto the same five-minute grid as the real data before
any statistic is computed, so the two sides are never compared at different cadences.

That meals are announced favours the simulator, since the announced case is the easier
one; this is deliberate, so that the comparison does not depend on giving the simulator an
unfair scenario.

### 3.2 Signatures

Eleven statistics were computed per user or per persona. Each is defined precisely so the
result can be judged without reading the source.

- **Glucose variability (CV%)**: 100 times SD over mean of the CGM.
- **Rise tail**: among consecutive samples 4 to 6 minutes apart, the percentage whose
  rise exceeds 10 mg/dL. A fat positive tail is the fingerprint of an unannounced-meal
  onset.
- **Autocorrelation at 30 and 60 minutes**: the Pearson correlation between each CGM value
  and the value 30 (or 60) minutes later, matched on actual timestamps within 90 seconds,
  a proxy for how fast the curve decorrelates.
- **Outcome SD at a stuck high**: for samples with CGM in 180 to 240 mg/dL, the SD of
  (CGM 30 minutes later minus now). Wide means the next half hour is unpredictable from a
  stuck high; narrow means deterministic. Requires at least 200 in-band samples.
- **Diurnal amplitude**: the peak minus trough of the hour-of-day mean profile,
  phase-invariant.
- **Hypo recovery**: for each crossing below 70 mg/dL, the minutes to return to 100 mg/dL,
  and the fraction of recoveries that then overshoot above 180 mg/dL within two hours.
- **Compression lows**: sharp reversing dips below 70 (a fall of more than 25 mg/dL from a
  pre-dip level of at least 85, recovering to within 15 mg/dL of that level inside 30
  minutes), the signature of a sensor artefact rather than a physiological low, scaled to
  events per 30 days.
- **Sensor jitter**: the SD of the second difference of the five-minute series, over
  contiguous five-minute triples only, a high-frequency measurement-noise measure.
- **ISF drift**: the algorithm's insulin-sensitivity value reduced to a weekly median,
  then the coefficient of variation of those weekly medians, over at least six weeks.

### 3.3 Aggregation and verdict

Each statistic is computed per user (real) or per persona (sim), then reported as the
median across users with a bootstrap 95% confidence interval (2000 resamples). This
per-user-then-pooled design ensures no single heavy user or unstable persona carries a
result. The four real cohorts define a real-world envelope for each statistic, the range
from the lowest to the highest of their four medians padded by 10% of that span. A persona
class matches a statistic if its own median falls inside the envelope. This is deliberately
lenient: a persona need only land anywhere within the spread of four independent real
datasets to count as a match.

## 4. Real-world data agrees with itself

The first result is the one that makes the rest interpretable. The four real cohorts,
built and worn independently, land in a tight band on almost every measure (Table 1,
Figure 1). Glucose variability sits between 30 and 34% for all four. The outcome spread 30
minutes after a stuck high runs from 27 to 33 mg/dL. Hypoglycaemia recovers to 100 mg/dL
in 50 to 59 minutes and overshoots above 180 mg/dL afterwards about a quarter of the time.
High-frequency sensor jitter runs 4.5 to 6.7 mg/dL. Week-to-week insulin-sensitivity drift
runs 8 to 22%.

This convergence is not guaranteed and it is the load-bearing assumption of the method. It
means these numbers describe closed-loop life in general rather than one idiosyncratic
group, and it provides a real-world target against which simulator fidelity can be judged.
Where the real cohorts disagree, as they do somewhat for the rate of compression lows, the
envelope is correspondingly wide and the test on that statistic is more lenient.

## 5. Where the simulator holds, and where it fails

**Table 1.** Each cell is the per-user median with a bootstrap 95% confidence interval.
Sim values outside the real-world envelope are marked with an asterisk.

| Signature | Boost | Trio | OpenAPS | AAPS-classic | Padova adult | Padova adolescent | Padova child |
|---|---|---|---|---|---|---|---|
| Glucose variability (CV%) | 30 | 33 | 34 | 32 | 23* | 24* | 30 |
| Rise tail P(rise>10)/5min (%) | 4.3 | 6.6 | 3.8 | 3.7 | 1.0* | 1.6* | 2.6* |
| Autocorrelation @30 min | 0.8 | 0.8 | 0.9 | 0.8 | 0.8 | 0.9 | 0.8 |
| Autocorrelation @60 min | 0.5 | 0.6 | 0.7 | 0.6 | 0.7 | 0.7 | 0.6 |
| Outcome SD @stuck-high (mg/dL) | 30 | 34 | 27 | 29 | 21* | 22* | 28 |
| Diurnal amplitude (mg/dL) | 35 | 41 | 48 | 56 | 47 | 59* | 52 |
| Hypo recovery to 100 (min) | 59 | 50 | 55 | 50 | 113* | 119* | 110* |
| Hypo rebound >180 (%) | 26 | 23 | 27 | 28 | 0* | 0* | 5* |
| Compression lows (/30d) | 4.6 | 5.3 | 1.9 | 3.0 | 0* | 0* | 0* |
| Sensor jitter (mg/dL) | 4.5 | 6.7 | 5.5 | 4.7 | 2.4* | 2.4* | 2.5* |
| ISF drift (weekly %CV) | 22 | 15 | 12 | 8 | 0* | 0* | 0* |

![**Figure 1.** Real-world AID cohorts (blue) and UVA/Padova personae (warm colours),
each panel one signature, with bootstrap confidence intervals. The shaded band is the
real-world envelope (the span of the four real medians). The four real cohorts cluster
inside it; the personae sit inside it for autocorrelation and, for the child, variability,
and outside it for every mechanism that makes real-world control hard.](../scripts/2026-07-insilico/fidelity_suite/fig_multicohort.png)

The simulator is faithful on the shape of the glucose curve over the short horizon. Its
autocorrelation at 30 and 60 minutes lands inside the real range for all three persona
classes. For smooth, benign, announced-meal stretches, which are the majority of any day,
it behaves like real data, and it remains a reasonable tool for controller stability
checks and regression testing.

Overall variability is a subtler case. With realistic, weight-appropriate meals the
simulator can reach real-world glucose variability, but only through its child personae,
which are the most variable. The adult and adolescent personae run persistently smoother
than any real cohort, at 23 to 24% against a real 30 to 34%, and the same is true of the
stuck-high outcome spread. Since controllers are almost always evaluated on the adult
personae, the default in-silico test understates the variability of real life; reaching
realism required the personae the field does not usually test.

The failures matter more than the passes, because they are precisely the situations a
safety test exists to probe, and because no persona reproduces them at any age.

The fat tail of sudden glucose rises, the fingerprint of an unannounced meal, is missing.
Real cohorts see a sharp five-minute rise 4 to 7% of the time; the personae, all of them,
sit at 1 to 3%, and their controllers were told the carbohydrate in advance in any case.

Hypoglycaemia behaves like a different phenomenon. Real lows recover to 100 mg/dL in about
50 to 59 minutes and overshoot above 180 mg/dL roughly a quarter of the time, because
people eat to treat them. The personae take about twice as long, 110 to 120 minutes, and
almost never overshoot, because the simulator has no rescue carbohydrate and can only
recover by withdrawing insulin. A controller tuned to look good against the simulator's
slow, monotone recovery is being tuned against a hypo that does not happen.

The sensor is too clean. Real continuous glucose data carries about twice the
high-frequency jitter of the simulator's sensor model, and it produces sharp reversing
compression lows a few times a month that the model has no mechanism for at all. A
controller that never has to tell a real low from a sensor artefact has not been tested on
one of the commonest causes of a bad automated decision overnight.

And the model never changes. Real insulin sensitivity drifts 8 to 22% from week to week
and the loops adapt to it; the 2008 model's parameters are fixed, so its drift is zero by
construction. One caveat belongs here: our drift measure reads the sensitivity the
algorithm itself used, so the AndroidAPS-classic cohort, which predates dynamic
sensitivity, sits at the low end because its algorithm barely adjusts, not because those
people do not change. The three adaptive cohorts, and the underlying physiology, drift;
the simulator does not.

Five of the eleven statistics are matched by no persona at any age, and a sixth,
sensitivity drift, is zero by construction.

## 6. Why refinement is not the whole answer

The obvious objection is that we tested the 2008 model, and that later versions are
better. It is a fair objection and it deserves a precise answer, because part of it is
correct.

Rather than reason about what the refinements should do, we measured it. The central
refinement of the S2013 version over the 2008 model is time-varying insulin sensitivity,
intraday and interday, calibrated from clinical data [2], together with a dawn-phenomenon
component. We implemented exactly that mechanism on the 2008 personae, scaling the
insulin-dependent glucose uptake and the hepatic insulin action by a common time-varying
sensitivity factor with a day-to-day coefficient of variation of 22% and a dawn amplitude
of 20%, both clinically plausible magnitudes. Everything else, the meals, the announcement,
the sensor and the controller, is identical to the 2008 baseline, so the only change is the
sensitivity process. This is an S2013-style augmentation of the 2008 personae, not the
licensed S2013 model, which is not freely available; it isolates the effect of the headline
refinement rather than reproducing the whole version.

**Table 2.** Adult personae, 2008 versus the S2013-style time-varying-sensitivity
augmentation, against the real-world envelope (per-persona median).

| Signature | Real range | 2008 | S2013-style | Effect |
|---|---|---|---|---|
| Glucose variability (CV%) | 30 to 34 | 23 | 32 | into range |
| Diurnal amplitude (mg/dL) | 35 to 56 | 47 | 65 | overshoots |
| Outcome SD at a stuck high (mg/dL) | 27 to 34 | 21 | 20 | unchanged |
| Rise tail (%) | 3.7 to 6.6 | 1.0 | 1.3 | unchanged |
| Hypo recovery (min) | 50 to 59 | 113 | 116 | unchanged |
| Hypo rebound (%) | 23 to 28 | 0 | 0 | unchanged |
| Compression lows (/30d) | 1.9 to 5.3 | 0 | 0 | unchanged |
| Sensor jitter (mg/dL) | 4.5 to 6.7 | 2.4 | 2.3 | unchanged |

![**Figure 2.** Adult personae, 2008 (grey) versus the S2013-style time-varying-sensitivity
augmentation (orange), against the real-world range (blue band). The refinement raises
overall variability into range and overshoots the diurnal amplitude, and leaves every
structural gap untouched.](../scripts/2026-07-insilico/fidelity_suite/fig_s2013.png)

The result is precise. The refinement does one useful thing to fidelity: it raises overall
glucose variability from below the real range into it. It overshoots the diurnal amplitude,
pushing a statistic the 2008 model already matched out of range. It does not change the
stuck-high outcome spread at all, because slow sensitivity variation is nearly constant over
the half hour that statistic looks ahead, so the model's insulin still acts too predictably.
And it leaves every structural gap where it was: the announced-meal rise tail, the
untreated-hypo recovery and rebound, and the sensor compression rate and noise are unmoved
to two significant figures, because none of them depends on insulin sensitivity. The same
pattern holds for the adolescent and child personae.

Three things follow. First, the refinements are not what the open AID community uses; the
freely available, community-standard tool is the 2008 model, and the algorithms trained and
screened on it inherit its gaps directly. Second, and now measured rather than asserted, the
refinement leaves the features that drive most of our failures untouched: meals are still
announced, hypoglycaemia is still not treated with carbohydrate, and the sensor still has no
compression artefact. Real-world CGM lows are frequently sensor artefacts and real-world
highs frequently begin with unannounced food; a simulator that omits both is omitting two of
the most common triggers of a consequential automated decision. Third, the licensed S2013
bundles further changes we did not implement in the augmentation above. Most do not act on
the scenario or the sensor, but one, the glucagon counter-regulation model, does act on how
a low resolves and could in principle move the hypo-recovery and rebound results; we
therefore measured it too, and report the result in Section 6.1. No version, to our
knowledge, has been benchmarked against a real-world distributional envelope. The claim that
a refinement closes a gap is an empirical claim, and where we could test it, it closed one
gap of eight and opened another.

That is the shift the pattern of results argues for. The problem is not that a particular
model version is inadequate. It is that simulator fidelity to real-world data is treated
as established rather than as something to be quantified for the specific statistics a
safety decision depends on. The failures we find are not random; they cluster on the
disturbances the model does not represent, and they are exactly the disturbances that
define real-world safety.

### 6.1 The one refinement that could close the hypo gap does not

Of the S2013 changes we did not test above, one, the glucagon counter-regulation model,
acts directly on how a low resolves and could in principle move the hypo-recovery and
rebound results. We therefore implemented its functional effect on top of the
sensitivity augmentation, raising endogenous glucose production during hypoglycaemia in
proportion to how far below 80 mg/dL glucose has fallen and how fast it is falling, the
two drivers of glucagon secretion in the model, and measured again.

**Table 3.** Adult personae across the two refinements, hypoglycaemia signatures.

| Signature | Real range | 2008 | + sensitivity | + glucagon | 
|---|---|---|---|---|
| Hypo recovery to 100 (min) | 50 to 59 | 113 | 116 | 106 |
| Hypo rebound above 180 (%) | 23 to 28 | 0 | 0 | 0 |

![**Figure 3.** Adult personae across the S2013 refinements. Counter-regulation (green)
narrows the hypo-recovery gap but does not close it, and does not restore the rebound; the
variability panel confirms the sensitivity effect is preserved.](../scripts/2026-07-insilico/fidelity_suite/fig_s2013_glucagon.png)

Counter-regulation does exactly what physiology predicts and no more. It speeds
hypoglycaemia recovery, from about 116 to 106 minutes, moving it towards the real 50 to 59
minutes but leaving it nearly twice too slow; and it does not produce the post-treatment
rebound that a real low shows, because endogenous glucose release is self-limiting where
eating is not. The effect is larger for the personae that actually go low, but the
direction and the shortfall are the same. The reason is simple and it is the reason the
whole hypoglycaemia signature fails: real lows are treated with carbohydrate, a faster and
larger glucose input than any counter-regulatory model, and the simulator does not eat.
(One incidental artefact: with counter-regulation the compression-low signature reads a
small non-zero value, because sharp counter-regulatory reversals resemble the reversing
shape of a sensor compression low; the model has gained hypos that look like the artefact,
not the artefact itself.) So the S2013 refinement most likely to rescue the hypoglycaemia
result narrows the gap and does not close it, which is the strongest form of the argument:
even the right physiological addition cannot substitute for a disturbance, carbohydrate
treatment, that the simulator does not model at all.

## 7. A new approach

The constructive reading of these results is not to distrust simulation, which replaced
animal trials for good reasons and remains valuable for what it does well, but to widen it
and to measure it. We propose three levels, in decreasing order of readiness.

**An empirical fidelity benchmark, usable now.** The signatures used here are a first
draft of a standard: any in-silico safety claim could report where the scenarios it was
tested on sit relative to a real-world envelope on unannounced-meal rises, hypo treatment,
sensor artefact and sensitivity drift, alongside the usual outcome metrics. This turns
fidelity from an assumption into a reported quantity, and it is version-agnostic: it
applies equally to the 2008 model, to S2013, and to anything that follows. It requires no
new simulator, only that fidelity be measured and stated. The real-world envelopes can be
drawn from open corpora such as the OpenAPS Data Commons, so the benchmark can be
community-maintained.

**Hybrid, data-calibrated simulation, in the near term.** The validated mechanistic core
can be retained and augmented with empirically fitted stochastic layers for the specific
gaps measured here: an unannounced-meal onset process, time-varying insulin efficacy at
the magnitude real data shows, a sensor model that includes both realistic high-frequency
noise and compression artefacts, and week-scale sensitivity drift. Each of these can be
fitted directly from the corpora that now exist, and each closes a specific, measured
gap rather than adding unquantified realism. This is an incremental, testable programme,
not a rebuild.

**Real-disturbance replay and generative models, on the horizon.** The real datasets are
themselves the most faithful description of the disturbances a controller will meet. They
can be used directly as scenario libraries, replaying measured sequences of meals,
activity and sensor behaviour, so that a controller is tested against real disturbance
trajectories rather than synthetic ones. Further out, constrained generative models
trained on the large real corpora, with the mechanistic model as a physiological prior,
could produce populations that match the real-world envelope by construction. Both are
speculative and both would need their own validation, but the data to attempt them exists
today at a scale that did not a decade ago.

None of these displaces the mechanistic simulator. The benchmark sits on top of it, the
hybrid extends it, and even the generative approach would use it as a prior. The argument
is not against the UVA/Padova model but against using any single simulator as an unmeasured
proxy for reality.

## 8. Limitations

This is an observational, distributional comparison and it should be read as such. The
real cohorts are self-selected users of open-source AID systems and are not a
representative sample of the type 1 population; their convergence is reassuring but does
not establish population representativeness. We tested the open 2008 implementation, and in
Section 6 we measured the two S2013 refinements that could plausibly affect our results, the
time-varying insulin sensitivity and the glucagon counter-regulation, by implementing them on
the personae; but we did not run the licensed S2013 model itself, and our reimplementations
use clinically plausible magnitudes rather than the certified per-subject parameters, so they
isolate the mechanisms rather than reproduce the whole version. The remaining S2013 changes
do not act on the scenario or the sensor, which is where the structural gaps live.
The magnitude of our sensitivity augmentation is a clinically plausible choice rather than a
fitted value, and the closed-gap result scales with it, though the untouched structural gaps
do not. The announced-meal scenario handicaps the simulator on the
rise-tail statistic specifically, which we note rather than hide. The drift statistic
reads algorithm-reported sensitivity and so partly reflects whether an algorithm adapts,
not only whether physiology changes. Finally, our signatures are a first draft; they are
not a validated or exhaustive fidelity battery, and refining them into a standard is part
of the work we are proposing, not a claim that it is finished.

## 9. Conclusion

The UVA/Padova simulator has served the field well, and replacing animal trials was a real
advance. But a tool with this much influence over which systems reach patients should be
held to an explicit, measured standard of fidelity to the real world, and on the
community-standard implementation that standard is not currently met for the statistics
that matter most to safety. The simulator is smooth where real life is variable, its meals
are announced where real meals are not, its lows are untreated where real lows are eaten
for, its sensor is clean where real sensors are not, and its physiology is fixed where
real sensitivity drifts. A controller can pass in silico by mastering the world the
simulator represents and still meet, untested, the world that actually produces the lows
and the stuck highs.

The answer is not to abandon simulation but to measure its fidelity and to widen it with
the real-world data that now exists in abundance. It is time for a new approach to
closed-loop testing: one that treats fidelity to real data as a reported quantity, and
that builds simulators calibrated to, and validated against, the people they stand in for.

## References

*[Citations below give author, title, venue and year; exact volume and page numbers
should be verified against the source before submission.]*

1. Kovatchev BP, Breton M, Dalla Man C, Cobelli C. In silico preclinical trials: a proof
   of concept in closed-loop control of type 1 diabetes. Journal of Diabetes Science and
   Technology. 2009.
2. Dalla Man C, Micheletto F, Lv D, Breton M, Kovatchev B, Cobelli C. The UVA/PADOVA Type
   1 Diabetes Simulator: new features. Journal of Diabetes Science and Technology. 2014.
3. Visentin R, Campos-Nanez E, Schiavon M, et al. The UVA/Padova Type 1 Diabetes Simulator
   goes from single meal to single day. Journal of Diabetes Science and Technology. 2018.
4. Visentin R, Dalla Man C, Kovatchev B, Cobelli C. The University of Virginia/Padova Type
   1 Diabetes Simulator matches the glucose traces of a clinical trial. Diabetes Technology
   and Therapeutics. 2014.
5. Cobelli C, Kovatchev B. Developing the UVA/Padova Type 1 Diabetes Simulator: modeling,
   validation, refinements, and utility. Journal of Diabetes Science and Technology. 2023.
6. Xie J. simglucose: a type 1 diabetes simulator implemented in Python (v0.2.1, 2018).
   https://github.com/jxx123/simglucose
7. Dalla Man C, Rizza RA, Cobelli C. Meal simulation model of the glucose-insulin system.
   IEEE Transactions on Biomedical Engineering. 2007.
8. OpenAPS Data Commons, Open Humans Foundation. Openly shared automated-insulin-delivery
   data from the #WeAreNotWaiting community.

---

*Reproducibility: all cohort loaders, the thirty-persona simulator generator, the eleven
signatures, the aggregation and the figure are open and re-runnable. Real cohort data is
anonymised and, for the OpenAPS cohort, drawn from an openly shared research commons.*

# Commit-to-peak interval and the low that follows (2026-08-13)

*Reproduce: `peak_timing.py` against the local TimescaleDB refreshed to t=now. 2,505 commits
across nine participants, one decision row per five-minute bucket, intervals from a cluster
bootstrap over participants.*

## Why this exists

A separate analysis asked whether a commit approached with decaying `delta_accl` predicts the
crash that sometimes follows, and returned a null across nine threshold variants. That metric is
one hundred times the difference between the current change and its short average, divided by
that average floored at two, so on a steady or steepening rise it converges on zero by
construction.

An event on 2026-08-13 shows what that costs. Across the approach `delta_accl` read −2.4, −1.4,
−1.9 and −0.6 while the raw five-minute increments were +12, +14 and +21. The metric called the
approach flat; the glucose was making its steepest increment yet. The commit fired 4.50 U at
183 mg/dL, glucose peaked five minutes later at 199, and fell 116 mg/dL over the next thirty
minutes with 4.33 U still on board at 83.

This asks the question the metric could not: how long after the commit does glucose peak, and
does a short interval predict the low.

## Design

The event is entry into the CONFIRMED state, which is where the committed dose fires. The peak is
the maximum sensor value in the three hours from the commit. The outcome is a glucose below
70 mg/dL sustained at least ten minutes within three hours of the commit.

The primary comparison was fixed before running: peak at or within ten minutes of the commit
against peak later. Every other cut examined is listed below rather than in a footnote.

## Results

Glucose peaks a median of 54 minutes after a commit, and within ten minutes on 12.8 per cent of
occasions.

| interval to peak | n | low rate | median dose | median glucose |
|---|---|---|---|---|
| at or before the commit | 104 | 0.288 | 0.28 | 166 |
| 0 to 10 min | 217 | 0.258 | 1.00 | 149 |
| 10 to 20 min | 288 | 0.274 | 1.00 | 136 |
| 20 to 40 min | 449 | 0.163 | 1.00 | 132 |
| 40 to 80 min | 527 | 0.114 | 0.70 | 127 |
| over 80 min | 920 | 0.150 | 0.65 | 117 |

On the primary comparison, commits whose peak falls within ten minutes are followed by a low on
26.8 per cent of occasions against 16.0 per cent for the rest, a difference of 10.8 points with an
interval from 5.5 to 14.7.

Scored as a continuous predictor against everything else available at the moment of commit:

| predictor | AUC | 95% CI |
|---|---|---|
| shorter interval to peak | 0.582 | [0.548, 0.631] |
| delta_accl, the retired metric | 0.498 | [0.449, 0.543] |
| committed dose | 0.492 | [0.458, 0.529] |
| insulin on board | 0.435 | [0.403, 0.515] |
| glucose at commit | 0.422 | [0.361, 0.499] |

The interval is the only quantity clear of chance in the expected direction. Glucose at commit is
clear of chance in the inverted direction, meaning commits at higher glucose are followed by fewer
lows, which is consistent with the recovering-high context found elsewhere in the programme.

Every cut examined, with the primary marked:

| variant | n | difference | 95% CI |
|---|---|---|---|
| peak at or before the commit | 104 | +0.119 | [+0.039, +0.211] |
| peak within 5 min | 206 | +0.112 | [+0.055, +0.159] |
| peak within 10 min (primary) | 321 | +0.108 | [+0.055, +0.147] |
| peak within 15 min | 464 | +0.138 | [+0.086, +0.191] |
| peak within 20 min | 609 | +0.128 | [+0.089, +0.164] |
| peak within 30 min | 851 | +0.121 | [+0.096, +0.139] |
| peak within 10 min and dose at least 2 U | 68 | +0.139 | [+0.039, +0.261] |
| peak rise over the commit under 20 mg/dL | 593 | +0.072 | [+0.022, +0.116] |
| peak within 10 min and glucose at least 180 | 93 | +0.031 | [−0.092, +0.159] |
| last increment was the largest of the approach | 1,426 | −0.030 | [−0.053, +0.007] |

The effect is stable from 0.112 to 0.138 across every threshold from zero to thirty minutes rather
than clearing zero at one cut and weakening either side, which is the signature that separates this
from the nine dead variants on the adjacent hypothesis.

That the approach shape does not carry it is worth stating: whether the last increment was the
largest of the approach makes no difference, at −0.030 with an interval spanning zero. The signal
is in when the peak arrives, not in how hard glucose was rising into the commit.

Eight of nine participants move in the same direction.

| user | commits | early | rate early | rate late | difference |
|---|---|---|---|---|---|
| A | 352 | 31 | 0.161 | 0.053 | +0.108 |
| B | 424 | 51 | 0.275 | 0.107 | +0.167 |
| C | 377 | 38 | 0.263 | 0.212 | +0.051 |
| D | 272 | 41 | 0.463 | 0.316 | +0.147 |
| E | 127 | 18 | 0.000 | 0.138 | −0.138 |
| F | 375 | 55 | 0.218 | 0.100 | +0.118 |
| H | 78 | 11 | 0.182 | 0.060 | +0.122 |
| I | 19 | 4 | 0.250 | 0.067 | +0.183 |
| tim | 481 | 72 | 0.319 | 0.235 | +0.085 |

E is the exception, on eighteen early commits with no lows among them.

## The limitation that governs what can be done with this

The interval is measured using data from after the commit. It is not available at the moment of
commit and cannot gate one. Nothing here is a lever.

What it establishes is that the mechanism is real and correctly identified: a commit that lands at
or near the peak delivers insulin against carbohydrate that has largely been absorbed, and is
followed by a low half again as often as one that lands early in a rise. It also confirms the
earlier null on the same events, since `delta_accl` scores 0.498 here.

The actionable question is therefore a different one, and it is a prediction problem rather than a
gating rule: is time-to-peak itself predictable at the moment of commit from what the algorithm
holds. That has not been tested. If it is not predictable, the finding stands as attribution and
the levers remain the two the programme already has, which are to give less or to withdraw
afterwards.

Two further caveats bound this. The association is observational, and nothing here shows that a
smaller dose at those commits would have avoided the low. And for the longer intervals the
causation may run partly the other way, since a large dose can bring a peak forward; that
possibility does not touch the short-interval cells, where insulin has not had time to act.

Confidence: SOLID for the association and its direction, being stable across nine cuts, consistent
across eight of nine participants, and clear of the alternatives on the same events. SPECULATIVE
for anything built on it, since the discriminating quantity is not observable at decision time.

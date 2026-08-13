# Exercise, activity, and the mechanism behind the post-meal crash

## Hypothesis

Time below range is dominated by activity, at roughly 48 per cent of it pooled across the cohort, so
activity is where the largest low-side opportunity lies. Two hypotheses followed. The first was that
recent movement is a leading indicator of hypoglycaemia and could drive a protective response. The
second, addressing the specific and much-reported pattern of a crash after exercising soon after a
meal, was that the meal dose is simply too large when exercise follows, so the fix is a smaller meal
dose.

## Investigation

The leading-indicator question was investigated as a dose-response, by measuring the forward
hypoglycaemia rate against recent step count, and then as a prediction problem with participants held
out, to see whether the relationship transfers between people.

The post-meal exercise question was investigated by comparing the insulin state of participants who
crashed against those who did not, from the same meals and the same boluses. If the dose story is
right, crashers should be carrying more insulin.

## Methods

Recorded under `backtesting/scripts/2026-07-residency/ACTIVITY_HYPO_REPORT.md` over roughly 89,500
cycles, and `2026-07-postmeal-exercise-mechanism/` over 686 events. Prediction used gradient boosting
with participants as folds. Heart rate was evaluated alongside steps but is 76 per cent absent from
the record, so its verdict is one of data sparsity rather than of usefulness.

## Results

Steps are a strong, monotone leading indicator. Forward hypoglycaemia within three hours rises from
13.1 per cent at no recent steps through 17.1, 18.5, 25.9 and 31.8 to 38.5 per cent above 1,200 steps
in the preceding hour, against a cohort base of 19.1. Sedentary to very active nearly triples the
rate.

The relationship does not transfer between people. A predictor built with participants held out
scores 0.739 without activity features and 0.717 with them, so the cross-user lift is minus 0.02,
within fold noise. The pooled dose-response is strong and the generalised model gains nothing, which
means each participant's fitness, step baseline and post-activity fall differ enough that a single
threshold cannot serve them. This validates per-user activity thresholds over a global model.

The post-meal crash is not dose-driven, and the evidence reverses the assumption. Participants who
crashed were carrying less insulin, at 0.96 U against 1.61, from the same boluses. The crash rate
falls as insulin on board rises, at 32, 22 and 18 per cent across low, middle and high tertiles.
Crashers also started from a lower glucose, 114 against 136.

## Discussion

The mechanism is a carbohydrate counterweight failure rather than an insulin excess. Exercise drains
glucose by a route that does not require insulin, and that drain lands when the meal's carbohydrate
flux is already thinning. The participants who crash are the ones with less insulin on board because
they are further through the meal, not because they were dosed harder.

This puts the loop on the wrong side of the problem. What is needed is glucose in, and the only
instrument available is insulin out, which has already been spent. Reducing the meal dose would
address a cause that is not operating, and would cost the meal coverage that the dose exists for.

The consequence is that the exercise lever is anticipatory withdrawal or carbohydrate, not smaller
meal doses, and that reframing was the point of the study. It also demonstrates the value of asking
what distinguishes the cases that go wrong rather than assuming the obvious cause, since the obvious
cause here was not merely absent but inverted.

Two smaller results sit alongside. The post-exercise recovery tail is real but modest, at about 1.2
times baseline hazard and flat across the first five hours once de-artefacted; an earlier report of a
doubled delayed hazard was an artefact of the window length. And a rolling day-scale step load does
not predict insulin sensitivity: matched-insulin forward-low rates differ by a factor of 1.06, the
residual slope has the wrong sign, and the correlation with the autosens ratio is minus 0.06.

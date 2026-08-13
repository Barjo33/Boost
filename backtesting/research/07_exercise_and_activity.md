# The post-meal exercise crash is a carbohydrate failure, not an insulin excess

Why the participants who crash after exercising near a meal are carrying less insulin than those who
do not, and what that does to the available lever.

## Abstract

Activity accounts for approximately 48 per cent of pooled time below range, making it the largest
low-side mechanism in the cohort. Recent step count is a strong monotone leading indicator: forward
hypoglycaemia within three hours rises from 13.1 per cent at no recent steps through 17.1, 18.5,
25.9 and 31.8 to 38.5 per cent above 1,200 steps in the preceding hour, against a cohort base of
19.1. The relationship does not transfer between people. A hypoglycaemia predictor with participants
held out scores 0.739 without activity features and 0.717 with them, a cross-participant lift of
minus 0.02 that sits inside fold noise, which establishes per-participant thresholds rather than a
global model as the correct shape for the protection. On the specific pattern of a crash after
exercising soon after a meal, across 686 events, participants who crashed were carrying 0.96 U
against 1.61 for those who did not, from the same boluses, and the crash rate falls across insulin
tertiles at 32, 22 and 18 per cent. The mechanism is therefore a carbohydrate counterweight failure
rather than an insulin excess, which places the loop on the wrong side of the problem and rules out
a smaller meal dose as the remedy.

## Introduction

Time below range dominates the addressable loss in this cohort, and activity dominates time below
range. Two questions follow, and they have different shapes.

The first is whether recent movement is a usable leading indicator. A relationship strong enough to
act on would support a protective response triggered by activity, and the question of whether that
response should be tuned per participant or globally is settled by whether the relationship
generalises across people.

The second concerns a specific and much-reported pattern: a crash following exercise taken soon
after a meal. The intuitive account is that the meal dose is too large when exercise follows, which
makes the remedy a smaller meal dose. That account has a testable consequence. If it is right, the
participants who crash should be carrying more insulin than those who do not, from comparable meals.

## Methods

The leading-indicator work is recorded under
`backtesting/scripts/2026-07-residency/ACTIVITY_HYPO_REPORT.md` over roughly 89,500 cycles. It was
approached first as a dose-response, measuring forward hypoglycaemia rate against recent step count,
and then as a prediction problem with participants held out as folds, so that the transfer question
is answered by cross-participant generalisation rather than by pooled association or by feature
importance. A feature block that lifts in-sample and ranks highly on importance while adding nothing
with a participant held out has established that the relationship lives within people and not
between them, which is a result rather than a failure.

The mechanism work is recorded under `backtesting/scripts/2026-07-postmeal-exercise-mechanism/` over
686 events, comparing the insulin state of participants who crashed against those who did not from
comparable meals and boluses.

Heart rate was evaluated alongside steps. It is 76 per cent absent from the record, so its verdict is
one of data sparsity rather than of usefulness.

## Results

Steps are a strong, monotone leading indicator. Forward hypoglycaemia within three hours runs 13.1,
17.1, 18.5, 25.9, 31.8 and 38.5 per cent across increasing bands of steps in the preceding hour,
against a cohort base of 19.1. Sedentary to very active nearly triples the rate.

The relationship does not transfer. With participants held out, the predictor scores 0.739 on the
baseline block and 0.717 with activity added, a lift of minus 0.02 that lies within fold noise, while
the same features rank fifth and sixth on importance in-sample.

The post-meal crash is not dose-driven, and the evidence inverts the assumption rather than merely
failing to support it. Crashers carried 0.96 U against 1.61 for non-crashers from the same boluses.
The crash rate falls as insulin on board rises, at 32, 22 and 18 per cent across tertiles. Crashers
also started from a lower glucose, 114 against 136 mg/dL.

The post-exercise recovery tail is real and modest, at about 1.2 times baseline hazard, flat across
the first five hours.

A rolling day-scale step load does not predict insulin sensitivity. Matched-insulin forward-low rates
differ by a factor of 1.06, the residual slope carries the wrong sign, and the correlation with the
sensitivity ratio is minus 0.06.

## Discussion

The mechanism is a carbohydrate counterweight failure. Exercise drains glucose by a route that does
not require insulin, and the drain lands when the meal's carbohydrate flux is already thinning. The
participants who crash carry less insulin because they are further through the meal, not because they
were dosed harder.

That places the loop on the wrong side of the problem. What the situation requires is glucose in, and
the only instrument the loop holds is insulin out, which has already been spent. Reducing the meal
dose addresses a cause that is not operating and forfeits the coverage the dose exists to provide.
The available levers are therefore anticipatory withdrawal well before the exercise, or carbohydrate,
and the reframing is the substantive output of the study.

The transfer result and the mechanism result point the same way on personalisation. A relationship
that is strong pooled and absent across held-out participants is a statement that people differ in
the parameter rather than in the phenomenon, and the correct response is to estimate the parameter
per person offline. That is what the shipping configuration does, and the activity thresholds are
among the quantities it derives.

The value of asking what distinguishes the cases that go wrong, rather than assuming the obvious
cause, is unusually clear here, because the obvious cause is not merely absent but reversed. An
intervention built on it would have removed insulin from the participants least able to spare it.

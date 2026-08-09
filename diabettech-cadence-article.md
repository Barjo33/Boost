# Does a one minute CGM actually make a closed loop better?

*The usual preamble, because it matters. Everything described here is highly experimental. It runs modified code that isn't in any released version of AndroidAPS or Trio, it uses insulin in an off-label fashion, and none of it is medical advice. It's an n=1, shared in the #WeAreNotWaiting spirit so the learning is useful to someone else. Take the ideas, not the settings.*

---

One minute continuous glucose monitors have arrived, and with them an assumption so obvious that nobody bothers to state it. More data must be better. Five times as many readings, five times as much to go on, so of course the loop should do a better job.

I've spent a fair while trying to work out whether that's true, and I've come round to doubting it. Not because I've measured it and found nothing, but because of what the physiology implies and because of what I can measure about how much my own control varies day to day, which turns out to set a hard limit on what any straightforward trial could detect. What I want to do here is set out the reasoning, say where I think the faster feed genuinely might help, and describe the experiment I'm about to run, which is deliberately not the obvious one.

## What I think the extra readings contain, and why I can't yet show you

I have to be straight about something before going further, because it changes what this piece can
claim. I do not yet have any one minute data. It arrives on Thursday, and it will be twenty four
hours of it. Everything below is therefore an argument and a plan, not a result, and the figures
that would settle it are not drawn yet.

I mention this because I nearly published a version of this article with a chart in it captioned as
six hours of a real one minute sensor. It was not. It was a five minute feed with duplicated
entries, and it looked convincing enough to fool me: most consecutive gaps were a minute apart. What
gave it away was that the readings all landed on the same second of the minute, a third of them
repeated the previous value exactly, and the gaps went back to five minutes wherever the duplication
stopped. A real sensor jitters. Backfill does not.

So here is the argument as it stands, with the evidence I actually have.

The reason I doubt the extra readings contain much is physiological rather than technical.
Interstitial fluid lags blood by something like four minutes, and that lag acts as a filter. By the
time glucose reaches the sensor it has already been smoothed, and sampling the smoothed version more
often does not recover what the smoothing removed. If that is right, a one minute trace laid over a
five minute one should look like the same curve with more dots on it, and not like a curve with
structure the five minute view was missing.

That is the first thing Thursday's data will test, and it is a test that can fail. If there is
genuine sub-five-minute structure in a real one minute feed, it will show up immediately as
departures between the two, and I will have been wrong.

## The one place I expect it to earn its keep

If the shape turns out to be the same, is there anything at all? I think so, and it is worth being
precise, because it would not be more information. It would be less waiting.

On a five minute grid a reading arrives and then nothing happens for five minutes. If glucose starts
dropping thirty seconds after a reading, your loop finds out four and a half minutes later. A one
minute feed does not tell you anything the five minute feed would not have told you eventually. It
tells you sooner, by an average of two minutes.

Two minutes is nothing at all when glucose is drifting, which is most of the day. It is not nothing
during a fast fall, where it is a meaningful share of the time you have to react, and where being
two minutes late can be the difference between easing off in time and not. So my expectation is that
any benefit is confined to the fast bits, and is invisible in an average day precisely because the
average day is flat.

Thursday's twenty four hours will let me put numbers on that: how far behind a held five minute
value actually runs, split by how fast glucose is moving at the time. I will add that chart when I
have it rather than describe it now.

## Why I can't answer this by staring at more data

Here I have to be honest about a limit that shapes everything I do with this system.

I can measure what my loop decided. I can measure what my glucose then did. What I can't do is produce the glucose that would have happened had the loop decided differently, because that trajectory doesn't exist and no amount of cleverness conjures it. Every offline comparison I run is therefore a comparison against something that actually happened, with all the reasons it happened baked in.

I could run a straightforward trial instead. Wear a one minute sensor for a month, wear a five minute sensor for a month, compare time in range. The trouble is I know in advance it won't work. My own day to day variability in time in range has a standard deviation of about nine percentage points, so a month per arm can only reliably detect a difference of around seven points against a baseline of eighty five. That would mean going from eighty five to ninety two on the strength of a two minute latency gain. Nobody thinks that's going to happen, and reporting "no significant difference" afterwards would be worthless, because it would be indistinguishable from a study too small to see anything.

That reframed the question for me. Before asking whether a faster feed produces better outcomes, ask whether it produces different decisions at all. That can be answered exactly, and without anyone taking any risk.

## Four loops, one body

So here's what I'm actually doing. Four copies of AndroidAPS on one phone, all fed from the same single sensor. One is my ordinary therapy and is connected to the pump. The other three run the virtual pump: they compute and decide exactly as the real one does, and deliver nothing.

![Four arms, one sensor, one body](fig3_design.png)

The four differ in three ways that are easy to muddle, so let me lay them out plainly.

| arm | glucose supplied | decision taken | earliest possible bolus |
|---|---|---|---|
| A | every 5 min | every 5 min | 5 min |
| B | every 1 min | every 5 min | 5 min |
| C | every 1 min | every 1 min | 1 min |
| D | every 1 min | every 1 min | 3 min |

Comparing A with B varies the sensing cadence and nothing else, since both think and act on the same schedule. That's the question people actually mean when they ask whether a one minute CGM is better.

Comparing C with D varies only how closely spaced boluses may be, with both thinking every minute. That matters because a faster loop doesn't only see sooner, it also gets more opportunities to dose, and those are separate things that get bundled together in casual discussion. Without D there'd be no way to tell which of the two was responsible for any difference between B and C.

It's worth saying why this needs four instances rather than clever configuration of one. I want the whole behaviour, not a single cycle. A loop that decides every minute and can bolus every minute builds a different insulin trajectory, the brakes and caps that respond to insulin on board then engage differently, and that interaction is the interesting part. Each copy has to run its own complete loop, accumulating its own insulin on board from its own decisions, for that to show up at all.

## What this can and can't tell me

The glucose those three copies observe is real. It responds to the insulin my actual pump delivered, not to the insulin they believe they delivered. Their internal state is coherent but counterfactual, and the gap widens the longer they run.

I don't think that's fatal, but it bounds the claim and it changes the design. They get re-anchored to reality daily, and every comparison gets reported split by how long it's been since that anchoring. If the differences grow as the day goes on, the later hours are measuring accumulated drift rather than cadence, and only the early hours support any statement about cadence at all. That's written into the protocol in advance, specifically so a growing difference can't be presented later as a large effect.

The other thing to state once: this measures decisions, not outcomes. Nobody's time in range appears anywhere in it. If the four copies agree on almost everything, the outcome question is largely answered by implication and nobody needs to be experimented on to learn it. If they disagree substantially, the circumstances where they disagree will tell me where to point a proper trial, which is a much better use of a month than measuring aggregate glycaemia and hoping.

## What I expect, written down beforehand

I think A and B will be nearly identical, because sensing cadence is the thing I've already failed to find a benefit in. I think C and D will differ mainly in the timing and granularity of delivery rather than the total, because the caps and brakes will absorb the extra opportunities. And I think whatever shows up between B and C will turn out to be mostly the extra dosing opportunities rather than the faster decision.

I'm writing that down now, before the data arrives, because a prediction made afterwards isn't a prediction. If I'm wrong, I'd rather it be on the record.

The one result I'd genuinely like to see is a difference confined to fast falls, because that would match the only place the offline work left a signal, and it would give the faster feed a real job rather than a marketing one. My honest expectation is a null, and a null is a perfectly good answer. It would mean the five minute grid was never the constraint, and that the effort is better spent on the things that are.

I'll report back either way.

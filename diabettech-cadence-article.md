# Does a one minute CGM actually make a closed loop better?

*The usual preamble, because it matters. Everything described here is highly experimental. It runs modified code that isn't in any released version of AndroidAPS or Trio, it uses insulin in an off-label fashion, and none of it is medical advice. It's an n=1, shared in the #WeAreNotWaiting spirit so the learning is useful to someone else. Take the ideas, not the settings.*

---

One minute continuous glucose monitors have arrived, and with them an assumption so obvious that nobody bothers to state it. More data must be better. Five times as many readings, five times as much to go on, so of course the loop should do a better job.

I've spent a fair while trying to find that improvement, and I can't. Not in the sense of having looked casually and shrugged, but in the sense of having gone at it with a variogram and an out-of-sample forecast comparison across a couple of months of real one minute data, and come away with nothing I'd be willing to defend. What I want to do here is show you why, explain the one place where the faster feed does seem to buy something, and describe the experiment I'm about to run to settle it, because I've reached the limit of what I can learn from data I already have.

## What the extra readings actually contain

Here is six hours of my own glucose from a one minute sensor. The line is every reading. The dots are the same day sampled every five minutes, which is what an ordinary loop sees.

![Six hours of a real one minute sensor with the five minute samples marked](fig1_trace.png)

That figure is the problem in one picture. The dots sit on the line. There's no hidden structure between them, no signal the five minute view is missing, nothing that would change your mind about what's happening. That includes the interesting parts, the climb from 100 to 250 and the fall off the top, where you'd most expect the extra readings to earn their keep.

The reason is physiological rather than technical. Interstitial fluid lags blood by something like four minutes, and that lag acts as a filter. By the time glucose reaches the sensor it has already been smoothed, and sampling the smoothed version more often doesn't recover what the smoothing removed. When I compared a real five minute era against a real one minute era properly, the two differed by a single scale factor, flat across every lag from five minutes out to two hours. Not a different shape. Not more detail at short range. One number.

I also checked whether the extra readings help prediction, since that's where you'd expect a benefit if there were one. Forecast lift at thirty minutes went from 9.14 to 9.18, which is nothing. More awkwardly, rate of change came out slightly worse at one minute than at five, because a shorter baseline makes the estimate noisier and noise is exactly what you're then differentiating.

## The one place it does earn its keep

If the shape is the same and prediction gains nothing, is there anything at all? Yes, and it's worth being precise, because it isn't more information. It's less waiting.

On a five minute grid a reading arrives and then nothing happens for five minutes. If glucose starts dropping thirty seconds after a reading, your loop finds out four and a half minutes later. The one minute feed doesn't tell you anything the five minute feed wouldn't have told you eventually. It tells you sooner.

![How far behind the five minute view is, by how fast glucose is moving](fig2_latency.png)

The left panel is the argument in one chart. When glucose is drifting at under 2 mg/dL per five minutes, which is most of the time, the five minute view is exactly right and the extra readings tell you nothing whatsoever. When it's moving faster than 25 mg/dL per five minutes, the held five minute value is typically 13 mg/dL adrift, and on the bad tail of that distribution nearly 60. The right panel shows how the day splits overall: across the whole six hours the median difference is 1 mg/dL.

That's a narrow but real benefit, and it points somewhere specific. It isn't going to improve your average day, because your average day is the flat bit where nothing is gained. It might help during a fast fall, where two minutes is a meaningful share of the time you have to react, and where being two minutes late is the difference between easing off in time and not.

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

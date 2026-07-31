// Cadence replay harness for the Boost V6 dosing engine.
//
// Drives the REAL DetermineBasalBoostV5.decide() on the JVM, twice over the same glucose
// record: once with the loop firing every minute on the one-minute series, and once every
// five minutes on the same series decimated to five. Everything else is held identical at
// the same wall-clock instant, so any difference in the dose recommendation is attributable
// to the sampling rate passing through the engine's arithmetic.
//
// The delta windows below mirror DeltaCalculator.kt: each candidate is change/minutesAgo*5,
// so a rate per five minutes, averaged over 2.5 to 7.5 minutes for delta, 2.5 to 17.5 for
// shortAvgDelta and 17.5 to 42.5 for longAvgDelta.
//
// NOT a closed loop. IOB comes from the recorded trajectory rather than from the doses this
// harness recommends, because without a glucodynamic model the glucose could not respond to
// a changed dose. This answers what the engine RECOMMENDS given the same state, not what
// would have happened.

import app.aaps.plugins.aps.openAPSBoostV5.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class Sample(val ts: Long, val bg: Double)

fun avgOrZero(v: List<Double>): Double = if (v.isEmpty()) 0.0 else v.sum() / v.size

/** Faithful reimplementation of DeltaCalculator's window arithmetic. */
fun deltas(hist: List<Sample>, nowIdx: Int): Triple<Double, Double, Double> {
    val now = hist[nowIdx]
    val last = mutableListOf<Double>(); val short = mutableListOf<Double>(); val long = mutableListOf<Double>()
    var i = nowIdx - 1
    while (i >= 0) {
        val minutesAgo = (now.ts - hist[i].ts) / 60000.0
        if (minutesAgo > 42.5) break
        val avgDel = (now.bg - hist[i].bg) / minutesAgo * 5.0
        if (minutesAgo in 2.5..7.5) last.add(avgDel)
        if (minutesAgo in 2.5..17.5) short.add(avgDel)
        if (minutesAgo in 17.5..42.5) long.add(avgDel)
        i--
    }
    val shortAvg = avgOrZero(short)
    val delta = if (last.isEmpty()) shortAvg else avgOrZero(last)
    return Triple(delta, shortAvg, avgOrZero(long))
}

fun main(args: Array<String>) {
    val inPath = args[0]; val outPath = args[1]; val stride = args[2].toInt()
    val isf = args.getOrNull(3)?.toDouble() ?: 137.0
    val target = args.getOrNull(4)?.toDouble() ?: 100.0
    val maxIob = args.getOrNull(5)?.toDouble() ?: 7.0

    val rows = File(inPath).readLines().drop(1).filter { it.isNotBlank() }
    val ts = ArrayList<Long>(rows.size); val bg = ArrayList<Double>(rows.size)
    val iob = ArrayList<Double>(rows.size)
    for (r in rows) {
        val p = r.split(",")
        ts.add(p[0].toDouble().toLong()); bg.add(p[1].toDouble()); iob.add(p[2].toDouble())
    }
    // Decimate to the arm's cadence. stride = 1 keeps every minute, 5 keeps every fifth.
    val idx = (ts.indices step stride).toList()
    val hist = idx.map { Sample(ts[it], bg[it]) }

    val engine = DetermineBasalBoostV5()
    var state = V5PersistedState()
    val out = StringBuilder("ts,bg,delta,shortAvgDelta,deltaAccl,state,age,score,budget," +
        "actionMult,velocityFactor,insulinToDeliver,finalDose,primerBolusU,baseInsulinReq,iob,eventualBg\n")

    for (k in hist.indices) {
        if (k < 45 / stride) continue                       // need 42.5 min of history
        val (d, sAvg, lAvg) = deltas(hist, k)
        val deltaAccl = 100.0 * (d - sAvg) / max(abs(sAvg), 2.0)
        val nowTs = hist[k].ts
        val curBg = hist[k].bg
        val curIob = iob[idx[k]]
        val cumRise30 = sAvg * 6.0
        // Identical in both arms: a projection 30 minutes forward less the remaining IOB effect.
        val eventualBg = curBg + cumRise30 - curIob * isf
        val baseInsulinReq = max(0.0, (eventualBg - target) / isf)
        val recentLow = (max(0, k - (45 / stride))..k).minOf { hist[it].bg }
        val maxDelta = (max(0, k - (30 / stride))..k).maxOf { hist[it].bg } - curBg
        val hour = ((nowTs / 3600000) % 24).toInt()

        val inputs = V5Inputs(
            delta = d, shortAvgDelta = sAvg, deltaAccl = deltaAccl, bg = curBg,
            eventualBg = eventualBg, targetBg = target, maxDelta = maxDelta,
            minGuardBg = recentLow, minGuardThreshold = 70.0,
            deltaHistory = listOf(lAvg, sAvg, d),
            iob = curIob, maxIob = maxIob, baseInsulinReq = baseInsulinReq,
            roundSmbTo = 0.05, enableSmbPreChecks = true,
            mlHypoRisk = null, mlMealLikely = null,
            recentLowBg = recentLow, cumulativeRise30min = cumRise30, hour = hour,
            exerciseActive = false, inPostExerciseWindow = false,
            nowMs = nowTs,
        )
        val dec = engine.decide(inputs, state)
        state = dec.newPersistedState
        out.append(nowTs).append(',').append(curBg).append(',')
            .append(fmt(d)).append(',').append(fmt(sAvg)).append(',').append(fmt(deltaAccl)).append(',')
            .append(dec.mealHypothesis.name).append(',').append(dec.mealHypothesisAge).append(',')
            .append(fmt(dec.score)).append(',').append(fmt(dec.aggressionBudget.budget)).append(',')
            .append(fmt(dec.actionMultiplier)).append(',').append(fmt(dec.velocityFactor)).append(',')
            .append(fmt(dec.insulinToDeliver)).append(',').append(fmt(dec.finalDose)).append(',')
            .append(fmt(dec.primerBolusU)).append(',').append(fmt(baseInsulinReq)).append(',')
            .append(fmt(curIob)).append(',').append(fmt(eventualBg)).append('\n')
    }
    File(outPath).writeText(out.toString())
    println("wrote $outPath  cycles=${hist.size - 45 / stride}  stride=$stride")
}

fun fmt(x: Double): String = ((x * 10000).roundToInt() / 10000.0).toString()

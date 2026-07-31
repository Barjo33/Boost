// Cadence replay for the oref SMB algorithm.
//
// Drives the shipped DetermineBasalSMB.determine_basal() over the same glucose record at two
// cadences. oref differs from the Boost engine in a way that matters here: most of its dose is
// a temp basal RATE, which is expressed per hour and is therefore cadence-neutral by
// construction, while only the microbolus is a per-cycle amount. The comparison shows how much
// of each survives a change of sampling rate.

import app.aaps.core.interfaces.aps.*
import app.aaps.core.interfaces.profile.PlainProfileUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun prof(isf: Double, target: Double, basal: Double, maxIob: Double, smbInterval: Int) = OapsProfile(
    dia = 6.0, min_5m_carbimpact = 8.0, max_iob = maxIob, max_daily_basal = basal,
    max_basal = basal*4, min_bg = target, max_bg = target, target_bg = target,
    carb_ratio = 10.0, sens = isf, autosens_adjust_targets = false,
    max_daily_safety_multiplier = 3.0, current_basal_safety_multiplier = 4.0,
    high_temptarget_raises_sensitivity = false, low_temptarget_lowers_sensitivity = false,
    sensitivity_raises_target = false, resistance_lowers_target = false,
    adv_target_adjustments = false, exercise_mode = false, half_basal_exercise_target = 160,
    maxCOB = 120, skip_neutral_temps = false, remainingCarbsCap = 90, enableUAM = true,
    A52_risk_enable = false, SMBInterval = smbInterval, enableSMB_with_COB = true,
    enableSMB_with_temptarget = true, allowSMB_with_high_temptarget = false,
    enableSMB_always = true, enableSMB_after_carbs = true, maxSMBBasalMinutes = 60,
    maxUAMSMBBasalMinutes = 30, bolus_increment = 0.05, carbsReqThreshold = 8,
    current_basal = basal, temptargetSet = false, autosens_max = 1.2, out_units = "mg/dl",
    lgsThreshold = null, variable_sens = isf, insulinDivisor = 75, TDD = basal*24*2
)

fun iobArr(iob: Double, activity: Double, basalIob: Double, now: Long,
           lastBolus: Long): Array<IobTotal> {
    // oref requires iobWithZeroTemp, its projection of IOB if a zero temp basal were set. With
    // no temp running at the moment of the call the two coincide, so the same figures are used.
    val zt = IobTotal(now).also {
        it.iob = iob; it.activity = activity; it.basaliob = basalIob
        it.bolussnooze = 0.0; it.netbasalinsulin = basalIob
    }
    val one = IobTotal(now).also {
        it.iob = iob; it.activity = activity; it.basaliob = basalIob
        it.bolussnooze = 0.0; it.netbasalinsulin = basalIob; it.iobWithZeroTemp = zt
        // oref reads the microbolus interval gate from iob_data.lastBolusTime, not from
        // meal_data. Setting the wrong one leaves SMBInterval permanently unbinding.
        it.lastBolusTime = lastBolus
    }
    return Array(1) { one }
}

fun main(args: Array<String>) {
    val inPath = args[0]; val outPath = args[1]; val stride = args[2].toInt()
    val isf = args.getOrNull(3)?.toDouble() ?: 137.0
    val target = args.getOrNull(4)?.toDouble() ?: 100.0
    val basal = args.getOrNull(5)?.toDouble() ?: 0.6
    val maxIob = args.getOrNull(6)?.toDouble() ?: 7.0
    val smbInterval = args.getOrNull(7)?.toInt() ?: 3

    val rows = File(inPath).readLines().drop(1).filter { it.isNotBlank() }
    val ts = ArrayList<Long>(); val bg = ArrayList<Double>()
    val iob = ArrayList<Double>(); val act = ArrayList<Double>()
    for (r in rows) {
        val p = r.split(",")
        ts.add(p[0].toDouble().toLong()); bg.add(p[1].toDouble()); iob.add(p[2].toDouble())
        act.add(if (p.size > 3) p[3].toDouble() else 0.0)
    }
    val idx = (ts.indices step stride).toList()

    val engine = DetermineBasalSMB(PlainProfileUtil(), FabricPrivacy())
    val profile = prof(isf, target, basal, maxIob, smbInterval)
    val out = StringBuilder("ts,bg,delta,shortAvgDelta,units,rate,duration,eventualBG,reason_smb\n")
    var lastSmbMs = 0L

    for (k in idx.indices) {
        if (k < 45 / stride) continue
        val i = idx[k]
        val now = ts[i]
        // DeltaCalculator windows on this arm's own cadence
        val last = ArrayList<Double>(); val short = ArrayList<Double>(); val lng = ArrayList<Double>()
        var j = k - 1
        while (j >= 0) {
            val mago = (now - ts[idx[j]]) / 60000.0
            if (mago > 42.5) break
            val av = (bg[i] - bg[idx[j]]) / mago * 5.0
            if (mago in 2.5..7.5) last.add(av)
            if (mago in 2.5..17.5) short.add(av)
            if (mago in 17.5..42.5) lng.add(av)
            j--
        }
        val sAvg = if (short.isEmpty()) 0.0 else short.average()
        val d = if (last.isEmpty()) sAvg else last.average()
        val lAvg = if (lng.isEmpty()) 0.0 else lng.average()
        val gs = GlucoseStatusSMB(glucose = bg[i], noise = 0.0, delta = d, shortAvgDelta = sAvg,
                               longAvgDelta = lAvg, date = now)
        val ct = CurrentTemp(duration = 0, rate = basal, minutesrunning = 0)
        val md = MealData(carbs = 0.0, mealCOB = 0.0, slopeFromMaxDeviation = 0.0,
                          slopeFromMinDeviation = 0.0, lastBolusTime = lastSmbMs,
                          lastCarbTime = 0L)
        val autos = AutosensResult(ratio = 1.0)
        val rt = engine.determine_basal(gs, ct, iobArr(iob[i], act[i], 0.0, now, lastSmbMs), profile, autos, md,
                                        microBolusAllowed = true, currentTime = now,
                                        flatBGsDetected = false, dynIsfMode = false)
        val units = rt.units ?: 0.0
        if (units > 0) lastSmbMs = now
        out.append(now).append(',').append(bg[i]).append(',').append(r4(d)).append(',')
            .append(r4(sAvg)).append(',').append(r4(units)).append(',')
            .append(r4(rt.rate ?: -1.0)).append(',').append(rt.duration ?: -1).append(',')
            .append(r4(rt.eventualBG ?: 0.0)).append(',')
            .append((rt.reason.toString().take(60)).replace(",", ";")).append('\n')
    }
    File(outPath).writeText(out.toString())
    println("wrote $outPath  cycles=${idx.size - 45/stride}  stride=$stride  SMBInterval=$smbInterval")
}
fun r4(x: Double) = ((x*10000).roundToInt()/10000.0).toString()

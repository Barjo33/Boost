// Per-cycle server around the real Boost V6 dosing engine.
//
// Reads one JSON object per line on stdin, runs DetermineBasalBoostV5.decide(), and writes one
// JSON object per line on stdout. Persisted state is carried between calls, so a simulator can
// drive the genuine engine inside a closed loop without any of it being reimplemented in
// Python. Send {"reset":true} to clear state between runs.
//
// Deliberately dependency-free: a hand-rolled reader for the flat numeric objects we send, so
// the harness needs no JSON library on the classpath.

import app.aaps.plugins.aps.openAPSBoostV5.*
import kotlin.math.roundToInt

fun parse(line: String): Map<String, String> {
    val m = HashMap<String, String>()
    var i = 0
    while (i < line.length) {
        val ks = line.indexOf('"', i); if (ks < 0) break
        val ke = line.indexOf('"', ks + 1); if (ke < 0) break
        val key = line.substring(ks + 1, ke)
        val colon = line.indexOf(':', ke); if (colon < 0) break
        var j = colon + 1
        while (j < line.length && line[j] == ' ') j++
        val start = j
        if (j < line.length && line[j] == '"') {
            j++
            while (j < line.length && line[j] != '"') j++
            m[key] = line.substring(start + 1, j); j++
        } else {
            while (j < line.length && line[j] != ',' && line[j] != '}') j++
            m[key] = line.substring(start, j).trim()
        }
        i = j + 1
    }
    return m
}

fun d(m: Map<String, String>, k: String, dflt: Double = 0.0) = m[k]?.toDoubleOrNull() ?: dflt
fun b(m: Map<String, String>, k: String, dflt: Boolean = false) =
    m[k]?.let { it == "true" || it == "1" } ?: dflt
fun r4(x: Double) = (x * 10000).roundToInt() / 10000.0

fun main() {
    val engine = DetermineBasalBoostV5()
    var state = V5PersistedState()
    val out = java.io.PrintWriter(java.io.BufferedWriter(java.io.OutputStreamWriter(System.out)), true)
    generateSequence(::readLine).forEach { line ->
        if (line.isBlank()) return@forEach
        val m = parse(line)
        if (b(m, "reset")) { state = V5PersistedState(); out.println("{\"ok\":true}"); return@forEach }
        val hist = (m["deltaHistory"] ?: "").split(";").mapNotNull { it.toDoubleOrNull() }
        val inputs = V5Inputs(
            delta = d(m, "delta"), shortAvgDelta = d(m, "shortAvgDelta"),
            deltaAccl = d(m, "deltaAccl"), bg = d(m, "bg"),
            eventualBg = d(m, "eventualBg"), targetBg = d(m, "targetBg", 100.0),
            maxDelta = d(m, "maxDelta"), minGuardBg = d(m, "minGuardBg", d(m, "bg")),
            minGuardThreshold = d(m, "minGuardThreshold", 70.0),
            deltaHistory = if (hist.size >= 3) hist else listOf(0.0, d(m, "shortAvgDelta"), d(m, "delta")),
            iob = d(m, "iob"), maxIob = d(m, "maxIob", 7.0),
            baseInsulinReq = d(m, "baseInsulinReq"), roundSmbTo = d(m, "roundSmbTo", 0.05),
            enableSmbPreChecks = b(m, "enableSmbPreChecks", true),
            mlHypoRisk = m["mlHypoRisk"]?.toDoubleOrNull(),
            mlMealLikely = m["mlMealLikely"]?.toDoubleOrNull(),
            recentLowBg = d(m, "recentLowBg", d(m, "bg")),
            cumulativeRise30min = d(m, "cumulativeRise30min"),
            hour = d(m, "hour").toInt(),
            exerciseActive = b(m, "exerciseActive"),
            inPostExerciseWindow = b(m, "inPostExerciseWindow"),
            asleep = b(m, "asleep"),
            fastCarbConfirmEnabled = b(m, "fastCarbConfirmEnabled"),
            aggressiveEarlyConfirmEnabled = b(m, "aggressiveEarlyConfirmEnabled"),
            composedFloorActive = b(m, "composedFloorActive"),
            velocityBudgetActive = b(m, "velocityBudgetActive"),
            confirmedCapU = d(m, "confirmedCapU", MAX_CONFIRMED_COMMIT_DOSE_U),
            committedCapU = d(m, "committedCapU", MAX_COMMITTED_DOSE_U),
            nowMs = (m["nowMs"]?.toDoubleOrNull() ?: 0.0).toLong(),
        )
        val dec = engine.decide(inputs, state)
        state = dec.newPersistedState
        out.println(
            "{\"finalDose\":${r4(dec.finalDose)}," +
            "\"insulinToDeliver\":${r4(dec.insulinToDeliver)}," +
            "\"primerBolusU\":${r4(dec.primerBolusU)}," +
            "\"state\":\"${dec.mealHypothesis.name}\"," +
            "\"age\":${dec.mealHypothesisAge}," +
            "\"score\":${r4(dec.score)}," +
            "\"budget\":${r4(dec.aggressionBudget.budget)}," +
            "\"actionMultiplier\":${r4(dec.actionMultiplier)}," +
            "\"velocityFactor\":${r4(dec.velocityFactor)}}"
        )
    }
}

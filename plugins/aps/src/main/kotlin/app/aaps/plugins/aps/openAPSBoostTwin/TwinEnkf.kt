package app.aaps.plugins.aps.openAPSBoostTwin

import java.util.Random
import kotlin.math.max
import kotlin.math.sqrt

/**
 * KAIROS Twin — Ensemble Kalman Filter (2026-07-18). Faithful port of the validated `twin_model.py`
 * EnKF. Assimilates the CGM stream + the known insulin stream into a posterior over the physiological
 * state, and forecasts a calibrated distribution of future CGM. Scalar measurement (CGM = Gi), so the
 * Kalman gain is a simple ratio — no matrix inversion. SHADOW: produces a forecast only; doses nothing.
 *
 * Seeded RNG ⇒ deterministic given the seed (unit-testable). In the live shadow path the ensemble is
 * held in memory across cycles and re-converges within ~30 min after a restart (fail-safe).
 */
class TwinEnkf(
    private val p: TwinParams = TwinParams(),
    private val members: Int = 150,
    seed: Long = 1L,
) {
    private val rng = Random(seed)
    // process-noise sd per state per 5-min step; Ra is LARGE so the filter discovers meals from CGM.
    private val qsd = doubleArrayOf(0.02, 0.02, 1e-4, 0.55, 2.0, 0.6)
    // forecast-only process noise: inject Ra/G uncertainty so the band honestly reflects unseen meals.
    private val qf = doubleArrayOf(0.0, 0.0, 0.0, 0.95, 2.2, 0.0)
    private val rSd = 6.0

    /** Ensemble: [members] state vectors of length [TW_N]. */
    private val ens = Array(members) { DoubleArray(TW_N) }
    var initialised = false
        private set

    /** Seed the ensemble near a plausible fasting state around [g0] mg/dL. */
    fun init(g0: Double) {
        for (m in 0 until members) {
            ens[m][TW_G] = g0 + rng.nextGaussian() * 8.0
            ens[m][TW_GI] = g0 + rng.nextGaussian() * 8.0
            ens[m][TW_RA] = rng.nextGaussian() * 2.0
        }
        initialised = true
    }

    /** Predict one 5-min step under insulin [u5] (U), adding process noise. */
    fun predict(u5: Double) {
        for (m in 0 until members) {
            val x = twinStep5(ens[m], u5, p)
            for (i in 0 until TW_N) x[i] += qsd[i] * rng.nextGaussian()
            x[TW_X] = max(x[TW_X], 0.0); x[TW_G] = max(x[TW_G], 10.0); x[TW_GI] = max(x[TW_GI], 10.0)
            ens[m] = x
        }
    }

    /** Assimilate a CGM reading (mg/dL). Scalar EnKF update on Gi. */
    fun update(cgm: Double) {
        val gim = ens.sumOf { it[TW_GI] } / members
        val xm = DoubleArray(TW_N) { i -> ens.sumOf { it[i] } / members }
        val pxy = DoubleArray(TW_N)
        var pyy = 0.0
        for (m in 0 until members) {
            val dgi = ens[m][TW_GI] - gim
            for (i in 0 until TW_N) pxy[i] += (ens[m][i] - xm[i]) * dgi
            pyy += dgi * dgi
        }
        for (i in 0 until TW_N) pxy[i] /= members
        pyy = pyy / members + rSd * rSd
        val k = DoubleArray(TW_N) { pxy[it] / pyy }
        for (m in 0 until members) {
            val innov = cgm + rng.nextGaussian() * rSd - ens[m][TW_GI]
            for (i in 0 until TW_N) ens[m][i] += k[i] * innov
            ens[m][TW_X] = max(ens[m][TW_X], 0.0); ens[m][TW_G] = max(ens[m][TW_G], 10.0); ens[m][TW_GI] = max(ens[m][TW_GI], 10.0)
        }
    }

    /** Posterior mean state. */
    fun meanState(): DoubleArray = DoubleArray(TW_N) { i -> ens.sumOf { it[i] } / members }

    /**
     * Forecast CGM (Gi) [hSteps] 5-min steps ahead, assuming [uPerStep] U of insulin each future step
     * (open-loop; the caller supplies the expected basal). Injects forecast process noise so the band
     * reflects unseen future meals. Returns (mean, p5, p95) mg/dL — a calibrated forecast interval.
     */
    fun forecast(hSteps: Int, uPerStep: Double): Triple<Double, Double, Double> {
        val gi = DoubleArray(members)
        for (m in 0 until members) {
            var x = ens[m].copyOf()
            repeat(hSteps) {
                x = twinStep5(x, uPerStep, p)
                for (i in 0 until TW_N) x[i] += qf[i] * rng.nextGaussian()
                x[TW_G] = max(x[TW_G], 10.0); x[TW_GI] = max(x[TW_GI], 10.0)
            }
            gi[m] = x[TW_GI]
        }
        gi.sort()
        val mean = gi.average()
        return Triple(mean, percentile(gi, 5.0), percentile(gi, 95.0))
    }

    private fun percentile(sorted: DoubleArray, pct: Double): Double {
        val idx = ((pct / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    /** Ensemble spread (sd) of a state — for diagnostics/calibration. */
    fun spread(i: Int): Double {
        val m = ens.sumOf { it[i] } / members
        return sqrt(ens.sumOf { (it[i] - m) * (it[i] - m) } / members)
    }
}

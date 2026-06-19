package app.aaps.plugins.aps.openAPSBoost

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log

/**
 * Step counting service for Boost activity detection.
 *
 * Tracks steps in 5-minute buckets and provides aggregated counts
 * for 5, 10, 15, 30, and 60 minute windows. Used by OpenAPSBoostPlugin
 * to detect activity/inactivity and adjust profile percentage and targets.
 *
 * Must be registered as a SensorEventListener for Sensor.TYPE_STEP_COUNTER
 * in the application's main activity or service.
 */
object StepService : SensorEventListener {

    private const val TAG = "StepService"
    private var previousStepCount = -1
    private val stepsMap = LinkedHashMap<Long, Int>()
    private const val FIVE_MINUTES_IN_MS = 300000
    private const val NUM_OF_5MIN_BLOCKS_TO_KEEP = 20

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
    }

    private fun currentTimeIn5Min(): Long {
        return System.currentTimeMillis() / FIVE_MINUTES_IN_MS
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        sensorEvent ?: return

        val now = currentTimeIn5Min()
        val stepCount = sensorEvent.values[0].toInt()
        if (previousStepCount >= 0) {
            var recentStepCount = stepCount - previousStepCount
            if (stepsMap.contains(now)) {
                recentStepCount += stepsMap.getValue(now)
            }
            stepsMap[now] = recentStepCount
        }
        previousStepCount = stepCount

        if (stepsMap.size > NUM_OF_5MIN_BLOCKS_TO_KEEP) {
            val removeBefore = now - NUM_OF_5MIN_BLOCKS_TO_KEEP
            stepsMap.entries.removeIf { it.key < removeBefore }
        }
    }

    fun getRecentStepCount5Min(): Int {
        val now = currentTimeIn5Min() - 1
        return if (stepsMap.contains(now)) stepsMap.getValue(now) else 0
    }

    fun getRecentStepCount10Min(): Int = getStepsInLastXMin(3)

    fun getRecentStepCount15Min(): Int = getStepsInLastXMin(4)

    fun getRecentStepCount30Min(): Int {
        return getStepsInLastXMin(6)
    }

    fun getRecentStepCount60Min(): Int {
        return getStepsInLastXMin(12)
    }

    private fun getStepsInLastXMin(numberOf5MinIncrements: Int): Int {
        var stepCount = 0
        val now = currentTimeIn5Min()
        val cutoff = now - numberOf5MinIncrements
        for (entry in stepsMap.entries) {
            if (entry.key > cutoff && entry.key < now) {
                stepCount += entry.value
            }
        }
        return stepCount
    }

    // ── Cumulative steps-since-local-midnight (2026-06-19, intraday activity-load shadow) ──
    // The hardware TYPE_STEP_COUNTER value (previousStepCount) is cumulative-since-boot. Today's
    // steps = current − a baseline captured at local midnight. Zero extra cost: the sensor is
    // already running; getStepsToday() is one subtraction off in-memory state, called once/cycle.
    @Volatile private var dayKey = -1L
    @Volatile private var midnightRaw = -1

    /** Steps since local midnight from the phone pedometer. Re-baselines on day rollover / reboot. */
    @Synchronized fun getStepsToday(localOffsetMs: Long): Int {
        val cur = previousStepCount
        if (cur < 0) return 0
        val day = (System.currentTimeMillis() + localOffsetMs) / 86_400_000L
        if (day != dayKey || midnightRaw < 0 || cur < midnightRaw) {  // new local day, uninit, or reboot
            midnightRaw = cur; dayKey = day
        }
        return (cur - midnightRaw).coerceAtLeast(0)
    }

    /**
     * One-time seed so a mid-day app start counts today's earlier steps: sets the midnight baseline
     * so getStepsToday() returns [hcTodaySteps] now, then the live sensor carries on from there.
     * Caller invokes this once (after the first Health Connect sync). No-op until a sensor reading
     * exists. A real midnight rollover later re-zeros without needing HC again.
     */
    @Synchronized fun seedTodayFromHc(hcTodaySteps: Int, localOffsetMs: Long) {
        val cur = previousStepCount
        if (cur < 0) return
        midnightRaw = (cur - hcTodaySteps).coerceIn(0, cur)
        dayKey = (System.currentTimeMillis() + localOffsetMs) / 86_400_000L
    }
}

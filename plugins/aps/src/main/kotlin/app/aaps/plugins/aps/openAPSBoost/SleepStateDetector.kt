package app.aaps.plugins.aps.openAPSBoost

import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import org.json.JSONObject

/**
 * SleepStateDetector — HR + step + clock-driven sleep state estimator for Boost night mode.
 *
 * Three-state machine: AWAKE → PRE_SLEEP → SLEEPING → AWAKE.
 *
 * Design (2026-06-02):
 *
 * Enter PRE_SLEEP (from AWAKE) when:
 *   - clock-of-day ∈ [nightStart − preSleepLeadMin, nightStart)
 *
 *   PRE_SLEEP is a *proactive* state: it engages Boost night-mode SMB suppression BEFORE
 *   the user falls asleep, so they're not carrying excess IOB into the night. No HR or
 *   step gating — this is a time-only pre-warm window.
 *
 * Enter SLEEPING (from PRE_SLEEP or AWAKE if already in night window) when ALL hold for
 * ≥ minSleepHysteresisMin:
 *   - avgHr ≤ hrRest × 1.15           (HR within ~15% of resting)
 *   - stepsLast15Min < 50             (no active walking)
 *   - clock-of-day ∈ [outerStart, outerEnd]   (broad outer night window)
 *   - mlMealLikely < 0.30 or null     (not about to eat)
 *
 * Exit SLEEPING (to AWAKE) requires BOTH simultaneously (per 2026-06-02 user spec — BG
 * trending up alone is NOT sufficient; REM sleep can drive HR rises without wakefulness):
 *   - avgHr > hrRest × 1.25 sustained ≥ wakeHrHysteresisMin
 *   - stepsLast15Min ≥ 100            (sustained physical movement)
 *   OR
 *   - clock-of-day exits outerEnd     (hard morning exit — fallback)
 *
 * PRE_SLEEP → AWAKE when clock-of-day exits nightEnd (morning).
 *
 * Failsafes:
 *   - If avgHr is null (no HR data in window), the detector cannot CONFIRM sleep; state
 *     stays AWAKE (or PRE_SLEEP if in time window). Callers should fall back to legacy
 *     time-based night mode.
 *   - State machine is monotonic per-cycle; hysteresis counters carried across calls
 *     via the caller's persistent state struct (see [State]).
 */
object SleepStateDetector {

    /**
     * Discrete sleep state. Persisted across plugin restarts via [serialize]/[deserialize].
     */
    enum class SleepState {
        AWAKE,        // Default state; no sleep behaviour applied
        PRE_SLEEP,    // Within pre-sleep lead window; night-mode SMB rules apply
        SLEEPING      // Sleep confirmed by HR + steps + time + meal-likely gate
    }

    /**
     * Carrier for hysteresis counters and state. Caller stores one instance across cycles
     * and passes it back to [evaluate]. Persist via [serialize] across app restarts.
     *
     * @param state                  Current state
     * @param sleepCandidateSinceMs  When the current PRE_SLEEP→SLEEPING qualification window started
     *                               (null = no current candidacy in progress)
     * @param wakeCandidateSinceMs   When the current SLEEPING→AWAKE qualification window started
     *                               (null = no current wake-candidacy)
     * @param enteredAtMs            When the current state was entered (for telemetry)
     */
    data class State(
        var state: SleepState = SleepState.AWAKE,
        var sleepCandidateSinceMs: Long? = null,
        var wakeCandidateSinceMs: Long? = null,
        var enteredAtMs: Long = 0L
    ) {
        fun serialize(): String =
            JSONObject()
                .put("state", state.name)
                .put("sleepCandidateSinceMs", sleepCandidateSinceMs ?: JSONObject.NULL)
                .put("wakeCandidateSinceMs", wakeCandidateSinceMs ?: JSONObject.NULL)
                .put("enteredAtMs", enteredAtMs)
                .toString()

        companion object {
            fun deserialize(raw: String): State {
                if (raw.isBlank()) return State()
                return try {
                    val j = JSONObject(raw)
                    State(
                        state = SleepState.valueOf(j.optString("state", "AWAKE")),
                        sleepCandidateSinceMs = j.optLong("sleepCandidateSinceMs", -1L).takeIf { it > 0 },
                        wakeCandidateSinceMs = j.optLong("wakeCandidateSinceMs", -1L).takeIf { it > 0 },
                        enteredAtMs = j.optLong("enteredAtMs", 0L)
                    )
                } catch (e: Exception) {
                    State()
                }
            }
        }
    }

    /**
     * Result of one evaluation cycle. The returned [newState] should be persisted by the
     * caller and passed back next cycle.
     */
    data class Result(
        val newState: State,
        val transitioned: Boolean,
        val debug: String
    )

    /**
     * Inputs from the host plugin. All time-of-day values are minutes-since-midnight (0–1439).
     *
     * @param nowMs                 Current system time in ms (UTC epoch)
     * @param minuteOfDay           Minute-of-day in local time (0..1439)
     * @param hrReadings            Recent HR records (caller fetches; detector filters by window)
     * @param hrWindowMinutes       Minutes of HR history to average for state evaluation (default 5)
     * @param hrResting             User's resting HR (from ApsBoostHrRestingBpm)
     * @param stepsLast15Min        Steps in last 15 min (from StepService)
     * @param mlMealLikely          Optional meal-likelihood score (null if model unavailable)
     * @param nightStartMin         Minute-of-day for night-mode start (e.g. 22:00 → 1320)
     * @param nightEndMin           Minute-of-day for night-mode end (e.g. 07:00 → 420)
     * @param preSleepLeadMin       How early before nightStart to enter PRE_SLEEP (default 60)
     * @param minSleepHysteresisMin Minutes the sleep conditions must hold before SLEEPING (default 10)
     * @param wakeHrHysteresisMin   Minutes HR > 1.25× resting must hold before AWAKE (default 5)
     */
    data class Inputs(
        val nowMs: Long,
        val minuteOfDay: Int,
        val hrReadings: List<HR>,
        val hrWindowMinutes: Int = 5,
        val hrResting: Int,
        val stepsLast15Min: Int,
        val mlMealLikely: Double?,
        val nightStartMin: Int,
        val nightEndMin: Int,
        val preSleepLeadMin: Int = 60,
        val minSleepHysteresisMin: Int = 10,
        val wakeHrHysteresisMin: Int = 5
    )

    /**
     * Evaluate the state machine for the current cycle. Pure function over [prev] and [inputs].
     * Caller persists [Result.newState] and passes it as [prev] next cycle.
     */
    fun evaluate(prev: State, inputs: Inputs, aapsLogger: AAPSLogger? = null): Result {
        val debug = StringBuilder()
        val avgHr = averageHr(inputs.hrReadings, inputs.nowMs, inputs.hrWindowMinutes)
        val sleepCap = inputs.hrResting * 1.15
        val wakeFloor = inputs.hrResting * 1.25
        val inOuterWindow = minuteInWrappedRange(inputs.minuteOfDay, inputs.nightStartMin, inputs.nightEndMin)
        val preSleepStart = (inputs.nightStartMin - inputs.preSleepLeadMin + 1440) % 1440
        val inPreSleep = minuteInWrappedRange(inputs.minuteOfDay, preSleepStart, inputs.nightStartMin)

        debug.append("avgHr=${avgHr?.let { String.format("%.1f", it) } ?: "null"}")
            .append(" sleepCap=${String.format("%.1f", sleepCap)}")
            .append(" wakeFloor=${String.format("%.1f", wakeFloor)}")
            .append(" steps15=${inputs.stepsLast15Min}")
            .append(" mealLikely=${inputs.mlMealLikely?.let { String.format("%.2f", it) } ?: "null"}")
            .append(" minOfDay=${inputs.minuteOfDay}")
            .append(" inOuter=$inOuterWindow inPreSleep=$inPreSleep")

        var newState = prev.copy()
        var transitioned = false

        when (prev.state) {
            SleepState.AWAKE -> {
                // Sleep candidacy check — possible even from AWAKE when we're inside outer window
                if (inOuterWindow && qualifiesAsSleepCandidate(avgHr, sleepCap, inputs.stepsLast15Min, inputs.mlMealLikely)) {
                    if (newState.sleepCandidateSinceMs == null) {
                        newState.sleepCandidateSinceMs = inputs.nowMs
                        debug.append(" | sleep-candidate-started")
                    } else {
                        val heldMin = ((inputs.nowMs - newState.sleepCandidateSinceMs!!) / 60_000L).toInt()
                        if (heldMin >= inputs.minSleepHysteresisMin) {
                            newState = State(state = SleepState.SLEEPING, enteredAtMs = inputs.nowMs)
                            transitioned = true
                            debug.append(" | →SLEEPING (held ${heldMin}m)")
                        } else {
                            debug.append(" | sleep-candidate-held=${heldMin}m")
                        }
                    }
                } else {
                    if (newState.sleepCandidateSinceMs != null) debug.append(" | sleep-candidate-reset")
                    newState.sleepCandidateSinceMs = null
                }

                if (!transitioned && inPreSleep) {
                    newState = State(state = SleepState.PRE_SLEEP, enteredAtMs = inputs.nowMs)
                    transitioned = true
                    debug.append(" | →PRE_SLEEP (time)")
                }
            }

            SleepState.PRE_SLEEP -> {
                // Exit PRE_SLEEP if we've left the outer night window (morning exit before ever sleeping)
                if (!inOuterWindow && !inPreSleep) {
                    newState = State(state = SleepState.AWAKE, enteredAtMs = inputs.nowMs)
                    transitioned = true
                    debug.append(" | →AWAKE (left window without sleeping)")
                } else if (qualifiesAsSleepCandidate(avgHr, sleepCap, inputs.stepsLast15Min, inputs.mlMealLikely)) {
                    if (newState.sleepCandidateSinceMs == null) {
                        newState.sleepCandidateSinceMs = inputs.nowMs
                        debug.append(" | sleep-candidate-started")
                    } else {
                        val heldMin = ((inputs.nowMs - newState.sleepCandidateSinceMs!!) / 60_000L).toInt()
                        if (heldMin >= inputs.minSleepHysteresisMin) {
                            newState = State(state = SleepState.SLEEPING, enteredAtMs = inputs.nowMs)
                            transitioned = true
                            debug.append(" | →SLEEPING (held ${heldMin}m)")
                        } else {
                            debug.append(" | sleep-candidate-held=${heldMin}m")
                        }
                    }
                } else {
                    if (newState.sleepCandidateSinceMs != null) debug.append(" | sleep-candidate-reset")
                    newState.sleepCandidateSinceMs = null
                }
            }

            SleepState.SLEEPING -> {
                // Hard morning exit
                if (!inOuterWindow) {
                    newState = State(state = SleepState.AWAKE, enteredAtMs = inputs.nowMs)
                    transitioned = true
                    debug.append(" | →AWAKE (outer window exit)")
                } else {
                    // Wake requires BOTH steps AND HR — per spec. BG trend alone is NOT sufficient.
                    val stepsConfirmWake = inputs.stepsLast15Min >= 100
                    val hrAboveWakeFloor = avgHr != null && avgHr > wakeFloor
                    if (stepsConfirmWake && hrAboveWakeFloor) {
                        if (newState.wakeCandidateSinceMs == null) {
                            newState.wakeCandidateSinceMs = inputs.nowMs
                            debug.append(" | wake-candidate-started")
                        } else {
                            val heldMin = ((inputs.nowMs - newState.wakeCandidateSinceMs!!) / 60_000L).toInt()
                            if (heldMin >= inputs.wakeHrHysteresisMin) {
                                newState = State(state = SleepState.AWAKE, enteredAtMs = inputs.nowMs)
                                transitioned = true
                                debug.append(" | →AWAKE (held ${heldMin}m — HR+steps confirmed)")
                            } else {
                                debug.append(" | wake-candidate-held=${heldMin}m")
                            }
                        }
                    } else {
                        if (newState.wakeCandidateSinceMs != null) debug.append(" | wake-candidate-reset")
                        newState.wakeCandidateSinceMs = null
                    }
                }
            }
        }

        aapsLogger?.debug(LTag.APS, "SleepStateDetector: ${newState.state} ${if (transitioned) "[T]" else "" } $debug")
        return Result(newState, transitioned, debug.toString())
    }

    /** Duration-weighted average HR over a window. null if no readings. */
    fun averageHr(readings: List<HR>, nowMs: Long, windowMinutes: Int): Double? {
        val cutoff = nowMs - windowMinutes * 60_000L
        val inWindow = readings.filter { it.isValid && it.timestamp in (cutoff + 1)..nowMs }
        if (inWindow.isEmpty()) return null
        val totalDur = inWindow.sumOf { it.duration.toDouble() }
        if (totalDur <= 0.0) return null
        return inWindow.sumOf { it.beatsPerMinute * it.duration } / totalDur
    }

    /**
     * Returns true if `minute` lies within the [start, end] window on a 24-hour clock,
     * handling wrap-around (e.g. start=1320 (22:00), end=420 (07:00)).
     */
    private fun minuteInWrappedRange(minute: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (end > start) minute in start until end
        else minute >= start || minute < end
    }

    private fun qualifiesAsSleepCandidate(
        avgHr: Double?,
        sleepCap: Double,
        stepsLast15Min: Int,
        mlMealLikely: Double?
    ): Boolean {
        if (avgHr == null) return false                              // no HR → can't confirm
        if (avgHr > sleepCap) return false                           // HR too high
        if (stepsLast15Min >= 50) return false                       // recent activity
        if (mlMealLikely != null && mlMealLikely >= 0.30) return false  // about to eat
        return true
    }
}

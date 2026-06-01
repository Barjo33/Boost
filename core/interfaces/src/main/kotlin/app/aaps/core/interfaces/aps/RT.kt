package app.aaps.core.interfaces.aps

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class RT(
    var algorithm: APSResult.Algorithm = APSResult.Algorithm.UNKNOWN,
    var runningDynamicIsf: Boolean,
    @Serializable(with = TimestampToIsoSerializer::class)
    var timestamp: Long? = null,
    val temp: String = "absolute",
    var bg: Double? = null,
    var tick: String? = null,
    var eventualBG: Double? = null,
    var minGuardBG: Double? = null,                  // V4.4.1's smart-selected predicted-low (COB/UAM/IOB blend) — read by V5 shadow
    var targetBG: Double? = null,
    var snoozeBG: Double? = null, // AMA only
    var insulinReq: Double? = null,
    var carbsReq: Int? = null,
    var carbsReqWithin: Int? = null,
    var units: Double? = null, // micro bolus
    @Serializable(with = TimestampToIsoSerializer::class)
    var deliverAt: Long? = null, // The time at which the micro bolus should be delivered
    var sensitivityRatio: Double? = null, // autosens ratio (fraction of normal basal)
    @Serializable(with = StringBuilderSerializer::class)
    var reason: StringBuilder = StringBuilder(),
    var duration: Int? = null,
    var rate: Double? = null,
    var predBGs: Predictions? = null,
    var COB: Double? = null,
    var IOB: Double? = null,
    var variable_sens: Double? = null,
    var isfMgdlForCarbs: Double? = null, // used to pass to AAPS client


    var consoleLog: MutableList<String>? = null,
    var consoleError: MutableList<String>? = null,

    // Boost-specific: tier dosing decision (uploaded to Nightscout)
    var boostTier: String? = null,               // Which tier was triggered (e.g. "UAM_BOOST", "PERCENT_SCALE", etc.)
    var boostActive: Boolean? = null,            // Whether Boost was in its active time window
    var fastCarbProtection: Boolean? = null,     // Whether fast-carb rebound protection suppressed UAM/Accel tiers this cycle

    // Boost-specific: DynamicISF data (uploaded to Nightscout)
    var dynamicISF: Double? = null,              // Dosing ISF (future_sens) used for insulin requirement
    var predictionISF: Double? = null,           // Prediction ISF (variable_sens) used for BG predictions
    var sensNormalTarget: Double? = null,        // ISF at normal target BG level
    var tdd: Double? = null,                     // Blended TDD value used in ISF calculation
    var tddRatio: Double? = null,                // Sensitivity ratio derived from TDD (8h weighted / 7D)
    var insulinReqPctEffective: Double? = null,  // Effective insulin required % used for dosing
    var deltaAcceleration: Double? = null,       // Delta acceleration percentage
    var boostProfileSwitch: Int? = null,         // Effective profile % (activity-adjusted)

    // Deviation-based sensitivity (Boost V3 DISFv3-sensitivity)
    var deviationSensRatio: Double? = null,      // The applied sensitivity ratio (> 1 = more resistant)
    var deviationSensSource: String? = null,     // "deviation" or "tdd_fallback" or "none"
    var deviationSensClean: Int? = null,         // Number of clean (non-meal) entries in the 8H window
    var deviationSensTotal: Int? = null,         // Total entries in the 8H window

    // ML risk model fields (Boost V3ML only)
    var mlHypoRisk: Double? = null,             // P(hypo event in next 4h), 0.0-1.0
    var mlRiskScale: Double? = null,            // SMB scaling factor applied (1.0 = no reduction)

    // Post-SMB risk gate (7.7) — second inference at projected post-SMB IOB
    var mlPostSmbRisk: Double? = null,          // P(hypo in next 4h) at projected post-SMB IOB
    var mlPostSmbScale: Double? = null,         // additional damping applied (1.0 = no reduction)
    var mlPostSmbMicroBolusBefore: Double? = null,  // microBolus before post-SMB damping (diagnostics)

    // Meal-likelihood model (7.10) — separate model predicting meal in progress
    var mlMealLikely: Double? = null,           // P(BG peak >= current+50 in next 90 min), 0.0-1.0
    var mlMealG3Released: Boolean? = null,      // true if any v4.4+ release condition lifted the G3 hold this cycle (V3MLG3 only)
    var mlG3ReleaseSource: String? = null,      // v4.4.1: which release condition fired ("delta_accl" | "bg_threshold" | "meal_model")

    // Boost V5 shadow fields (filled by OpenAPSBoostV5Plugin.runShadow during V4.4.1's invoke).
    // These ride along V4.4.1's RT through the existing NS deviceStatus uploader so V5's parallel
    // decision is visible alongside V4.4.1's actual delivery without a separate publication channel.
    var boostV5_score: Double? = null,           // meal_signal_score 0.0-1.0
    var boostV5_state: String? = null,           // IDLE | OBSERVING | CONFIRMED | COMMITTED | RECOVERING
    var boostV5_age: Int? = null,                // cycles in current state
    var boostV5_budget: Double? = null,          // aggression_budget U
    var boostV5_actionMult: Double? = null,      // action multiplier for the current state
    var boostV5_finalDose: Double? = null,       // V5's would-have-delivered SMB (U) — direct comparator to rT.units
    var boostV5_gateReduction: String? = null,   // compact summary of which Phase 3 gates fired

    // Boost ISF shadow telemetry — V4.4.2-style TDD-anchored EMA(τ=3h) sensitivity ratio
    // computed in parallel with V1/V2's instantaneous ratio so the EMA overlay's actual
    // contribution can be measured without changing dosing.
    var isfShadow_ratioRaw: Double? = null,          // raw tdd_24h / tdd_7d (also what V1/V2 use today)
    var isfShadow_ratioEma: Double? = null,          // V4.4.2's smoothed ratio (bounded by autosens)
    var isfShadow_warmup: Double? = null,            // 0.0-1.0, cold-start blend factor
    var isfShadow_variableSens: Double? = null,      // implied variable_sens if the EMA ratio had been used (mg/dL/U)
    var isfShadow_insulinReq: Double? = null,        // implied insulinReq under shadow variable_sens (U)
    var isfShadow_microBolus: Double? = null,        // implied microBolus under shadow insulinReq, same tier (U)
    var isfShadow_deltaPct: Double? = null           // (shadow/actual - 1) × 100 on variable_sens — single-number summary
) {

    fun serialize() = Json.encodeToString(serializer(), this)

    object StringBuilderSerializer : KSerializer<StringBuilder> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringBuilder", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: StringBuilder) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): StringBuilder {
            return StringBuilder().append(decoder.decodeString())
        }
    }

    object TimestampToIsoSerializer : KSerializer<Long> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LongToIso", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Long) {
            encoder.encodeString(toISOString(value))
        }

        override fun deserialize(decoder: Decoder): Long {
            return fromISODateString(decoder.decodeString())
        }

        fun fromISODateString(isoDateString: String): Long {
            val parser = ISODateTimeFormat.dateTimeParser()
            val dateTime = DateTime.parse(isoDateString, parser)
            return dateTime.toDate().time
        }

        fun toISOString(date: Long): String {
            @Suppress("SpellCheckingInspection", "LocalVariableName")
            val FORMAT_DATE_ISO_OUT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            val f: DateFormat = SimpleDateFormat(FORMAT_DATE_ISO_OUT, Locale.getDefault())
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(date)
        }
    }

    companion object {

        private val serializer = Json { ignoreUnknownKeys = true }
        fun deserialize(jsonString: String) = serializer.decodeFromString(serializer(), jsonString)
    }
}
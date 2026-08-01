package app.aaps.core.interfaces.aps

import androidx.collection.LongSparseArray
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil

interface AutosensDataStore {

    val dataLock: Any

    var bgReadings: List<GV>
    var autosensDataTable: LongSparseArray<AutosensData>
    var bucketedData: MutableList<InMemoryGlucoseValue>?

    /**
     * The same glucose at the sensor's OWN cadence, rather than resampled to five minutes.
     *
     * [bucketedData] is built by stepping back in fixed five-minute increments and interpolating,
     * so on a one-minute sensor four of every five readings are replaced by an interpolation of
     * their neighbours. That is deliberate for the sensitivity chain, which defines deviations
     * and carbohydrate impact per five-minute bucket and would be silently rescaled by a change
     * of interval. Consumers that work in elapsed time rather than sample counts, the smoother
     * and the delta calculation, can use this instead and see every reading.
     *
     * Identical to [bucketedData] on a five-minute feed. Null until [createBucketedData] runs.
     */
    var bucketedDataNative: MutableList<InMemoryGlucoseValue>?

    /** Median spacing of the raw readings in minutes, or null when it cannot be established. */
    var detectedCadenceMinutes: Double?

    var lastUsed5minCalculation: Boolean?

    /**
     * Return last valid (>39) InMemoryGlucoseValue from bucketed data or null if db is empty
     *
     * @return InMemoryGlucoseValue or null
     */
    fun lastBg(): InMemoryGlucoseValue?

    /**
     * Provide last bucketed InMemoryGlucoseValue or null if none exists within the last 9 minutes
     *
     * @return InMemoryGlucoseValue or null
     */
    fun actualBg(): InMemoryGlucoseValue?
    fun lastDataTime(dateUtil: DateUtil): String
    fun clone(): AutosensDataStore
    fun getBgReadingsDataTableCopy(): List<GV>
    fun getLastAutosensData(reason: String, aapsLogger: AAPSLogger, dateUtil: DateUtil): AutosensData?
    fun getAutosensDataAtTime(fromTime: Long): AutosensData?
    fun getBucketedDataTableCopy(): MutableList<InMemoryGlucoseValue>?
    fun createBucketedData(aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun slowAbsorptionPercentage(timeInMinutes: Int): Double
    fun newHistoryData(time: Long, aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun roundUpTime(time: Long): Long
    fun reset()
}
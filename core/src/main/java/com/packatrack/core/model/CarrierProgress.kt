package com.packatrack.core.model

/**
 * One stage of the route the carrier itself reports, e.g. "Mainland China" -> "Australia" ->
 * "Delivered". Cainiao/UBI call these progress points and mark the ones already reached with
 * `light`.
 */
data class JourneyStage(val name: String, val reached: Boolean)

/**
 * Journey progress as the carrier reports it (Cainiao/UBI's `processInfo`), rather than as the
 * app infers it from scans.
 *
 * Kept on the courier leg so the detail screen can still show the route after a poll fails, and
 * because only the carrier knows its own stage names.
 */
data class CarrierProgress(
    /** Fraction of the journey complete, 0..1, or null when the carrier omits or garbles it. */
    val rate: Double? = null,
    val stages: List<JourneyStage> = emptyList(),
) {

    /** Index of the furthest stage reached, or -1 when the carrier has lit none. */
    val reachedIndex: Int get() = stages.indexOfLast { it.reached }

    /** Stage names in one column-friendly string, as stored on the leg row. */
    fun toStoredStages(): String = stages.joinToString(STAGE_SEPARATOR) { it.name }

    companion object {
        const val STAGE_SEPARATOR = "|"

        /** Rebuilds what [toStoredStages] wrote; null when the leg has stored nothing. */
        fun fromStored(stages: String?, rate: Double?, reachedIndex: Int?): CarrierProgress? {
            val names = stages.orEmpty()
                .split(STAGE_SEPARATOR)
                .map(String::trim)
                .filter { it.isNotEmpty() }
            if (names.isEmpty() && rate == null) return null
            val reached = reachedIndex ?: -1
            return CarrierProgress(
                rate = rate,
                stages = names.mapIndexed { index, name -> JourneyStage(name, reached = index <= reached) },
            )
        }
    }
}

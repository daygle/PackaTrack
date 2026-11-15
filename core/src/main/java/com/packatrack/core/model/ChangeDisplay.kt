package com.packatrack.core.model

/**
 * Locale-independent facts about a detected change, attached to a
 * [com.packatrack.core.db.ChangeEntity] in memory so the notification can be worded in the
 * user's language at display time.
 *
 * [com.packatrack.core.db.ChangeEntity.message] stays the canonical stored form - it is written
 * to the database, exported in backups and used as the de-duplication key - and is always
 * English. This carries only the pieces a translated sentence needs.
 */
data class ChangeDisplay(
    val kind: Kind,
    val category: Category,
    /** Display name of the parcel the change belongs to. */
    val parcel: String? = null,
    /** Previous tracking number for [Kind.RENUMBERED]. */
    val oldNumber: String? = null,
    /** New/current tracking number for [Kind.RENUMBERED] or [Kind.COURIER_ADDED]. */
    val newNumber: String? = null,
    /** How many parcels were folded together for [Kind.COMBINED]. */
    val count: Int = 0,
    /** Destination number for [Kind.COMBINED], or the merged-in parcel for the merge kinds. */
    val into: String? = null,
    /** Carrier-reported scan text for [Kind.PROGRESS]. */
    val description: String? = null,
    val timeMs: Long? = null,
    /** Courier display name for [Kind.COURIER_ADDED]. */
    val carrier: String? = null,
) {
    /** Which notification sentence describes this change. */
    enum class Kind {
        PROGRESS,
        RENUMBERED,
        COMBINED,
        MERGED_WITH,
        CONSOLIDATED,
        FOLDED_INTO,
        COURIER_ADDED,
    }

    /** Which notification setting governs this change. */
    enum class Category {
        DELIVERED,
        EXCEPTION,
        TRANSIT,
    }

    companion object {
        /**
         * Category inferred from wording alone, for changes that carry no [ChangeDisplay]: rows
         * restored from a backup, and scans whose carrier sends no normalized code. Failures are
         * matched first, because "Delivery failed" and "delayed delivery" contain "deliver" but
         * belong to the exception category.
         */
        fun categoryFromWording(text: String?): Category {
            val lower = text.orEmpty().lowercase()
            return when {
                lower.contains("exception") || lower.contains("fail") || lower.contains("delay") ||
                    lower.contains("customs") || lower.contains("return") ||
                    lower.contains("damage") || lower.contains("lost") -> Category.EXCEPTION
                lower.contains("deliver") -> Category.DELIVERED
                else -> Category.TRANSIT
            }
        }
    }
}

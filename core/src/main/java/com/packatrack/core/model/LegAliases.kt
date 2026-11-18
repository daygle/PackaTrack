package com.packatrack.core.model

import com.packatrack.core.util.FingerprintUtil

/**
 * A leg's alias list with the numbers removed that the parcel tracks as a live leg.
 *
 * An alias records a number this leg was tracked under, so a number that has a leg of its own on
 * the same parcel is current, not previous: keeping it showed the same number twice on the parcel
 * (once as an "Aliases:" line, once as its own courier card). Renumbered aliases are kept, because
 * the "View response" lookup falls back to them to find the payload captured under a number the
 * leg no longer polls under.
 *
 * Entries are normalized and de-duplicated, since stored lists mixed formats from carrier reports.
 *
 * @param aliasNumbers comma-separated list as stored on the leg; blanks are dropped.
 * @param liveNumbers numbers other legs of the parcel track today, in any format.
 */
fun aliasesWithoutLiveNumbers(
    aliasNumbers: String,
    liveNumbers: Collection<String>,
): String {
    val live = liveNumbers.mapTo(HashSet()) { FingerprintUtil.normalize(it) }
    return aliasNumbers.split(',')
        .mapNotNull { alias ->
            FingerprintUtil.normalize(alias).takeIf { it.isNotEmpty() && it !in live }
        }
        .distinct()
        .joinToString(",")
}

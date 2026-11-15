package com.packatrack.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.packatrack.core.db.ChangeEntity

/**
 * Change types that earn a row on the parcel timeline.
 *
 * Only what the app decided on the user's behalf belongs here. Carrier-added and progress rows are
 * already represented by the courier list and the scans themselves, and older backups carry types
 * this build cannot word.
 */
val timelineChangeTypes: Set<String> = setOf("DUPLICATE")

/**
 * Localized line for an event the app recorded itself, as opposed to a carrier scan.
 *
 * Rows read back from storage carry no [com.packatrack.core.model.ChangeDisplay] - it is attached
 * in memory when the change is detected so a notification can be worded in the user's language -
 * so the stored [ChangeEntity.type] is what identifies the wording here. Types this build does not
 * know (a change restored from an older backup) fall back to the stored message.
 */
@Composable
fun appChangeText(change: ChangeEntity): String = when (change.type) {
    "DUPLICATE" -> stringResource(R.string.timeline_duplicate_removed)
    else -> change.message
}

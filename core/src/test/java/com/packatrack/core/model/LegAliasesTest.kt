package com.packatrack.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LegAliasesTest {

    @Test fun aliasThatBecameALiveLegIsDropped() {
        // The iMile hand-off number is a leg of the same parcel now, so it is no longer a previous
        // number of the Cainiao leg.
        val pruned = aliasesWithoutLiveNumbers(
            aliasNumbers = "3781062090701",
            liveNumbers = listOf("AP00100209209377", "3781062090701"),
        )
        assertEquals("", pruned)
    }

    @Test fun renumberedAliasSurvives() {
        // A previous number after a renumbering has no leg of its own; the View response lookup
        // needs it to find the payload captured under that number.
        val pruned = aliasesWithoutLiveNumbers(
            aliasNumbers = "AP00100209209377",
            liveNumbers = listOf("3781062090701"),
        )
        assertEquals("AP00100209209377", pruned)
    }

    @Test fun liveNumbersMatchAfterNormalization() {
        val pruned = aliasesWithoutLiveNumbers(
            aliasNumbers = " 3781 0620-90701 , AP0001",
            liveNumbers = listOf("3781062090701"),
        )
        assertEquals("AP0001", pruned)
    }

    @Test fun duplicateAndBlankEntriesAreCollapsed() {
        val pruned = aliasesWithoutLiveNumbers(
            aliasNumbers = "AP001, ap001 ,, ,AP002",
            liveNumbers = emptyList(),
        )
        assertEquals("AP001,AP002", pruned)
    }

    @Test fun emptyListStaysEmpty() {
        assertEquals("", aliasesWithoutLiveNumbers("", listOf("AP001")))
    }
}

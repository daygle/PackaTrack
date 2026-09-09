package com.packatrack.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.packatrack.app.MainActivity
import com.packatrack.feature.common.R
import com.packatrack.data.TrackingRepository
import com.packatrack.core.db.OrderItemEntity
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.ShipmentWithLegs
import com.packatrack.core.db.TrackingLegEntity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [HomeScreen] covering the empty state, searching, and
 * sorting - the flows called out in the modularization plan. The repository is
 * a relaxed MockK bound over the real Hilt graph via [BindValue].
 */
@HiltAndroidTest
class HomeScreenTest {

    private fun shipment(id: Long, title: String?, createdAt: Long = 0L) =
        ShipmentEntity(id = id, title = title, createdAt = createdAt)

    private fun leg(id: Long, shipmentId: Long, trackingNumber: String, statusCode: String? = null) =
        TrackingLegEntity(id = id, shipmentId = shipmentId, trackingNumber = trackingNumber, carrierId = "test", lastStatusCode = statusCode)

    private fun entry(
        id: Long,
        title: String?,
        trackingNumber: String,
        createdAt: Long = 0L,
        statusCode: String? = null,
    ) = ShipmentWithLegs(
        shipment = shipment(id, title, createdAt),
        legs = listOf(leg(id, id, trackingNumber, statusCode)),
        orders = listOf(OrderItemEntity(id = id, shipmentId = id, name = title ?: trackingNumber)),
    )

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val repository: TrackingRepository = mockk(relaxed = true)

    @Before
    fun init() {
        hiltRule.inject()
        // Provide empty flows by default to avoid crashes
        every { repository.observeActive() } returns flowOf(emptyList())
        every { repository.observeArchived() } returns flowOf(emptyList())
        every { repository.observeRecentChanges() } returns flowOf(emptyList())
        every { repository.observeFirstEventTimes() } returns flowOf(emptyMap())
        every { repository.observeLatestEvents() } returns flowOf(emptyMap())
    }

    @Test
    fun showsEmptyState_whenNoParcels() {
        composeTestRule.onNodeWithText("No Parcels Tracking").assertIsDisplayed()
    }

    @Test
    fun openingSearch_showsSearchField() {
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search parcels…").assertIsDisplayed()
    }

    @Test
    fun searchFiltersParcelsByTitleAndTrackingNumber() {
        every { repository.observeActive() } returns flowOf(
            listOf(
                entry(1, "Keyboard", "CNKEYB123456"),
                entry(2, "Monitor", "CNMON098765"),
            )
        )
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        val searchField = composeTestRule.onNodeWithTag("home_search_field")

        // Matches by title
        searchField.performTextInput("key")
        composeTestRule.onNodeWithText("Keyboard").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Monitor").assertCountEquals(0)

        // Matches by tracking number
        searchField.performTextClearance()
        searchField.performTextInput("CNMON098765")
        composeTestRule.onNodeWithText("Monitor").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Keyboard").assertCountEquals(0)

        // No match shows no parcel cards
        searchField.performTextClearance()
        searchField.performTextInput("zzz")
        composeTestRule.onAllNodesWithText("Keyboard").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Monitor").assertCountEquals(0)
    }

    @Test
    fun sortMenu_reordersParcelsByName() {
        every { repository.observeActive() } returns flowOf(
            listOf(
                entry(1, "Zebra Plush", "CNZEB111111"),
                entry(2, "Apple Pencil", "CNAPP222222"),
            )
        )
        composeTestRule.onNodeWithContentDescription("Sort").performClick()
        composeTestRule.onNodeWithText("Name").performClick()

        composeTestRule.waitForIdle()
        val titles = composeTestRule
            .onAllNodesWithText("Zebra Plush")
            .fetchSemanticsNodes()
            .map { it.positionInRoot.y }
        val names = composeTestRule
            .onAllNodesWithText("Apple Pencil")
            .fetchSemanticsNodes()
            .map { it.positionInRoot.y }
        org.junit.Assert.assertTrue(
            "Expected 'Apple Pencil' above 'Zebra Plush' after name sort (y=$titles vs y=$names)",
            names.single() < titles.single(),
        )
    }

    @Test
    fun sortMenu_reordersParcelsByDateAdded() {
        every { repository.observeActive() } returns flowOf(
            listOf(
                entry(1, "Old Parcel", "CNOLD111111", createdAt = 1_000L),
                entry(2, "New Parcel", "CNNEW222222", createdAt = 2_000L),
            )
        )
        composeTestRule.onNodeWithContentDescription("Sort").performClick()
        composeTestRule.onNodeWithText("Date Added").performClick()

        composeTestRule.waitForIdle()
        val oldY = composeTestRule.onAllNodesWithText("Old Parcel").fetchSemanticsNodes().single().positionInRoot.y
        val newY = composeTestRule.onAllNodesWithText("New Parcel").fetchSemanticsNodes().single().positionInRoot.y
        org.junit.Assert.assertTrue(
            "Expected newest parcel first after Date Added sort (y=$oldY vs y=$newY)",
            newY < oldY,
        )
    }

    @Test
    fun clickingSettings_navigatesToSettings() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}

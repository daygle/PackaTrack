package com.packatrack.app.data

import android.content.Context
import com.packatrack.data.PrefsStore
import com.packatrack.data.TrackingRepository
import com.packatrack.data.db.AppDatabase
import com.packatrack.data.db.ChangeDao
import com.packatrack.data.db.EventDao
import com.packatrack.data.db.LegDao
import com.packatrack.data.db.OrderDao
import com.packatrack.data.db.ShipmentDao
import com.packatrack.core.db.LegLatestEvent
import com.packatrack.core.db.ShipmentEntity
import com.packatrack.core.db.TrackingLegEntity
import com.packatrack.core.model.Carrier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class TrackingRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<PrefsStore>(relaxed = true)
    private val db = mockk<AppDatabase>(relaxed = true)
    private val shipments = mockk<ShipmentDao>(relaxed = true)
    private val legs = mockk<LegDao>(relaxed = true)
    private val orders = mockk<OrderDao>(relaxed = true)
    private val events = mockk<EventDao>(relaxed = true)
    private val changes = mockk<ChangeDao>(relaxed = true)

    private lateinit var repository: TrackingRepository

    @Before
    fun setup() {
        mockkObject(AppDatabase)
        every { AppDatabase.get(any()) } returns db
        every { db.shipmentDao() } returns shipments
        every { db.legDao() } returns legs
        every { db.orderDao() } returns orders
        every { db.eventDao() } returns events
        every { db.changeDao() } returns changes

        repository = TrackingRepository(context, prefs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `addShipment inserts shipment and leg`() = runTest {
        val number = "7123456789012" // Valid AusPost Consignment
        coEvery { shipments.insert(any()) } returns 1L
        coEvery { legs.findByTrackingNumber(any()) } returns null

        repository.addShipment(number, "My Parcel", null, null)

        coVerify { shipments.insert(match { it.createdAt > 0 }) }
        coVerify { legs.insert(match { it.trackingNumber == number && it.shipmentId == 1L }) }
    }

    @Test
    fun `archive updates shipment archived status`() = runTest {
        val shipmentId = 123L
        val entity = ShipmentEntity(id = shipmentId, createdAt = 1000L, archived = false)
        coEvery { shipments.byId(shipmentId) } returns entity

        repository.archive(shipmentId)

        coVerify { shipments.update(match { it.id == shipmentId && it.archived }) }
    }

    /** Leg whose lastSyncAt is "now" so the poll cooldown skips it - no network in tests. */
    private fun skippedLeg(id: Long, shipmentId: Long, statusCode: String?, syncedAt: Long = System.currentTimeMillis()) =
        TrackingLegEntity(
            id = id,
            shipmentId = shipmentId,
            trackingNumber = "NUM$id",
            carrierId = "cainiao",
            lastStatusCode = statusCode,
            lastSyncAt = syncedAt,
        )

    @Test
    fun `auto-archive fires when overall status is delivered`() = runTest {
        every { prefs.autoArchiveDelivered } returns true
        val leg = skippedLeg(10L, shipmentId = 1L, statusCode = "DELIVERED", syncedAt = System.currentTimeMillis())
        coEvery { legs.allActiveLegs() } returns listOf(leg)
        coEvery { legs.legsForShipment(1L) } returns listOf(leg)
        coEvery { events.latestEventMsByLeg() } returns listOf(LegLatestEvent(legId = 10L, firstMs = System.currentTimeMillis()))
        coEvery { shipments.byId(1L) } returns ShipmentEntity(id = 1L, createdAt = 1L, archived = false)

        repository.refreshAll()

        coVerify { shipments.update(match { it.id == 1L && it.archived }) }
    }

    @Test
    fun `auto-archive does not fire while a fresher leg is still in transit`() = runTest {
        // Regression: the origin carrier's stale DELIVERED beat (3 days old) must not archive
        // a parcel whose destination leg scanned IN_TRANSIT just now - the overall status,
        // not any single leg, decides.
        every { prefs.autoArchiveDelivered } returns true
        val now = System.currentTimeMillis()
        val deliveredLeg = skippedLeg(10L, shipmentId = 1L, statusCode = "DELIVERED", syncedAt = now)
        val transitLeg = skippedLeg(11L, shipmentId = 1L, statusCode = "IN_TRANSIT", syncedAt = now)
        coEvery { legs.allActiveLegs() } returns listOf(deliveredLeg)
        coEvery { legs.legsForShipment(1L) } returns listOf(deliveredLeg, transitLeg)
        coEvery { events.latestEventMsByLeg() } returns listOf(
            LegLatestEvent(legId = 10L, firstMs = now - 3L * 24 * 60 * 60 * 1000),
            LegLatestEvent(legId = 11L, firstMs = now),
        )
        coEvery { shipments.byId(1L) } returns ShipmentEntity(id = 1L, createdAt = 1L, archived = false)

        repository.refreshAll()

        coVerify(exactly = 0) { shipments.update(any()) }
    }
}

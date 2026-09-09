package com.packatrack.app.data

import android.content.Context
import com.packatrack.app.data.db.AppDatabase
import com.packatrack.app.data.db.ChangeDao
import com.packatrack.app.data.db.EventDao
import com.packatrack.app.data.db.LegDao
import com.packatrack.app.data.db.OrderDao
import com.packatrack.app.data.db.ShipmentDao
import com.packatrack.app.data.db.ShipmentEntity
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
}

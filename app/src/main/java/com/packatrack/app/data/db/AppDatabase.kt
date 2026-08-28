package com.packatrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShipmentEntity::class,
        TrackingLegEntity::class,
        OrderItemEntity::class,
        EventEntity::class,
        ChangeEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shipmentDao(): ShipmentDao
    abstract fun legDao(): LegDao
    abstract fun orderDao(): OrderDao
    abstract fun eventDao(): EventDao
    abstract fun changeDao(): ChangeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tracking_legs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shipmentId` INTEGER NOT NULL, `trackingNumber` TEXT NOT NULL, `carrierId` TEXT NOT NULL, `aliasNumbers` TEXT NOT NULL DEFAULT '', `pollCount` INTEGER NOT NULL DEFAULT 0, `lastSyncAt` INTEGER, `lastStatusCode` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_legs_shipmentId` ON `tracking_legs` (`shipmentId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracking_legs_trackingNumber_carrierId` ON `tracking_legs` (`trackingNumber`, `carrierId`)")
                db.execSQL("INSERT INTO tracking_legs (shipmentId, trackingNumber, carrierId, createdAt) SELECT id, trackingNumber, 'cainiao', createdAt FROM shipments WHERE trackingNumber IS NOT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shipmentId` INTEGER NOT NULL, `name` TEXT NOT NULL, `orderUrl` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_shipmentId` ON `orders` (`shipmentId`)")
                db.execSQL("INSERT INTO orders (shipmentId, name, orderUrl, createdAt) SELECT id, 'Item', orderUrl, createdAt FROM shipments WHERE orderUrl IS NOT NULL AND orderUrl != ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracking_legs_trackingNumber_carrierId` ON `tracking_legs` (`trackingNumber`, `carrierId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "packatrack.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}

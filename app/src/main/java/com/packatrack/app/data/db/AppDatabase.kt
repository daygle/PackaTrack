package com.packatrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShipmentEntity::class,
        TrackingLegEntity::class,
        OrderItemEntity::class,
        EventEntity::class,
        ChangeEntity::class,
    ],
    version = 3,
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "packatrack.db",
                )
                    // Schema still evolving pre-release (v2 introduced courier legs; v3 added
                    // per-parcel orders). Rather than ship fragile hand-written migrations for a
                    // pre-1.0 app, rebuild cleanly on any version change; demo data regenerates on
                    // the next refresh.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

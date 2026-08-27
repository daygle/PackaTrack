package com.packatrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShipmentEntity::class,
        TrackingLegEntity::class,
        EventEntity::class,
        ChangeEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shipmentDao(): ShipmentDao
    abstract fun legDao(): LegDao
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
                    // v2 splits each single-number shipment into a parcel + one courier leg.
                    // The pre-release schema had no data worth a hand-written migration, so we
                    // rebuild cleanly; demo data regenerates on the next refresh.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

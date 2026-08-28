package com.packatrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.packatrack.app.data.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ShipmentEntity::class,
        TrackingLegEntity::class,
        OrderItemEntity::class,
        EventEntity::class,
        ChangeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shipmentDao(): ShipmentDao
    abstract fun legDao(): LegDao
    abstract fun orderDao(): OrderDao
    abstract fun eventDao(): EventDao
    abstract fun changeDao(): ChangeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Schema baseline is version 1: the app has no released installs, so the historical
        // 1->4 migrations were dead code. Future schema changes add migrations from here.
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    // The database file is encrypted at rest with SQLCipher. The key is random,
                    // generated once and wrapped by the Android Keystore (see DatabaseKey).
                    System.loadLibrary("sqlcipher")
                    val factory = SupportOpenHelperFactory(DatabaseKey.getOrCreate(appContext))
                    Room.databaseBuilder(appContext, AppDatabase::class.java, "packatrack.db")
                        .openHelperFactory(factory)
                        .build()
                        .also { instance = it }
                }
            }
    }
}

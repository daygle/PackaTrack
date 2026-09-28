package com.packatrack.data.db

import com.packatrack.core.db.*
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.packatrack.data.DatabaseKey
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
        private const val DB_NAME = "packatrack.db"

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
                    val key = DatabaseKey.getOrCreate(appContext) {
                        // Encrypted under a key that no longer exists: unreadable, so start over.
                        appContext.deleteDatabase(DB_NAME)
                    }
                    val factory = SupportOpenHelperFactory(key)
                    Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                        .openHelperFactory(factory)
                        .build()
                        .also { instance = it }
                }
            }
    }
}

/**
 * Initialises the encrypted database (SQLCipher load + Keystore key unwrap) ahead of first use.
 * Blocking - call it off the main thread.
 */
fun warmUpDatabase(context: Context) {
    AppDatabase.get(context)
}

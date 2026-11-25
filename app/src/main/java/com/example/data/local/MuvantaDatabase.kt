package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserTitleEntryEntity::class,
        CachedMetadataEntity::class,
        CustomCollectionEntity::class,
        CollectionItemCrossRef::class,
        LocalProfileEntity::class,
        ViewingLogEntity::class,
        CachedEpisodeEntity::class,
        WatchedEpisodeEntity::class,
        SearchHistoryEntity::class,
        PendingSyncOpEntity::class,
        SyncConflictEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MuvantaDatabase : RoomDatabase() {
    abstract fun muvantaDao(): MuvantaDao

    companion object {
        @Volatile
        private var instance: MuvantaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN reviewText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN rewatchCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_metadata ADD COLUMN tvmazeShowId INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS viewing_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId TEXT NOT NULL,
                        loggedAt INTEGER NOT NULL,
                        actionLabel TEXT NOT NULL,
                        ratingAtLog INTEGER NOT NULL,
                        isRewatch INTEGER NOT NULL,
                        note TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_viewing_history_itemId ON viewing_history(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_viewing_history_loggedAt ON viewing_history(loggedAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_episodes (
                        itemId TEXT NOT NULL,
                        season INTEGER NOT NULL,
                        episode INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        airDate TEXT NOT NULL,
                        runtimeMinutes INTEGER NOT NULL,
                        summary TEXT NOT NULL,
                        PRIMARY KEY(itemId, season, episode)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_episodes_itemId ON cached_episodes(itemId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watched_episodes (
                        itemId TEXT NOT NULL,
                        season INTEGER NOT NULL,
                        episode INTEGER NOT NULL,
                        watchedAt INTEGER NOT NULL,
                        PRIMARY KEY(itemId, season, episode)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watched_episodes_itemId ON watched_episodes(itemId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        query TEXT NOT NULL PRIMARY KEY,
                        searchedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_title_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE custom_collections ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE custom_collections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE viewing_history ADD COLUMN clientLogKey TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_sync_ops (
                        opId TEXT NOT NULL PRIMARY KEY,
                        ownerUserId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityKey TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        baseVersion INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL,
                        lastError TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_ops_ownerUserId ON pending_sync_ops(ownerUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_ops_createdAt ON pending_sync_ops(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        fieldName TEXT NOT NULL,
                        localValue TEXT NOT NULL,
                        cloudValue TEXT NOT NULL,
                        cloudVersion INTEGER NOT NULL,
                        detectedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_itemId ON sync_conflicts(itemId)")
            }
        }

        fun getInstance(context: Context): MuvantaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuvantaDatabase::class.java,
                    "muvanta_archive.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

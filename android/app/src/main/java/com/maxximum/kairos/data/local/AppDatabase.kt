package com.maxximum.kairos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.maxximum.kairos.domain.model.Todo

@Database(entities = [Todo::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN isOneOffTask INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE todos
                    SET clientId =
                        lower(hex(randomblob(4))) || '-' ||
                        lower(hex(randomblob(2))) || '-4' ||
                        substr(lower(hex(randomblob(2))), 2) || '-' ||
                        substr('89ab', ((random() & 3) + 1), 1) ||
                        substr(lower(hex(randomblob(2))), 2) || '-' ||
                        lower(hex(randomblob(6)))
                    WHERE clientId = ''
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_todos_clientId ON todos(clientId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE todos ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE todos SET updatedAt = timestamp WHERE updatedAt = 0")
                db.execSQL("ALTER TABLE todos ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'DIRTY'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_syncStatus ON todos(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_deletedAt ON todos(deletedAt)")
            }
        }

        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "todo_database")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}


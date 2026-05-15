package com.maxximum.kairos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.maxximum.kairos.domain.model.KairosTag
import com.maxximum.kairos.domain.model.LocalNoteNoteReference
import com.maxximum.kairos.domain.model.LocalNoteTagLink
import com.maxximum.kairos.domain.model.LocalNoteTaskReference
import com.maxximum.kairos.domain.model.LocalTaskTagLink
import com.maxximum.kairos.domain.model.Note
import com.maxximum.kairos.domain.model.NoteFolder
import com.maxximum.kairos.domain.model.Todo

@Database(
    entities = [
        Todo::class,
        SyncConflict::class,
        Note::class,
        NoteFolder::class,
        KairosTag::class,
        LocalNoteTagLink::class,
        LocalTaskTagLink::class,
        LocalNoteTaskReference::class,
        LocalNoteNoteReference::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun noteDao(): NoteDao
    abstract fun syncConflictDao(): SyncConflictDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN remoteUpdatedAt INTEGER")
                db.execSQL(
                    """
                    UPDATE todos
                    SET remoteUpdatedAt = updatedAt
                    WHERE serverId IS NOT NULL
                        AND syncStatus = 'SYNCED'
                        AND updatedAt > 0
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN baseSnapshotJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        objectType TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        localSnapshotJson TEXT NOT NULL,
                        serverSnapshotJson TEXT NOT NULL,
                        conflictedFields TEXT NOT NULL,
                        detectedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_conflicts_objectType_clientId ON sync_conflicts(objectType, clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_detectedAt ON sync_conflicts(detectedAt)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        serverId TEXT,
                        folderClientId TEXT,
                        remoteUpdatedAt INTEGER,
                        title TEXT NOT NULL,
                        markdownBody TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        lastSyncedAt INTEGER,
                        baseSnapshotJson TEXT,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_clientId ON notes(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderClientId ON notes(folderClientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_syncStatus ON notes(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_deletedAt ON notes(deletedAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        serverId TEXT,
                        parentClientId TEXT,
                        remoteUpdatedAt INTEGER,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        lastSyncedAt INTEGER,
                        baseSnapshotJson TEXT,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_folders_clientId ON note_folders(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_parentClientId ON note_folders(parentClientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_syncStatus ON note_folders(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_deletedAt ON note_folders(deletedAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        serverId TEXT,
                        remoteUpdatedAt INTEGER,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        lastSyncedAt INTEGER,
                        baseSnapshotJson TEXT,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_clientId ON tags(clientId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_normalizedName ON tags(normalizedName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_syncStatus ON tags(syncStatus)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_tag_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        noteClientId TEXT NOT NULL,
                        tagClientId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_tag_links_clientId ON note_tag_links(clientId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_tag_links_noteClientId_tagClientId ON note_tag_links(noteClientId, tagClientId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_tag_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        taskClientId TEXT NOT NULL,
                        tagClientId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_tag_links_clientId ON task_tag_links(clientId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_tag_links_taskClientId_tagClientId ON task_tag_links(taskClientId, tagClientId)")

                db.execSQL("CREATE TABLE IF NOT EXISTS note_task_references (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteClientId TEXT NOT NULL, taskClientId TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_task_references_noteClientId_taskClientId ON note_task_references(noteClientId, taskClientId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS note_note_references (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceNoteClientId TEXT NOT NULL, targetNoteClientId TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_note_references_sourceNoteClientId_targetNoteClientId ON note_note_references(sourceNoteClientId, targetNoteClientId)")
            }
        }

        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "todo_database")
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { Instance = it }
            }
        }
    }
}


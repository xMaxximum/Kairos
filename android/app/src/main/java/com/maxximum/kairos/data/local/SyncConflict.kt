package com.maxximum.kairos.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["objectType", "clientId"], unique = true),
        Index(value = ["detectedAt"])
    ]
)
data class SyncConflict(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectType: String,
    val clientId: String,
    val localSnapshotJson: String,
    val serverSnapshotJson: String,
    val conflictedFields: String,
    val detectedAt: Long = System.currentTimeMillis()
)

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<SyncConflict>>

    @Query("SELECT * FROM sync_conflicts WHERE objectType = :objectType AND clientId = :clientId LIMIT 1")
    suspend fun getConflict(objectType: String, clientId: String): SyncConflict?

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: SyncConflict)

    @Query("DELETE FROM sync_conflicts WHERE objectType = :objectType AND clientId = :clientId")
    suspend fun delete(objectType: String, clientId: String)
}

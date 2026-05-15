package com.maxximum.kairos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.maxximum.kairos.domain.model.KairosTag
import com.maxximum.kairos.domain.model.LocalNoteNoteReference
import com.maxximum.kairos.domain.model.LocalNoteTagLink
import com.maxximum.kairos.domain.model.LocalNoteTaskReference
import com.maxximum.kairos.domain.model.LocalTaskTagLink
import com.maxximum.kairos.domain.model.Note
import com.maxximum.kairos.domain.model.NoteFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE clientId = :clientId LIMIT 1")
    suspend fun getNoteByClientId(clientId: String): Note?

    @Query("SELECT * FROM notes WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingNotes(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("SELECT * FROM note_folders WHERE deletedAt IS NULL ORDER BY name")
    fun observeFolders(): Flow<List<NoteFolder>>

    @Query("SELECT * FROM note_folders WHERE clientId = :clientId LIMIT 1")
    suspend fun getFolderByClientId(clientId: String): NoteFolder?

    @Query("SELECT * FROM note_folders WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingFolders(): List<NoteFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: NoteFolder): Long

    @Update
    suspend fun updateFolder(folder: NoteFolder)

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeTags(): Flow<List<KairosTag>>

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName AND deletedAt IS NULL LIMIT 1")
    suspend fun getTagByNormalizedName(normalizedName: String): KairosTag?

    @Query("SELECT * FROM tags WHERE clientId = :clientId LIMIT 1")
    suspend fun getTagByClientId(clientId: String): KairosTag?

    @Query("SELECT * FROM tags WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingTags(): List<KairosTag>

    @Update
    suspend fun updateTag(tag: KairosTag)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: KairosTag): Long

    @Query("SELECT * FROM note_tag_links WHERE deletedAt IS NULL")
    fun observeNoteTagLinks(): Flow<List<LocalNoteTagLink>>

    @Query("SELECT * FROM note_tag_links WHERE clientId = :clientId LIMIT 1")
    suspend fun getNoteTagLinkByClientId(clientId: String): LocalNoteTagLink?

    @Query("SELECT * FROM note_tag_links WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingNoteTagLinks(): List<LocalNoteTagLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTagLink(link: LocalNoteTagLink): Long

    @Update
    suspend fun updateNoteTagLink(link: LocalNoteTagLink)

    @Query("SELECT * FROM task_tag_links WHERE deletedAt IS NULL")
    fun observeTaskTagLinks(): Flow<List<LocalTaskTagLink>>

    @Query("SELECT * FROM task_tag_links WHERE clientId = :clientId LIMIT 1")
    suspend fun getTaskTagLinkByClientId(clientId: String): LocalTaskTagLink?

    @Query("SELECT * FROM task_tag_links WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingTaskTagLinks(): List<LocalTaskTagLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskTagLink(link: LocalTaskTagLink): Long

    @Update
    suspend fun updateTaskTagLink(link: LocalTaskTagLink)

    @Query("SELECT * FROM note_task_references")
    fun observeNoteTaskReferences(): Flow<List<LocalNoteTaskReference>>

    @Query("DELETE FROM note_task_references WHERE noteClientId = :noteClientId")
    suspend fun deleteTaskReferencesForNote(noteClientId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskReference(reference: LocalNoteTaskReference)

    @Query("SELECT * FROM note_note_references")
    fun observeNoteNoteReferences(): Flow<List<LocalNoteNoteReference>>

    @Query("DELETE FROM note_note_references WHERE sourceNoteClientId = :noteClientId")
    suspend fun deleteNoteReferencesForNote(noteClientId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteReference(reference: LocalNoteNoteReference)
}

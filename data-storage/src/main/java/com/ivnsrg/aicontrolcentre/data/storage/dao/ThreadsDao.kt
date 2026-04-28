package com.ivnsrg.aicontrolcentre.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadsDao {
    @Query("SELECT * FROM threads WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeThreads(projectId: Long): Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ThreadEntity): Long

    @Query("UPDATE threads SET updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadUpdatedAt(threadId: Long, updatedAt: Long): Int

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = (SELECT projectId FROM threads WHERE id = :threadId)")
    suspend fun updateParentProjectUpdatedAt(threadId: Long, updatedAt: Long): Int

    @Query("DELETE FROM threads")
    suspend fun clear(): Int
}

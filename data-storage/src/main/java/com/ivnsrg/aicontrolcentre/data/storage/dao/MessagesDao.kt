package com.ivnsrg.aicontrolcentre.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessagesDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessages(threadId: Long): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages")
    suspend fun clear(): Int
}

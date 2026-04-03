package com.ivnsrg.aicontrolcentre.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity

@Dao
interface ModelsDao {
    @Query("SELECT * FROM cached_models ORDER BY label ASC")
    suspend fun getAll(): List<CachedModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<CachedModelEntity>)

    @Query("DELETE FROM cached_models")
    suspend fun clear(): Int
}

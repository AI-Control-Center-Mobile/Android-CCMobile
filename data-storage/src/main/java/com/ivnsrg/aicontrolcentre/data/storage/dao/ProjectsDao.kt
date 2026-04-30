package com.ivnsrg.aicontrolcentre.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectsDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun countProjects(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProject(projectId: Long): ProjectEntity?

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateProjectUpdatedAt(projectId: Long, updatedAt: Long): Int

    @Query("DELETE FROM projects")
    suspend fun clear(): Int
}

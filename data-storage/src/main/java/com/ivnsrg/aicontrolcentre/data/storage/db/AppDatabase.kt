package com.ivnsrg.aicontrolcentre.data.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ivnsrg.aicontrolcentre.data.storage.dao.MessagesDao
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.dao.ProjectsDao
import com.ivnsrg.aicontrolcentre.data.storage.dao.ThreadsDao
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity

@Database(
    entities = [
        ProjectEntity::class,
        ThreadEntity::class,
        MessageEntity::class,
        CachedModelEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectsDao(): ProjectsDao
    abstract fun threadsDao(): ThreadsDao
    abstract fun messagesDao(): MessagesDao
    abstract fun modelsDao(): ModelsDao
}

package com.ivnsrg.aicontrolcentre.data.storage.db

import android.content.Context
import androidx.room.Room

object AppDatabaseFactory {
    fun create(context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_control_centre.db",
        ).build()
}

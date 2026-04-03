package com.ivnsrg.aicontrolcentre.`data`.storage.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ivnsrg.aicontrolcentre.`data`.storage.entity.ThreadEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ThreadsDao_Impl(
  __db: RoomDatabase,
) : ThreadsDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfThreadEntity: EntityInsertAdapter<ThreadEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfThreadEntity = object : EntityInsertAdapter<ThreadEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `threads` (`id`,`projectId`,`title`,`createdAt`,`updatedAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ThreadEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        statement.bindText(3, entity.title)
        statement.bindLong(4, entity.createdAt)
        statement.bindLong(5, entity.updatedAt)
      }
    }
  }

  public override suspend fun insert(thread: ThreadEntity): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfThreadEntity.insertAndReturnId(_connection, thread)
    _result
  }

  public override fun observeThreads(projectId: Long): Flow<List<ThreadEntity>> {
    val _sql: String = "SELECT * FROM threads WHERE projectId = ? ORDER BY updatedAt DESC"
    return createFlow(__db, false, arrayOf("threads")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<ThreadEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ThreadEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = ThreadEntity(_tmpId,_tmpProjectId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateThreadUpdatedAt(threadId: Long, updatedAt: Long): Int {
    val _sql: String = "UPDATE threads SET updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 2
        _stmt.bindLong(_argIndex, threadId)
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear(): Int {
    val _sql: String = "DELETE FROM threads"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

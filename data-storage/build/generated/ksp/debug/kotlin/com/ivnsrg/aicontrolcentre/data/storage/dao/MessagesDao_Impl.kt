package com.ivnsrg.aicontrolcentre.`data`.storage.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ivnsrg.aicontrolcentre.`data`.storage.entity.MessageEntity
import javax.`annotation`.processing.Generated
import kotlin.Double
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
public class MessagesDao_Impl(
  __db: RoomDatabase,
) : MessagesDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMessageEntity = object : EntityInsertAdapter<MessageEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `messages` (`id`,`threadId`,`role`,`content`,`provider`,`model`,`latencyMs`,`estimatedCost`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.threadId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        val _tmpProvider: String? = entity.provider
        if (_tmpProvider == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpProvider)
        }
        val _tmpModel: String? = entity.model
        if (_tmpModel == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpModel)
        }
        val _tmpLatencyMs: Long? = entity.latencyMs
        if (_tmpLatencyMs == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpLatencyMs)
        }
        val _tmpEstimatedCost: Double? = entity.estimatedCost
        if (_tmpEstimatedCost == null) {
          statement.bindNull(8)
        } else {
          statement.bindDouble(8, _tmpEstimatedCost)
        }
        statement.bindLong(9, entity.createdAt)
      }
    }
  }

  public override suspend fun insert(message: MessageEntity): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfMessageEntity.insertAndReturnId(_connection, message)
    _result
  }

  public override fun observeMessages(threadId: Long): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE threadId = ? ORDER BY createdAt ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, threadId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfProvider: Int = getColumnIndexOrThrow(_stmt, "provider")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfLatencyMs: Int = getColumnIndexOrThrow(_stmt, "latencyMs")
        val _columnIndexOfEstimatedCost: Int = getColumnIndexOrThrow(_stmt, "estimatedCost")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpThreadId: Long
          _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpProvider: String?
          if (_stmt.isNull(_columnIndexOfProvider)) {
            _tmpProvider = null
          } else {
            _tmpProvider = _stmt.getText(_columnIndexOfProvider)
          }
          val _tmpModel: String?
          if (_stmt.isNull(_columnIndexOfModel)) {
            _tmpModel = null
          } else {
            _tmpModel = _stmt.getText(_columnIndexOfModel)
          }
          val _tmpLatencyMs: Long?
          if (_stmt.isNull(_columnIndexOfLatencyMs)) {
            _tmpLatencyMs = null
          } else {
            _tmpLatencyMs = _stmt.getLong(_columnIndexOfLatencyMs)
          }
          val _tmpEstimatedCost: Double?
          if (_stmt.isNull(_columnIndexOfEstimatedCost)) {
            _tmpEstimatedCost = null
          } else {
            _tmpEstimatedCost = _stmt.getDouble(_columnIndexOfEstimatedCost)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item =
              MessageEntity(_tmpId,_tmpThreadId,_tmpRole,_tmpContent,_tmpProvider,_tmpModel,_tmpLatencyMs,_tmpEstimatedCost,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear(): Int {
    val _sql: String = "DELETE FROM messages"
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

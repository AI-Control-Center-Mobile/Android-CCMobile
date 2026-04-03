package com.ivnsrg.aicontrolcentre.`data`.storage.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.ivnsrg.aicontrolcentre.`data`.storage.entity.CachedModelEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ModelsDao_Impl(
  __db: RoomDatabase,
) : ModelsDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCachedModelEntity: EntityInsertAdapter<CachedModelEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCachedModelEntity = object : EntityInsertAdapter<CachedModelEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `cached_models` (`id`,`provider`,`model`,`label`,`supportsChat`,`supportsCompare`,`cachedAt`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CachedModelEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.provider)
        statement.bindText(3, entity.model)
        statement.bindText(4, entity.label)
        val _tmp: Int = if (entity.supportsChat) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        val _tmp_1: Int = if (entity.supportsCompare) 1 else 0
        statement.bindLong(6, _tmp_1.toLong())
        statement.bindLong(7, entity.cachedAt)
      }
    }
  }

  public override suspend fun insertAll(models: List<CachedModelEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCachedModelEntity.insert(_connection, models)
  }

  public override suspend fun getAll(): List<CachedModelEntity> {
    val _sql: String = "SELECT * FROM cached_models ORDER BY label ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProvider: Int = getColumnIndexOrThrow(_stmt, "provider")
        val _columnIndexOfModel: Int = getColumnIndexOrThrow(_stmt, "model")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfSupportsChat: Int = getColumnIndexOrThrow(_stmt, "supportsChat")
        val _columnIndexOfSupportsCompare: Int = getColumnIndexOrThrow(_stmt, "supportsCompare")
        val _columnIndexOfCachedAt: Int = getColumnIndexOrThrow(_stmt, "cachedAt")
        val _result: MutableList<CachedModelEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CachedModelEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpProvider: String
          _tmpProvider = _stmt.getText(_columnIndexOfProvider)
          val _tmpModel: String
          _tmpModel = _stmt.getText(_columnIndexOfModel)
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpSupportsChat: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSupportsChat).toInt()
          _tmpSupportsChat = _tmp != 0
          val _tmpSupportsCompare: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfSupportsCompare).toInt()
          _tmpSupportsCompare = _tmp_1 != 0
          val _tmpCachedAt: Long
          _tmpCachedAt = _stmt.getLong(_columnIndexOfCachedAt)
          _item =
              CachedModelEntity(_tmpId,_tmpProvider,_tmpModel,_tmpLabel,_tmpSupportsChat,_tmpSupportsCompare,_tmpCachedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear(): Int {
    val _sql: String = "DELETE FROM cached_models"
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

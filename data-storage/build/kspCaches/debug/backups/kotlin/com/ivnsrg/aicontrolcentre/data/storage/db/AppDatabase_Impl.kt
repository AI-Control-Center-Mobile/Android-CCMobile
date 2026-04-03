package com.ivnsrg.aicontrolcentre.`data`.storage.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.MessagesDao
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.MessagesDao_Impl
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ModelsDao_Impl
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ProjectsDao
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ProjectsDao_Impl
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ThreadsDao
import com.ivnsrg.aicontrolcentre.`data`.storage.dao.ThreadsDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _projectsDao: Lazy<ProjectsDao> = lazy {
    ProjectsDao_Impl(this)
  }

  private val _threadsDao: Lazy<ThreadsDao> = lazy {
    ThreadsDao_Impl(this)
  }

  private val _messagesDao: Lazy<MessagesDao> = lazy {
    MessagesDao_Impl(this)
  }

  private val _modelsDao: Lazy<ModelsDao> = lazy {
    ModelsDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "92db0aabba191c074caf58a25d38f387", "6ce6a85606f68a1bc0469c9d37c0d03b") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `threads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_projectId` ON `threads` (`projectId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `threadId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `provider` TEXT, `model` TEXT, `latencyMs` INTEGER, `estimatedCost` REAL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`threadId`) REFERENCES `threads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_threadId` ON `messages` (`threadId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cached_models` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `model` TEXT NOT NULL, `label` TEXT NOT NULL, `supportsChat` INTEGER NOT NULL, `supportsCompare` INTEGER NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '92db0aabba191c074caf58a25d38f387')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `projects`")
        connection.execSQL("DROP TABLE IF EXISTS `threads`")
        connection.execSQL("DROP TABLE IF EXISTS `messages`")
        connection.execSQL("DROP TABLE IF EXISTS `cached_models`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsProjects: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProjects.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProjects: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProjects: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProjects: TableInfo = TableInfo("projects", _columnsProjects, _foreignKeysProjects,
            _indicesProjects)
        val _existingProjects: TableInfo = read(connection, "projects")
        if (!_infoProjects.equals(_existingProjects)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |projects(com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity).
              | Expected:
              |""".trimMargin() + _infoProjects + """
              |
              | Found:
              |""".trimMargin() + _existingProjects)
        }
        val _columnsThreads: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsThreads.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsThreads.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsThreads.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsThreads.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsThreads.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysThreads: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysThreads.add(TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION",
            listOf("projectId"), listOf("id")))
        val _indicesThreads: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesThreads.add(TableInfo.Index("index_threads_projectId", false, listOf("projectId"),
            listOf("ASC")))
        val _infoThreads: TableInfo = TableInfo("threads", _columnsThreads, _foreignKeysThreads,
            _indicesThreads)
        val _existingThreads: TableInfo = read(connection, "threads")
        if (!_infoThreads.equals(_existingThreads)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |threads(com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity).
              | Expected:
              |""".trimMargin() + _infoThreads + """
              |
              | Found:
              |""".trimMargin() + _existingThreads)
        }
        val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMessages.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("threadId", TableInfo.Column("threadId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("role", TableInfo.Column("role", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("content", TableInfo.Column("content", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("provider", TableInfo.Column("provider", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("model", TableInfo.Column("model", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("latencyMs", TableInfo.Column("latencyMs", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("estimatedCost", TableInfo.Column("estimatedCost", "REAL", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMessages.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysMessages.add(TableInfo.ForeignKey("threads", "CASCADE", "NO ACTION",
            listOf("threadId"), listOf("id")))
        val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMessages.add(TableInfo.Index("index_messages_threadId", false, listOf("threadId"),
            listOf("ASC")))
        val _infoMessages: TableInfo = TableInfo("messages", _columnsMessages, _foreignKeysMessages,
            _indicesMessages)
        val _existingMessages: TableInfo = read(connection, "messages")
        if (!_infoMessages.equals(_existingMessages)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |messages(com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity).
              | Expected:
              |""".trimMargin() + _infoMessages + """
              |
              | Found:
              |""".trimMargin() + _existingMessages)
        }
        val _columnsCachedModels: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCachedModels.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("provider", TableInfo.Column("provider", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("model", TableInfo.Column("model", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("label", TableInfo.Column("label", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("supportsChat", TableInfo.Column("supportsChat", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("supportsCompare", TableInfo.Column("supportsCompare", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCachedModels.put("cachedAt", TableInfo.Column("cachedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCachedModels: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCachedModels: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCachedModels: TableInfo = TableInfo("cached_models", _columnsCachedModels,
            _foreignKeysCachedModels, _indicesCachedModels)
        val _existingCachedModels: TableInfo = read(connection, "cached_models")
        if (!_infoCachedModels.equals(_existingCachedModels)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |cached_models(com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity).
              | Expected:
              |""".trimMargin() + _infoCachedModels + """
              |
              | Found:
              |""".trimMargin() + _existingCachedModels)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "projects", "threads",
        "messages", "cached_models")
  }

  public override fun clearAllTables() {
    super.performClear(true, "projects", "threads", "messages", "cached_models")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ProjectsDao::class, ProjectsDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ThreadsDao::class, ThreadsDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(MessagesDao::class, MessagesDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ModelsDao::class, ModelsDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun projectsDao(): ProjectsDao = _projectsDao.value

  public override fun threadsDao(): ThreadsDao = _threadsDao.value

  public override fun messagesDao(): MessagesDao = _messagesDao.value

  public override fun modelsDao(): ModelsDao = _modelsDao.value
}

package cn.pylin.xiaoyutyper.pinyin

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

enum class DictionaryState { Loading, Ready, Error }

class PinyinRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var engine: PinyinEngine? = null

    private var retryAfter = 0L

    private val _state = MutableStateFlow(DictionaryState.Loading)
    val state: StateFlow<DictionaryState> = _state.asStateFlow()

    suspend fun engine(): PinyinEngine? {
        engine?.let { return it }
        return mutex.withLock {
            engine ?: loadEngine()
        }
    }

    private suspend fun loadEngine(): PinyinEngine? {
        if (retryAfter > SystemClock.elapsedRealtime()) return null
        _state.value = DictionaryState.Loading
        return try {
            val database = withContext(Dispatchers.IO) { openDatabase() }
            val loaded = PinyinEngine(SqlitePinyinDictionary(database))
            engine = loaded
            retryAfter = 0L
            _state.value = DictionaryState.Ready
            loaded
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            retryAfter = SystemClock.elapsedRealtime() + RETRY_DELAY_MS
            _state.value = DictionaryState.Error
            null
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        var databaseFile = ensureDatabase()
        var database = open(databaseFile)
        if (database.version != DB_VERSION) {
            database.close()
            databaseFile.delete()
            databaseFile = ensureDatabase()
            database = open(databaseFile)
            if (database.version != DB_VERSION) {
                database.close()
                throw IllegalStateException("Dictionary version mismatch")
            }
        }
        return database
    }

    private fun open(databaseFile: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )

    private fun removeStaleDatabases() {
        appContext.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(DB_NAME)) return@forEach
            if (DATABASE_FILE.matches(file.name)) file.delete()
        }
    }

    private fun ensureDatabase(): File {
        removeStaleDatabases()
        val target = File(appContext.filesDir, DB_NAME)
        if (target.isFile && target.length() > 0L) return target

        val temporary = File(appContext.filesDir, "$DB_NAME.tmp")
        temporary.delete()
        try {
            appContext.assets.open(ASSET_NAME).use { asset ->
                XZInputStream(
                    BufferedInputStream(asset, BUFFER_SIZE),
                    XZ_MEMORY_LIMIT_KIB,
                ).use { archive ->
                    FileOutputStream(temporary).use { output ->
                        archive.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (t: Throwable) {
            temporary.delete()
            throw t
        }
        return target
    }

    companion object {
        private const val DB_VERSION = 1
        private const val DB_NAME = "pinyin-v$DB_VERSION.db"
        private const val ASSET_NAME = "$DB_NAME.xz"
        private val DATABASE_FILE = Regex("""^pinyin-v\d+\.db(?:\.tmp|-journal|-wal|-shm)?$""")
        private const val BUFFER_SIZE = 256 * 1024
        private const val XZ_MEMORY_LIMIT_KIB = 64 * 1024
        private const val RETRY_DELAY_MS = 5_000L

        @Volatile
        private var instance: PinyinRepository? = null

        fun get(context: Context): PinyinRepository =
            instance ?: synchronized(this) {
                instance ?: PinyinRepository(context).also { instance = it }
            }
    }
}

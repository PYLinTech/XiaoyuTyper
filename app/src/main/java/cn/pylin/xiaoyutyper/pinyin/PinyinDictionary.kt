package cn.pylin.xiaoyutyper.pinyin

import android.database.sqlite.SQLiteDatabase

private val prefixTopSql = """
    SELECT w.word, p.freq
    FROM prefix_top p
    JOIN words w ON w.id = p.word_id
    WHERE p.prefix = ?
    ORDER BY p.freq DESC
    LIMIT ?
""".trimIndent()

private val prefixRangeSql = """
    SELECT w.word, w.freq
    FROM entries e
    JOIN words w ON w.id = e.word_id
    WHERE e.key >= ? AND e.key < ?
    GROUP BY e.word_id
    ORDER BY w.freq DESC
    LIMIT ?
""".trimIndent()

private val initialSql = """
    SELECT w.word, w.freq
    FROM initial_top i
    JOIN words w ON w.id = i.word_id
    WHERE i.key = ?
    ORDER BY w.freq DESC
    LIMIT ?
""".trimIndent()

private val exactSql = """
    SELECT w.word, w.freq
    FROM entries e
    JOIN words w ON w.id = e.word_id
    WHERE e.key = ?
    ORDER BY w.freq DESC
    LIMIT ?
""".trimIndent()

data class DictionaryWord(
    val word: String,
    val frequency: Int,
)

class SqlitePinyinDictionary(private val database: SQLiteDatabase) {

    fun wordsForPrefix(prefix: String, limit: Int): List<DictionaryWord> =
        if (prefix.length <= 2) {
            queryWords(prefixTopSql, arrayOf(prefix, limit.toString()), limit)
        } else {
            queryWords(prefixRangeSql, arrayOf(prefix, prefix + "\uFFFF", limit.toString()), limit)
        }

    fun initialWords(key: String, limit: Int): List<DictionaryWord> =
        queryWords(initialSql, arrayOf(key, limit.toString()), limit)

    fun exactWords(key: String, limit: Int): List<DictionaryWord> =
        queryWords(exactSql, arrayOf(key, limit.toString()), limit)

    private fun queryWords(
        sql: String,
        arguments: Array<String>,
        limit: Int,
    ): List<DictionaryWord> {
        val words = ArrayList<DictionaryWord>(minOf(limit, 128))
        database.rawQuery(sql, arguments).use { cursor ->
            while (cursor.moveToNext()) {
                words.add(DictionaryWord(cursor.getString(0), cursor.getInt(1)))
            }
        }
        return words
    }
}

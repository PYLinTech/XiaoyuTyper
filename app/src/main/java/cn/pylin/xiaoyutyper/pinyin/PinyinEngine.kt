package cn.pylin.xiaoyutyper.pinyin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PinyinEngine(private val dictionary: SqlitePinyinDictionary) {

    private val cache = object : LinkedHashMap<String, List<PinyinCandidate>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<PinyinCandidate>>?,
        ): Boolean = size > CACHE_SIZE
    }

    suspend fun getCandidates(input: String): List<PinyinCandidate> {
        if (input.isEmpty()) return emptyList()

        synchronized(cache) { cache[input] }?.let { return it }

        return withContext(Dispatchers.IO) {
            synchronized(cache) { cache[input] }?.let { return@withContext it }
            val candidates = computeCandidates(input)
            synchronized(cache) { cache[input] = candidates }
            candidates
        }
    }

    private suspend fun computeCandidates(input: String): List<PinyinCandidate> {
        val collected = ArrayList<PinyinCandidate>(CANDIDATE_LIMIT)
        val indexByWord = HashMap<String, Int>(CANDIDATE_LIMIT)

        fun upsert(
            words: List<DictionaryWord>,
            matchedLength: Int,
            initialMatch: Boolean = false,
            exactMatch: Boolean = false,
        ) {
            for (entry in words) {
                val existingIndex = indexByWord[entry.word]
                if (existingIndex == null) {
                    indexByWord[entry.word] = collected.size
                    collected.add(
                        PinyinCandidate(
                            word = entry.word,
                            matchedLength = matchedLength,
                            frequency = entry.frequency,
                            initialMatch = initialMatch,
                            exactMatch = exactMatch,
                        ),
                    )
                } else {
                    val existing = collected[existingIndex]
                    if (matchedLength > existing.matchedLength) {
                        collected[existingIndex] = PinyinCandidate(
                            word = entry.word,
                            matchedLength = matchedLength,
                            frequency = entry.frequency,
                            initialMatch = initialMatch,
                            exactMatch = exactMatch,
                        )
                    } else if (matchedLength == existing.matchedLength) {
                        val merged = existing.copy(
                            initialMatch = existing.initialMatch || initialMatch,
                            exactMatch = existing.exactMatch || exactMatch,
                        )
                        if (merged != existing) collected[existingIndex] = merged
                    }
                }
            }
        }

        currentCoroutineContext().ensureActive()
        upsert(dictionary.wordsForPrefix(input, CANDIDATE_LIMIT), input.length)

        currentCoroutineContext().ensureActive()
        upsert(
            dictionary.exactWords(input, EXACT_LIMIT),
            input.length,
            exactMatch = true,
        )

        if (input.length <= INITIAL_MAX_LENGTH) {
            currentCoroutineContext().ensureActive()
            upsert(
                dictionary.initialWords(input, INITIAL_LIMIT),
                input.length,
                initialMatch = true,
            )
        }

        for (prefixLength in input.length - 1 downTo 1) {
            if (input.length > SHORT_INPUT_MAX_LENGTH && collected.size >= CANDIDATE_LIMIT) break
            currentCoroutineContext().ensureActive()
            val limit = if (prefixLength == 1) SINGLE_LETTER_PREFIX_CAP else CANDIDATE_LIMIT
            upsert(
                dictionary.wordsForPrefix(
                    input.substring(0, prefixLength),
                    limit,
                ),
                prefixLength,
            )
        }

        currentCoroutineContext().ensureActive()
        return PinyinRanker.mergeAndRank(collected, input.length)
    }

    private companion object {
        const val SINGLE_LETTER_PREFIX_CAP = 200
        const val INITIAL_MAX_LENGTH = 3
        const val INITIAL_LIMIT = 32
        const val EXACT_LIMIT = 64
        const val CACHE_SIZE = 200
    }
}

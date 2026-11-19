package cn.pylin.xiaoyutyper.pinyin

internal const val CANDIDATE_LIMIT = 300
internal const val SHORT_INPUT_MAX_LENGTH = 2

data class PinyinCandidate(
    val word: String,
    val matchedLength: Int,
    val frequency: Int,
    val initialMatch: Boolean = false,
    val exactMatch: Boolean = false,
)

internal object PinyinRanker {

    private const val SHORT_RANKING_MAX_INPUT_LENGTH = 6
    private const val SINGLE_CHAR_BOOST = 50_000
    private const val TWO_CHAR_BOOST = 25_000
    private const val THREE_CHAR_PENALTY = 40_000
    private const val LONG_WORD_PENALTY = 60_000
    private const val INITIAL_MATCH_BOOST = 45_000
    private const val EXACT_MATCH_BOOST = 80_000
    private const val SINGLE_CHAR_FLOOR = 2
    private const val SINGLE_CHAR_FLOOR_WINDOW = 10
    private const val SINGLE_CHAR_INSERT_AT = 2

    fun mergeAndRank(items: List<PinyinCandidate>, inputLength: Int): List<PinyinCandidate> {
        if (items.isEmpty()) return emptyList()

        val sorted = items
            .map { it to score(it, inputLength) }
            .sortedWith(
                compareByDescending<Pair<PinyinCandidate, Int>> { it.first.matchedLength }
                    .thenByDescending { it.second },
            )
            .map { it.first }
        val floored = applySingleCharFloor(sorted, inputLength)
        return if (floored.size <= CANDIDATE_LIMIT) floored else floored.take(CANDIDATE_LIMIT)
    }

    private fun score(item: PinyinCandidate, inputLength: Int): Int {
        var score = item.frequency
        when {
            item.exactMatch -> score += EXACT_MATCH_BOOST
            item.initialMatch -> score += INITIAL_MATCH_BOOST
        }
        if (inputLength <= SHORT_RANKING_MAX_INPUT_LENGTH) {
            score += when (item.word.length) {
                1 -> SINGLE_CHAR_BOOST
                2 -> TWO_CHAR_BOOST
                3 -> if (item.exactMatch || item.initialMatch) 0 else -THREE_CHAR_PENALTY
                else -> if (item.exactMatch || item.initialMatch) 0 else -LONG_WORD_PENALTY
            }
        }
        return score
    }

    private fun applySingleCharFloor(
        items: List<PinyinCandidate>,
        inputLength: Int,
    ): List<PinyinCandidate> {
        if (inputLength > SHORT_INPUT_MAX_LENGTH || items.isEmpty()) return items

        val top = items.take(minOf(items.size, SINGLE_CHAR_FLOOR_WINDOW))
        val existingSingles = top.count { it.word.length == 1 }
        if (existingSingles >= SINGLE_CHAR_FLOOR) return items

        val missing = SINGLE_CHAR_FLOOR - existingSingles
        val promote = items
            .drop(top.size)
            .filter { it.word.length == 1 }
            .take(missing)
        if (promote.isEmpty()) return items

        val result = items.toMutableList()
        for (item in promote) result.remove(item)
        val insertAt = minOf(result.size, maxOf(SINGLE_CHAR_INSERT_AT, existingSingles))
        result.addAll(insertAt, promote)
        return result
    }
}

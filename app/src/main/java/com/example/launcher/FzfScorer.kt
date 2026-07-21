package com.example.launcher

object FzfScorer {
    private val BOUNDARY_CHARS = charArrayOf(' ', '-', '_', '/', '\t')

    fun score(query: String, text: String): Int {
        if (query.isBlank()) return Int.MAX_VALUE
        val q = query.lowercase().trim()
        val t = text.lowercase()

        // If query contains spaces, treat as multi-word: every word must match
        if (q.contains(' ')) {
            val words = q.split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return Int.MAX_VALUE
            var totalScore = 0
            for (word in words) {
                val wordScore = scoreSingle(word, t)
                if (wordScore == 0) return 0  // ALL words must match somewhere
                totalScore += wordScore
            }
            return totalScore
        }

        return scoreSingle(q, t)
    }

    private fun isWordBoundary(text: String, i: Int): Boolean =
        i == 0 || text[i - 1] in BOUNDARY_CHARS

    // Big bonus when the query is an exact, contiguous prefix of some "word"
    // in the text — either the whole text, or right after a boundary like a
    // space, dash, underscore, or slash. This makes a clean prefix match
    // (e.g. "banc" -> the "Banca" in "Mia Banca" or "Finance - Banca") always
    // outrank a merely fuzzy, gappy match elsewhere in a string (e.g.
    // "banc" -> "bandcamp", which only fuzzy-matches b-a-n-_-c), regardless
    // of where in the string the clean match happens to sit.
    private fun wordPrefixBonus(query: String, text: String): Int {
        for (i in text.indices) {
            if (isWordBoundary(text, i) && text.startsWith(query, i)) {
                return if (i == 0) 700 else 550
            }
        }
        return 0
    }

    private fun scoreSingle(query: String, text: String): Int {
        var bestScore = 0

        for (start in text.indices) {
            if (text[start] != query.getOrNull(0)) continue

            var score = 0
            var qIdx = 0
            var lastMatch = -1
            var consecutive = 0
            var firstMatchPos = -1

            for (i in start..<text.length) {
                if (qIdx < query.length && text[i] == query[qIdx]) {
                    score += 10

                    if (lastMatch != -1 && i == lastMatch + 1) {
                        consecutive++
                        score += consecutive * 10
                    } else {
                        consecutive = 0
                    }

                    val isBoundary = isWordBoundary(text, i)

                    if (qIdx == 0) {
                        if (isBoundary) score += 100
                    } else if (isBoundary) {
                        score += 15
                    }

                    if (firstMatchPos == -1) firstMatchPos = i

                    if (lastMatch != -1) {
                        // Penalize skipped/non-consecutive characters more
                        // heavily so a gappy match at the start of a string
                        // can't out-score a clean match deeper in the string.
                        score -= (i - lastMatch - 1) * 10
                    }

                    lastMatch = i
                    qIdx++
                }
            }

            if (firstMatchPos >= 0) {
                score -= firstMatchPos
            }

            if (qIdx >= query.length && score > bestScore) {
                bestScore = score
            }
        }

        if (bestScore > 0) {
            bestScore += wordPrefixBonus(query, text)
            if (text == query) {
                bestScore += 1000
            }
        }

        return bestScore
    }
}

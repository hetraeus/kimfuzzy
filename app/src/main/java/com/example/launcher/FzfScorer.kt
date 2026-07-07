package com.example.launcher

object FzfScorer {
    fun score(query: String, text: String): Int {
        if (query.isBlank()) return Int.MAX_VALUE
        val q = query.lowercase().trim()
        val t = text.lowercase()

        // If query contains spaces, treat as multi-word: score each word and sum
        if (q.contains(' ')) {
            val words = q.split(' ')
            var totalScore = 0
            for (word in words) {
                if (word.isBlank()) continue
                totalScore += scoreSingle(word, t)
            }
            return totalScore
        }

        return scoreSingle(q, t)
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
                    score += consecutive * 10   // increased from 5
                } else {
                    consecutive = 0
                }

                val isWordBoundary = i == 0 ||
                    text[i - 1] == ' ' || text[i - 1] == '-' || text[i - 1] == '_' || text[i - 1] == '/' || text[i - 1] == '\t'

                if (qIdx == 0) {
                    if (isWordBoundary) score += 100
                    if (i == 0) score += 200    // bonus for matching at the very start
                } else if (isWordBoundary) {
                    score += 15
                }

                if (firstMatchPos == -1) firstMatchPos = i

                if (lastMatch != -1) {
                    score -= (i - lastMatch - 1) * 5   // increased from 3
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
    return bestScore
  }
}

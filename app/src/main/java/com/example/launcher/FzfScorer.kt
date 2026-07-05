package com.example.launcher

object FzfScorer {
    fun score(query: String, text: String): Int {
        if (query.isBlank()) return Int.MAX_VALUE
        val q = query.lowercase().trim()
        val t = text.lowercase()

        var bestScore = 0

        // Try matching from each occurrence of the first query char
        for (start in t.indices) {
            if (t[start] != q.getOrNull(0)) continue

            var score = 0
            var qIdx = 0
            var lastMatch = -1
            var consecutive = 0
            var firstMatchPos = -1

            for (i in start..<t.length) {
                if (qIdx < q.length && t[i] == q[qIdx]) {
                    score += 10

                    if (lastMatch != -1 && i == lastMatch + 1) {
                        consecutive++
                        score += consecutive * 5
                    } else {
                        consecutive = 0
                    }

                    val isWordBoundary = i == 0 || 
                        t[i - 1] == ' ' || t[i - 1] == '-' || t[i - 1] == '_' || t[i - 1] == '/' || t[i - 1] == '\t'

                    if (qIdx == 0) {
                        // First char of query: huge bonus for word boundary
                        if (isWordBoundary) {
                            score += 100
                        }
                    } else if (isWordBoundary) {
                        score += 15
                    }

                    if (firstMatchPos == -1) {
                        firstMatchPos = i
                    }

                    if (lastMatch != -1) {
                        score -= (i - lastMatch - 1) * 3
                    }

                    lastMatch = i
                    qIdx++
                }
            }

            // Bonus for earlier first match
            if (firstMatchPos >= 0) {
                score -= firstMatchPos
            }

            if (qIdx >= q.length && score > bestScore) {
                bestScore = score
            }
        }

        return bestScore
    }
}

package com.example.launcher

object FzfScorer {
    fun score(query: String, text: String): Int {
        if (query.isBlank()) return Int.MAX_VALUE
        val q = query.lowercase().trim()
        val t = text.lowercase()

        var score = 0
        var qIdx = 0
        var lastMatch = -1
        var consecutive = 0

        for (i in t.indices) {
            if (qIdx < q.length && t[i] == q[qIdx]) {
                score += 10

                if (lastMatch != -1 && i == lastMatch + 1) {
                    consecutive++
                    score += consecutive * 5
                } else {
                    consecutive = 0
                }

                if (i == 0 || t[i - 1] == ' ' || t[i - 1] == '-' || t[i - 1] == '_' || t[i - 1] == '/') {
                    score += 15
                }

                if (lastMatch != -1) {
                    score -= (i - lastMatch - 1) * 3
                }

                lastMatch = i
                qIdx++
            }
        }

        return if (qIdx >= q.length) score else 0
    }
}

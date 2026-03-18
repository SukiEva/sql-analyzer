package com.github.sukieva.sqlanalyzer.util

object ExplainSqlBuilder {
    fun build(sql: String): String {
        val normalized = sql.trim().trimEnd(';')
        require(normalized.isNotBlank()) { "SQL is empty." }

        return when {
            normalized.startsWith("EXPLAIN", ignoreCase = true) && normalized.contains("FORMAT JSON", ignoreCase = true) -> normalized
            normalized.startsWith("EXPLAIN", ignoreCase = true) -> normalized.replaceFirst(Regex("(?i)^EXPLAIN\\s+"), "EXPLAIN (FORMAT JSON) ")
            else -> "EXPLAIN (FORMAT JSON) $normalized"
        }
    }
}

package com.github.sukieva.sqlanalyzer.model

data class ExplainPlan(
    val root: PlanNode,
    val planningTimeMs: Double? = null,
    val executionTimeMs: Double? = null,
    val settings: Map<String, String> = emptyMap(),
)

data class PlanNode(
    val nodeType: String,
    val relationName: String? = null,
    val startupCost: Double? = null,
    val totalCost: Double? = null,
    val planRows: Long? = null,
    val planWidth: Int? = null,
    val actualStartupTime: Double? = null,
    val actualTotalTime: Double? = null,
    val actualRows: Long? = null,
    val actualLoops: Long? = null,
    val parallelAware: Boolean? = null,
    val parentRelationship: String? = null,
    val workersPlanned: Int? = null,
    val workersLaunched: Int? = null,
    val sharedHitBlocks: Long? = null,
    val sharedReadBlocks: Long? = null,
    val tempReadBlocks: Long? = null,
    val tempWrittenBlocks: Long? = null,
    val ioReadTime: Double? = null,
    val ioWriteTime: Double? = null,
    val extras: Map<String, String> = emptyMap(),
    val children: List<PlanNode> = emptyList(),
) {
    val title: String
        get() = relationName?.let { "$nodeType · $it" } ?: nodeType
}

data class AnalyzerViewState(
    val sqlText: String,
    val explainSql: String = "",
    val planJson: String,
    val plan: ExplainPlan? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

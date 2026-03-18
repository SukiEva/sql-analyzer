package com.github.sukieva.sqlanalyzer.services

import com.github.sukieva.sqlanalyzer.model.AnalyzerViewState
import com.github.sukieva.sqlanalyzer.model.ExplainPlan
import com.github.sukieva.sqlanalyzer.parser.ExplainPlanParser
import com.github.sukieva.sqlanalyzer.util.ExplainSqlBuilder
import com.intellij.openapi.components.Service
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class MyProjectService(
    private val parser: ExplainPlanParser = ExplainPlanParser(),
) {
    private val listeners = CopyOnWriteArrayList<(AnalyzerViewState) -> Unit>()

    @Volatile
    private var state = AnalyzerViewState(
        sqlText = DEFAULT_SQL,
        explainSql = ExplainSqlBuilder.build(DEFAULT_SQL),
        planJson = DEFAULT_PLAN_JSON,
        plan = parser.parse(DEFAULT_PLAN_JSON),
    )

    fun currentState(): AnalyzerViewState = state

    fun addListener(listener: (AnalyzerViewState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (AnalyzerViewState) -> Unit) {
        listeners -= listener
    }

    fun prepareExplain(sqlText: String) {
        val normalized = sqlText.trim().takeIf(String::isNotBlank) ?: return
        updateState(
            state.copy(
                sqlText = normalized,
                explainSql = ExplainSqlBuilder.build(normalized),
                errorMessage = null,
                isLoading = true,
            ),
        )
    }

    fun applyExplainResult(sqlText: String, planJson: String) {
        val explainSql = ExplainSqlBuilder.build(sqlText)
        val analyzedState = runCatching {
            val plan = parser.parse(planJson)
            AnalyzerViewState(
                sqlText = sqlText,
                explainSql = explainSql,
                planJson = planJson,
                plan = plan,
                isLoading = false,
            )
        }.getOrElse { error ->
            AnalyzerViewState(
                sqlText = sqlText,
                explainSql = explainSql,
                planJson = planJson,
                errorMessage = error.message ?: error.javaClass.simpleName,
                isLoading = false,
            )
        }
        updateState(analyzedState)
    }

    fun applyExplainFailure(sqlText: String, message: String) {
        updateState(
            state.copy(
                sqlText = sqlText,
                explainSql = runCatching { ExplainSqlBuilder.build(sqlText) }.getOrDefault(state.explainSql),
                errorMessage = message,
                isLoading = false,
            ),
        )
    }

    fun updateSql(sqlText: String) {
        updateState(state.copy(sqlText = sqlText, explainSql = runCatching { ExplainSqlBuilder.build(sqlText) }.getOrDefault(state.explainSql)))
    }

    fun updatePlanJson(planJson: String) {
        updateState(state.copy(planJson = planJson))
    }

    fun analyze() {
        val analyzedState = runCatching {
            val plan = parser.parse(state.planJson)
            state.copy(plan = plan, errorMessage = null, isLoading = false)
        }.getOrElse { error ->
            state.copy(plan = null, errorMessage = error.message ?: error.javaClass.simpleName, isLoading = false)
        }
        updateState(analyzedState)
    }

    fun loadDemo() {
        updateState(
            AnalyzerViewState(
                sqlText = DEFAULT_SQL,
                explainSql = ExplainSqlBuilder.build(DEFAULT_SQL),
                planJson = DEFAULT_PLAN_JSON,
                plan = parser.parse(DEFAULT_PLAN_JSON),
            ),
        )
    }

    fun loadPlan(planJson: String): ExplainPlan = parser.parse(planJson)

    private fun updateState(newState: AnalyzerViewState) {
        state = newState
        listeners.forEach { it(newState) }
    }

    companion object {
        val DEFAULT_SQL = """
            SELECT c.customer_name, SUM(o.amount) AS revenue
            FROM customers c
            JOIN orders o ON o.customer_id = c.id
            WHERE o.created_at >= now() - interval '30 days'
            GROUP BY c.customer_name
            ORDER BY revenue DESC
            LIMIT 10;
        """.trimIndent()

        val DEFAULT_PLAN_JSON = """
            [
              {
                "Plan": {
                  "Node Type": "Limit",
                  "Startup Cost": 128.47,
                  "Total Cost": 128.49,
                  "Plan Rows": 10,
                  "Plan Width": 40,
                  "Actual Startup Time": 1.18,
                  "Actual Total Time": 1.22,
                  "Actual Rows": 10,
                  "Actual Loops": 1,
                  "Parallel Aware": false,
                  "Plans": [
                    {
                      "Node Type": "Sort",
                      "Startup Cost": 128.47,
                      "Total Cost": 130.89,
                      "Plan Rows": 968,
                      "Plan Width": 40,
                      "Sort Key": ["(sum(o.amount)) DESC"],
                      "Plans": [
                        {
                          "Node Type": "HashAggregate",
                          "Startup Cost": 75.15,
                          "Total Cost": 84.83,
                          "Plan Rows": 968,
                          "Plan Width": 40,
                          "Group Key": ["c.customer_name"],
                          "Plans": [
                            {
                              "Node Type": "Hash Join",
                              "Startup Cost": 33.20,
                              "Total Cost": 62.05,
                              "Plan Rows": 2619,
                              "Plan Width": 24,
                              "Hash Cond": "(o.customer_id = c.id)",
                              "Plans": [
                                {
                                  "Node Type": "Seq Scan",
                                  "Relation Name": "orders",
                                  "Startup Cost": 0.00,
                                  "Total Cost": 21.30,
                                  "Plan Rows": 1130,
                                  "Plan Width": 16,
                                  "Shared Hit Blocks": 11,
                                  "Filter": "(created_at >= (now() - '30 days'::interval))"
                                },
                                {
                                  "Node Type": "Seq Scan",
                                  "Relation Name": "customers",
                                  "Startup Cost": 0.00,
                                  "Total Cost": 19.10,
                                  "Plan Rows": 1021,
                                  "Plan Width": 16,
                                  "Workers Planned": 1,
                                  "Workers Launched": 1
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                },
                "Planning Time": 0.55,
                "Execution Time": 1.63,
                "Settings": {
                  "work_mem": "4MB",
                  "jit": "on"
                }
              }
            ]
        """.trimIndent()
    }
}

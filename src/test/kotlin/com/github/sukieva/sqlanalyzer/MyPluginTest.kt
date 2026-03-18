package com.github.sukieva.sqlanalyzer

import com.github.sukieva.sqlanalyzer.parser.ExplainPlanParser
import com.github.sukieva.sqlanalyzer.services.MyProjectService
import com.github.sukieva.sqlanalyzer.util.ExplainSqlBuilder
import com.github.sukieva.sqlanalyzer.util.SqlSelectionExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPluginTest {
    private val parser = ExplainPlanParser()

    @Test
    fun parsesRootPlanAndChildren() {
        val plan = parser.parse(
            """
                [
                  {
                    "Plan": {
                      "Node Type": "Hash Join",
                      "Startup Cost": 12.4,
                      "Total Cost": 44.8,
                      "Plan Rows": 23,
                      "Plan Width": 16,
                      "Plans": [
                        { "Node Type": "Seq Scan", "Relation Name": "orders" },
                        { "Node Type": "Seq Scan", "Relation Name": "customers" }
                      ]
                    },
                    "Planning Time": 0.12,
                    "Execution Time": 1.54
                  }
                ]
            """.trimIndent(),
        )

        assertEquals("Hash Join", plan.root.nodeType)
        assertEquals(2, plan.root.children.size)
        assertEquals("orders", plan.root.children.first().relationName)
        assertEquals(0.12, plan.planningTimeMs!!, 0.001)
        assertEquals(1.54, plan.executionTimeMs!!, 0.001)
    }

    @Test
    fun collectsAdditionalNodeMetadata() {
        val plan = parser.parse(
            """
                {
                  "Plan": {
                    "Node Type": "Seq Scan",
                    "Relation Name": "orders",
                    "Filter": "(amount > 100)",
                    "Parallel Aware": true,
                    "Workers Planned": 2,
                    "Shared Hit Blocks": 9
                  }
                }
            """.trimIndent(),
        )

        assertEquals("(amount > 100)", plan.root.extras["Filter"])
        assertTrue(plan.root.parallelAware == true)
        assertEquals(2, plan.root.workersPlanned)
        assertEquals(9L, plan.root.sharedHitBlocks)
    }

    @Test
    fun rejectsMissingPlanField() {
        val error = runCatching { parser.parse("{}") }.exceptionOrNull()

        assertTrue(error != null)
        assertTrue(error!!.message!!.contains("Plan"))
    }

    @Test
    fun buildsExplainSqlOnlyWhenNeeded() {
        assertEquals(
            "EXPLAIN (FORMAT JSON) SELECT * FROM orders",
            ExplainSqlBuilder.build("SELECT * FROM orders;"),
        )
        assertEquals(
            "EXPLAIN (FORMAT JSON) SELECT * FROM orders",
            ExplainSqlBuilder.build("EXPLAIN SELECT * FROM orders"),
        )
    }

    @Test
    fun extractsStatementAroundCaret() {
        val sql = "SELECT 1;\nSELECT 2;\nSELECT 3;"
        assertEquals("SELECT 2", SqlSelectionExtractor.extractStatement(sql, sql.indexOf("SELECT 2") + 3))
    }

    @Test
    fun serviceTracksLoadingAndResult() {
        val service = MyProjectService()
        service.prepareExplain("select * from t")

        assertTrue(service.currentState().isLoading)
        assertTrue(service.currentState().explainSql.startsWith("EXPLAIN (FORMAT JSON)"))

        service.applyExplainResult(
            "select * from t",
            """
                {
                  "Plan": {
                    "Node Type": "Seq Scan",
                    "Relation Name": "t"
                  },
                  "Execution Time": 0.33
                }
            """.trimIndent(),
        )

        assertFalse(service.currentState().isLoading)
        assertEquals("Seq Scan", service.currentState().plan!!.root.nodeType)
    }
}

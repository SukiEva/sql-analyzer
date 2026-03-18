package com.github.sukieva.sqlanalyzer.parser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sukieva.sqlanalyzer.model.ExplainPlan
import com.github.sukieva.sqlanalyzer.model.PlanNode

class ExplainPlanParser(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    fun parse(planJson: String): ExplainPlan {
        require(planJson.isNotBlank()) { "EXPLAIN (FORMAT JSON) output is empty." }

        val rootNode = objectMapper.readTree(planJson)
        val explainRoot = when {
            rootNode.isArray && rootNode.isEmpty -> error("EXPLAIN result array is empty.")
            rootNode.isArray -> rootNode[0]
            else -> rootNode
        }

        val planNode = explainRoot.path("Plan")
        require(!planNode.isMissingNode) { "Missing top-level 'Plan' field." }

        return ExplainPlan(
            root = parseNode(planNode),
            planningTimeMs = explainRoot.optionalDouble("Planning Time"),
            executionTimeMs = explainRoot.optionalDouble("Execution Time"),
            settings = explainRoot.path("Settings").toFlatMap(),
        )
    }

    private fun parseNode(node: JsonNode): PlanNode {
        return PlanNode(
            nodeType = node.requiredText("Node Type"),
            relationName = node.optionalText("Relation Name"),
            startupCost = node.optionalDouble("Startup Cost"),
            totalCost = node.optionalDouble("Total Cost"),
            planRows = node.optionalLong("Plan Rows"),
            planWidth = node.optionalInt("Plan Width"),
            actualStartupTime = node.optionalDouble("Actual Startup Time"),
            actualTotalTime = node.optionalDouble("Actual Total Time"),
            actualRows = node.optionalLong("Actual Rows"),
            actualLoops = node.optionalLong("Actual Loops"),
            parallelAware = node.optionalBoolean("Parallel Aware"),
            parentRelationship = node.optionalText("Parent Relationship"),
            workersPlanned = node.optionalInt("Workers Planned"),
            workersLaunched = node.optionalInt("Workers Launched"),
            sharedHitBlocks = node.optionalLong("Shared Hit Blocks"),
            sharedReadBlocks = node.optionalLong("Shared Read Blocks"),
            tempReadBlocks = node.optionalLong("Temp Read Blocks"),
            tempWrittenBlocks = node.optionalLong("Temp Written Blocks"),
            ioReadTime = node.optionalDouble("I/O Read Time"),
            ioWriteTime = node.optionalDouble("I/O Write Time"),
            extras = node.collectExtras(),
            children = node.path("Plans")
                .takeIf(JsonNode::isArray)
                ?.map(::parseNode)
                .orEmpty(),
        )
    }

    private fun JsonNode.collectExtras(): Map<String, String> {
        val excludedFields = setOf(
            "Node Type",
            "Relation Name",
            "Startup Cost",
            "Total Cost",
            "Plan Rows",
            "Plan Width",
            "Actual Startup Time",
            "Actual Total Time",
            "Actual Rows",
            "Actual Loops",
            "Parallel Aware",
            "Parent Relationship",
            "Workers Planned",
            "Workers Launched",
            "Shared Hit Blocks",
            "Shared Read Blocks",
            "Temp Read Blocks",
            "Temp Written Blocks",
            "I/O Read Time",
            "I/O Write Time",
            "Plans",
        )

        return fields().asSequence()
            .filterNot { (name, _) -> name in excludedFields }
            .associate { (name, value) ->
                name to when {
                    value.isValueNode -> value.asText()
                    else -> value.toPrettyString()
                }
            }
    }

    private fun JsonNode.toFlatMap(): Map<String, String> {
        if (!isObject) return emptyMap()
        return fields().asSequence().associate { (key, value) -> key to value.asText(value.toString()) }
    }

    private fun JsonNode.requiredText(fieldName: String): String {
        val value = optionalText(fieldName)
        require(!value.isNullOrBlank()) { "Missing required '$fieldName' field." }
        return value
    }

    private fun JsonNode.optionalText(fieldName: String): String? =
        path(fieldName).takeUnless(JsonNode::isMissingNode)?.asText()?.takeIf(String::isNotBlank)

    private fun JsonNode.optionalDouble(fieldName: String): Double? =
        path(fieldName).takeUnless(JsonNode::isMissingNode)?.takeIf(JsonNode::isNumber)?.doubleValue()

    private fun JsonNode.optionalLong(fieldName: String): Long? =
        path(fieldName).takeUnless(JsonNode::isMissingNode)?.takeIf(JsonNode::isNumber)?.longValue()

    private fun JsonNode.optionalInt(fieldName: String): Int? =
        path(fieldName).takeUnless(JsonNode::isMissingNode)?.takeIf(JsonNode::isNumber)?.intValue()

    private fun JsonNode.optionalBoolean(fieldName: String): Boolean? =
        path(fieldName).takeUnless(JsonNode::isMissingNode)?.takeIf(JsonNode::isBoolean)?.booleanValue()
}

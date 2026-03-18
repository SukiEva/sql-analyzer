package com.github.sukieva.sqlanalyzer.toolWindow

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sukieva.sqlanalyzer.model.AnalyzerViewState
import com.github.sukieva.sqlanalyzer.model.ExplainPlan
import com.github.sukieva.sqlanalyzer.model.PlanNode
import com.github.sukieva.sqlanalyzer.services.MyProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class SqlAnalyzerPanel(
    private val service: MyProjectService,
) : JBPanel<SqlAnalyzerPanel>(BorderLayout()), Disposable {
    private val objectMapper = ObjectMapper()
    private val sqlInput = createEditorArea()
    private val explainInput = createEditorArea().apply { isEditable = false }
    private val planInput = createEditorArea()
    private val listener: (AnalyzerViewState) -> Unit = { state -> renderState(state) }
    private val browserPanel = PlanBrowser(objectMapper)

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val topEditors = JBSplitter(false, 0.5f).apply {
            firstComponent = createEditorCard("SQL from DataGrip editor", sqlInput)
            secondComponent = createEditorCard("Generated EXPLAIN SQL", explainInput)
        }
        val left = JBSplitter(true, 0.58f).apply {
            firstComponent = topEditors
            secondComponent = createEditorCard("PostgreSQL EXPLAIN (FORMAT JSON)", planInput)
        }

        val controls = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(JButton("Use Demo", AllIcons.Actions.Reset).apply {
                addActionListener { service.loadDemo() }
            })
            add(JButton("Analyze JSON", AllIcons.Actions.Execute).apply {
                addActionListener {
                    service.updateSql(sqlInput.text)
                    service.updatePlanJson(planInput.text)
                    service.analyze()
                }
            })
        }

        val leftPanel = JPanel(BorderLayout(0, 8)).apply {
            add(left, BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }

        val main = JBSplitter(false, 0.46f).apply {
            firstComponent = leftPanel
            secondComponent = browserPanel
        }

        add(main, BorderLayout.CENTER)
        service.addListener(listener)
    }

    private fun createEditorArea(): JBTextArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    private fun createEditorCard(title: String, area: JBTextArea): JPanel {
        return JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
            )
            add(JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD)
            }, BorderLayout.NORTH)
            add(JBScrollPane(area), BorderLayout.CENTER)
            preferredSize = Dimension(520, 220)
        }
    }

    private fun renderState(state: AnalyzerViewState) {
        if (sqlInput.text != state.sqlText) {
            sqlInput.text = state.sqlText
        }
        if (explainInput.text != state.explainSql) {
            explainInput.text = state.explainSql
        }
        if (planInput.text != state.planJson) {
            planInput.text = state.planJson
        }
        browserPanel.render(state)
    }

    override fun dispose() {
        service.removeListener(listener)
        browserPanel.dispose()
    }

    private class PlanBrowser(
        private val objectMapper: ObjectMapper,
    ) : JPanel(BorderLayout()), Disposable {
        private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) {
            JBCefBrowser().also { add(it.component, BorderLayout.CENTER) }
        } else {
            null
        }

        init {
            if (browser == null) {
                add(JBLabel("JCEF is not available in this IDE runtime."), BorderLayout.NORTH)
            }
        }

        fun render(state: AnalyzerViewState) {
            val payload = mapOf(
                "sqlText" to state.sqlText,
                "explainSql" to state.explainSql,
                "plan" to state.plan?.toMap(),
                "errorMessage" to state.errorMessage,
                "isLoading" to state.isLoading,
            )
            browser?.loadHTML(buildHtml(objectMapper.writeValueAsString(payload)))
        }

        override fun dispose() {
            browser?.dispose()
        }

        private fun buildHtml(serializedState: String): String {
            val html = loadResource("/web/plan-viewer.html")
            val css = loadResource("/web/plan-viewer.css")
            val script = loadResource("/web/plan-viewer.js")
            return html
                .replace("__STYLE__", css)
                .replace("__STATE__", serializedState)
                .replace("__SCRIPT__", script)
        }

        private fun loadResource(path: String): String {
            return checkNotNull(javaClass.getResourceAsStream(path)) { "Missing web resource: $path" }
                .bufferedReader()
                .use { it.readText() }
        }

        private fun ExplainPlan.toMap(): Map<String, Any?> = mapOf(
            "planningTimeMs" to planningTimeMs,
            "executionTimeMs" to executionTimeMs,
            "settings" to settings,
            "root" to root.toMap(),
        )

        private fun PlanNode.toMap(): Map<String, Any?> = mapOf(
            "title" to title,
            "nodeType" to nodeType,
            "relationName" to relationName,
            "startupCost" to startupCost,
            "totalCost" to totalCost,
            "planRows" to planRows,
            "planWidth" to planWidth,
            "actualStartupTime" to actualStartupTime,
            "actualTotalTime" to actualTotalTime,
            "actualRows" to actualRows,
            "actualLoops" to actualLoops,
            "parallelAware" to parallelAware,
            "parentRelationship" to parentRelationship,
            "workersPlanned" to workersPlanned,
            "workersLaunched" to workersLaunched,
            "sharedHitBlocks" to sharedHitBlocks,
            "sharedReadBlocks" to sharedReadBlocks,
            "tempReadBlocks" to tempReadBlocks,
            "tempWrittenBlocks" to tempWrittenBlocks,
            "ioReadTime" to ioReadTime,
            "ioWriteTime" to ioWriteTime,
            "extras" to extras,
            "children" to children.map { it.toMap() },
        )
    }
}

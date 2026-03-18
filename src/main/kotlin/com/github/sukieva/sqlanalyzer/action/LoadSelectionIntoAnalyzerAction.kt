package com.github.sukieva.sqlanalyzer.action

import com.github.sukieva.sqlanalyzer.execution.DataGripExplainExecutor
import com.github.sukieva.sqlanalyzer.services.MyProjectService
import com.github.sukieva.sqlanalyzer.util.SqlSelectionExtractor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

class LoadSelectionIntoAnalyzerAction(
    private val executor: DataGripExplainExecutor = DataGripExplainExecutor(),
) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val sql = SqlSelectionExtractor.extract(editor)
        if (sql.isBlank()) return

        val service = project.service<MyProjectService>()
        service.prepareExplain(sql)
        ToolWindowManager.getInstance(project).getToolWindow("SQL Analyzer")?.activate(null)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running EXPLAIN in SQL Analyzer", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Executing EXPLAIN (FORMAT JSON)"
                val result = executor.execute(project, virtualFile, service.currentState().explainSql)
                ApplicationManager.getApplication().invokeLater {
                    result.onSuccess { planJson ->
                        service.applyExplainResult(sql, planJson)
                    }.onFailure { error ->
                        service.applyExplainFailure(
                            sql,
                            error.message ?: "Unable to execute EXPLAIN through the active DataGrip connection.",
                        )
                    }
                }
            }
        })
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = event.project != null && editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

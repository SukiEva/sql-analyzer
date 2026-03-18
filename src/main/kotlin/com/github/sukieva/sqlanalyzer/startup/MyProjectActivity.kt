package com.github.sukieva.sqlanalyzer.startup

import com.github.sukieva.sqlanalyzer.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<MyProjectService>().analyze()
    }
}

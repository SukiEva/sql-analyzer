package com.github.sukieva.sqlanalyzer.execution

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.lang.reflect.Proxy
import java.sql.SQLException

class DataGripExplainExecutor {
    fun execute(project: Project, virtualFile: VirtualFile?, explainSql: String): Result<String> = runCatching {
        require(explainSql.isNotBlank()) { "EXPLAIN SQL is empty." }
        val console = findConsole(project, virtualFile)
            ?: error("Open the SQL from a DataGrip query console or SQL file attached to a database session.")
        val simpleConnection = createConnection(console)
            ?: error("No active DataGrip connection matched the current console.")

        try {
            executeExplain(simpleConnection, explainSql)
        } finally {
            releaseConnection(simpleConnection)
        }
    }

    private fun findConsole(project: Project, virtualFile: VirtualFile?): Any? {
        if (virtualFile == null) return null
        val providerClass = Class.forName("com.intellij.database.console.JdbcConsoleProvider")
        val methods = providerClass.methods.filter { it.name == "getConsole" || it.name == "getValidConsole" }
        return methods.firstNotNullOfOrNull { method ->
            runCatching { method.invoke(null, project, virtualFile) }.getOrNull()
        }
    }

    private fun createConnection(console: Any): Any? {
        val managerClass = Class.forName("com.intellij.database.dataSource.DatabaseConnectionManager")
        val manager = managerClass.getMethod("getInstance").invoke(null)
        val activeConnections = managerClass.getMethod("getActiveConnections").invoke(manager) as? Iterable<*>
            ?: return null

        val consoleDataSource = console.javaClass.getMethod("getDataSource").invoke(console) ?: return null
        val consoleUrl = consoleDataSource.javaClass.getMethod("getUrl").invoke(consoleDataSource)

        val matchingConnection = activeConnections.firstOrNull { connection ->
            connection != null && runCatching {
                val point = connection.javaClass.getMethod("getConnectionPoint").invoke(connection)
                point.javaClass.getMethod("getUrl").invoke(point) == consoleUrl
            }.getOrDefault(false)
        } ?: return null

        val simpleConnectionClass = Class.forName("com.intellij.database.dataSource.SimpleDatabaseConnection")
        val connectionPoint = matchingConnection.javaClass.getMethod("getConnectionPoint").invoke(matchingConnection)
        val remoteConnection = matchingConnection.javaClass.getMethod("getRemoteConnection").invoke(matchingConnection)
        val configuration = matchingConnection.javaClass.getMethod("getConfiguration").invoke(matchingConnection)
        val requestor = matchingConnection.javaClass.getMethod("getRequestor").invoke(matchingConnection)
        val project = configuration.javaClass.getMethod("getProject").invoke(configuration)

        val constructor = simpleConnectionClass.constructors.firstOrNull { it.parameterCount == 5 }
            ?: return null
        return constructor.newInstance(connectionPoint, remoteConnection, configuration, requestor, project)
    }

    private fun executeExplain(simpleConnection: Any, explainSql: String): String {
        val dbImplUtil = Class.forName("com.intellij.database.util.DbImplUtil")
        val functionInterface = Class.forName("java.util.function.Function")
        val applyMethod = functionInterface.getMethod("apply", Any::class.java)

        val mapper = Proxy.newProxyInstance(
            functionInterface.classLoader,
            arrayOf(functionInterface),
        ) { _, method, args ->
            if (method.name != applyMethod.name) {
                return@newProxyInstance null
            }
            val resultSet = args?.firstOrNull() ?: return@newProxyInstance null
            try {
                val next = resultSet.javaClass.getMethod("next").invoke(resultSet) as Boolean
                if (!next) {
                    error("EXPLAIN returned no rows.")
                }
                val raw = runCatching { resultSet.javaClass.getMethod("getString", Int::class.javaPrimitiveType).invoke(resultSet, 1) }
                    .recoverCatching { resultSet.javaClass.getMethod("getObject", Int::class.javaPrimitiveType).invoke(resultSet, 1)?.toString() }
                    .getOrElse { throw it }
                raw?.toString() ?: error("EXPLAIN returned an empty first column.")
            } finally {
                runCatching { resultSet.javaClass.getMethod("close").invoke(resultSet) }
            }
        }

        val executeMethod = dbImplUtil.methods.firstOrNull {
            it.name == "executeAndGetResult" && it.parameterCount == 3
        } ?: error("DbImplUtil.executeAndGetResult is unavailable.")

        return try {
            executeMethod.invoke(null, simpleConnection, explainSql, mapper)?.toString()
                ?: error("EXPLAIN returned no payload.")
        } catch (error: java.lang.reflect.InvocationTargetException) {
            val cause = error.targetException
            if (cause is SQLException) throw cause
            throw cause
        }
    }

    private fun releaseConnection(connection: Any) {
        runCatching { connection.javaClass.getMethod("release").invoke(connection) }
    }
}

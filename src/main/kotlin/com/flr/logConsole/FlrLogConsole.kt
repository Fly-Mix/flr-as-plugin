package com.flr.logConsole

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class FlrLogConsole(private val curProject: Project, private val curConsoleView: ConsoleView) {

    enum class LogType {
        normal,
        tips,
        warning,
        error
    }

    fun println(text: String, logType: LogType) {
        val contentType = when (logType) {
            LogType.tips -> ConsoleViewContentType.LOG_INFO_OUTPUT
            LogType.warning -> ConsoleViewContentType.LOG_WARNING_OUTPUT
            LogType.error -> ConsoleViewContentType.ERROR_OUTPUT
            LogType.normal -> ConsoleViewContentType.SYSTEM_OUTPUT
        }

        // ConsoleView.print 线程安全，无需 WriteCommandAction（那会污染 undo 栈）；仅滚动到底部需要在 EDT 上执行。
        curConsoleView.print(text + "\n", contentType)
        if (curConsoleView is ConsoleViewImpl) {
            ApplicationManager.getApplication().invokeLater(curConsoleView::scrollToEnd)
        }
    }

    fun println(coloredLogEntity: FlrColoredLogEntity) {
        println("", LogType.normal)
        for (item in coloredLogEntity.items) {
            println(item.text, item.logType)
        }
    }

    fun clear() {
        curConsoleView.clear()
    }
}

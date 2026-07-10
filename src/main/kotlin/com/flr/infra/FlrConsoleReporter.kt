package com.flr.infra

import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrColoredLogEntity
import com.flr.logConsole.FlrLogConsole
import com.flr.messageBox.FlrMessageBox
import com.intellij.openapi.project.Project

/**
 * [FlrReporter] 的 IDE 实现：日志写入 [FlrLogConsole]，通知走 [FlrMessageBox]。
 *
 * success/failure 的 HTML 内容拼装与原 FlrCommand.showSuccessMessage/showFailureMessage 一致。
 */
class FlrConsoleReporter(
    private val project: Project,
    private val logConsole: FlrLogConsole
) : FlrReporter {

    override fun log(text: String, type: FlrLogConsole.LogType) {
        logConsole.println(text, type)
    }

    override fun log(coloredLogEntity: FlrColoredLogEntity) {
        logConsole.println(coloredLogEntity)
    }

    override fun success(title: String, message: String, hasWarning: Boolean) {
        val content = "<p>$title</p><p>$message</p>"
        if (hasWarning) {
            FlrMessageBox.showWarning(project, MESSAGE_BOX_TITLE, content)
        } else {
            FlrMessageBox.showInfo(project, MESSAGE_BOX_TITLE, content)
        }
    }

    override fun failure(title: String, message: String) {
        val content = "<p>$title</p><p>$message</p>"
        FlrMessageBox.showError(project, MESSAGE_BOX_TITLE, content)
    }

    companion object {
        private const val MESSAGE_BOX_TITLE = "Flr"
    }
}

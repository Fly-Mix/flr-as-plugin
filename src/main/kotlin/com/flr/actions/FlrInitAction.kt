package com.flr.actions

import com.flr.FlrApp
import com.flr.toolWindow.FlrToolWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

class FlrInitAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return

        val flrLogConsole = FlrToolWindowFactory.getLogConsole(project)
        FlrToolWindowFactory.showCurLogConsole(project)
        flrLogConsole.clear()

        val flrApp = FlrApp.getInstance(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Flr Init", false) {
            override fun run(indicator: ProgressIndicator) {
                flrApp.flrCommand.initAll(e, flrLogConsole)
            }
        })
    }
}

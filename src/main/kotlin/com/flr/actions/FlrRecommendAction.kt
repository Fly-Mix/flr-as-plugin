package com.flr.actions

import com.flr.FlrApp
import com.flr.toolWindow.FlrToolWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

class FlrRecommendAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return

        val flrLogConsole = FlrToolWindowFactory.getLogConsole(project)
        FlrToolWindowFactory.showCurLogConsole(project)
        flrLogConsole.clear()

        val flrApp = FlrApp.getInstance(project)
        flrApp.flrCommand.displayRecommendedFlutterResourceStructure(e, flrLogConsole)
    }
}

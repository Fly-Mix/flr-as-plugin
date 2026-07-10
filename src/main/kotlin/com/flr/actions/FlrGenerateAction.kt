package com.flr.actions

import com.flr.FlrApp
import com.flr.logConsole.FlrLogConsole
import com.flr.toolWindow.FlrToolWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

class FlrGenerateAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return

        val flrLogConsole = FlrToolWindowFactory.getLogConsole(project)
        FlrToolWindowFactory.showCurLogConsole(project)

        val flrApp = FlrApp.getInstance(project)

        // 如果当前资源变化监控服务正在运行，则不清空当前日志，否则就清空
        if (flrApp.flrCommand.isMonitoringAssets() == false) {
            flrLogConsole.clear()
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Flr Generate", false) {
            override fun run(indicator: ProgressIndicator) {
                flrApp.flrCommand.generateAll(e, flrLogConsole)

                // 如果当前资源变化监控服务正在运行，则在执行 generate 后，打印监控服务在运行的提示
                if (flrApp.flrCommand.isMonitoringAssets()) {
                    val indicatorType = FlrLogConsole.LogType.normal
                    flrLogConsole.println("", indicatorType)
                    val indicatorMessage =
                        "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                            "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n"
                    flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips)
                }
            }
        })
    }
}

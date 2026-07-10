package com.flr.actions

import com.flr.FlrApp
import com.flr.settings.FlrConfigDialog
import com.flr.settings.FlrConfigIO
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

        // 运行前配置对话框：预填现有 flr 配置，OK 则落盘后执行 Init，取消则不改动、不执行。
        // 无可读 pubspec（config==null）时跳过对话框，直接交给 initAll 处理/报错（与原行为一致）。
        val config = FlrConfigIO.load(project)
        if (config != null) {
            if (!FlrConfigDialog(project, config).showAndGet()) {
                return
            }
            FlrConfigIO.save(project, config)
        }

        flrLogConsole.clear()

        val flrApp = FlrApp.getInstance(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Flr Init", false) {
            override fun run(indicator: ProgressIndicator) {
                flrApp.flrCommand.initAll(e, flrLogConsole)
            }
        })
    }
}

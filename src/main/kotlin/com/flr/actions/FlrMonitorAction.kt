package com.flr.actions

import com.flr.FlrApp
import com.flr.toolWindow.FlrToolWindowFactory
import com.flr.toolWindow.FlrToolWindowService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

class FlrMonitorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return

        val flrLogConsole = FlrToolWindowFactory.getLogConsole(project)
        FlrToolWindowFactory.showCurLogConsole(project)
        flrLogConsole.clear()

        val flrApp = FlrApp.getInstance(project)
        // 仅负责启停；菜单文案不在此设置——在 actionPerformed 里改 presentation 不会持久到菜单，
        // 文案统一由下面的 update() 依据 isMonitoringAssets 刷新（菜单每次弹出时回调 update）。
        if (flrApp.flrCommand.isMonitoringAssets()) {
            flrApp.flrCommand.stopMonitor(e, flrLogConsole)
        } else {
            flrApp.flrCommand.startMonitor(e, flrLogConsole)
        }

        // 同步刷新工具窗状态面板（若已创建）
        FlrToolWindowService.getInstance(project).statusPanel?.refresh()
    }

    /**
     * 依据资源监控服务的运行状态刷新菜单项文案：运行中显示 "Stop Monitor"，否则显示 "Start Monitor"。
     * 注：startMonitor 启动失败时不会置 isMonitoringAssets，故此处会自动回落到 "Start Monitor"。
     */
    override fun update(e: AnActionEvent) {
        val project = e.getData(PlatformDataKeys.PROJECT)
        val presentation = e.presentation
        if (project == null) {
            return
        }

        val isMonitoring = FlrApp.getInstance(project).flrCommand.isMonitoringAssets()
        if (isMonitoring) {
            presentation.setText("Stop Monitor")
            presentation.description = "terminate the monitoring service"
            presentation.icon = AllIcons.Actions.Suspend
        } else {
            presentation.setText("Start Monitor")
            presentation.description = "launch a monitoring service"
            presentation.icon = AllIcons.Actions.Execute
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        // 仅读取项目服务上的布尔字段，放在 EDT 上执行足够轻量且安全
        return ActionUpdateThread.EDT
    }
}

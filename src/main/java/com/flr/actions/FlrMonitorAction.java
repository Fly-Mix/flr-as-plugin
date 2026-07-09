package com.flr.actions;

import com.flr.FlrApp;
import com.flr.logConsole.FlrLogConsole;
import com.flr.toolWindow.FlrToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public class FlrMonitorAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) return;

        FlrLogConsole flrLogConsole = FlrToolWindowFactory.getLogConsole(project);
        FlrToolWindowFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = FlrApp.getInstance(project);
        // 仅负责启停；菜单文案不在此设置——在 actionPerformed 里改 presentation 不会持久到菜单，
        // 文案统一由下面的 update() 依据 isMonitoringAssets 刷新（菜单每次弹出时回调 update）。
        if (flrApp.getFlrCommand().isMonitoringAssets()) {
            flrApp.getFlrCommand().stopMonitor(e, flrLogConsole);
        } else {
            flrApp.getFlrCommand().startMonitor(e, flrLogConsole);
        }
    }

    /*
     * 依据资源监控服务的运行状态刷新菜单项文案：
     * 运行中显示 "Stop Monitor"，否则显示 "Start Monitor"。
     * 注：startMonitor 启动失败时不会置 isMonitoringAssets，故此处会自动回落到 "Start Monitor"。
     */
    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Presentation presentation = e.getPresentation();
        if (project == null) {
            return;
        }

        boolean isMonitoring = FlrApp.getInstance(project).getFlrCommand().isMonitoringAssets();
        if (isMonitoring) {
            presentation.setText("Stop Monitor");
            presentation.setDescription("terminate the monitoring service");
        } else {
            presentation.setText("Start Monitor");
            presentation.setDescription("launch a monitoring service");
        }
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        // 仅读取项目服务上的布尔字段，放在 EDT 上执行足够轻量且安全
        return ActionUpdateThread.EDT;
    }
}

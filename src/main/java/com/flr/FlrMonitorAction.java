package com.flr;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlrMonitorAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrProjectComponent flrProjectComponent = project.getComponent(FlrProjectComponent.class);
        Presentation actionPresentation = e.getPresentation();
        if(flrProjectComponent.flrCommand.isMonitoringAssets) {
            actionPresentation.setText("Start Monitor");
            actionPresentation.setDescription("launch a monitoring service");
            flrProjectComponent.flrCommand.stopAssertMonitor(e, flrLogConsole);
        } else {
            actionPresentation.setText("Stop Monitor");
            actionPresentation.setDescription("terminate the monitoring service");
            Boolean isStartSuccess = flrProjectComponent.flrCommand.startAssertMonitor(e, flrLogConsole);
            if(isStartSuccess == false) {
                actionPresentation.setText("Start Monitor");
                actionPresentation.setDescription("launch a monitoring service");
            }
        }
    }
}

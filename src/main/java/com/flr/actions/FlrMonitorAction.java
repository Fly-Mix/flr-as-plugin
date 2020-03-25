package com.flr.actions;

import com.flr.logConsole.FlrLogConsole;
import com.flr.logConsole.FlrLogConsoleFactory;
import com.flr.FlrApp;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public class FlrMonitorAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = project.getComponent(FlrApp.class);
        Presentation actionPresentation = e.getPresentation();
        if(flrApp.flrCommand.isMonitoringAssets) {
            actionPresentation.setText("Start Monitor");
            actionPresentation.setDescription("launch a monitoring service");
            flrApp.flrCommand.stopMonitor(e, flrLogConsole);
        } else {
            actionPresentation.setText("Stop Monitor");
            actionPresentation.setDescription("terminate the monitoring service");
            Boolean isStartSuccess = flrApp.flrCommand.startMonitor(e, flrLogConsole);
            if(isStartSuccess == false) {
                actionPresentation.setText("Start Monitor");
                actionPresentation.setDescription("launch a monitoring service");
            }
        }
    }
}

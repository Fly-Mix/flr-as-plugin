package com.flr.actions;

import com.flr.FlrApp;
import com.flr.logConsole.FlrLogConsole;
import com.flr.logConsole.FlrLogConsoleFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class FlrRecommendAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = project.getComponent(FlrApp.class);
        flrApp.flrCommand.displayRecommendedFlutterResourceStructure(e, flrLogConsole);
    }
}

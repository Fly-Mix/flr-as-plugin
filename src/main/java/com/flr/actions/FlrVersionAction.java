package com.flr.actions;

import com.flr.FlrApp;
import com.flr.logConsole.FlrLogConsole;
import com.flr.toolWindow.FlrToolWindowFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class FlrVersionAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null) return;

        FlrLogConsole flrLogConsole = FlrToolWindowFactory.getLogConsole(project);
        FlrToolWindowFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = FlrApp.getInstance(project);
        flrApp.getFlrCommand().displayVersion(e, flrLogConsole);
    }
}

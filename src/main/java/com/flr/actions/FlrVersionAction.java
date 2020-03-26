package com.flr.actions;

import com.flr.FlrApp;
import com.flr.logConsole.FlrLogConsole;
import com.flr.logConsole.FlrLogConsoleFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlrVersionAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = project.getComponent(FlrApp.class);
        flrApp.flrCommand.displayVersion(e, flrLogConsole);
    }
}

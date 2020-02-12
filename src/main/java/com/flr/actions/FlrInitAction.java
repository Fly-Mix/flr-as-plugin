package com.flr.actions;

import com.flr.logConsole.FlrLogConsole;
import com.flr.logConsole.FlrLogConsoleFactory;
import com.flr.FlrApp;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlrInitAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrApp flrApp = project.getComponent(FlrApp.class);

        // Java Code Examples for com.intellij.openapi.progress.ProgressIndicator
        // https://www.programcreek.com/java-api-examples/?api=com.intellij.openapi.progress.ProgressIndicator
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Flr Init", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                flrApp.flrCommand.init(e, flrLogConsole);
            }
        });
    }
}

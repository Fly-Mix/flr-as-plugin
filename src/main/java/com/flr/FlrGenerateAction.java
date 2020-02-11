package com.flr;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import org.jetbrains.annotations.NotNull;

public class FlrGenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);

        FlrLogConsole flrLogConsole = FlrLogConsoleFactory.createLogConsole(project);
        FlrLogConsoleFactory.showCurLogConsole(project);
        flrLogConsole.clear();

        FlrProjectComponent flrProjectComponent = project.getComponent(FlrProjectComponent.class);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Flr Generate", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                flrProjectComponent.flrCommand.generate(e, flrLogConsole);
            }
        });
    }
}

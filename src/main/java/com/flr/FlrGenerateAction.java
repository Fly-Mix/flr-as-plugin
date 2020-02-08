package com.flr;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlrGenerateAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Flr Generate", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                FlrProjectComponent flrProjectComponent = project.getComponent(FlrProjectComponent.class);
                flrProjectComponent.flrCommand.generate(indicator, e);
            }
        });
    }
}

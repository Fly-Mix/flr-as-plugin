package com.flr;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class FlrProjectComponent implements ProjectComponent {
    public final Project curProject;

    public final FlrCommand flrCommand;

    public FlrProjectComponent(Project project) {
        curProject = project;
        flrCommand = new FlrCommand(curProject);
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
        flrCommand.dispose();
    }
}

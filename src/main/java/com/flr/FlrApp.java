package com.flr;

import com.flr.command.FlrCommand;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class FlrApp implements ProjectComponent {
    public final Project curProject;

    public final FlrCommand flrCommand;

    public FlrApp(Project project) {
        curProject = project;
        flrCommand = new FlrCommand(curProject);
    }

    @Override
    public void initComponent() {
        System.out.println("AppLifecycle-1. initComponent");
    }

    @Override
    public void projectOpened() {
        System.out.println("AppLifecycle-2. projectOpened");
    }

    @Override
    public void projectClosed() {
        System.out.println("AppLifecycle-3. projectClosed");
    }

    @Override
    public void disposeComponent() {
        System.out.println("AppLifecycle-4. disposeComponent");
        flrCommand.dispose();
    }


}

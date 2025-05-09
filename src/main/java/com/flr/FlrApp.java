package com.flr;

import com.flr.command.FlrCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(
    name = "FlrApp",
    storages = {@Storage("flr.xml")}
)
public final class FlrApp {
    private static FlrApp instance;
    private final Project curProject;
    private final FlrCommand flrCommand;

    private FlrApp(@NotNull Project project) {
        curProject = project;
        flrCommand = new FlrCommand(curProject);
        
        // 注册项目关闭监听器
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                if (project.equals(curProject)) {
                    dispose();
                }
            }
        });
    }

    public static FlrApp getInstance(@NotNull Project project) {
        if (instance == null) {
            synchronized (FlrApp.class) {
                if (instance == null) {
                    instance = project.getService(FlrApp.class);
                }
            }
        }
        return instance;
    }

    public void dispose() {
        if (flrCommand != null) {
            flrCommand.dispose();
        }
        instance = null;
    }

    public FlrCommand getFlrCommand() {
        return flrCommand;
    }
}

package com.flr;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public class FlrLogConsoleFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        Content content = toolWindow.getContentManager().getFactory().createContent(consoleView.getComponent(), "", true);
        content.setDisplayName("");
        toolWindow.getContentManager().addContent(content);

        String welcomeMessage = "Welcome to use Flr\nYou can get more details from https://github.com/Fly-Mix/flr-as-plugin";
        consoleView.print(welcomeMessage, ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public static FlrLogConsole createLogConsole(@NotNull Project project){
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flr");
        if(toolWindow == null) {
            return null;
        }

        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.findContent("");
        ConsoleView consoleView = (ConsoleView) content.getComponent();

        FlrLogConsole flrLogConsole = new FlrLogConsole(project, consoleView);
        return flrLogConsole;
    }

    public static void showCurLogConsole(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flr");
        if(toolWindow == null) {
            return;
        }

        toolWindow.show(new Runnable() {
            @Override
            public void run() {

            }
        });
    }
}
package com.flr;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FlrLogConsole {
    public enum LogType {
        normal,
        tips,
        warning,
        error
    }

    private ConsoleView curConsoleView;
    private Project curProject;

    public FlrLogConsole(@NotNull Project project, @NotNull ConsoleView consoleView) {
        curProject = project;
        curConsoleView = consoleView;
    }

    public void println(@NotNull String text, @NotNull FlrLogConsole.LogType logType) {
        if(curConsoleView == null) {
            System.out.println("FlrLogConsole: curConsoleView is null !!!");
            return;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(curProject, new Runnable() {
                    @Override
                    public void run() {
                        ConsoleViewContentType contentType = ConsoleViewContentType.SYSTEM_OUTPUT;
                        switch (logType) {
                            case normal:
                                contentType = ConsoleViewContentType.SYSTEM_OUTPUT;
                                break;
                            case tips:
                                contentType = ConsoleViewContentType.LOG_INFO_OUTPUT;
                                break;
                            case warning:
                                contentType = ConsoleViewContentType.LOG_WARNING_OUTPUT;
                                break;
                            case error:
                                contentType = ConsoleViewContentType.ERROR_OUTPUT;
                                break;
                        }
                        curConsoleView.print( text + "\n", contentType);
                        ConsoleViewImpl consoleViewImpl = (ConsoleViewImpl)curConsoleView;
                        if(consoleViewImpl != null) {
                            consoleViewImpl.scrollToEnd();
                        }
                    }
                });
            }
        });
    }

    public void clear() {
        if(curConsoleView == null) {
            System.out.println("FlrLogConsole: curConsoleView is null !!!");
            return;
        }
        curConsoleView.clear();
    }
}

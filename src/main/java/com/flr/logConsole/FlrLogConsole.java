package com.flr.logConsole;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlrLogConsole {
    public enum LogType {
        normal,
        tips,
        warning,
        error
    }

    private static final Logger LOG = Logger.getInstance(FlrLogConsole.class);

    private ConsoleView curConsoleView;
    private Project curProject;

    public FlrLogConsole(@NotNull Project project, @NotNull ConsoleView consoleView) {
        curProject = project;
        curConsoleView = consoleView;
    }

    public void println(@NotNull String text, @NotNull FlrLogConsole.LogType logType) {
        if(curConsoleView == null) {
            LOG.warn("FlrLogConsole: curConsoleView is null");
            return;
        }

        ConsoleViewContentType contentType;
        switch (logType) {
            case tips:
                contentType = ConsoleViewContentType.LOG_INFO_OUTPUT;
                break;
            case warning:
                contentType = ConsoleViewContentType.LOG_WARNING_OUTPUT;
                break;
            case error:
                contentType = ConsoleViewContentType.ERROR_OUTPUT;
                break;
            case normal:
            default:
                contentType = ConsoleViewContentType.SYSTEM_OUTPUT;
                break;
        }

        // ConsoleView.print 线程安全，无需 WriteCommandAction（那会污染 undo 栈）；
        // 仅滚动到底部需要在 EDT 上执行。
        curConsoleView.print(text + "\n", contentType);
        if (curConsoleView instanceof ConsoleViewImpl) {
            ConsoleViewImpl consoleViewImpl = (ConsoleViewImpl) curConsoleView;
            ApplicationManager.getApplication().invokeLater(consoleViewImpl::scrollToEnd);
        }
    }

    public void println(@NotNull FlrColoredLogEntity coloredLogEntity) {
        println("", LogType.normal);
        for (FlrColoredLogEntity.Item item : coloredLogEntity.items) {
            println(item.text, item.logType);
        }
    }

    public void clear() {
        if(curConsoleView == null) {
            LOG.warn("FlrLogConsole: curConsoleView is null");
            return;
        }
        curConsoleView.clear();
    }
}

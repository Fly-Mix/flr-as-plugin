package com.flr.infra;

import com.flr.core.report.FlrReporter;
import com.flr.logConsole.FlrColoredLogEntity;
import com.flr.logConsole.FlrLogConsole;
import com.flr.messageBox.FlrMessageBox;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * {@link FlrReporter} 的 IDE 实现：日志写入 {@link FlrLogConsole}，通知走 {@link FlrMessageBox}。
 *
 * success/failure 的 HTML 内容拼装与原 FlrCommand.showSuccessMessage/showFailureMessage 一致。
 */
public final class FlrConsoleReporter implements FlrReporter {

    private static final String MESSAGE_BOX_TITLE = "Flr";

    private final Project project;
    private final FlrLogConsole logConsole;

    public FlrConsoleReporter(@NotNull Project project, @NotNull FlrLogConsole logConsole) {
        this.project = project;
        this.logConsole = logConsole;
    }

    @Override
    public void log(String text, FlrLogConsole.LogType type) {
        logConsole.println(text, type);
    }

    @Override
    public void log(FlrColoredLogEntity coloredLogEntity) {
        logConsole.println(coloredLogEntity);
    }

    @Override
    public void success(@NotNull String title, @NotNull String message, boolean hasWarning) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        if (hasWarning) {
            FlrMessageBox.showWarning(project, MESSAGE_BOX_TITLE, content);
        } else {
            FlrMessageBox.showInfo(project, MESSAGE_BOX_TITLE, content);
        }
    }

    @Override
    public void failure(@NotNull String title, @NotNull String message) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        FlrMessageBox.showError(project, MESSAGE_BOX_TITLE, content);
    }
}

package com.flr.messageBox;

import com.flr.FlrIds;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlrMessageBox {

    public static void showInfo(@Nullable Project project, @NotNull String title, @NotNull String content) {
        final Notification notification = new Notification(
                FlrIds.NOTIFICATION_GROUP,
                title,
                content,
                NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, project);
    }

    public static void showWarning(@Nullable Project project, @NotNull String title, @NotNull String content) {
        final Notification notification = new Notification(
                FlrIds.NOTIFICATION_GROUP,
                title,
                content,
                NotificationType.WARNING);
        Notifications.Bus.notify(notification, project);
    }

    public static void showError(@Nullable Project project, @NotNull String title, @NotNull String content) {
        final Notification notification = new Notification(
                FlrIds.NOTIFICATION_GROUP,
                title,
                content,
                NotificationType.ERROR);
        Notifications.Bus.notify(notification, project);
    }
}

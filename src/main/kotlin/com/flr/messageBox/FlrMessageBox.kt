package com.flr.messageBox

import com.flr.FlrIds
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

object FlrMessageBox {

    @JvmStatic
    fun showInfo(project: Project?, title: String, content: String) {
        val notification = Notification(FlrIds.NOTIFICATION_GROUP, title, content, NotificationType.INFORMATION)
        Notifications.Bus.notify(notification, project)
    }

    @JvmStatic
    fun showWarning(project: Project?, title: String, content: String) {
        val notification = Notification(FlrIds.NOTIFICATION_GROUP, title, content, NotificationType.WARNING)
        Notifications.Bus.notify(notification, project)
    }

    @JvmStatic
    fun showError(project: Project?, title: String, content: String) {
        val notification = Notification(FlrIds.NOTIFICATION_GROUP, title, content, NotificationType.ERROR)
        Notifications.Bus.notify(notification, project)
    }
}

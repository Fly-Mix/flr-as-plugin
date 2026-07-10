package com.flr

/**
 * 与 `META-INF/plugin.xml` 对齐的 IDE 注册标识集中处。
 *
 * 这些 id 一方面在 plugin.xml 中声明（`<toolWindow id=...>` / `<notificationGroup id=...>`），
 * 另一方面在 Java/Kotlin 端用于查找 / 构造。plugin.xml 是 XML，无法引用常量，因此二者无法在编译期强制同步；
 * 修改任一 id 时，必须同时改动本类与 plugin.xml（plugin.xml 相应位置已加注释交叉引用本类）。
 *
 * 注意：两个 id 当前恰好都取值 "Flr"，但语义不同（工具窗口 id / 通知组 id），故分列为两个常量，不合并。
 */
object FlrIds {

    /** 工具窗口 id，对应 plugin.xml `<toolWindow id="Flr">`，用于 `ToolWindowManager.getToolWindow(...)` */
    const val TOOL_WINDOW = "Flr"

    /** 通知组 id，对应 plugin.xml `<notificationGroup id="Flr">`，用于构造 `Notification(groupId, ...)` */
    const val NOTIFICATION_GROUP = "Flr"
}

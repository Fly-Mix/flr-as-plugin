package com.flr.toolWindow

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 工程级服务：持有当前工程 Flr 工具窗内的 [ConsoleView] 引用。
 *
 * 取代原先 `FlrToolWindowFactory.getLogConsole` 靠 `getComponent(index)` 层层向下强取组件的脆弱写法：
 * 工具窗内容创建时（[FlrToolWindowFactory.createToolWindowContent]）把 ConsoleView 存入本服务，
 * 需要时直接读取。
 */
@Service(Service.Level.PROJECT)
class FlrToolWindowService {

    var consoleView: ConsoleView? = null

    companion object {
        @JvmStatic
        fun getInstance(project: Project): FlrToolWindowService = project.getService(FlrToolWindowService::class.java)
    }
}

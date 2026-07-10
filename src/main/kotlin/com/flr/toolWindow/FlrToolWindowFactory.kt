package com.flr.toolWindow

import com.flr.FlrIds
import com.flr.logConsole.FlrLogConsole
import com.flr.pubspecFileTree.FlrPubspecFileTree
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.OnePixelSplitter
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Flr 工具窗内容工厂：左侧 pubspec 文件树，右侧日志 console，用 [OnePixelSplitter]（1px 分隔）分栏。
 *
 * 重写自原 GUI Designer(.form) 版本：不再依赖 FlrToolWindowProvider/.form；ConsoleView 引用存入
 * [FlrToolWindowService]，[getLogConsole] 直接读取，消除原先 getComponent(index) 的脆弱取法。
 */
class FlrToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rootPanel = JPanel(BorderLayout())

        // ---- NORTH：动作工具栏（复用 flr.toolbar 组）+ UI DSL v2 状态面板 ----
        val actionGroup = ActionManager.getInstance().getAction("flr.toolbar") as ActionGroup
        val actionToolbar = ActionManager.getInstance().createActionToolbar("FlrToolWindow", actionGroup, true)
        actionToolbar.targetComponent = rootPanel

        val statusPanel = FlrStatusPanel(project)
        FlrToolWindowService.getInstance(project).statusPanel = statusPanel

        val northPanel = JPanel(BorderLayout())
        northPanel.add(actionToolbar.component, BorderLayout.NORTH)
        northPanel.add(statusPanel.component, BorderLayout.CENTER)
        rootPanel.add(northPanel, BorderLayout.NORTH)

        // ---- CENTER：横向分栏，左 pubspec 文件树、右日志 console（proportion 0.3 可按需调整）----
        val splitter = OnePixelSplitter(false, 0.3f)

        val flrPubspecFileTree = FlrPubspecFileTree(project)
        flrPubspecFileTree.refreshContent()
        splitter.firstComponent = flrPubspecFileTree

        // 日志 console（引用存入工程级服务，供 getLogConsole 读取）
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        FlrToolWindowService.getInstance(project).consoleView = consoleView

        val consoleContainer = JPanel(BorderLayout())
        consoleContainer.toolTipText = "Flr Log View"
        consoleContainer.add(consoleView.component, BorderLayout.CENTER)
        splitter.secondComponent = consoleContainer

        val welcomeMessage = "Welcome to use Flr\nYou can get more details from https://github.com/Fly-Mix/flr-as-plugin"
        consoleView.print(welcomeMessage, ConsoleViewContentType.SYSTEM_OUTPUT)

        rootPanel.add(splitter, BorderLayout.CENTER)

        val content = toolWindow.contentManager.factory.createContent(rootPanel, "", true)
        content.setDisplayName("")
        toolWindow.contentManager.addContent(content)
    }

    companion object {

        /**
         * 获取当前工程 Flr 工具窗的日志控制台。
         *
         * 依赖工具窗内容已创建（createToolWindowContent 已运行，ConsoleView 已存入服务）；与原实现一致，
         * 若尚未创建则此处会抛出（等价于原实现在内容缺失时的 NPE）。
         */
        @JvmStatic
        fun getLogConsole(project: Project): FlrLogConsole {
            val consoleView = FlrToolWindowService.getInstance(project).consoleView!!
            return FlrLogConsole(project, consoleView)
        }

        @JvmStatic
        fun showCurLogConsole(project: Project) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(FlrIds.TOOL_WINDOW) ?: return
            toolWindow.show(null)
        }
    }
}

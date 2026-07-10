package com.flr.toolWindow

import com.flr.FlrApp
import com.flr.settings.FlrConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JLabel

/**
 * 工具窗顶部的状态/摘要面板（UI DSL v2）：显示工程名、资源监控状态，并提供「打开 Flr 设置」入口。
 *
 * [refresh] 由 [FlrToolWindowFactory] 初始化时与 Monitor 启停后（[com.flr.actions.FlrMonitorAction]）调用，
 * 以同步监控状态显示。
 */
class FlrStatusPanel(private val project: Project) {

    private lateinit var monitorStatusLabel: JLabel

    val component: DialogPanel = panel {
        row {
            label("工程：")
            label(project.name)
        }
        row {
            label("监控状态：")
            monitorStatusLabel = label("").component
        }
        row {
            link("打开 Flr 设置") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, FlrConfigurable::class.java)
            }
        }
    }

    init {
        refresh()
    }

    fun refresh() {
        val monitoring = FlrApp.getInstance(project).flrCommand.isMonitoringAssets()
        monitorStatusLabel.text = if (monitoring) "运行中" else "已停止"
    }
}

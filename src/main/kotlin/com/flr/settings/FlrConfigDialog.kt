package com.flr.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Init 运行前的配置对话框：用共享的 [flrConfigRows]（与设置页同一表单）让用户确认/编辑资源目录与行长。
 *
 * 预填由调用方传入的 [model]（来自 [FlrConfigIO.load]）；点 OK 时 [panel] 的绑定回写到 model，
 * 调用方随后 [FlrConfigIO.save] 落盘再执行 Init。取消则不改动。
 *
 * 说明：nullsafety 不在此配置——它由 SDK 版本探测决定（见 IdeFlutterEnvironment），非用户可选项。
 */
class FlrConfigDialog(project: Project, private val model: FlrConfig) : DialogWrapper(project) {

    private val contentPanel = panel { flrConfigRows(project, model) }

    init {
        title = "Flr — 配置资源目录"
        init()
    }

    override fun createCenterPanel(): JComponent = contentPanel

    override fun doOKAction() {
        contentPanel.apply() // UI -> model
        super.doOKAction()
    }
}

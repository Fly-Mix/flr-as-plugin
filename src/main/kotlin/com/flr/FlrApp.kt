package com.flr

import com.flr.command.FlrCommand
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 工程级服务：持有该工程的 [FlrCommand]。
 *
 * 作为 [Service.Level.PROJECT] 轻量服务，平台按工程各创建一个实例并管理其生命周期；
 * 实现 [Disposable]，工程关闭时由平台调用 [dispose] 级联释放 FlrCommand（及其监控监听器）。
 *
 * 注意：不要再手写 static 单例——那会让多个打开的工程共享同一实例而互相串号。
 */
@Service(Service.Level.PROJECT)
class FlrApp(project: Project) : Disposable {

    val flrCommand: FlrCommand = FlrCommand(project)

    override fun dispose() {
        flrCommand.dispose()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): FlrApp = project.getService(FlrApp::class.java)
    }
}

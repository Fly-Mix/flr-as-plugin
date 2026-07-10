package com.flr.command.util

import com.flr.FlrConstant
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.ide.actions.DartStyleAction
import java.io.File
import java.util.regex.Pattern

object FlrUtil {

    // MARK: - Version Util Methods
    /**
     * @return v1 > v2 返回 1；v1 < v2 返回 2；相等返回 0；输入非法返回 -1
     */
    @JvmStatic
    fun versionCompare(v1: String, v2: String): Int {
        val pattern = Pattern.compile("\\d+(\\.\\d+)*")

        if (!pattern.matcher(v1).matches() || !pattern.matcher(v2).matches()) {
            return -1
        }

        val s1 = v1.split(Regex("\\."))
        val s2 = v2.split(Regex("\\."))

        val length = if (s1.size < s2.size) s1.size else s2.size
        for (i in 0 until length) {
            val diff = s1[i].toInt() - s2[i].toInt()
            if (diff == 0) {
                continue
            } else {
                return if (diff > 0) 1 else 2
            }
        }

        return 0
    }

    /** 获取当前插件的版本 */
    @JvmStatic
    fun getFlrVersion(): String {
        val flrPluginId = PluginId.getId(FlrConstant.PLUGIN_ID)
        return PluginManagerCore.getPlugin(flrPluginId)?.version ?: ""
    }

    // MARK: - Flutter Util Methods
    @JvmStatic
    fun runFlutterPubGet(actionEvent: AnActionEvent) {
        // 从后台任务线程切换到 UI 线程
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                // 使用 ActionManager 来执行 FlutterPackagesGetAction
                val action = ActionManager.getInstance().getAction("flutter.pub.get")
                action?.actionPerformed(actionEvent)
            }
        }
    }

    @JvmStatic
    fun formatDartFile(project: Project, dartFile: File) {
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                // 格式化方案：Android Studio（non-IDEA JetBrains IDE）和 IDEA JetBrains IDE 上均可行
                val dartVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(dartFile)
                    ?: return@runWriteCommandAction
                val dartFiles = ArrayList<VirtualFile>()
                dartFiles.add(dartVirtualFile)
                DartStyleAction.runDartfmt(project, dartFiles)

                dartVirtualFile.refresh(false, false)
            }
        }
    }
}

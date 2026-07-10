package com.flr.settings

import com.flr.FlrConstant
import com.flr.FlrException
import com.flr.PubspecKeys
import com.flr.command.util.FlrFileUtil
import com.intellij.openapi.project.Project
import java.io.File

/**
 * flr 配置的读写：集中「主工程 pubspec.yaml 的 flr 节点 ↔ [FlrConfig]」的加载/落盘逻辑，
 * 供设置页（U1）与配置对话框（U3）共用。复用 [FlrFileUtil] 的 load/dump（后者经 VFS WriteCommandAction）。
 */
object FlrConfigIO {

    /** 主工程 pubspec.yaml；工程无 basePath 时返回 null。 */
    private fun mainPubspecFile(project: Project): File? {
        val root = project.basePath ?: return null
        return File(FlrFileUtil.getPubspecFilePath(root))
    }

    private fun loadPubspec(file: File): MutableMap<String, Any>? {
        if (!file.exists()) return null
        return try {
            FlrFileUtil.loadPubspecConfigFromFile(NoopFlrReporter, file)
        } catch (e: FlrException) {
            null
        }
    }

    /**
     * 读取当前工程 flr 配置；无 pubspec / 解析失败 / 无 flr 节点时的缺省值按 [FlrConstant] 兜底。
     * 返回 null 表示当前工程没有可读的 pubspec.yaml（表单据此禁用编辑）。
     */
    fun load(project: Project): FlrConfig? {
        val file = mainPubspecFile(project) ?: return null
        val pubspec = loadPubspec(file) ?: return null

        val flr = pubspec[PubspecKeys.FLR] as? Map<*, *>
        val assets = (flr?.get(PubspecKeys.ASSETS) as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
        val fonts = (flr?.get(PubspecKeys.FONTS) as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
        val lineLength = (flr?.get("dartfmt_line_length") as? Int) ?: FlrConstant.DARTFMT_LINE_LENGTH
        val coreVersion = (flr?.get("core_version") as? String) ?: FlrConstant.CORE_VERSION

        return FlrConfig(assets, fonts, lineLength, coreVersion)
    }

    /**
     * 把 [config] 写回 flr 节点并落盘（保留 pubspec 其它节点与 flr 节点内的其它键），经 VFS 写入。
     * 应在 EDT 调用（设置页 apply / 对话框 OK 均在 EDT）。
     */
    fun save(project: Project, config: FlrConfig) {
        val file = mainPubspecFile(project) ?: return
        val pubspec = loadPubspec(file) ?: return

        @Suppress("UNCHECKED_CAST")
        val flr = (pubspec[PubspecKeys.FLR] as? MutableMap<String, Any>) ?: LinkedHashMap()
        flr["core_version"] = config.coreVersion
        flr["dartfmt_line_length"] = config.dartfmtLineLength
        flr[PubspecKeys.ASSETS] = config.assetDirs.toMutableList()
        flr[PubspecKeys.FONTS] = config.fontDirs.toMutableList()
        pubspec[PubspecKeys.FLR] = flr

        FlrFileUtil.dumpPubspecConfigToFile(project, pubspec, file)
    }
}

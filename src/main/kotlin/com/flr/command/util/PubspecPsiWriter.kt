package com.flr.command.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * 保留注释地把「受管键」定点写入 pubspec.yaml：用 YAML PSI 只替换 Flr 关心的键，其余文本（注释/格式/键序）逐字保留。
 *
 * 受管键：
 * - `flr`：整节点（Flr 全权管理，可整体替换其值）；
 * - `flutter.assets` / `flutter.fonts`：只动这两个子键，保留 flutter 其它键及注释；
 * - `dependencies.r_dart_library`：只动此子键，保留其它依赖及注释。
 *
 * 取代原 [FlrFileUtil.dumpPubspecConfigToFile] 的「整份 snakeyaml dump」（会丢注释/重排）。
 * 失败（非 YAMLFile、无顶层 mapping 等）返回 false，供调用方回退到旧的全量 dump 作兜底。
 *
 * 线程：后台线程调用亦安全（内部 invokeAndWait 切 EDT + WriteCommandAction）。
 */
object PubspecPsiWriter {

    private val LOG = Logger.getInstance(PubspecPsiWriter::class.java)

    private fun newDumper(): Yaml {
        val opts = DumperOptions()
        opts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        opts.indent = 2
        opts.indicatorIndent = 0
        return Yaml(opts)
    }

    /**
     * 把 [target] 中的受管键定点写入 [pubspecVFile]。成功返回 true；无法用 PSI 处理时返回 false（调用方应回退全量 dump）。
     */
    fun applyManagedKeys(project: Project, pubspecVFile: VirtualFile, target: Map<String, Any?>): Boolean {
        val ok = booleanArrayOf(false)
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                ok[0] = try {
                    doApply(project, pubspecVFile, target)
                } catch (e: Exception) {
                    LOG.warn("PubspecPsiWriter 定点写入失败，将回退全量 dump", e)
                    false
                }
            }
        }
        return ok[0]
    }

    private fun doApply(project: Project, pubspecVFile: VirtualFile, target: Map<String, Any?>): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(pubspecVFile) as? YAMLFile ?: return false
        val topMapping = psiFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        val gen = YAMLElementGenerator.getInstance(project)

        // 1) flr：整节点替换/新增
        (target["flr"] as? Map<*, *>)?.let { flr ->
            val kv = topLevelKeyValue(gen, "flr", flr) ?: return false
            topMapping.putKeyValue(kv)
        }

        // 2) flutter.assets / flutter.fonts：只动这两个子键
        (target["flutter"] as? Map<*, *>)?.let { flutter ->
            val existing = topMapping.getKeyValueByKey("flutter")?.value as? YAMLMapping
            if (existing == null) {
                // 无既有 flutter 节点：整体新建（无注释可保留）
                val onlyManaged = LinkedHashMap<String, Any?>()
                if (flutter.containsKey("assets")) onlyManaged["assets"] = flutter["assets"]
                if (flutter.containsKey("fonts")) onlyManaged["fonts"] = flutter["fonts"]
                if (onlyManaged.isNotEmpty()) {
                    val kv = topLevelKeyValue(gen, "flutter", onlyManaged) ?: return false
                    topMapping.putKeyValue(kv)
                }
            } else {
                applyChildKey(gen, existing, "flutter", "assets", flutter.containsKey("assets"), flutter["assets"])
                applyChildKey(gen, existing, "flutter", "fonts", flutter.containsKey("fonts"), flutter["fonts"])
            }
        }

        // 3) dependencies.r_dart_library：只动此子键（值未变则不动，避免 Generate 每次重写、误伤该行注释）
        (target["dependencies"] as? Map<*, *>)?.let { deps ->
            val newVer = deps["r_dart_library"]?.toString()
            if (newVer != null) {
                val existing = topMapping.getKeyValueByKey("dependencies")?.value as? YAMLMapping
                if (existing != null) {
                    val curVer = existing.getKeyValueByKey("r_dart_library")?.valueText
                    if (curVer != newVer) {
                        applyChildKey(gen, existing, "dependencies", "r_dart_library", true, newVer)
                    }
                } else {
                    val onlyManaged = LinkedHashMap<String, Any?>()
                    onlyManaged["r_dart_library"] = newVer
                    val kv = topLevelKeyValue(gen, "dependencies", onlyManaged) ?: return false
                    topMapping.putKeyValue(kv)
                }
            }
        }

        // PSI -> Document -> 落盘（后续 pub get 等会从磁盘读 pubspec）
        val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        if (doc != null) {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc)
            FileDocumentManager.getInstance().saveDocument(doc)
        }
        return true
    }

    /** 构造顶层（缩进 0）的 `key: value` YAMLKeyValue（value 由 snakeyaml 序列化）。 */
    private fun topLevelKeyValue(gen: YAMLElementGenerator, key: String, value: Any?): YAMLKeyValue? {
        val snippet = dumpEntry(key, value)
        val dummyTop = gen.createDummyYamlWithText(snippet).documents.firstOrNull()?.topLevelValue as? YAMLMapping
            ?: return null
        return dummyTop.getKeyValueByKey(key)
    }

    /**
     * 在父 mapping（缩进 1 层）中定点设置或删除子键 [childKey]。
     * [present] 为 false 时删除该键（若存在）；否则用 [value] 设置（存在则替换、不存在则新增）。
     */
    private fun applyChildKey(
        gen: YAMLElementGenerator,
        parentMapping: YAMLMapping,
        parentKey: String,
        childKey: String,
        present: Boolean,
        value: Any?
    ) {
        if (!present) {
            parentMapping.getKeyValueByKey(childKey)?.let { parentMapping.deleteKeyValue(it) }
            return
        }
        // 在与目标同层级的上下文里构造子键 keyValue，令其携带正确缩进，再 putKeyValue
        val childSnippet = reindent(dumpEntry(childKey, value), 2)
        val snippet = "$parentKey:\n$childSnippet"
        val dummyParent = gen.createDummyYamlWithText(snippet)
            .documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return
        val kv = (dummyParent.getKeyValueByKey(parentKey)?.value as? YAMLMapping)?.getKeyValueByKey(childKey) ?: return
        parentMapping.putKeyValue(kv)
    }

    /** snakeyaml 序列化单键条目为 `key:\n  ...` 或 `key: scalar`（block 样式、2 空格），去掉尾部换行。 */
    private fun dumpEntry(key: String, value: Any?): String {
        val map = LinkedHashMap<String, Any?>()
        map[key] = value
        return newDumper().dump(map).trimEnd('\n')
    }

    /** 每个非空行前加 [spaces] 个空格。 */
    private fun reindent(text: String, spaces: Int): String {
        val pad = " ".repeat(spaces)
        return text.lineSequence().joinToString("\n") { if (it.isEmpty()) it else pad + it }
    }
}

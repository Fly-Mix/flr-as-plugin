package com.flr.settings

import com.flr.FlrConstant
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntText
import javax.swing.JComponent

/**
 * flr 配置的可复用 UI DSL v2 表单，供设置页（U1）与配置对话框（U3）共用，保证两处表单一致。
 *
 * assets/fonts 为「相对工程根的目录列表」，用 [DirListEditor]（JBList + ToolbarDecorator 增删，"增"走目录选择器）；
 * dartfmt_line_length 用 intTextField 双向绑定；core_version 只读展示。
 */
fun Panel.flrConfigRows(project: Project, model: FlrConfig) {
    val assetsEditor = DirListEditor(project, model.assetDirs)
    val fontsEditor = DirListEditor(project, model.fontDirs)

    row("core_version:") {
        label(model.coreVersion)
    }
    row("dartfmt_line_length:") {
        intTextField(FlrConstant.DARTFMT_LINE_LENGTH..10000)
            .bindIntText(model::dartfmtLineLength)
    }

    group("assets（需扫描的图片/文本资源目录，相对工程根）") {
        row {
            cell(assetsEditor.component)
                .align(Align.FILL)
                .onReset { assetsEditor.setItems(model.assetDirs) }
                .onApply {
                    model.assetDirs.clear()
                    model.assetDirs.addAll(assetsEditor.items())
                }
                .onIsModified { assetsEditor.items() != model.assetDirs }
        }.resizableRow()
    }

    group("fonts（需扫描的字体资源目录，相对工程根）") {
        row {
            cell(fontsEditor.component)
                .align(Align.FILL)
                .onReset { fontsEditor.setItems(model.fontDirs) }
                .onApply {
                    model.fontDirs.clear()
                    model.fontDirs.addAll(fontsEditor.items())
                }
                .onIsModified { fontsEditor.items() != model.fontDirs }
        }.resizableRow()
    }
}

/**
 * 目录列表编辑器：JBList + ToolbarDecorator（增/删）。"增"弹目录选择器，选中目录以「相对工程根」的路径入列。
 */
class DirListEditor(private val project: Project, initial: List<String>) {

    private val listModel = CollectionListModel(initial.toMutableList())
    private val list = JBList(listModel)

    val component: JComponent = ToolbarDecorator.createDecorator(list)
        .setAddAction { addDir() }
        .setRemoveAction { removeSelected() }
        .createPanel()

    fun items(): List<String> = listModel.items.toList()

    fun setItems(items: List<String>) {
        listModel.replaceAll(items.toMutableList())
    }

    private fun addDir() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val base = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val chosen = FileChooser.chooseFile(descriptor, project, base) ?: return
        val rel = relativize(chosen.path)
        if (!listModel.items.contains(rel)) {
            listModel.add(rel)
        }
    }

    private fun removeSelected() {
        val idx = list.selectedIndex
        if (idx >= 0) {
            listModel.remove(idx)
        }
    }

    /** 绝对路径 -> 相对工程根路径（不在工程内则原样返回）。 */
    private fun relativize(absPath: String): String {
        val root = project.basePath ?: return absPath
        val prefix = "$root/"
        return if (absPath.startsWith(prefix)) absPath.removePrefix(prefix) else absPath
    }
}

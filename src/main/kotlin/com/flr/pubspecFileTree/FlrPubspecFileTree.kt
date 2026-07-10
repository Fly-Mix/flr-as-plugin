package com.flr.pubspecFileTree

import com.flr.FlrPaths
import com.flr.command.util.FlrFileUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * Flr 工具窗左侧：列出主工程及各子工程的 pubspec.yaml，选中叶子节点即在编辑器打开对应文件。
 * 逻辑与原 Java 版一致（Kotlin 重写，去掉 GUI Designer 依赖）。
 */
class FlrPubspecFileTree(private val curProject: Project) : JPanel() {

    private val fileTree: Tree

    init {
        layout = BorderLayout()

        val rootTreeNode = DefaultMutableTreeNode("All Pubspec.yaml Files")

        fileTree = Tree(rootTreeNode)
        fileTree.cellRenderer = DefaultTreeCellRenderer()
        fileTree.addTreeSelectionListener { e ->
            val node = e.path.lastPathComponent as DefaultMutableTreeNode
            if (!node.isLeaf) {
                return@addTreeSelectionListener
            }

            val flutterMainProjectRootDir = curProject.basePath
            val pubspecFilePath = "$flutterMainProjectRootDir/${node.userObject}"
            val pubspecFile = File(pubspecFilePath)
            val virtualPubspecFile = LocalFileSystem.getInstance().findFileByIoFile(pubspecFile)
                ?: return@addTreeSelectionListener
            FileEditorManager.getInstance(curProject).openFile(virtualPubspecFile, true)
        }

        val scrollPane = JBScrollPane()
        scrollPane.viewport.add(fileTree)
        add(scrollPane, BorderLayout.CENTER)
    }

    fun refreshContent() {
        // 检测当前 flutter 主工程根目录是否存在 pubspec.yaml；若不存在，则结束刷新
        val flutterMainProjectRootDir = curProject.basePath ?: return
        val mainPubspecFilePath = "$flutterMainProjectRootDir/${FlrPaths.PUBSPEC_YAML}"
        val mainPubspecFile = File(mainPubspecFilePath)
        if (!mainPubspecFile.exists()) {
            return
        }

        // 获取 fileTree 的数据源，做数据更新操作
        val model = fileTree.model as DefaultTreeModel
        val rootTreeNode = model.root as DefaultMutableTreeNode
        // 移除旧的数据
        rootTreeNode.removeAllChildren()

        // 添加 flutter 主工程的 pubspec.yaml
        rootTreeNode.add(DefaultMutableTreeNode(FlrPaths.PUBSPEC_YAML))

        // 获取 flutter 主工程根目录下所有的子工程目录，并添加它们的 pubspec.yaml
        val flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir)
        for (flutterSubProjectRootDir in flutterSubProjectRootDirArray) {
            val relativeDir = flutterSubProjectRootDir.replaceFirst("$flutterMainProjectRootDir/", "")
            val relativePubspecFile = "$relativeDir/${FlrPaths.PUBSPEC_YAML}"
            rootTreeNode.add(DefaultMutableTreeNode(relativePubspecFile))
        }

        model.reload(rootTreeNode)
    }
}

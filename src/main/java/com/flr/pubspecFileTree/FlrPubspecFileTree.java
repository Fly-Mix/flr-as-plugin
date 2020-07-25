package com.flr.pubspecFileTree;

import com.flr.command.util.FlrFileUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

public class FlrPubspecFileTree extends JPanel {

    private Project curProject;

    private Tree fileTree;

    public FlrPubspecFileTree(@NotNull Project project) {
        curProject = project;

        setLayout(new BorderLayout());

        DefaultMutableTreeNode rootTreeNode =new DefaultMutableTreeNode("All Pubspec.yaml Files");

        fileTree = new Tree(rootTreeNode);
        DefaultTreeCellRenderer treeCellRenderer = new DefaultTreeCellRenderer();
        fileTree.setCellRenderer(treeCellRenderer);
        fileTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();

                if(node.isLeaf() == false) {
                    return;
                }

                String flutterMainProjectRootDir = curProject.getBasePath();
                String pubspecFilePath = flutterMainProjectRootDir + "/" + node.getUserObject();
                File pubspecFile = new File(pubspecFilePath);
                VirtualFile virtualPubspecFile = LocalFileSystem.getInstance().findFileByIoFile(pubspecFile);
                if(virtualPubspecFile == null) {
                    return;
                }
                FileEditorManager.getInstance(curProject).openFile(virtualPubspecFile, true);
            }
        });

        JBScrollPane scrollPane = new JBScrollPane();
        scrollPane.getViewport().add(fileTree);
        add(BorderLayout.CENTER, scrollPane);
    }

    public void refreshContent() {
        // 检测当前flutter主工程根目录是否存在 pubspec.yaml；若不存在，则结束刷新
        String flutterMainProjectRootDir = curProject.getBasePath();
        String mainPubspecFilePath = flutterMainProjectRootDir + "/pubspec.yaml";
        File mainPubspecFile = new File(mainPubspecFilePath);
        if(mainPubspecFile.exists() == false) {
            return;
        }

        // 获取fileTree的数据源，做数据更新操作
        DefaultTreeModel model = (DefaultTreeModel)fileTree.getModel();
        DefaultMutableTreeNode rootTreeNode = (DefaultMutableTreeNode)model.getRoot();
        // 移除旧的数据
        rootTreeNode.removeAllChildren();

        // 添加flutter主工程的pubspec.yaml
        DefaultMutableTreeNode mainPubspecFileTreeNode = new DefaultMutableTreeNode("pubspec.yaml");
        rootTreeNode.add(mainPubspecFileTreeNode);

        // 获取flutter主工程根目录下所有的子工程目录，并添加它们的pubspec.yaml
        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);
        for(String flutterSubProjectRootDir : flutterSubProjectRootDirArray) {
            String relativeDir = flutterSubProjectRootDir.replaceFirst(flutterMainProjectRootDir + "/", "");
            String relativePubspecFile = String.format("%s/pubspec.yaml", relativeDir);
            DefaultMutableTreeNode fileTreeNode = new DefaultMutableTreeNode(relativePubspecFile);
            rootTreeNode.add(fileTreeNode);
        }

        model.reload(rootTreeNode);
    }
}

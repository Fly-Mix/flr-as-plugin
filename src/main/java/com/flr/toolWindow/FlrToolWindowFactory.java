package com.flr.toolWindow;

import com.flr.logConsole.FlrLogConsole;
import com.flr.pubspecFileTree.FlrPubspecFileTree;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class FlrToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FlrToolWindowProvider flrToolWindowProvider = new FlrToolWindowProvider();

        // 添加FlrToolWindow内容到IDE的ID为Flr的toolWindow
        Content content = toolWindow.getContentManager().getFactory().createContent(flrToolWindowProvider.windowContent, "", true);
        content.setDisplayName("");
        toolWindow.getContentManager().addContent(content, 0);

        // 添加FlrPubspecFileTree控件到flrToolWindowContainer-splitPane-left中
        FlrPubspecFileTree flrPubspecFileTree = new FlrPubspecFileTree(project);
        flrToolWindowProvider.splitPane.setLeftComponent(flrPubspecFileTree);
        flrPubspecFileTree.refreshContent();

        // 添加consoleView到flrToolWindowContainer-splitPane-right中
        ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        flrToolWindowProvider.consoleContainer.add(consoleView.getComponent(), BorderLayout.CENTER);

        String welcomeMessage = "Welcome to use Flr\nYou can get more details from https://github.com/Fly-Mix/flr-as-plugin";
        consoleView.print(welcomeMessage, ConsoleViewContentType.SYSTEM_OUTPUT);


//        DefaultMutableTreeNode color =new DefaultMutableTreeNode("pubspec.yaml");
//        DefaultMutableTreeNode font =new DefaultMutableTreeNode("example/pubspec.yaml");
//
//        DefaultTreeModel model = (DefaultTreeModel)flrToolWindowProvider.fileTree.getModel();
//        DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
//        root.setUserObject("All pubspec.yaml files");
//        root.removeAllChildren();
//        root.add(color);
//        root.add(font);
//        model.reload(root);
    }

    public static FlrLogConsole getLogConsole(@NotNull Project project){
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flr");
        if(toolWindow == null) {
            return null;
        }

        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.findContent("");

        JPanel flrToolWindowContent  = (JPanel) content.getComponent();
        JSplitPane splitPane = (JSplitPane)flrToolWindowContent.getComponent(0);

        JPanel consoleContainer = (JPanel) splitPane.getRightComponent();
        System.out.println("consoleContainer: ");
        System.out.println(consoleContainer);

        ConsoleView consoleView = (ConsoleView) consoleContainer.getComponent(0);
        System.out.println("consoleView: ");
        System.out.println(consoleView);

        FlrLogConsole flrLogConsole = new FlrLogConsole(project, consoleView);
        return flrLogConsole;
    }

    public static void showCurLogConsole(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Flr");
        if(toolWindow == null) {
            return;
        }

        toolWindow.show(new Runnable() {
            @Override
            public void run() {

            }
        });
    }
}
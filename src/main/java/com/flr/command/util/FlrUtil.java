package com.flr.command.util;


import com.flr.FlrConstant;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FlrUtil {

    // MARK: - Version Util Methods
    /*
    * @param v1
    * @param v2
    * @return if v1 > v2, return 1
    *         if v1 < v2, return 2
    *         if equal, return 0
    *         input error, return -1
     * */
    public static int versionCompare(String v1, String v2) {
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)*");

        if(!pattern.matcher(v1).matches() || !pattern.matcher(v2).matches()) {
            return -1;
        }

        String[] s1 = v1.split("\\.");
        String[] s2 = v2.split("\\.");

        int length = s1.length < s2.length ? s1.length : s2.length;
        for(int i = 0; i < length; i++) {
            int diff = Integer.valueOf(s1[i]) - Integer.valueOf(s2[i]);

            if(diff == 0) {
                continue;
            }else{
                return  diff > 0 ? 1 : 2;
            }
        }

        return 0;
    }

    /*
    * 获取当前插件的版本
    * */
    public static String getFlrVersion() {
        PluginId flrPluginId = PluginId.getId(FlrConstant.PLUGIN_ID);
        return PluginManagerCore.getPlugin(flrPluginId).getVersion();
    }

    // MARK: - Flutter Util Methods
    public static void runFlutterPubGet(AnActionEvent actionEvent) {
        // 从后台任务线程切换到UI线程
        ApplicationManager.getApplication().invokeLater(() -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
                // 使用 ActionManager 来执行 FlutterPackagesGetAction
                AnAction action = ActionManager.getInstance().getAction("flutter.pub.get");
                if (action != null) {
                    action.actionPerformed(actionEvent);
                }
            });
        });
    }

    public static void formatDartFile(@NotNull Project project, @NotNull File dartFile) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        // 格式化方案：Android Studio（non-IDEA JetBrains IDE）和 IDEA JetBrains IDE 上均可行
                        VirtualFile dartVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(dartFile);
                        if (dartVirtualFile == null) {
                            return;
                        }
                        List<VirtualFile> dartFiles = new ArrayList<VirtualFile>();
                        dartFiles.add(dartVirtualFile);
                        DartStyleAction.runDartfmt(project, dartFiles);

                        dartVirtualFile.refresh(false, false);
                    }
                });
            }
        });
    }
}

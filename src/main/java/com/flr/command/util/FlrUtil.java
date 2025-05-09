package com.flr.command.util;


import com.flr.FlrConstant;
import com.flr.FlrException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import com.sun.istack.NotNull;
import io.flutter.actions.FlutterPackagesGetAction;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
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

    // MARK: - Shell Util Methods

    // 在IDEA JetBrains IDE上可执行脚本成功，但是在 Android Studio（non-IDEA JetBrains IDE）上却执行失败，
    // 原因预估是IDE版本差异导致API差异导致执行脚本失败，具体待后面进一步确定
    /*
    * 运行shell脚本并返回运行结果
    * 注意：如果shell脚本中含有awk,一定要按new String[]{"/bin/sh","-c",shStr}写,才可以获得流
    *
    * @param shStr 需要运行的shell脚本
    * @return List<String> shell脚本运行结果
    * */
    public static List<String> execShell(String shStr) {
        List<String> outputLineList = new ArrayList<String>();
        System.out.println(String.format("Running shell command `%s` now ...", shStr));
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh","-c",shStr},null,null);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;

            // Read the output from the command
            System.out.println("Here is the standard output of the command:");
            while ((line = stdInput.readLine()) != null){
                System.out.println("    " + line);
                outputLineList.add(line);
            }

            // Read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):");
            while ((line = stdError.readLine()) != null) {
                System.out.println("    " + line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(String.format("Running shell command `%s` done!", shStr));
        return outputLineList;
    }

    // MARK: - Flutter Util Methods
    public static void runFlutterPubGet(AnActionEvent actionEvent) {
        // 从后台任务线程切换到UI线程
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206124399-Error-Write-access-is-allowed-from-event-dispatch-thread-only
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        FlutterPackagesGetAction flutterPubGetAction = new FlutterPackagesGetAction();
                        flutterPubGetAction.actionPerformed(actionEvent);
                    }
                });
            }
        });
    }

    public static void formatDartFile(@NotNull Project project, @NotNull File dartFile) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        // 格式化方案一：Android Studio（non-IDEA JetBrains IDE）和 IDEA JetBrains IDE 上均可行
                        VirtualFile dartVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(dartFile);
                        if (dartVirtualFile == null) {
                            return;
                        }
                        List<VirtualFile> dartFiles = new ArrayList<VirtualFile>();
                        dartFiles.add(dartVirtualFile);
                        DartStyleAction.runDartfmt(project, dartFiles);

                        // 格式化方案二：Android Studio（non-IDEA JetBrains IDE）上不成功；在IDEA JetBrains IDE 上可行
                        /*
                        PsiFile dartPsiFile = PsiManager.getInstance(project).findFile(dartVirtualFile);
                        CodeStyleManager.getInstance(project).reformat(dartPsiFile);
                        */

                        dartVirtualFile.refresh(false, false);
                    }
                });
            }
        });
    }
}

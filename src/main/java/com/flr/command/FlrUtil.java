package com.flr.command;


import com.flr.FlrConstant;
import com.flr.FlrException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.lang.dart.ide.actions.DartStyleAction;
import com.sun.istack.NotNull;
import io.flutter.actions.FlutterPackagesGetAction;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.common.FlowStyle;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/*
 * 专有名词解释：
 * PS：以下有部分定义（file_dirname、file_basename、file_basename_no_extension、file_extname）参考自 Visual Studio Code
 *
 * asset：flutter工程的资源，其定义是“packages/#{package_name}/#{asset_name}”，例如“packages/flutter_demo/assets/images/hot_foot_N.png”
 * package_name：flutter工程的包名，例如“flutter_demo”
 * asset_name：资源名称，其定义是“#{file_dirname}/#{file_basename}”，例如“assets/images/hot_foot_N.png”
 * file_dirname：资源的目录路径名称，例如“assets/images”
 * file_basename：资源的文件名，其定义是“#{file_basename_no_extension}#{file_extname}”，例如“hot_foot_N.png”
 * file_basename_no_extension：资源的不带扩展名的文件名，例如“hot_foot_N”
 * file_extname：资源的扩展名，例如“.png”
 *
 * asset_id：资源ID，其值一般为 file_basename_no_extension
 */

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
        PluginId flrPluginId = PluginId.getId(FlrConstant.flrId);
        String flrVersion = PluginManager.getPlugin(flrPluginId).getVersion();
        return  flrVersion;
    }

    // MARK: - Pubspec.yaml Util Methods

    public static Map<String, Object> loadPubspecMapFromYaml(String pubspecFilePath) {
        try {
            LoadSettings settings = LoadSettings.builder().build();
            Load load = new Load(settings);
            File pubspecFile = new File(pubspecFilePath);
            InputStream inputStream = new FileInputStream(pubspecFile);
            Map<String, Object> pubspecMap = (Map<String, Object>) load.loadFromInputStream(inputStream);
            return  pubspecMap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void dumpPubspecMapToYaml(Map<String, Object> pubspecMap, String pubspecFilePath) {
        try {
            DumpSettingsBuilder settingsBuilder = DumpSettings.builder();
            settingsBuilder.setDefaultFlowStyle(FlowStyle.BLOCK);
            settingsBuilder.setIndicatorIndent(2);
            DumpSettings settings = settingsBuilder.build();
            Dump dump = new Dump(settings);
            File file = new File(pubspecFilePath);
            StreamDataWriter writer = new YamlOutputStreamWriter(new FileOutputStream(file),
                    StandardCharsets.UTF_8) {
                @Override
                public void processIOException(IOException e) {
                    throw new RuntimeException(e);
                }
            };
            dump.dump(pubspecMap, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public static void formatDartFile(@NotNull Project project, @NotNull VirtualFile dartVirtualFile) {

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        // 格式化方案一：Android Studio（non-IDEA JetBrains IDE）和 IDEA JetBrains IDE 上均可行
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

    // MARK: - File Util Methods

    public static String getFileBasename(File file) {
        return file.getName();
    }

    public static String getFileBasenameWithoutExtension(File file) {
        if(file == null) {
            return null;
        }

        String fileBasename = file.getName();
        int lastIndexOf = fileBasename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return fileBasename;
        }
        String fileBasenameWithoutExtension = fileBasename.substring(0, lastIndexOf);
        return fileBasenameWithoutExtension;
    }

    public static String getFileExtension(File file) {
        if(file == null) {
            return null;
        }

        String fileBasename = file.getName();
        int lastIndexOf = fileBasename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        String fileExtension = fileBasename.substring(lastIndexOf);
        return fileExtension;
    }

    // MARK: - Asset Util Methods

    /*
    * 遍历指定asset文件夹下所有文件（包括子文件夹），返回合法asset数组和非法asset数组，如
    * 合法asset数组： ["packages/flutter_demo/assets/images/hot_foot_N.png", "packages/flutter_demo/assets/images/hot_foot_S.png"]
    * 非法asset数组： ["packages/flutter_demo/assets/images/test blank .png", "packages/flutter_demo/assets/images/test_#.png"]
    *
    * @param project 指定的项目
    * @param assetDirPath 指定资源目录相对路径
    * @param ignoredAssetTypes 需要忽略的资源类型数组，如 [".DS_Store"]
    * @param packageName 项目工程的包名
    * @return [legalAssetList, illegalAssetList]
    *  * */
    public static List<List<String>> getAssetsInDir(Project project, String assetDirPath, List<String> ignoredAssetTypes, String packageName) throws FlrException {
        List<String> legalAssetList = new ArrayList<String>();
        List<String> illegalAssetList = new ArrayList<String>();

        String assetDirFullPath = project.getBasePath() + "/" + assetDirPath;
        File assetDirFile = new File(assetDirFullPath);
        if(assetDirFile.exists() == false) {
            String message = String.format("%s not exists", assetDirFullPath);
            FlrException exception = new FlrException(message);
            throw(exception);
        }
        VirtualFile assetDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(assetDirFile);
        VirtualFile[] assetDirChildren = assetDirVirtualFile.getChildren();
        List<VirtualFile> assetFiles = new ArrayList<VirtualFile>();
        for(VirtualFile assetDirChild: assetDirChildren) {
            if(assetDirChild.isDirectory()) {
                VirtualFile[] assetSubDirChildren = assetDirChild.getChildren();
                for (VirtualFile assetSubDirChild: assetSubDirChildren) {
                    if(assetSubDirChild.isDirectory() == false) {
                        assetFiles.add(assetSubDirChild);
                    }
                }
            } else {
                assetFiles.add(assetDirChild);
            }
        }

        String fileDirName = assetDirPath.split("lib/")[1];

        Set<String> legalAssetSet = new LinkedHashSet<String>();
        Set<String> illegalAssetSet = new LinkedHashSet<String>();
        for(VirtualFile assetFile: assetFiles) {
            // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
            String virtualFileExtension = assetFile.getExtension();
            if (virtualFileExtension == null) {
                continue;
            }

            String assetType = "." + virtualFileExtension;
            if (ignoredAssetTypes != null && ignoredAssetTypes.contains(assetType)) {
                continue;
            }

            String fileBasename = assetFile.getName();
            String fileBasenameWithoutExtension = assetFile.getNameWithoutExtension();
            String assetName = fileDirName + "/" + fileBasename;
            String asset = "packages/" + packageName + "/" + assetName;

            if(isLegalFileBasename(fileBasenameWithoutExtension)) {
                legalAssetSet.add(asset);
            } else {
                illegalAssetSet.add(asset);
            }

        }

        legalAssetList = new ArrayList(legalAssetSet);
        illegalAssetList = new ArrayList(illegalAssetSet);

        List<List<String>> result = new ArrayList<List<String>>();
        result.add(legalAssetList);
        result.add(illegalAssetList);

        return result;
    }

    // 判断当前file_basename（无拓展名）是不是合法的文件名
    // 合法的文件名应该由数字、字母、其他合法字符（'_', '+', '-', '.', '·', '!', '@', '&', '$', '￥'）组成
    public static boolean isLegalFileBasename(String fileBasenameWithoutExtension) {
        Pattern pattern = Pattern.compile("^[0-9A-Za-z_\\+\\-\\.·!@&$￥]+$");

        if(pattern.matcher(fileBasenameWithoutExtension).matches()) {
            return true;
        }

        return false;
    }

    /*
    * 为asset生成assetId；其中priorAssetType为优先的资产类型，其决定了当前asset的assetId是否带有资产类型信息。
    * 如priorAssetType为".png"，
    * 这时候若asset为“packages/flutter_demo/assets/images/test.png”，这时其生成assetId为“test”，不带有类型信息；
    * 这时候若asset为“packages/flutter_demo/assets/images/test.jpg”，这时其生成assetId为“test_jpg”，带有类型信息；
    *
    * @param asset 指定的资产
    * @param priorAssetType 优先的资产类型，默认值为null，代表“.*”，意味生成的assetId总是带有资产类型信息
    * @return assetId 资产ID
    * */
    public static String generateAssetId(String asset, String priorAssetType) {
        File assetFile = new File(asset);
        String fileExtName = getFileExtension(assetFile).toLowerCase();
        String fileBasenameWithoutExtension = getFileBasenameWithoutExtension(assetFile);

        String assetId = fileBasenameWithoutExtension;
        if(priorAssetType == null || priorAssetType.equals(fileExtName) == false) {
            String extInfo = "_" + fileExtName.substring(1);
            assetId = fileBasenameWithoutExtension + extInfo;
        }

        // 过滤非法字符
        assetId = assetId.replaceAll("[^0-9A-Za-z_$]", "_");

        // 检测首字符是不是字母；
        // 若是字母，则检测其是不是大写字母，若是，则转换为小写字母；
        // 若不是字母，则添加一个前缀字母“a”
        Character firstChar = assetId.charAt(0);
        if(Character.isLetter(firstChar)) {
            if(Character.isUpperCase(firstChar)) {
                String firstCharStr = firstChar.toString().toLowerCase();
                assetId = firstCharStr + assetId.substring(1);
            }
        } else {
            String firstCharStr = "a";
            assetId = firstCharStr + assetId;
        }

        return assetId;
    }

    /*
    * 为asset生成对应的注释；注释内容为资产在pubspec.yaml中的对应声明
    * */
    public static String generateAssetComment(String asset, String packageName) {
        String packageInfo = "packages/" + packageName + "/";
        String assetDigest = asset.replace(packageInfo, "");
        String assetComment = "asset: " + assetDigest;
        return assetComment;
    }

    /*
    * 为asset生成对应的AssetResource代码
    *
    * @param asset 指定的资产
    * @param packageName 报名
    * @param priorAssetType 优先的资产类型，默认值为null，代表“.*”，意味生成的assetId总是带有资产类型信息；具体看generateAssetId方法的介绍
    * @return assetResourceCode AssetResource代码
    * */
    public static String generateAssetResourceCode(String asset, String packageName, String priorAssetType) {
        String assetId = generateAssetId(asset, priorAssetType);
        String assetComment = generateAssetComment(asset, packageName);

        File assetFile = new File(asset);
        String fileExtName = getFileExtension(assetFile).toLowerCase();
        String fileBasename = getFileBasename(assetFile);

        String fileDirName = asset;
        String packageInfo = "packages/" + packageName + "/";
        fileDirName = fileDirName.replace(packageInfo, "");
        String fileBasenameInfo = "/" + fileBasename;
        fileDirName = fileDirName.replace(fileBasenameInfo, "");

        String paramFileBasename = fileBasename.replace("$", "\\$");
        String paramAssetName =  fileDirName + "/" + paramFileBasename;

        String assetResourceCode = String.format("  /// %s\n" +
                "  // ignore: non_constant_identifier_names\n" +
                "  final %s = const AssetResource(\"%s\", packageName: R.package);\n" + "\n",
                assetComment, assetId, paramAssetName) ;

        return assetResourceCode;
    }
    
}

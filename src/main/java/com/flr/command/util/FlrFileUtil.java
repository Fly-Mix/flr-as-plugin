package com.flr.command.util;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.core.report.FlrReporter;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/*
* 资源文件相关的工具类方法
* */
public class FlrFileUtil {

    private static final Logger LOG = Logger.getInstance(FlrFileUtil.class);

    /*
     * 获取flutter主工程的所有子工程的根目录
     * */
    public static List<String> getFlutterSubProjectRootDirs(@NotNull String flutterMainProjectRootDir) {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute(() -> {
        List<String> flutterSubProjectRootDirArray = new ArrayList<>();

        File resourceDirFile = new File(flutterMainProjectRootDir);
        VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
        if(resourceDirVirtualFile == null) {
            return flutterSubProjectRootDirArray;
        }

        String pubspecFileName = "pubspec.yaml";
        VirtualFile[] resourceDirChildren = resourceDirVirtualFile.getChildren();
        for(VirtualFile resourceDirChild: resourceDirChildren) {
            if(resourceDirChild.isDirectory()) {
                VirtualFile[] subResourceDirChildren = resourceDirChild.getChildren();
                for (VirtualFile subResourceDirChild: subResourceDirChildren) {
                    if(subResourceDirChild.isDirectory()) {
                        continue;
                    }

                    String fileBaseName = subResourceDirChild.getName();
                    if(fileBaseName.equals(pubspecFileName)) {
                        String flutterSubProjectRootDir = subResourceDirChild.getParent().getPath();
                        flutterSubProjectRootDirArray.add(flutterSubProjectRootDir);
                        break;
                    }
                }
            }
        }

        return flutterSubProjectRootDirArray;
        });
    }

    /*
     * 获取指定flutter工程的pubspec.yaml文件的路径
     * */
    public static String getPubspecFilePath(@NotNull String flutterProjectDir) {
        String pubspecFilePath = flutterProjectDir + "/pubspec.yaml";
        return pubspecFilePath;
    }

    /*
     * 判断指定flutter工程的工程类型是不是Package工程类型
     *
     * flutter工程共有4种工程类型：
     * - app：Flutter App工程，用于开发纯Flutter的App
     * - module：Flutter Component工程，用于开发Flutter组件以嵌入iOS和Android原生工程
     * - package：General Dart Package工程，用于开发一个供应用层开发者使用的包
     * - plugin：Plugin Package工程（属于特殊的Dart Package工程），用于开发一个调用特定平台API的包*
     *
     * flutter工程的工程类型可从flutter工程目录的 .metadata 文件中读取获得
     * 如果不存在 .metadata 文件，则判断 pubspec.yaml 是否存在 author 配置，若存在，说明是一个 Package工程
     * */
    public static boolean isPackageProjectType(@NotNull FlrReporter reporter, @NotNull String flutterProjectDir) {
        String metadataFilePath = flutterProjectDir + "/.metadata";
        File metadataFile = new File(metadataFilePath);

        if(metadataFile.exists()) {
            try {
                Yaml yaml = new Yaml();
                InputStream inputStream = new FileInputStream(metadataFile);
                Iterable<Object> itr = yaml.loadAll(inputStream);
                Map<String, Object> metadataConfig = null;
                for (Object obj : itr) {
                    if(obj instanceof Map) {
                        metadataConfig = (Map<String, Object>)obj;
                        break;
                    }
                }

                String projectType = (String) metadataConfig.get("project_type");
                if(projectType instanceof String == false) {
                    projectType = "unknown";
                }

                if(projectType.equals("package") || projectType.equals("plugin")) {
                    return true;
                }
            } catch (Exception e) {
                LOG.warn(e);
                reporter.log(e.getMessage(), FlrLogConsole.LogType.normal);
                reporter.log("", FlrLogConsole.LogType.normal);
            }
        } else {
            String warningStr = "[!]: warning, metadata file is missed, flr can not make sure to get a right project type of this flutter project"
                    + "\n"
                    + "[!]: then flr maybe generate buggy r.g.dart";
            reporter.log(warningStr, FlrLogConsole.LogType.warning);
            String tipsStr = String.format("[*]: to fix it, you can manually copy the metadata file of a flutter project with same project type to %s\n", metadataFilePath);
            reporter.log(tipsStr, FlrLogConsole.LogType.tips);


            String pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectDir);
            File pubspecFile = new File(pubspecFilePath);
            try {
                Map<String, Object> pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile);
                if(pubspecConfig.containsKey("author")) {
                    return true;
                }
            } catch (FlrException e) {
                LOG.warn(e);
                reporter.log(e.getMessage(), FlrLogConsole.LogType.normal);
                reporter.log("", FlrLogConsole.LogType.normal);
            }
        }

        return false;
    }

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

    /*
    * 把resourceDir转换为relativeResourceDir
    *
    * === Examples
    * flutterProjectDir =  "~/path/to/flutter_r_demo"
    * resourceDir = "~/path/to/flutter_r_demo/lib/assets/images"
    * relativeResourceDir = "lib/assets/images"
    * */
    public static String convertToRelativeResourceDir(@NotNull String flutterProjectDir, @NotNull String resourceDir) {
        String relativeResourceDir = resourceDir.replaceFirst(flutterProjectDir + "/", "");
        return relativeResourceDir;
    }

    /*
     * 把resourceDir数组转换为relativeResourceDir数组
     *
     * === Examples
     * flutterProjectDir =  "~/path/to/flutter_r_demo"
     * resourceDirArray = ["~/path/to/flutter_r_demo/lib/assets/images"]
     * relativeResourceDirArray = ["lib/assets/images"]
     * */
    public static List<String> convertToRelativeResourceDirs(@NotNull String flutterProjectDir, @NotNull List<String> resourceDirs) {
        List<String> relativeResourceDirArray = new ArrayList<>();
        for(String resourceDir : resourceDirs) {
            String relativeResourceDir = convertToRelativeResourceDir(flutterProjectDir, resourceDir);
            relativeResourceDirArray.add(relativeResourceDir);
        }
        return relativeResourceDirArray;
    }

    /*
    * 写文件（经 VFS，走 WriteCommandAction）
    * */
    public static void writeContentToFile(@NotNull Project project, @NotNull String content, @NotNull File file) throws FlrException {
        writeTextViaVfs(project, file, content);
    }

    /*
     * 在 EDT + WriteCommandAction 内经 VFS 写入文本：写入走 VFS/Document 层，自动同步编辑器、可撤销、无需手动 refresh。
     *
     * 调用方在 Task.Backgroundable 后台线程（无写锁）执行，故用 invokeAndWait 切到 EDT 并保持同步语义
     * （返回即写完、失败即抛 FlrException）；写动作内的异常经 holder 回抛到调用线程。
     * */
    private static void writeTextViaVfs(@NotNull Project project, @NotNull File file, @NotNull String content) throws FlrException {
        // 父目录先在后台线程做同步刷新（不持写锁，此处允许 sync refresh），确保 VFS 已感知磁盘现状
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());

        final FlrException[] thrown = new FlrException[1];
        ApplicationManager.getApplication().invokeAndWait(() ->
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    VirtualFile parent = VfsUtil.createDirectoryIfMissing(file.getParentFile().getPath());
                    if (parent == null) {
                        thrown[0] = new FlrException("can not create directory: " + file.getParent());
                        return;
                    }
                    VirtualFile vFile = parent.findChild(file.getName());
                    if (vFile == null) {
                        vFile = parent.createChildData(FlrFileUtil.class, file.getName());
                    }
                    VfsUtil.saveText(vFile, content);
                } catch (IOException e) {
                    LOG.warn(e);
                    thrown[0] = new FlrException(e.getMessage());
                }
            }));
        if (thrown[0] != null) {
            throw thrown[0];
        }
    }

    /*
    * 读取pubspec.yaml到 pubspecConfig
    * 若读取成功，返回一个字典对象pubspecConfig
    * 若读取失败，则返回null
    * */
    public static Map<String, Object> loadPubspecConfigFromFile(@NotNull FlrReporter reporter, @NotNull File pubspecFile) throws FlrException {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = new FileInputStream(pubspecFile);
            Iterable<Object> itr = yaml.loadAll(inputStream);
            Map<String, Object> pubspecConfig = null;
            for (Object obj : itr) {
                if(obj instanceof Map) {
                    pubspecConfig = (Map<String, Object>)obj;
                    break;
                }
            }
            return pubspecConfig;
        } catch (Exception e) {
            LOG.warn(e);
            reporter.log(e.getMessage(), FlrLogConsole.LogType.normal);
            reporter.log("", FlrLogConsole.LogType.normal);
            reporter.log("[x]: pubspec.yaml is damaged, maybe it has some syntax errors", FlrLogConsole.LogType.error);
            reporter.log(String.format("[*]: please correct the pubspec.yaml file at %s", pubspecFile), FlrLogConsole.LogType.tips);
            throw FlrException.ILLEGAL_ENV;
        }
    }

    /*
     * 保存pubspecConfig到pubspec.yaml（经 VFS，走 WriteCommandAction）
     * */
    public static void dumpPubspecConfigToFile(@NotNull Project project, Map<String, Object> pubspecConfig, File pubspecFile) {
        try {
            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumperOptions.setIndent(2);
            dumperOptions.setIndicatorIndent(0);
            Yaml yaml = new Yaml(dumperOptions);
            StringWriter writer = new StringWriter();
            yaml.dump(pubspecConfig, writer);

            writeTextViaVfs(project, pubspecFile, writer.toString());
        } catch (Exception e) {
            LOG.warn(e);
        }
    }

    /*
     * 判断当前资源文件是否合法
     *
     * 判断资源文件合法的标准是：
     * 其file_basename_no_extension 由字母（a-z、A-Z）、数字（0-9）、其他合法字符（'_', '+', '-', '.', '·', '!', '@', '&', '$', '￥'）组成
     *
     * */
    public static boolean isLegalResourceFile(@NotNull VirtualFile virtualFile) {
        String fileBasenameWithoutExtension = virtualFile.getNameWithoutExtension();

        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_\\+\\-\\.·!@&$￥]+$");
        if(pattern.matcher(fileBasenameWithoutExtension).matches()) {
            return true;
        }

        return false;
    }

    public static boolean isLegalResourceFile(@NotNull File file) {
        String fileBasenameWithoutExtension = getFileBasenameWithoutExtension(file);

        Pattern pattern = Pattern.compile("^[a-zA-Z0-9_\\+\\-\\.·!@&$￥]+$");
        if(pattern.matcher(fileBasenameWithoutExtension).matches()) {
            return true;
        }

        return false;
    }

    public static boolean isNonSvgImageResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        fullVirtualFileExtension = fullVirtualFileExtension.toLowerCase();
        if(FlrConstant.NON_SVG_IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isNonSvgImageResourceFile(@NotNull File file) {
        String fileExtName = getFileExtension(file).toLowerCase();
        if(FlrConstant.NON_SVG_IMAGE_FILE_TYPES.contains(fileExtName)) {
            return true;
        }
        return false;
    }

    public static boolean isSvgImageResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        fullVirtualFileExtension = fullVirtualFileExtension.toLowerCase();
        if(FlrConstant.SVG_IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isSvgImageResourceFile(@NotNull File file) {
        String fileExtName = getFileExtension(file).toLowerCase();
        if(FlrConstant.SVG_IMAGE_FILE_TYPES.contains(fileExtName)) {
            return true;
        }
        return false;
    }

    public static boolean isImageResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        fullVirtualFileExtension = fullVirtualFileExtension.toLowerCase();
        if(FlrConstant.IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isImageResourceFile(@NotNull File file) {
        String fileExtName = getFileExtension(file).toLowerCase();
        if(FlrConstant.IMAGE_FILE_TYPES.contains(fileExtName)) {
            return true;
        }
        return false;
    }

    public static boolean isTextResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        fullVirtualFileExtension = fullVirtualFileExtension.toLowerCase();
        if(FlrConstant.TEXT_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isTextResourceFile(@NotNull File file) {
        String fileExtName = getFileExtension(file).toLowerCase();
        if(FlrConstant.TEXT_FILE_TYPES.contains(fileExtName)) {
            return true;
        }
        return false;
    }

    public static boolean isFontResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        fullVirtualFileExtension = fullVirtualFileExtension.toLowerCase();
        if(FlrConstant.FONT_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isFontResourceFile(@NotNull File file) {
        String fileExtName = getFileExtension(file).toLowerCase();
        if(FlrConstant.FONT_FILE_TYPES.contains(fileExtName)) {
            return true;
        }
        return false;
    }

    /*
    * 扫描指定的资源目录和其所有层级的子目录，查找所有图片文件
    * 返回文本文件结果二元组 imageFileResultTuple
    * imageFileResultTuple = [legalImageFileArray, illegalImageFileArray]
    *
    * 判断资源文件合法的标准参考：isLegalResourceFile 方法
    *
    * === Examples
    * resourceDir = "~/path/to/flutter_project/lib/assets/images"
    * legalImageFileArray = ["~/path/to/flutter_project/lib/assets/images/test.png", "~/path/to/flutter_project/lib/assets/images/2.0x/test.png"]
    * illegalImageFileArray = ["~/path/to/flutter_project/lib/assets/images/~.png"]
    * */
    public static List<List<VirtualFile>> findImageFiles(@NotNull String resourceDir) {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute(() -> {
        List<VirtualFile> legalImageFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalImageFileArray = new ArrayList<VirtualFile>();

        File resourceDirFile = new File(resourceDir);
        VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
        VfsUtilCore.visitChildrenRecursively(resourceDirVirtualFile, new VirtualFileVisitor<Object>(){
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory() == false && isImageResourceFile(file)) {
                    if(isLegalResourceFile(file)) {
                        legalImageFileArray.add(file);
                    } else {
                        illegalImageFileArray.add(file);
                    }
                    return true;
                }
                return super.visitFile(file);
            }
        });

        List<List<VirtualFile>> imageFileResultTuple = new ArrayList<List<VirtualFile>>();
        imageFileResultTuple.add(legalImageFileArray);
        imageFileResultTuple.add(illegalImageFileArray);
        return imageFileResultTuple;
        });
    }

    /*
     * 扫描指定的资源目录和其所有层级的子目录，查找所有文本文件
     * 返回图片文件结果二元组 textFileResultTuple
     * textFileResultTuple = [legalTextFileArray, illegalTextFileArray]
     *
     * 判断资源文件合法的标准参考：isLegalResourceFile 方法
     *
     * === Examples
     * resourceDir = "~/path/to/flutter_project/lib/assets/jsons"
     * legalTextFileArray = ["~/path/to/flutter_project/lib/assets/jsons/city.json", "~/path/to/flutter_project/lib/assets/jsons/mock/city.json"]
     * illegalTextFileArray = ["~/path/to/flutter_project/lib/assets/jsons/~.json"]
     * */
    public static List<List<VirtualFile>> findTextFiles(@NotNull String resourceDir) {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute(() -> {
        List<VirtualFile> legalTextFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalTextFileArray = new ArrayList<VirtualFile>();

        File resourceDirFile = new File(resourceDir);
        VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
        VfsUtilCore.visitChildrenRecursively(resourceDirVirtualFile, new VirtualFileVisitor<Object>(){
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory() == false && isTextResourceFile(file)) {
                    if(isLegalResourceFile(file)) {
                        legalTextFileArray.add(file);
                    } else {
                        illegalTextFileArray.add(file);
                    }
                    return true;
                }
                return super.visitFile(file);
            }
        });

        List<List<VirtualFile>> textFileResultTuple = new ArrayList<List<VirtualFile>>();
        textFileResultTuple.add(legalTextFileArray);
        textFileResultTuple.add(illegalTextFileArray);
        return textFileResultTuple;
        });
    }

    /*
     * 扫描指定的资源目录，返回其所有第一级子目录
     *
     * === Examples
     * resourceDir = "~/path/to/flutter_project/lib/assets/fonts"
     * topChildDirArray = ["~/path/to/flutter_project/lib/assets/fonts/Amiri", "~/path/to/flutter_project/lib/assets/fonts/Open_Sans"]
     * */
    public static List<VirtualFile> findTopChildDirs(@NotNull String resourceDir) {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute(() -> {
        List<VirtualFile> topChildDirArray = new ArrayList<VirtualFile>();

        File resourceDirFile = new File(resourceDir);
        VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
        VirtualFile[] resourceDirChildren = resourceDirVirtualFile.getChildren();
        for(VirtualFile resourceDirChild: resourceDirChildren) {
            if(resourceDirChild.isDirectory()) {
                topChildDirArray.add(resourceDirChild);
            }
        }

        return topChildDirArray;
        });
    }

    /*
     * 扫描指定的字体家族目录和其所有层级的子目录，查找所有字体文件
     * 返回字体文件结果二元组 fontFileResultTuple
     * textFileResultTuple = [legalFontFileArray, illegalFontFileArray]
     *
     * 判断资源文件合法的标准参考：isLegalResourceFile 方法
     *
     * === Examples
     * fontFamilyDirFile = "~/path/to/flutter_project/lib/assets/fonts/Amiri"
     * legalFontFileArray = ["~/path/to/flutter_project/lib/assets/fonts/Amiri/Amiri-Regular.ttf"]
     * illegalFontFileArray = ["~/path/to/flutter_project/lib/assets/fonts/Amiri/~.ttf"]
     * */
    public static List<List<VirtualFile>> findFontFilesInFontFamilyDir(@NotNull VirtualFile fontFamilyDirFile) {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute(() -> {
        List<VirtualFile> legalFontFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalFontFileArray = new ArrayList<VirtualFile>();

        VfsUtilCore.visitChildrenRecursively(fontFamilyDirFile, new VirtualFileVisitor<Object>(){
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory() == false && isFontResourceFile(file)) {
                    if(isLegalResourceFile(file)) {
                        legalFontFileArray.add(file);
                    } else {
                        illegalFontFileArray.add(file);
                    }
                    return true;
                }
                return super.visitFile(file);
            }
        });

        List<List<VirtualFile>> fontFileResultTuple = new ArrayList<List<VirtualFile>>();
        fontFileResultTuple.add(legalFontFileArray);
        fontFileResultTuple.add(illegalFontFileArray);
        return fontFileResultTuple;
        });
    }
}

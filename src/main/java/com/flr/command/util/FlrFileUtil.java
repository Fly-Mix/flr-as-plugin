package com.flr.command.util;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/*
* 资源文件相关的工具类方法
* */
public class FlrFileUtil {

    /*
     * 获取flutter主工程的所有子工程的根目录
     * */
    public static List<String> getFlutterSubProjectRootDirs(@NotNull String flutterMainProjectRootDir) {
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
    public static boolean isPackageProjectType(@NotNull FlrLogConsole flrLogConsole, @NotNull String flutterProjectDir) {
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
                e.printStackTrace();
                flrLogConsole.println(e.getMessage(), FlrLogConsole.LogType.normal);
                flrLogConsole.println("", FlrLogConsole.LogType.normal);
            }
        } else {
            String warningStr = "[!]: warning, metadata file is missed, flr can not make sure to get a right project type of this flutter project"
                    + "\n"
                    + "[!]: then flr maybe generate buggy r.g.dart";
            flrLogConsole.println(warningStr, FlrLogConsole.LogType.warning);
            String tipsStr = String.format("[*]: to fix it, you can manually copy the metadata file of a flutter project with same project type to %s\n", metadataFilePath);
            flrLogConsole.println(tipsStr, FlrLogConsole.LogType.tips);


            String pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectDir);
            File pubspecFile = new File(pubspecFilePath);
            try {
                Map<String, Object> pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(flrLogConsole, pubspecFile);
                if(pubspecConfig.containsKey("author")) {
                    return true;
                }
            } catch (FlrException e) {
                e.printStackTrace();
                flrLogConsole.println(e.getMessage(), FlrLogConsole.LogType.normal);
                flrLogConsole.println("", FlrLogConsole.LogType.normal);
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
    * 写文件，并刷新文件
    * */
    public static void writeContentToFile(@com.sun.istack.NotNull Project project, @com.sun.istack.NotNull String content, @com.sun.istack.NotNull File file) throws FlrException {
        if(file.exists() == false) {
            try {
                // 创建文件，并同步加载文件到工程
                file.createNewFile();
                List<File> ioFiles = new ArrayList<File>();
                ioFiles.add(file);
                LocalFileSystem.getInstance().refreshIoFiles(ioFiles);
            } catch (IOException e) {
                e.printStackTrace();
                FlrException flrException = new FlrException(e.getMessage());
                throw flrException;
            }
        }
        //Use try-with-resource to get auto-closeable writer instance
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(file.getPath())))
        {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
            FlrException flrException = new FlrException(e.getMessage());
            throw flrException;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if(virtualFile == null) {
            return;
        }
        virtualFile.refresh(false, false);
    }

    /*
    * 读取pubspec.yaml到 pubspecConfig
    * 若读取成功，返回一个字典对象pubspecConfig
    * 若读取失败，则返回null
    * */
    public static Map<String, Object> loadPubspecConfigFromFile(@NotNull FlrLogConsole flrLogConsole, @NotNull File pubspecFile) throws FlrException {
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
            e.printStackTrace();
            flrLogConsole.println(e.getMessage(), FlrLogConsole.LogType.normal);
            flrLogConsole.println("", FlrLogConsole.LogType.normal);
            flrLogConsole.println("[x]: pubspec.yaml is damaged, maybe it has some syntax errors", FlrLogConsole.LogType.error);
            flrLogConsole.println(String.format("[*]: please correct the pubspec.yaml file at %s", pubspecFile), FlrLogConsole.LogType.tips);
            throw FlrException.ILLEGAL_ENV;
        }
    }

    /*
     * 保存pubspecConfig到pubspec.yaml，并刷新pubspec.yaml
     * */
    public static void dumpPubspecConfigToFile(Map<String, Object> pubspecConfig, File pubspecFile) {
        try {
            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumperOptions.setIndent(2);
            dumperOptions.setIndicatorIndent(0);
            Yaml yaml = new Yaml(dumperOptions);
            FileWriter writer = new FileWriter(pubspecFile);
            yaml.dump(pubspecConfig, writer);

            VirtualFile pubspecVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(pubspecFile);
            if(pubspecVirtualFile == null) {
                return;
            }
            pubspecVirtualFile.refresh(false, false);
        } catch (Exception e) {
            e.printStackTrace();
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
    }

    /*
     * 扫描指定的资源目录，返回其所有第一级子目录
     *
     * === Examples
     * resourceDir = "~/path/to/flutter_project/lib/assets/fonts"
     * topChildDirArray = ["~/path/to/flutter_project/lib/assets/fonts/Amiri", "~/path/to/flutter_project/lib/assets/fonts/Open_Sans"]
     * */
    public static List<VirtualFile> findTopChildDirs(@NotNull String resourceDir) {
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
    }
}

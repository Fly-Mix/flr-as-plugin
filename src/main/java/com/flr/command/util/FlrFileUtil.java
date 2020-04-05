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
            if(pubspecFile == null) {
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

    public static boolean isImageResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        if(FlrConstant.IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isTextResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        if(FlrConstant.TEXT_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    public static boolean isFontResourceFile(@NotNull VirtualFile virtualFile) {
        // virtualFileExtension 不带“.”，如 path_to/test.png 的 virtualFileExtension 是 png
        String virtualFileExtension = virtualFile.getExtension();
        String fullVirtualFileExtension = "." + virtualFileExtension;
        if(FlrConstant.FONT_FILE_TYPES.contains(fullVirtualFileExtension)) {
            return true;
        }
        return false;
    }

    /*
    * v1.0.0: 扫描指定的资源目录和其第1级子目录，查找所有图片文件
    * v1.1.0: 放开图片资源扫描目录层级限制，以支持不标准的资源组织目录结构
    * 返回文本文件结果二元组 imageFileResultTuple
    * imageFileResultTuple = [legalImageFileArray, illegalImageFileArray]
    *
    * 判断资源文件合法的标准参考：isLegalResourceFile 方法
    * */
    public static List<List<VirtualFile>> findImageFiles(@NotNull Project project,@NotNull String resourceDir) {
        List<VirtualFile> legalImageFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalImageFileArray = new ArrayList<VirtualFile>();

        String resourceDirFullPath = project.getBasePath() + "/" + resourceDir;
        File resourceDirFile = new File(resourceDirFullPath);

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
     * */
    public static List<List<VirtualFile>> findTextFiles(@NotNull Project project,@NotNull String resourceDir) {
        List<VirtualFile> legalTextFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalTextFileArray = new ArrayList<VirtualFile>();

        String resourceDirFullPath = project.getBasePath() + "/" + resourceDir;
        File resourceDirFile = new File(resourceDirFullPath);

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
     * */
    public static List<VirtualFile> findTopChildDirs(@NotNull Project project,@NotNull String resourceDir) {
        List<VirtualFile> resourceDirFileArray = new ArrayList<VirtualFile>();

        String resourceDirFullPath = project.getBasePath() + "/" + resourceDir;
        File resourceDirFile = new File(resourceDirFullPath);

        VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
        VirtualFile[] resourceDirChildren = resourceDirVirtualFile.getChildren();
        for(VirtualFile resourceDirChild: resourceDirChildren) {
            if(resourceDirChild.isDirectory()) {
                resourceDirFileArray.add(resourceDirChild);
            }
        }

        return resourceDirFileArray;
    }

    /*
     * 扫描指定的字体家族目录和其所有层级的子目录，查找所有字体文件
     * 返回字体文件结果二元组 fontFileResultTuple
     * textFileResultTuple = [legalFontFileArray, illegalFontFileArray]
     *
     * 判断资源文件合法的标准参考：isLegalResourceFile 方法
     * */
    public static List<List<VirtualFile>> findFontFilesInFontFamilyDir(@NotNull Project project,@NotNull VirtualFile FontFamilyDirFile) {
        List<VirtualFile> legalFontFileArray = new ArrayList<VirtualFile>();
        List<VirtualFile> illegalFontFileArray = new ArrayList<VirtualFile>();

        // 递归遍历
//        VfsUtilCore.iterateChildrenRecursively(FontFamilyDirFile, new VirtualFileFilter() {
//            @Override
//            public boolean accept(VirtualFile file) {
//                // 筛选字体文件
//                if(file.isDirectory() == false && isFontResource(file)) {
//                    return true;
//                }
//                return false;
//            }
//        }, new ContentIterator() {
//            @Override
//            public boolean processFile(@NotNull VirtualFile fileOrDir) {
//                if(isLegalResourceFile(fileOrDir)) {
//                    legalFontFileArray.add(fileOrDir);
//                } else {
//                    illegalFontFileArray.add(fileOrDir);
//                }
//                return true;
//            }
//        });

        VfsUtilCore.visitChildrenRecursively(FontFamilyDirFile, new VirtualFileVisitor<Object>(){
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

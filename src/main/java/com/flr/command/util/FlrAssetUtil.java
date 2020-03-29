package com.flr.command.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/*
* 资产相关的工具类方法
* */
public class FlrAssetUtil {

    /*
    * 遍历指定资源目录下扫描找到的legalImageFile数组生成imageAsset数组
    * */
    public static List<String> generateImageAssets(@NotNull List<VirtualFile> legalImageFileArray,@NotNull String flutterProjectRootDir,@NotNull String resourceDir,@NotNull String packageName) {
        Set<String> imageAssetSet = new LinkedHashSet<String>();

        // implied_resource_dir = "assets/images"
        String impliedResourceDir = resourceDir.replaceFirst("lib/", "");

        for (VirtualFile imageVirtualFile : legalImageFileArray) {
            // imageVirtualFile: ~/path/to/flutter_r_demo/lib/assets/images/test.png
            // fileBasename: test.png
            String fileBasename = imageVirtualFile.getName();
            // packages/flutter_r_demo/assets/images/test.png
            String imageAsset = "packages/" + packageName + "/" + impliedResourceDir + "/" + fileBasename;
            imageAssetSet.add(imageAsset);
        }

        List<String> imageAssetArray = new ArrayList(imageAssetSet);
        return imageAssetArray;
    }

    /*
     * 遍历指定资源目录下扫描找到的legalTextFile数组生成textAsset数组
     * */
    public static List<String> generateTextAssets(@NotNull List<VirtualFile> legalTextFileArray,@NotNull String flutterProjectRootDir,@NotNull String resourceDir,@NotNull String packageName) {
        Set<String> textAssetSet = new LinkedHashSet<String>();

        for (VirtualFile textVirtualFile : legalTextFileArray) {
            // textVirtualFile: ~/path/to/flutter_r_demo/lib/assets/jsons/test.json
            String filePath = textVirtualFile.getPath();
            // lib/assets/jsons/test.json
            String resourceFile = filePath.replace(flutterProjectRootDir + "/", "");
            // assets/jsons/test.json
            String impliedResourceFile = resourceFile.replaceFirst("lib/", "");
            // packages/flutter_r_demo/assets/jsons/test.json
            String textAsset = "packages/" + packageName + "/" + impliedResourceFile;
            textAssetSet.add(textAsset);
        }

        List<String> textAssetArray = new ArrayList(textAssetSet);
        return textAssetArray;
    }

    /*
    * 遍历指定资源目录下扫描找到的legalFontFile数组生成fontAssetConfig数组
    *
    * fontAssetConfig = {"asset": "packages/flutter_r_demo/assets/fonts/Amiri/Amiri-Regular.ttf"}
    * */
    public static List<Map> generateFontAssetConfigs(@NotNull List<VirtualFile> legalFontFileArray,@NotNull String flutterProjectRootDir,@NotNull String resourceDir,@NotNull String packageName) {
        List<Map> fontAssetConfigArray = new ArrayList<Map>();

        for (VirtualFile fontVirtualFile : legalFontFileArray) {
            // fontVirtualFile: ~/path/to/flutter_r_demo/lib/assets/fonts/Amiri/Amiri-Regular.ttf
            String filePath = fontVirtualFile.getPath();
            // lib/assets/fonts/Amiri/Amiri-Regular.ttf
            String resourceFile = filePath.replace(flutterProjectRootDir + "/", "");
            // assets/fonts/Amiri/Amiri-Regular.ttf
            String impliedResourceFile = resourceFile.replaceFirst("lib/", "");
            // packages/flutter_r_demo/assets/fonts/Amiri/Amiri-Regular.ttf
            String fontAsset = "packages/" + packageName + "/" + impliedResourceFile;

            Map<String, String> fontAssetConfig = new LinkedHashMap<String, String>();
            fontAssetConfig.put("asset", fontAsset);

            fontAssetConfigArray.add(fontAssetConfig);
        }

        return fontAssetConfigArray;
    }
}

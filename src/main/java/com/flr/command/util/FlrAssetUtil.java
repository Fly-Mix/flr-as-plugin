package com.flr.command.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/*
* 资产相关的工具类方法
* */
public class FlrAssetUtil {

    /*
     * 判断当前的资源文件是不是资产变体（asset_variant）类型
     *
     * 判断的核心算法是：
     * - 获取资源文件的父目录；
     * - 判断父目录是否符合资产变体目录的特征
     *   资产变体映射的的资源文件要求存放在“与 main_asset 在同一个目录下的”、“符合指定特征的”子目录中；
     *   截止目前，Flutter只支持一种变体类型：倍率变体；
     *   倍率变体只适用于非SVG类图片资源；
     *   倍率变体目录特征可使用此正则来判断：“^((0\.[0-9]+)|([1-9]+[0-9]*(\.[0-9]+)?))[x]$”；
     *   倍率变体目录名称示例：“0.5x”、“1.5x”、“2.0x”、“3.0x”，“2x”、“3x”；
     *
     * */
    public static boolean isAssetVariant(@NotNull VirtualFile legalResourceFile) {
        if(FlrFileUtil.isNonSvgImageResourceFile(legalResourceFile)) {
            VirtualFile parentDirFile =  legalResourceFile.getParent();
            String parentDirName = parentDirFile.getName();

            String ratioRegex = "^((0\\.[0-9]+)|([1-9]+[0-9]*(\\.[0-9]+)?))[x]$";
            Pattern pattern = Pattern.compile(ratioRegex);
            if(pattern.matcher(parentDirName).matches()) {
                return true;
            }
        }

        return false;
    }

    /*
     * 为当前资源文件生成 main_asset
     *
     * === Examples
     * flutterProjectDir =  "~/path/to/flutter_r_demo"
     * packageName = "flutter_r_demo"
     *
     * === Example-1
     * legalResourceFile = "~/path/to/flutter_r_demo/lib/assets/images/test.png"
     * mainAsset = "packages/flutter_r_demo/assets/images/test.png"
     *
     * === Example-2
     * legalResourceFile = "~/path/to/flutter_r_demo/lib/assets/images/3.0x/test.png"
     * mainAsset = "packages/flutter_r_demo/assets/images/test.png"
     *
     * === Example-3
     * legalResourceFile = "~/path/to/flutter_r_demo/lib/assets/texts/3.0x/test.json"
     * mainAsset = "packages/flutter_r_demo/assets/texts/3.0x/test.json"
     *
     * === Example-3
     * legalResourceFile = "~/path/to/flutter_r_demo/lib/assets/fonts/Amiri/Amiri-Regular.ttf"
     * mainAsset = "packages/flutter_r_demo/fonts/Amiri/Amiri-Regular.ttf"
     *
     * === Example-4
     * legalResourceFile = "~/path/to/flutter_r_demo/assets/images/test.png"
     * mainAsset = "assets/images/test.png"
     *
     * === Example-5
     * legalResourceFile = "~/path/to/flutter_r_demo/assets/images/3.0x/test.png"
     * mainAsset = "assets/images/test.png"
     *
     * */
    public static String generateMainAsset(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull VirtualFile legalResourceFile) {
        // legalResourceFile:  ~/path/to/flutter_r_demo/lib/assets/images/3.0x/test.png
        // to get mainResourceFile:  ~/path/to/flutter_r_demo/lib/assets/images/test.png
        String mainResourceFile = legalResourceFile.getPath();
        if(isAssetVariant(legalResourceFile)) {
            // test.png
            String fileBasename = legalResourceFile.getName();
            // ~/path/to/flutter_r_demo/lib/assets/images/3.0x
            VirtualFile parentDirVirtualFile =  legalResourceFile.getParent();

            //to get mainResourceFileDir: ~/path/to/flutter_r_demo/lib/assets/images
            VirtualFile mainResourceFileDirVirtualFile =  parentDirVirtualFile.getParent();
            String mainResourceFileDir = mainResourceFileDirVirtualFile.getPath();

            // ~/path/to/flutter_r_demo/lib/assets/images/test.png
            mainResourceFile = String.format("%s/%s",mainResourceFileDir,fileBasename);
        }

        // mainResourceFile:  ~/path/to/flutter_r_demo/lib/assets/images/test.png
        // to get mainRelativeResourceFile: lib/assets/images/test.png
        String mainRelativeResourceFile = mainResourceFile.replaceFirst(flutterProjectDir + "/", "");

        // 判断 mainRelativeResourceFile 是不是 impliedResourceFile 类型
        // impliedResourceFile 的定义是：放置在 "lib/" 目录内 resource_file
        // 具体实现是：mainRelativeResourceFile 的前缀若是 "lib/" ，则其是 impliedResourceFile 类型；
        //
        // impliedResourceFile 生成 mainAsset 的算法是： mainAsset = "packages/#{packageName}/#{assetName}"
        // non-impliedResourceFile 生成 mainAsset 的算法是： mainAsset = "#{assetName}"
        //
        String libPrefix = "lib/";
        if(mainRelativeResourceFile.startsWith(libPrefix)) {
            // mainRelativeResourceFile: lib/assets/images/test.png
            // to get assetName: assets/images/test.png
            String assetName = mainRelativeResourceFile.replaceFirst("lib/", "");

            // mainAsset: packages/flutter_r_demo/assets/images/test.png
            String mainAsset = "packages/" + packageName + "/" + assetName;
            return mainAsset;
        } else {
            // mainRelativeResourceFile: assets/images/test.png
            // to get assetName: assets/images/test.png
            String assetName = mainRelativeResourceFile;

            // mainAsset: assets/images/test.png
            String mainAsset = assetName;
            return mainAsset;
        }
    }

    /*
    * 遍历指定资源目录下扫描找到的legalImageFile数组生成imageAsset数组
    * */
    public static List<String> generateImageAssets(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull List<VirtualFile> legalImageFileArray) {
        Set<String> imageAssetSet = new LinkedHashSet<String>();

        for (VirtualFile imageVirtualFile : legalImageFileArray) {
            String imageAsset = generateMainAsset(flutterProjectDir, packageName, imageVirtualFile);
            imageAssetSet.add(imageAsset);
        }

        List<String> imageAssetArray = new ArrayList(imageAssetSet);
        return imageAssetArray;
    }

    /*
     * 遍历指定资源目录下扫描找到的legalTextFile数组生成textAsset数组
     * */
    public static List<String> generateTextAssets(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull List<VirtualFile> legalTextFileArray) {
        Set<String> textAssetSet = new LinkedHashSet<String>();

        for (VirtualFile textVirtualFile : legalTextFileArray) {
            String textAsset = generateMainAsset(flutterProjectDir, packageName, textVirtualFile);
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
    public static List<Map> generateFontAssetConfigs(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull List<VirtualFile> legalFontFileArray) {
        List<Map> fontAssetConfigArray = new ArrayList<Map>();

        for (VirtualFile fontVirtualFile : legalFontFileArray) {
            String fontAsset = generateMainAsset(flutterProjectDir, packageName, fontVirtualFile);

            Map<String, String> fontAssetConfig = new LinkedHashMap<String, String>();
            fontAssetConfig.put("asset", fontAsset);

            fontAssetConfigArray.add(fontAssetConfig);
        }

        return fontAssetConfigArray;
    }
}

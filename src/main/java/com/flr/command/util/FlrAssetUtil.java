package com.flr.command.util;

// import com.intellij.history.core.Paths;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
// import java.nio.file.Path;
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
    * 判断当前资产是不是图片类资产
    *
    * === Examples
    *
    * === Example-1
    * asset = "packages/flutter_r_demo/assets/images/test.png"
    * @return true
    *
    * === Example-2
    * asset = "assets/images/test.png"
    * @return true
    *
    * */
    public static boolean isImageAsset(@NotNull String asset) {
        File file = new File(asset);
        if(FlrFileUtil.isImageResourceFile(file)) {
            return true;
        }

        return false;
    }

    /*
     * 判断当前资产是不是package类资产
     *
     * === Examples
     *
     * === Example-1
     * asset = "packages/flutter_r_demo/assets/images/test.png"
     * @return true
     *
     * === Example-2
     * asset = "assets/images/test.png"
     * @return false
     *
     * */
    public static boolean isPackageAsset(@NotNull String asset) {
        String packagePrefix = "packages/";
        if(asset.startsWith(packagePrefix)) {
            return true;
        }

        return false;
    }

    /*
     * 判断当前资产是不是指定的package的资产
     *
     * === Examples
     *
     * === Example-1
     * asset = "packages/flutter_r_demo/assets/images/test.png"
     * @return true
     *
     * === Example-2
     * asset = "assets/images/test.png"
     * @return false
     *
     * */
    public static boolean isSpecifiedPackageAsset(@NotNull String packageName, @NotNull String asset) {
        String specifiedPackagePrefix = "packages/" + packageName + "/";
        if(asset.startsWith(specifiedPackagePrefix)) {
            return true;
        }

        return false;
    }

    /*
     * 获取指定flutter工程的asset对应的主资源文件
     * 注意：主资源文件不一定存在，比如图片资产可能只存在变体资源文件
     *
     * === Examples
     * flutter_project_dir = "~/path/to/flutter_r_demo"
     * package_name = "flutter_r_demo"
     *
     * === Example-1
     * asset = "packages/flutter_r_demo/assets/images/test.png"
     * main_resource_file = "~/path/to/flutter_r_demo/lib/assets/images/test.png"
     *
     * === Example-2
     * asset = "assets/images/test.png"
     * main_resource_file = "~/path/to/flutter_r_demo/assets/images/test.png"
     *
     * */
    public static File getMainResourceFile(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull String asset) {
        if(isSpecifiedPackageAsset(packageName, asset)) {
            String specifiedPackagePrefix = "packages/" + packageName + "/";

            // asset: packages/flutter_r_demo/assets/images/test.png
            // to get impliedRelativeResourceFile: lib/assets/images/test.png
            String impliedRelativeResourceFile = asset.replaceFirst(specifiedPackagePrefix, "");
            impliedRelativeResourceFile = "lib/" + impliedRelativeResourceFile;

            // mainResourceFile:  ~/path/to/flutter_r_demo/lib/assets/images/test.png
            String mainResourceFilePath = flutterProjectDir + "/" + impliedRelativeResourceFile;
            File mainResourceFile = new File(mainResourceFilePath);
            return mainResourceFile;
        } else {
            // asset: assets/images/test.png
            // mainResourceFile:  ~/path/to/flutter_r_demo/assets/images/test.png
            String mainResourceFilePath = flutterProjectDir + "/" + asset;
            File mainResourceFile = new File(mainResourceFilePath);
            return mainResourceFile;
        }
    }

    /*
     * 判断指定flutter工程的asset是不是存在；存在的判断标准是：asset需要存在对应的资源文件
     *
     * === Examples
     * flutter_project_dir = "~/path/to/flutter_r_demo"
     * package_name = "flutter_r_demo"
     *
     * === Example-1
     * asset = "packages/flutter_r_demo/assets/images/test.png"
     * @return true
     *
     * === Example-2
     * asset = "packages/flutter_r_demo/404/not-existed.png"
     * @return false
     *
     * */
    public static boolean isAssetExisted(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull String asset) {
        // 处理指定flutter工程的asset
        // 1. 获取asset对应的main_resource_file
        // 2. 若main_resource_file是非SVG类图片资源文件，判断asset是否存在的标准是：主资源文件或者至少一个变体资源文件存在
        // 3. 若main_resource_file是SVG类图片资源文件或者其他资源文件，判断asset是否存在的标准是：主资源文件存在
        //

        File mainResourceFile = getMainResourceFile(flutterProjectDir, packageName, asset);
        if(FlrFileUtil.isNonSvgImageResourceFile(mainResourceFile)) {
            if(mainResourceFile.exists()) {
                return true;
            }

            String fileBaseName = mainResourceFile.getName();
            String fileDir = mainResourceFile.getParent();
            boolean didFindVariantResourceFile = false;
            File resourceDirFile = new File(fileDir);

            VirtualFile resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile);
            if(resourceDirVirtualFile == null) {
                return false;
            }
            VirtualFile[] resourceDirChildren = resourceDirVirtualFile.getChildren();
            for(VirtualFile resourceDirChild: resourceDirChildren) {
                if(resourceDirChild.isDirectory()) {
                    VirtualFile[] subResourceDirChildren = resourceDirChild.getChildren();
                    for (VirtualFile subResourceDirChild: subResourceDirChildren) {
                        if(subResourceDirChild.isDirectory()) {
                            continue;
                        }

                        if(!isAssetVariant(subResourceDirChild)) {
                            continue;
                        }

                        String variantResourceFileBaseName = subResourceDirChild.getName();
                        if(fileBaseName.equals(variantResourceFileBaseName)) {
                            didFindVariantResourceFile = true;
                        }
                    }
                }
            }

            if(didFindVariantResourceFile) {
                return true;
            }

        } else {
            if(mainResourceFile.exists()) {
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
            String assetName = mainRelativeResourceFile.replaceFirst(libPrefix, "");

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

    /*
     * 合并新旧2个asset数组：
     * - old_asset_array - new_asset_array = diff_asset_array，获取old_asset_array与new_asset_array的差异集合
     * - 遍历diff_asset_array，筛选合法的asset得到legal_old_asset_array；合法的asset标准是：非图片资源 + 存在对应的资源文件
     * - 按照字典序对legal_old_asset_array进行排序，并追加到new_asset_array
     * - 返回合并结果merged_asset_array
     *
     * === Examples
     * flutter_project_dir = "~/path/to/flutter_r_demo"
     * package_name = "flutter_r_demo"
     * new_asset_array = ["packages/flutter_r_demo/assets/images/test.png", "packages/flutter_r_demo/assets/jsons/test.json"]
     * old_asset_array = ["packages/flutter_r_demo/assets/htmls/test.html"]
     * merged_asset_array = ["packages/flutter_r_demo/assets/images/test.png", "packages/flutter_r_demo/assets/jsons/test.json", "packages/flutter_r_demo/assets/htmls/test.html"]
     *
     *  */
    public static List<String> mergeFlutterAssets(@NotNull String flutterProjectDir, @NotNull String packageName, @NotNull List<String> newAssetArray,  @NotNull List<String> oldAssetArray) {
        List<String> legalOldAssetArray = new ArrayList<>();

        List<String> diffAssetArray = oldAssetArray;
        diffAssetArray.removeAll(newAssetArray);
        for(String asset: diffAssetArray) {
            // 若是第三方package的资源，newAssetArray
            // 引用第三方package的资源的推荐做法是：通过引用第三方package的R类来访问
            if(isPackageAsset(asset)) {
                if(!isSpecifiedPackageAsset(packageName, asset)) {
                    legalOldAssetArray.add(asset);
                    continue;
                }
            }

            // 处理指定flutter工程的asset
            // 1. 判断asset是否存在
            // 2. 若asset存在，则合并到new_asset_array
            //
            if(isAssetExisted(flutterProjectDir, packageName, asset)) {
                legalOldAssetArray.add(asset);
            }
        }

        Collections.sort(legalOldAssetArray);
        List<String> mergedAssetArray = newAssetArray;
        mergedAssetArray.addAll(legalOldAssetArray);
        return mergedAssetArray;
    }
}

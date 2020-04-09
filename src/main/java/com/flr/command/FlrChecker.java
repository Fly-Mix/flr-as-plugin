package com.flr.command;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.logConsole.FlrLogConsole;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// 条件检测器，提供检测各种条件是否合法的方法
public class FlrChecker {
    /*
    * 检测当前flutter工程目录是否存在pubspec.yaml文件
    * 若存在返回true
    * 否则抛出异常
    * */
    public static boolean checkPubspecFileIsExisted(@NotNull FlrLogConsole flrLogConsole, @NotNull String flutter_dir) throws FlrException {
        String pubspecFilePath = flutter_dir + "/pubspec.yaml";
        File pubspecFile = new File(pubspecFilePath);

        if(pubspecFile.exists()) {
            return true;
        }

        flrLogConsole.println(String.format("[x]: %s not found", pubspecFilePath), FlrLogConsole.LogType.error);
        String tipsStr = String.format("[*]: please make sure %s is existed", pubspecFilePath);
        flrLogConsole.println(tipsStr, FlrLogConsole.LogType.tips);

        throw FlrException.ILLEGAL_ENV;
    }

    /*
    * 检测pubspec.yaml中是否存在flr的配置信息`flr_config`：
    * 若存在返回true
    * 否则抛出异常
    *
    * flr的配置：
    *
    * ``` yaml
    * flr:
    *   core_version: 1.0.0
    *   dartfmt_line_length: 80
    *   assets:
    *   fonts:
    * ```
    *
    * */
    public static boolean checkFlrConfigIsExisted(@NotNull FlrLogConsole flrLogConsole, @NotNull Map<String, Object> pubspecConfig) throws FlrException {
        Object flrConfig = pubspecConfig.get("flr");
        if(flrConfig instanceof Map) {
            return true;
        }

        flrLogConsole.println("[x]: have no flr configuration in pubspec.yaml", FlrLogConsole.LogType.error);
        flrLogConsole.println("[*]: please click menu \"Tools-Flr-Init\" to fix it", FlrLogConsole.LogType.tips);

        throw FlrException.ILLEGAL_ENV;
    }

    /*
    * 检测当前flr配置信息中的assets配置是否合法
    * 若合法，返回资源目录结果三元组 resourceDirResultTuple
    * 否则抛出异常
    *
    * flutterProjectRootDir = "~/path/to/flutter_r_demo"
    * resourceDirResultTuple = [assetsLegalResourceDirArray, fontsLegalResourceDirArray, illegalResourceDirArray]
    * assetsLegalResourceDirArray = ["~/path/to/flutter_r_demo/lib/assets/images", "~/path/to/flutter_r_demo/lib/assets/texts"]
    * fontsLegalResourceDirArray = ["~/path/to/flutter_r_demo/lib/assets/fonts"]
    * illegalResourceDirArray = ["~/path/to/flutter_r_demo/to/non-existed_folder"]
    *
    * */
    public static List<List<String>> checkFlrAssetsIsLegal(@NotNull FlrLogConsole flrLogConsole, @NotNull Map<String, Object> flrConfig, @NotNull String flutterProjectRootDir) throws FlrException {
        String flrCoreVersion = FlrConstant.CORE_VERSION;
        if(flrConfig.containsKey("core_version")) {
            flrCoreVersion = String.format("%s", flrConfig.get("core_version"));
        }
        String dartfmtLineLengthStr = String.format("%d",FlrConstant.DARTFMT_LINE_LENGTH);
        if(flrConfig.containsKey("dartfmt_line_length")) {
            dartfmtLineLengthStr = String.format("%s", flrConfig.get("dartfmt_line_length"));
        }
        List<String> assetsResourceDirArray = (List<String>)flrConfig.get("assets");
        List<String> fontsResourceDirArray = (List<String>)flrConfig.get("fonts");

        if(assetsResourceDirArray instanceof List == false) {
            assetsResourceDirArray = new ArrayList<String>();
        }
        if(fontsResourceDirArray instanceof List == false) {
            fontsResourceDirArray = new ArrayList<String>();
        }

        // 移除非法的 resource_dir（nil，空字符串，空格字符串）
        assetsResourceDirArray.remove("");
        assetsResourceDirArray.remove(null);
        fontsResourceDirArray.remove("");
        fontsResourceDirArray.remove(null);
        // 过滤重复的 resource_dir
        LinkedHashSet<String> tempSet = new LinkedHashSet<String>(assetsResourceDirArray);
        assetsResourceDirArray = new ArrayList<String>(tempSet);
        tempSet = new LinkedHashSet<String>(fontsResourceDirArray);
        fontsResourceDirArray = new ArrayList<String>(tempSet);

        // 筛选合法的和非法的resource_dir
        List<String> assetsLegalResourceDirArray = new ArrayList<String>();
        List<String> fontsLegalResourceDirArray = new ArrayList<String>();
        List<String> illegalResourceDirArray = new ArrayList<String>();

        for (String relativeResourceDir : assetsResourceDirArray) {
            String resourceDir = flutterProjectRootDir + "/" + relativeResourceDir;
            File dir = new File(resourceDir);
            if(dir.isDirectory() && dir.exists()) {
                assetsLegalResourceDirArray.add(resourceDir);
            } else {
                illegalResourceDirArray.add(resourceDir);
            }
        }

        for (String relativeResourceDir : fontsResourceDirArray) {
            String resourceDir = flutterProjectRootDir + "/" + relativeResourceDir;
            File dir = new File(resourceDir);
            if(dir.isDirectory() && dir.exists()) {
                fontsLegalResourceDirArray.add(resourceDir);
            } else {
                illegalResourceDirArray.add(resourceDir);
            }
        }

        int legalResourceDirCount = assetsLegalResourceDirArray.size() + fontsLegalResourceDirArray.size();
        if(legalResourceDirCount > 0) {
            List<List<String>> resourceDirResultTuple = new ArrayList<List<String>>();
            resourceDirResultTuple.add(assetsLegalResourceDirArray);
            resourceDirResultTuple.add(fontsLegalResourceDirArray);
            resourceDirResultTuple.add(illegalResourceDirArray);

            return resourceDirResultTuple;
        }

        if(illegalResourceDirArray.size() > 0) {
            String warningText = "[!]: warning, found the following resource directory which is not existed: ";
            for (String resourceDir : illegalResourceDirArray) {
                warningText += "\n" + String.format("  - %s", resourceDir);
            }
            warningText += "\n";
            flrLogConsole.println(warningText, FlrLogConsole.LogType.warning);
        }

        flrLogConsole.println("[x]: have no valid resource directories configuration in pubspec.yaml", FlrLogConsole.LogType.error);
        flrLogConsole.println(String.format(
                "[*]: please manually configure the resource directories to fix it, for example:\n" +
                        "\u202D \n" +
                        "\u202D     flr:\n" +
                        "\u202D       core_version: %s\n" +
                        "\u202D       dartfmt_line_length: %s\n" +
                        "\u202D       # config the image and text resource directories that need to be scanned\n" +
                        "\u202D       assets:\n" +
                        "\u202D         - lib/assets/images\n" +
                        "\u202D         - lib/assets/texts\n" +
                        "\u202D       # config the font resource directories that need to be scanned\n" +
                        "\u202D       fonts:\n" +
                        "\u202D         - lib/assets/fonts\n",
                flrCoreVersion, dartfmtLineLengthStr
        ), FlrLogConsole.LogType.tips);

        throw FlrException.ILLEGAL_ENV;
    }
}

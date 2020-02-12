package com.flr.command;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import io.flutter.sdk.*;


// 处理泛型对象时，若不做检查，就进行类型强制转换（如 (Map<String, Object>)map.get("key")），
// 编译器会报警告：FlrCommand.java使用了未经检查或不安全的操作。
// 解决办法之一是，添加 @SuppressWarnings("unchecked")。
@SuppressWarnings("unchecked")
public class FlrCommand implements Disposable {

    public boolean isMonitoringAssets = false;

    private final Project curProject;

    private FlrListener curFlrListener;

    private FlrLogConsole.LogType titleLogType = FlrLogConsole.LogType.tips;

    public FlrCommand(Project project) {
        curProject = project;
    }

    @Override
    public void dispose() {
        if(curFlrListener != null) {
            curFlrListener.dispose();
            curFlrListener = null;
        }
    }

    // MARK: Command Action Methods

    /*
    * 按照以下步骤执行初始化：
    * 1. 检测当前目录是否是合法的flutter工程目录
    * 2. 添加Flr配置到pubspec.yaml
    * 3. 添加依赖包`r_dart_library`(https://github.com/YK-Unit/r_dart_library)的声明到pubspec.yaml
    *  */
    public void init(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Init]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String pubspecFilePath = getPubspecFilePath();
        File pubspecFile = new File(pubspecFilePath);

        if(pubspecFile.exists() == false) {
            flrLogConsole.println(String.format("[x]: %s not found", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure current directory is a flutter project directory", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "<p>[x]: %s not found</p>" +
                    "<p>[*]: please make sure current directory is a flutter project directory</p>",
                    pubspecFilePath
            );
            FlrException exception = new FlrException(message);
            handleFlrException(exception);
            return;
        }

        // 读取pubspec.yaml，然后添加相关配置
        Map<String, Object> pubspecMap = FlrUtil.loadPubspecMapFromYaml(pubspecFilePath);
        if(pubspecMap == null) {
            flrLogConsole.println(String.format("[x]: %s is a bad YAML file", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure the pubspec.yaml is right", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "[x]: %s is a bad YAML file\n" +
                    "[*]: please make sure the pubspec.yaml is right",
                    pubspecFilePath
            );
            flrLogConsole.println(message, FlrLogConsole.LogType.error);
            FlrException exception = new FlrException(message);
            handleFlrException(exception);
            return;
        }

        indicatorMessage = String.format("init %s now ...", curProject.getBasePath());
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加Flr的配置到pubspec.yaml
        // Flr的配置:
        // flr:
        //    - version: 0.2.0
        //    - assets:
        //      - lib/assets/images
        //      - lib/assets/texts
        //
        Map<String, Object> flrMap = new LinkedHashMap<>();
        String usedFlrVersion = FlrUtil.getFlrVersion();
        flrMap.put("version", usedFlrVersion);
        List<String> assetList = new ArrayList<String>();
        flrMap.put("assets", assetList);
        pubspecMap.put("flr", flrMap);

        indicatorMessage = "add flr configuration into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加依赖包`r_dart_library`(https://github.com/YK-Unit/r_dart_library)的声明到pubspec.yaml
        String rDartLibraryVersion = getRDartLibraryVersion();
        Map<String, Object> rDartLibraryMap = new LinkedHashMap<String, Object>();
        Map<String, String> rDartLibraryGitMap = new LinkedHashMap<String, String>();
        rDartLibraryGitMap.put("url", "https://github.com/YK-Unit/r_dart_library.git");
        rDartLibraryGitMap.put("ref", rDartLibraryVersion);
        rDartLibraryMap.put("git", rDartLibraryGitMap);

        Map<String, Object> dependenciesMap = (Map<String, Object>)pubspecMap.get("dependencies");
        dependenciesMap.put("r_dart_library", rDartLibraryMap);
        pubspecMap.put("dependencies", dependenciesMap);

        indicatorMessage = "add dependency \"r_dart_library\"(https://github.com/YK-Unit/r_dart_library) into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 保存pubspec.yaml
        // 更新并刷新 pubspec.yaml
        FlrUtil.dumpPubspecMapToYaml(pubspecMap, pubspecFilePath);
        VirtualFile pubspecVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(pubspecFile);
        pubspecVirtualFile.refresh(false, false);

        indicatorMessage = "get dependency \"r_dart_library\" via running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "get dependency \"r_dart_library\" done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "[√]: init done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String cmdResultMessage = "<p>[√]: init done !!!</p>";
        cmdResultMessage += "<p>\u202D</p>";
        displayInfoLog(cmdResultMessage);
    }

    public void generate(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Generate]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        List<String> allValidAssetDirPaths = null;
        try {
            allValidAssetDirPaths = checkBeforeGenerate(flrLogConsole);
        } catch (FlrException e) {
            handleFlrException(e);
            return;
        }

        String pubspecFilePath = getPubspecFilePath();
        Map<String, Object> pubspecMap = FlrUtil.loadPubspecMapFromYaml(pubspecFilePath);

        if(pubspecMap == null) {
            flrLogConsole.println(String.format("[x]: %s is a bad YAML file", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure the pubspec.yaml is right", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "[x]: %s is a bad YAML file\n" +
                    "[*]: please make sure the pubspec.yaml is right",
                    pubspecFilePath
            );
            flrLogConsole.println(message, FlrLogConsole.LogType.error);
            FlrException exception = new FlrException(message);
            handleFlrException(exception);
            return;
        }

        Map<String, Object> flrMap = (Map<String, Object>)pubspecMap.get("flr");
        String flrVersion = (String) flrMap.get("version");

        indicatorMessage = "scan assets now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 需要过滤的资源类型
        // .DS_Store 是 macOS 下文件夹里默认自带的的隐藏文件
        List<String> ignoredAssetTypes = new ArrayList<String>();
        ignoredAssetTypes.add(".DS_Store");

        // 扫描资源，然后为扫描到的资源添加声明到pubspec.yaml
        String packageName = (String) pubspecMap.get("name");
        List<String> legalAssetList = new ArrayList<String>();
        List<String> illegalAssetList = new ArrayList<String>();

        for(String assetDirPath: allValidAssetDirPaths) {
            List<List<String>> assetsResult =  FlrUtil.getAssetsInDir(curProject, assetDirPath, ignoredAssetTypes, packageName);
            List<String> partOfLegalAssetList = assetsResult.get(0);
            List<String> partOfIllegalAssetList = assetsResult.get(1);

            legalAssetList.addAll(partOfLegalAssetList);
            illegalAssetList.addAll(partOfIllegalAssetList);

        }

        indicatorMessage = "scan assets done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加资源声明到 `pubspec.yaml`
        indicatorMessage = "specify scanned assets in pubspec.yaml now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        Map<String, Object> flutterMap = (Map<String, Object>)pubspecMap.get("flutter");
        flutterMap.put("assets", legalAssetList);
        pubspecMap.put("flutter", flutterMap);

        // 更新并刷新 pubspec.yaml
        FlrUtil.dumpPubspecMapToYaml(pubspecMap, pubspecFilePath);
        File pubspecFile = new File(pubspecFilePath);
        VirtualFile pubspecVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(pubspecFile);
        pubspecVirtualFile.refresh(false, false);

        indicatorMessage = "specify scanned assets in pubspec.yaml done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 创建生成 `r.g.dart`
        indicatorMessage = "generate \"r.g.dart\" now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String rDartContent = "";

        // ----- R Begin -----
        String rCode = String.format("// GENERATED CODE - DO NOT MODIFY BY HAND\n" +
                "// GENERATED BY FLR ANDROID STUDIO PLUGIN, SEE https://github.com/Fly-Mix/flr-as-plugin\n" +
                "//\n" +
                "\n" +
                "import 'package:flutter/widgets.dart';\n" +
                "import 'package:flutter/services.dart' show rootBundle;\n" +
                "import 'package:path/path.dart' as path;\n" +
                "import 'package:flutter_svg/flutter_svg.dart';\n" +
                "import 'package:r_dart_library/asset_svg.dart';\n" +
                "\n" +
                "/// This `R` class is generated and contains references to static asset resources.\n" +
                "class R {\n" +
                "  /// package name: %s\n" +
                "  static const package = \"%s\";\n" +
                "\n" +
                "  /// This `R.image` struct is generated, and contains static references to static non-svg type image asset resources.\n" +
                "  static const image = _R_Image();\n" +
                "\n" +
                "  /// This `R.svg` struct is generated, and contains static references to static svg type image asset resources.\n" +
                "  static const svg = _R_Svg();\n" +
                "\n" +
                "  /// This `R.text` struct is generated, and contains static references to static text asset resources.\n" +
                "  static const text = _R_Text();\n" +
                "}\n" +
                "\n" +
                "/// Asset resource’s metadata class.\n" +
                "/// For example, here is the metadata of `packages/flutter_demo/assets/images/example.png` asset:\n" +
                "/// - packageName：flutter_demo\n" +
                "/// - assetName：assets/images/example.png\n" +
                "/// - fileDirname：assets/images\n" +
                "/// - fileBasename：example.png\n" +
                "/// - fileBasenameNoExtension：example\n" +
                "/// - fileExtname：.png\n" +
                "class AssetResource {\n" +
                "  /// Creates an object to hold the asset resource’s metadata.\n" +
                "  const AssetResource(this.assetName, {this.packageName}) : assert(assetName != null);\n" +
                "\n" +
                "  /// The name of the main asset from the set of asset resources to choose from.\n" +
                "  final String assetName;\n" +
                "\n" +
                "  /// The name of the package from which the asset resource is included.\n" +
                "  final String packageName;\n" +
                "\n" +
                "  /// The name used to generate the key to obtain the asset resource. For local assets\n" +
                "  /// this is [assetName], and for assets from packages the [assetName] is\n" +
                "  /// prefixed 'packages/<package_name>/'.\n" +
                "  String get keyName => packageName == null ? assetName : \"packages/$packageName/$assetName\";\n" +
                "\n" +
                "  /// The file basename of the asset resource.\n" +
                "  String get fileBasename {\n" +
                "    final basename = path.basename(assetName);\n" +
                "    return basename;\n" +
                "  }\n" +
                "\n" +
                "  /// The no extension file basename of the asset resource.\n" +
                "  String get fileBasenameNoExtension {\n" +
                "    final basenameWithoutExtension = path.basenameWithoutExtension(assetName);\n" +
                "    return basenameWithoutExtension;\n" +
                "  }\n" +
                "\n" +
                "  /// The file extension name of the asset resource.\n" +
                "  String get fileExtname {\n" +
                "    final extension = path.extension(assetName);\n" +
                "    return extension;\n" +
                "  }\n" +
                "\n" +
                "  /// The directory path name of the asset resource.\n" +
                "  String get fileDirname {\n" +
                "    var dirname = assetName;\n" +
                "    if (packageName != null) {\n" +
                "      final packageStr = \"packages/$packageName/\";\n" +
                "      dirname = dirname.replaceAll(packageStr, \"\");\n" +
                "    }\n" +
                "    final filenameStr = \"$fileBasename/\";\n" +
                "    dirname = dirname.replaceAll(filenameStr, \"\");\n" +
                "    return dirname;\n" +
                "  }\n" +
                "}\n", packageName, packageName);

        rDartContent +=  rCode;

        // ----- R End -----

        List<String> supportedAssetImages = Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".webp", ".icon", ".bmp", ".wbmp");
        List<String> supportedAssetTexts = Arrays.asList(".txt", ".json", ".yaml", ".xml");

        // ----- _R_Image_AssetResource Begin -----
        // 生成 `class _R_Image_AssetResource` 的代码

        String rImageAssetResourceCodeHeader = "\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Image_AssetResource {\n" +
                "  const _R_Image_AssetResource();\n";
        rDartContent += rImageAssetResourceCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (supportedAssetImages.contains(fileExtName) == false) {
                continue;
            }

            String assetResourceCode = FlrUtil.generateAssetResourceCode(asset, packageName, ".png");
            rDartContent +=  assetResourceCode;
        }

        String rImageAssetResourceCodeFooter = "}\n";
        rDartContent +=  rImageAssetResourceCodeFooter;

        // ----- _R_Image_AssetResource End -----

        // ----- _R_Svg_AssetResource Begin -----
        // 生成 `class _R_Svg_AssetResource` 的代码

        String rSvgAssetResourceCodeHeader = "\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Svg_AssetResource {\n" +
                "  const _R_Svg_AssetResource();\n";
        rDartContent += rSvgAssetResourceCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (fileExtName.equals(".svg") == false) {
                continue;
            }

            String assetResourceCode = FlrUtil.generateAssetResourceCode(asset, packageName, ".svg");
            rDartContent +=  assetResourceCode;
        }

        String rSvgAssetResourceCodeFooter = "}\n";
        rDartContent += rSvgAssetResourceCodeFooter;

        // ----- _R_Svg_AssetResource End -----

        // ----- _R_Text_AssetResource Begin -----
        // 生成 `class _R_Text_AssetResource` 的代码

        String rTextAssetResourceCodeHeader = "\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Text_AssetResource {\n" +
                "  const _R_Text_AssetResource();\n";
        rDartContent += rTextAssetResourceCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (supportedAssetTexts.contains(fileExtName) == false) {
                continue;
            }

            String assetResourceCode = FlrUtil.generateAssetResourceCode(asset, packageName, null);
            rDartContent +=  assetResourceCode;
        }

        String rTextAssetResourceCodeFooter = "}\n";
        rDartContent +=  rTextAssetResourceCodeFooter;

        // ----- _R_Text_AssetResource End -----

        // -----  _R_Image Begin -----
        // 生成 `class _R_Image` 的代码

        String rImageCodeHeader = "\n" +
                "/// This `_R_Image` class is generated and contains references to static non-svg type image asset resources.\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Image {\n" +
                "  const _R_Image();\n" +
                "\n" +
                "  final asset = const _R_Image_AssetResource();\n";
        rDartContent += rImageCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (supportedAssetImages.contains(fileExtName) == false) {
                continue;
            }

            String assetId = FlrUtil.generateAssetId(asset, ".png");
            String assetComment = FlrUtil.generateAssetComment(asset, packageName);

            String assetMethodCode = String.format("\n" +
                    "  /// %s\n" +
                    "  // ignore: non_constant_identifier_names\n" +
                    "  AssetImage %s() {\n" +
                    "    return AssetImage(asset.%s.keyName);\n" +
                    "  }\n",
                    assetComment, assetId, assetId);
            rDartContent +=  assetMethodCode;
        }

        String rImageCodeFooter = "}\n";
        rDartContent += rImageCodeFooter;

        // -----  _R_Image End -----

        // -----  _R_Svg Begin -----
        // 生成 `class _R_Svg` 的代码

        String rSvgCodeHeader = "\n" +
                "/// This `_R_Svg` class is generated and contains references to static svg type image asset resources.\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Svg {\n" +
                "  const _R_Svg();\n" +
                "\n" +
                "  final asset = const _R_Svg_AssetResource();\n";
        rDartContent += rSvgCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (fileExtName.equals(".svg") == false) {
                continue;
            }

            String assetId = FlrUtil.generateAssetId(asset, ".svg");
            String assetComment = FlrUtil.generateAssetComment(asset, packageName);

            String assetMethodCode = String.format("\n" +
                    "  /// %s\n" +
                    "  // ignore: non_constant_identifier_names\n" +
                    "  AssetSvg %s({@required double width, @required double height}) {\n" +
                    "    final imageProvider = AssetSvg(asset.%s.keyName, width: width, height: height);\n" +
                    "    return imageProvider;\n" +
                    "  }\n", assetComment, assetId, assetId);
            rDartContent += assetMethodCode;
        }

        String rSvgCodeFooter = "}\n";
        rDartContent += rSvgCodeFooter;

        // -----  _R_Svg End -----

        // -----  _R_Text Begin -----
        // 生成 `class _R_Text` 的代码

        String rTextCodeHeader = "\n" +
                "/// This `_R_Text` class is generated and contains references to static text asset resources.\n" +
                "// ignore: camel_case_types\n" +
                "class _R_Text {\n" +
                "  const _R_Text();\n" +
                "\n" +
                "  final asset = const _R_Text_AssetResource();\n";
        rDartContent += rTextCodeHeader;

        for(String asset: legalAssetList) {
            File assetFile = new File(asset);

            if (assetFile == null) {
                continue;
            }

            String fileExtName = FlrUtil.getFileExtension(assetFile).toLowerCase();
            if (supportedAssetTexts.contains(fileExtName) == false) {
                continue;
            }

            String assetId = FlrUtil.generateAssetId(asset, null);
            String assetComment = FlrUtil.generateAssetComment(asset, packageName);

            String assetMethodCode = String.format("\n" +
                    "  /// %s\n" +
                    "  // ignore: non_constant_identifier_names\n" +
                    "  Future<String> %s() {\n" +
                    "    final str = rootBundle.loadString(asset.%s.keyName);\n" +
                    "    return str;\n" +
                    "  }\n", assetComment, assetId, assetId);
            rDartContent += assetMethodCode;
        }

        String rTextCodeFooter = "}\n";
        rDartContent += rTextCodeFooter;

        // ----- r.g.dart Begin -----

        // 把 rDartContent 写到 r.g.dart 中
        String rDartFilePath = curProject.getBasePath() + "/lib/r.g.dart";
        //Use try-with-resource to get auto-closeable writer instance
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(rDartFilePath)))
        {
            writer.write(rDartContent);
        } catch (IOException e) {
            FlrException flrException = new FlrException(e.getMessage());
            handleFlrException(flrException);
            return;
        }

        File rDartFile = new File(rDartFilePath);
        VirtualFile rDartVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(rDartFile);
        rDartVirtualFile.refresh(false, false);

        indicatorMessage = String.format("generate for %s done!", curProject.getBasePath());
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 格式化 r.g.dart
        indicatorMessage = "format r.g.dart now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.formatDartFile(curProject, rDartVirtualFile);
        indicatorMessage = "format r.g.dart done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 刷新 r.g.dart

        // 执行 "Flutter Packages Get" action
        indicatorMessage = "running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "running \"Flutter Packages Get\" action done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- r.g.dart End -----

        indicatorMessage = "[√]: generate done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String cmdResultMessage = "<p>[√]: generate done !!!</p>";
        int warningCount = 0;

        String usedFlrVersion = FlrUtil.getFlrVersion();
        if(flrVersion.equals(usedFlrVersion) == false) {
            flrLogConsole.println(String.format("[!]: warning, the configured Flr version is %s, while the currently used Flr version is %s", flrVersion, usedFlrVersion), FlrLogConsole.LogType.warning);
            flrLogConsole.println("[*]: to fix it, you should make sure that both versions are the same", FlrLogConsole.LogType.tips);
            warningCount += 1;
        }

        if(illegalAssetList.isEmpty() == false) {
            flrLogConsole.println("[!]: warning, find illegal assets who's file basename contains illegal characters:", FlrLogConsole.LogType.warning);
            for(String illegalAsset: illegalAssetList) {
                flrLogConsole.println(String.format("   - %s", illegalAsset), FlrLogConsole.LogType.warning);
            }
            flrLogConsole.println("[*]: to fix it, you should only use letters (a-z, A-Z), numbers (0-9), and the other legal characters ('_', '+', '-', '.', '·', '!', '@', '&', '$', '￥') to name the asset", FlrLogConsole.LogType.tips);
            warningCount += 1;
        }

        if(warningCount > 0) {
            String warningMessage = String.format("<p>[!]: have %d warnings, you can get the details from Flr ToolWindow</p>", warningCount);
            cmdResultMessage += warningMessage;
            displayWarningLog(cmdResultMessage);
        } else {
            displayInfoLog(cmdResultMessage);
        }

    }

    public Boolean startAssertMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Start Monitor]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        List<String> allValidAssetDirPaths = null;
        try {
            allValidAssetDirPaths = checkBeforeGenerate(flrLogConsole);
        } catch (FlrException e) {
            handleFlrException(e);
            return false;
        }

        if(curFlrListener != null) {
            stopAssertMonitor(actionEvent, flrLogConsole);
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String nowStr = df.format(new Date());// new Date()为获取当前系统时间，也可使用当前时间戳
        indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);
        generate(actionEvent, flrLogConsole);
        flrLogConsole.println("", indicatorType);
        indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" done!";
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "---------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);

        nowStr = df.format(new Date());
        indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "launch a monitoring service now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "launching ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String terminateTipsMessage =
                "<p>[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...</p>" +
                "<p>[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it</p>";

        FlrListener.AssetChangesEventCallback assetChangesEventCallback = new FlrListener.AssetChangesEventCallback() {
            @Override
            public void run() {
                String assetChangesEventMessage = "<p>detect some asset changes, run Flr-Generate Action now ...</p>";
                displayInfoLog(assetChangesEventMessage);

                String indicatorMessage = "";
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String nowStr = df.format(new Date());

                indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
                flrLogConsole.println(indicatorMessage, indicatorType);
                indicatorMessage = "detect some asset changes, run Flr-Generate Action now";
                flrLogConsole.println(indicatorMessage, indicatorType);
                indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" now ...";
                flrLogConsole.println(indicatorMessage, indicatorType);
                flrLogConsole.println("", indicatorType);
                generate(actionEvent, flrLogConsole);
                flrLogConsole.println("", indicatorType);
                indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" done!";
                flrLogConsole.println(indicatorMessage, indicatorType);
                indicatorMessage = "---------------------------------------------------------------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);
                flrLogConsole.println("", indicatorType);

                indicatorMessage =
                        "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                                "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
                flrLogConsole.println(indicatorMessage, indicatorType);

                String cmdResultMessage = "<p>[√]: generate done !!!</p>";
                cmdResultMessage += "<p>\u202D</p>";
                cmdResultMessage += terminateTipsMessage;
                displayInfoLog(cmdResultMessage);
            }
        };
        curFlrListener = new FlrListener(curProject, allValidAssetDirPaths, assetChangesEventCallback);
        isMonitoringAssets = true;

        indicatorMessage = "launch a monitoring service done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "the monitoring service is monitoring these asset directories:";
        flrLogConsole.println(indicatorMessage, indicatorType);
        String cmdResultMessage = "<p>[√]: launch a monitoring service done !!!</p>";
        String monitoredAssetDirMessage = "<p>the monitoring service is monitoring these asset directories:</p>";
        for(String assetDirPath: allValidAssetDirPaths) {
            monitoredAssetDirMessage += String.format("<p>\u202D   - %s</p>", assetDirPath);

            indicatorMessage = String.format("  - %s", assetDirPath);
            flrLogConsole.println(indicatorMessage, indicatorType);
        }
        indicatorMessage = "---------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);

        indicatorMessage =
                "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                        "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        cmdResultMessage += monitoredAssetDirMessage;
        cmdResultMessage += "<p>\u202D</p>";
        cmdResultMessage += terminateTipsMessage;
        displayInfoLog(cmdResultMessage);

        return true;
    }

    public void stopAssertMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Stop Monitor]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        if(curFlrListener != null) {
            curFlrListener.dispose();
            curFlrListener = null;
        }
        isMonitoringAssets = false;

        indicatorMessage = "[√]: terminate the monitoring service done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String cmdResultMessage = "<p>[√]: terminate the monitoring service done !!!</p>";
        cmdResultMessage += "<p>\u202D</p>";
        displayInfoLog(cmdResultMessage);
    }

    // MARK: Command Action Util Methods

    private String getPubspecFilePath() {
        String flutterProjectRootDir = curProject.getBasePath();
        String pubspecFilePath = flutterProjectRootDir + "/pubspec.yaml";
        return pubspecFilePath;
    }

    /*
    * get the right version of r_dart_library package based on flutter's version
    * to get more detail, see https://github.com/YK-Unit/r_dart_library#dependency-relationship-table
    * */
    /*
    private String getRDartLibraryVersion() {
        String rDartLibraryVersion = "0.1.0";

        String shStr = "flutter --version";
        List<String> outputLineList = FlrUtil.execShell(shStr);

        if (outputLineList.isEmpty()) {
            return rDartLibraryVersion;
        }

        String flutterVersionResult = outputLineList.get(0);
        String versionWithHotfixStr = flutterVersionResult.split(" ")[1];
        String versionWithoutHotfixStr = versionWithHotfixStr.split("\\+")[0];

        int compareResult = FlrUtil.versionCompare(versionWithoutHotfixStr, "1.10.15");
        if(compareResult == 0 || compareResult == 1) {
            rDartLibraryVersion = "0.2.0";
        }

        return rDartLibraryVersion;
    }*/

    private String getRDartLibraryVersion() {
        String rDartLibraryVersion = "0.1.0";

        VirtualFile flutterSdkHome = FlutterSdk.getFlutterSdk(curProject).getHome();
        FlutterSdkVersion flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome);

        String flutterVersionWithoutHotfixStr = flutterSdkVersion.toString();
        int compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15");
        if(compareResult == 0 || compareResult == 1) {
            rDartLibraryVersion = "0.2.0";
        }

        return rDartLibraryVersion;
    }

    /*
    * 按照以下步骤检测是否符合执行创建任务的条件
    * 1. 检测当前目录是否存在pubspec.yaml
    * 2. 检测pubspec.yaml中是否存在flr的配置
    * 3. 检测flr的配置中是否有配置了合法的资源目录路径
     * 4. 返回所有合法的资源目录的路径数组
    * */
    private List<String> checkBeforeGenerate(@NotNull FlrLogConsole flrLogConsole) throws FlrException {
        String flutterProjectRootDir = curProject.getBasePath();
        String pubspecFilePath = flutterProjectRootDir + "/pubspec.yaml";
        File pubspecFile = new File(pubspecFilePath);

        // 检测当前目录是否存在 pubspec.yaml；
        // 若不存在，说明当前目录不是一个flutter工程目录，这时直接终止当前任务，并抛出异常提示；
        if(pubspecFile.exists() == false) {
            flrLogConsole.println(String.format("[x]: %s not found", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure current directory is a flutter project directory", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "[x]: %s not found\n" +
                    "[*]: please make sure current directory is a flutter project directory",
                    pubspecFilePath
            );
            FlrException exception = new FlrException(message);
            throw(exception);
        }

        // 读取 pubspec_yaml，判断是否有 flr 的配置信息；
        // 若有，说明已经进行了初始化；然后检测是否配置了资源目录，若没有配置，这时直接终止当前任务，并提示开发者手动配置它
        // 若没有，说明还没进行初始化，这时直接终止当前任务，并提示开发者手动配置它

        Map<String, Object> pubspecMap = FlrUtil.loadPubspecMapFromYaml(pubspecFilePath);
        if(pubspecMap == null) {
            flrLogConsole.println(String.format("[x]: %s is a bad YAML file", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure the pubspec.yaml is right", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "[x]: %s is a bad YAML file\n" +
                    "[*]: please make sure the pubspec.yaml is right",
                    pubspecFilePath
            );
            FlrException exception = new FlrException(message);
            throw(exception);
        }

        Map<String, Object> flrMap = (Map<String, Object>)pubspecMap.get("flr");
        if(flrMap == null) {
            flrLogConsole.println("[x]: have no flr configuration in pubspec.yaml", FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please click menu \"Tools-Flr-Init\" to fix it", FlrLogConsole.LogType.tips);

            String message = String.format(
                    "[x]: have no flr configuration in pubspec.yaml\n" +
                    "[*]: please click menu \"Tools-Flr-Init\" to fix it",
                    pubspecFilePath
            );
            FlrException exception = new FlrException(message);
            throw(exception);
        }

        String flrVersion = (String) flrMap.get("version");
        Object assetDirPaths = flrMap.get("assets");
        if(assetDirPaths == null || !(assetDirPaths instanceof List)) {
            flrLogConsole.println("[x]: have no valid asset directories configuration in pubspec.yaml", FlrLogConsole.LogType.error);
            flrLogConsole.println(String.format(
                            "[*]: please manually configure the asset directories to fix it, for example:\n" +
                            "\u202D \n" +
                            "\u202D     flr:\n" +
                            "\u202D       version:%s\n" +
                            "\u202D       assets:\n" +
                            "\u202D       # config the asset directories that need to be scanned\n" +
                            "\u202D         - lib/assets/images\n" +
                            "\u202D         - lib/assets/texts\n",
                    flrVersion
            ), FlrLogConsole.LogType.tips);

            String message = String.format(
                            "[x]: have no valid asset directories configuration in pubspec.yaml\n" +
                            "[*]: please manually configure the asset directories to fix it, for example:\n" +
                            "\u202D \n" +
                            "\u202D     flr:\n" +
                            "\u202D       version:%s\n" +
                            "\u202D       assets:\n" +
                            "\u202D       # config the asset directories that need to be scanned\n" +
                            "\u202D         - lib/assets/images\n" +
                            "\u202D         - lib/assets/texts\n",
                    flrVersion
            );
            FlrException exception = new FlrException(message);
            throw(exception);
        }

        List<String> allValidAssetDirPaths = (List<String>)assetDirPaths;
        allValidAssetDirPaths.remove("");
        LinkedHashSet<String> tempSet = new LinkedHashSet<String>(allValidAssetDirPaths);
        allValidAssetDirPaths = new ArrayList<String>(tempSet);

        if(allValidAssetDirPaths.isEmpty()) {
            flrLogConsole.println("[x]: have no valid asset directories configuration in pubspec.yaml", FlrLogConsole.LogType.error);
            flrLogConsole.println(String.format(
                    "[*]: please manually configure the asset directories to fix it, for example:\n" +
                            "\u202D \n" +
                            "\u202D     flr:\n" +
                            "\u202D       version:%s\n" +
                            "\u202D       assets:\n" +
                            "\u202D       # config the asset directories that need to be scanned\n" +
                            "\u202D         - lib/assets/images\n" +
                            "\u202D         - lib/assets/texts\n",
                    flrVersion
            ), FlrLogConsole.LogType.tips);

            String message = String.format(
                            "[x]: have no valid asset directories configuration in pubspec.yaml\n" +
                            "[*]: please manually configure the asset directories to fix it, for example:\n" +
                            "\u202D \n" +
                            "\u202D     flr:\n" +
                            "\u202D       version:%s\n" +
                            "\u202D       assets:\n" +
                            "\u202D       # config the asset directories that need to be scanned\n" +
                            "\u202D         - lib/assets/images\n" +
                            "\u202D         - lib/assets/texts\n",
                    flrVersion
            );
            FlrException exception = new FlrException(message);
            throw(exception);
        }

        return allValidAssetDirPaths;
    }

    private void handleFlrException(FlrException exception) {
        String message = exception.getMessage();
        displayErrorLog(message);
    }

    private void displayInfoLog(String infoMessage) {
        Notification notification = new Notification(FlrConstant.flrDisplayName, "Flr", infoMessage, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, curProject);
    }

    private void displayWarningLog(String warningMessage) {
        Notification notification = new Notification(FlrConstant.flrDisplayName, "Flr", warningMessage, NotificationType.WARNING);
        Notifications.Bus.notify(notification, curProject);
    }

    private void displayErrorLog(String errorMessage) {
        Notification notification = new Notification(FlrConstant.flrDisplayName, "Flr", errorMessage, NotificationType.ERROR);
        Notifications.Bus.notify(notification, curProject);
    }

}

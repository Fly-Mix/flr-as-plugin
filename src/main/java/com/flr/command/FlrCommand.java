package com.flr.command;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.command.util.FlrFileUtil;
import com.flr.command.util.FlrUtil;
import com.flr.core.env.FlutterEnvironment;
import com.flr.core.format.DartFormatter;
import com.flr.core.report.FlrReporter;
import com.flr.core.usecase.GenerateUseCase;
import com.flr.infra.FlrConsoleReporter;
import com.flr.infra.IdeDartFormatter;
import com.flr.infra.IdeFlutterEnvironment;
import com.flr.logConsole.FlrLogConsole;
import com.flr.messageBox.FlrMessageBox;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * 专有名词简单解释和示例：
 * （详细定义请看 flr-core 项目的文档描述）
 *
 * package_name：flutter工程的package产物的名称，例如“flutter_demo”
 * resource_file：flutter工程的资源文件，例如“lib/assets/images/hot_foot_N.png”、“lib/assets/images/3.0x/hot_foot_N.png”
 * asset：flutter工程的package产物中资源，可当作是工程中的资源文件的映射和声明，例如上述2个资源对于的asset都是“packages/flutter_demo/assets/images/hot_foot_N.png”
 * file_basename：资源的文件名，其定义是“#{file_basename_no_extension}#{file_extname}”，例如“hot_foot_N.png”
 * file_basename_no_extension：资源的不带扩展名的文件名，例如“hot_foot_N”
 * file_extname：资源的扩展名，例如“.png”
 *
 * asset_name：main asset的名称，例如“assets/images/hot_foot_N.png”
 * asset_id：资源ID，其值一般为 file_basename_no_extension
 * */

// 处理泛型对象时，若不做检查，就进行类型强制转换（如 (Map<String, Object>)map.get("key")），
// 编译器会报警告：FlrCommand.java使用了未经检查或不安全的操作。
// 解决办法之一是，添加 @SuppressWarnings("unchecked")。
@SuppressWarnings("unchecked")
public class FlrCommand implements Disposable {

    public boolean isMonitoringAssets = false;

    private final Project curProject;

    private FlrListener curFlrListener;

    private String messageBoxTitle = "Flr";

    private FlrLogConsole.LogType titleLogType = FlrLogConsole.LogType.tips;

    // Flutter/Dart 环境探测接缝（SDK 探测、nullsafety 判定、r_dart_library 版本）
    private final FlutterEnvironment flutterEnvironment;

    // Dart 代码格式化接缝
    private final DartFormatter dartFormatter;

    public FlrCommand(Project project) {
        curProject = project;
        flutterEnvironment = new IdeFlutterEnvironment(project);
        dartFormatter = new IdeDartFormatter(project);
    }

    @Override
    public void dispose() {
        if(curFlrListener != null) {
            curFlrListener.dispose();
            curFlrListener = null;
        }
    }

    // MARK: Private Util Methods
    /*
    * 检测当前project是不是 flutter project，如果不是就打印提示
    * */
    public boolean checkIsFlutterProjectAndShowTips(@NotNull FlrLogConsole flrLogConsole) {
        if (flutterEnvironment.isFlutterProject()) {
            return true;
        }

        String tipsStr = String.format("[*]: this is not a flutter project");
        flrLogConsole.println(tipsStr, FlrLogConsole.LogType.tips);

        return false;
    }

    // MARK: Command Action Methods

    /*
    * 初始化所有工作空间下的flutter主工程及其所有子工程
    * */
    public void initAll(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        if (checkIsFlutterProjectAndShowTips(flrLogConsole) == false) {
            return;
        }

        String indicatorMessage = "[Flr Init]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: init failed !!!";

        String flutterMainProjectRootDir = curProject.getBasePath();
        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程
        // - 检测当前flutter主工程根目录是否存在 pubspec.yaml
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterMainProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return;
        }

        // ----- Step-1 End -----

        indicatorMessage = "init all flutter projects now...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-2 Begin -----
        // 获取主工程和其所有子工程，对它们进行init_one操作
        // - 获取flutter主工程根目录下所有的子工程目录
        // - 初始化主工程
        // - 初始化所有子工程

        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);

        flrLogConsole.println("", indicatorType);
        initOne(actionEvent, flrLogConsole, flutterMainProjectRootDir);

        for(String flutterProjectRootDir : flutterSubProjectRootDirArray) {
            flrLogConsole.println("", indicatorType);
            initOne(actionEvent, flrLogConsole, flutterProjectRootDir);
        }

        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        // 调用flutter工具，为flutter工程获取依赖
        //

        flrLogConsole.println("", indicatorType);
        indicatorMessage = "get dependencies for all flutter projects via running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "[√]: get dependencies for all flutter projects done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-3 End -----

        flrLogConsole.println("", indicatorType);
        indicatorMessage = "[√]: init all flutter projects done !!!\n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "[*]: if you want to know how to make a good resource structure for your flutter project, please click menu \"Tools-Flr-Recommend\" ";
        flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips);

        String contentTitle = "[√]: init all flutter projects done !!!";
        showSuccessMessage(contentTitle, "", false);
    }

    /*
    * 对指定 flutter 工程进行初始化
    *  */
    public void initOne(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole, @NotNull String flutterProjectRootDir) {

        String indicatorMessage = "------------------------------- init specified project -------------------------------";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, indicatorType);

        String flrExceptionTitle = String.format("[x]: init %s failed", flutterProjectRootDir);

        String pubspecFilePath;
        File pubspecFile;
        Map<String, Object> pubspecConfig;

        indicatorMessage = String.format("init %s now ...", flutterProjectRootDir);
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-1 Begin -----
        // 进行环境检测:
        //  - 检测当前 flutter 工程根目录是否存在 pubspec.yaml
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterProjectRootDir);

            pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir);
            pubspecFile = new File(pubspecFilePath);
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(flrLogConsole, pubspecFile);

        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);

            flrLogConsole.println(flrExceptionTitle, FlrLogConsole.LogType.error);

            indicatorMessage = "--------------------------------------------------------------------------------------";
            flrLogConsole.println(indicatorMessage, indicatorType);
            return;
        }

        // ----- Step-1 End -----

        indicatorMessage = String.format("init %s now ...", curProject.getBasePath());
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-2 Begin -----
        // 添加 flr_config 和 r_dart_library 的依赖声明到 pubspec.yaml
        //

        // 添加flr_config到pubspec.yaml：检测当前是否存在flr_config；若不存在，则添加flr_config；若存在，则按照以下步骤处理：
        //  - 读取dartfmt_line_length选项、assets选项和fonts选项的值（这些选项值若存在，则应用于新建的flr_config；需要注意，使用前需要判断选项值是否合法：dartfmt_line_length选项值 >=80；assets选项和fonts选项的值为数组）
        //  - 新建flr_config，然后使用旧值或者默认值设置各个选项
        //
        // flr_config: Flr的配置信息
        // ```yaml
        // flr:
        //  core_version: 1.0.0
        //  dartfmt_line_length: 80
        //  assets: []
        //  fonts: []
        // ```
        //

        int dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH;
        List<String> assetResourceDirArray = new ArrayList<String>();
        List<String> fontResourceDirArray = new ArrayList<String>();
        Map<String, Object> flrConfig = new LinkedHashMap<>();

        String usedFlrCoreLogicVersion = FlrConstant.CORE_VERSION;
        flrConfig.put("core_version", usedFlrCoreLogicVersion);

        Map<String, Object> oldFlrConfig = (Map<String, Object>)pubspecConfig.get("flr");
        if(oldFlrConfig instanceof Map) {
            if(oldFlrConfig.containsKey("dartfmt_line_length")) {
                Object length = oldFlrConfig.get("dartfmt_line_length");
                if(length instanceof  Integer) {
                    dartfmtLineLength = (Integer)length;
                    if(dartfmtLineLength < FlrConstant.DARTFMT_LINE_LENGTH) {
                        dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH;
                    }
                }
            }

            if (oldFlrConfig.containsKey("assets")) {
                Object assets = oldFlrConfig.get("assets");
                if(assets instanceof List) {
                    assetResourceDirArray = (List<String>)assets;
                }
            }

            if (oldFlrConfig.containsKey("fonts")) {
                Object fonts = oldFlrConfig.get("fonts");
                if(fonts instanceof List) {
                    fontResourceDirArray = (List<String>)fonts;
                }
            }
        }

        flrConfig.put("dartfmt_line_length", dartfmtLineLength);
        flrConfig.put("assets", assetResourceDirArray);
        flrConfig.put("fonts", fontResourceDirArray);
        pubspecConfig.put("flr", flrConfig);

        indicatorMessage = "add flr configuration into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加依赖包`r_dart_library`(https://github.com/YK-Unit/r_dart_library)的声明到pubspec.yaml
        Map<String, Object> dependenciesMap = (Map<String, Object>)pubspecConfig.get("dependencies");
        String rDartLibraryVersion = flutterEnvironment.getRDartLibraryVersion();
        dependenciesMap.put("r_dart_library", rDartLibraryVersion);
        pubspecConfig.put("dependencies", dependenciesMap);

        // ----- Step-2 End -----

        indicatorMessage = "add dependency \"r_dart_library\"(https://github.com/YK-Unit/r_dart_library) into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-3 Begin -----
        // 对Flutter配置进行修正，以避免执行获取依赖操作时会失败：
        // - 检测Flutter配置中的assets选项是否是一个非空数组；若不是，则删除assets选项；
        // - 检测Flutter配置中的fonts选项是否是一个非空数组；若不是，则删除fonts选项。
        //
        Map<String, Object> flutterConfig = (Map<String, Object>)pubspecConfig.get("flutter");
        if(flutterConfig == null || flrConfig instanceof Map == false) {
            flutterConfig = new LinkedHashMap<>();
        }

        String flutterAssetsKey = "assets";
        Object flutterAssets = flutterConfig.get(flutterAssetsKey);
        Boolean shouldRmFlutterAssetsKey = true;
        if(flutterAssets instanceof List && ((List)flutterAssets).isEmpty() == false) {
            shouldRmFlutterAssetsKey = false;
        }
        if(shouldRmFlutterAssetsKey) {
            flutterConfig.remove(flutterAssetsKey);
        }

        // 检测 flutter 下的fonts配置是否有效（fonts要求为非空数组），若无效，则删除该配置，避免执行 flutter pub get 时会失败
        String flutterFontsKey = "fonts";
        Object flutterFonts = flutterConfig.get(flutterFontsKey);
        Boolean shouldRmFlutterFontsKey = true;
        if(flutterFonts instanceof List && ((List)flutterFonts).isEmpty() == false) {
            shouldRmFlutterFontsKey = false;
        }
        if(shouldRmFlutterFontsKey) {
            flutterConfig.remove(flutterFontsKey);
        }

        pubspecConfig.put("flutter", flutterConfig);
        // ----- Step-3 End -----

        // 保存并刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(pubspecConfig, pubspecFile);

        String contentTitle = String.format("[√]: init %s done !!!", flutterProjectRootDir);
        showSuccessMessage(contentTitle, "", false);

        indicatorMessage = String.format("[√]: init %s done !!!", flutterProjectRootDir);
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "--------------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
    }

    public void generateAll(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        if (checkIsFlutterProjectAndShowTips(flrLogConsole) == false) {
            return;
        }

        String indicatorMessage = "[Flr Generate]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: generate failed !!!";

        String flutterMainProjectRootDir = curProject.getBasePath();
        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程
        // - 检测当前flutter主工程根目录是否存在 pubspec.yaml
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterMainProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return;
        }

        // ----- Step-1 End -----

        indicatorMessage = "generate for all flutter projects now...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-2 Begin -----
        // 获取主工程和其所有子工程，对它们进行generate_one操作
        // - 获取flutter主工程根目录下所有的子工程目录
        // - 对主工程执行generate_one操作
        // - 对所有子工程执行generate_one操作

        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);

        flrLogConsole.println("", indicatorType);
        generateOne(actionEvent, flrLogConsole, flutterMainProjectRootDir);

        for(String flutterProjectRootDir : flutterSubProjectRootDirArray) {
            flrLogConsole.println("", indicatorType);
            generateOne(actionEvent, flrLogConsole, flutterProjectRootDir);
        }

        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        // 调用flutter工具，为flutter工程获取依赖
        //

        flrLogConsole.println("", indicatorType);
        indicatorMessage = "get dependencies for all flutter projects via running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "[√]: get dependencies for all flutter projects done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-3 End -----

        flrLogConsole.println("", indicatorType);
        indicatorMessage = "[√]: generate for all flutter projects done !!!\n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String contentTitle = "[√]: generate for all flutter projects done !!!";
        showSuccessMessage(contentTitle, "", false);
    }

    /*
    * 扫描资源目录，自动为资源添加声明到 pubspec.yaml 和生成 r.g.dart
    *
    * 薄适配层：构造 FlrReporter 与三接缝，委托 GenerateUseCase 执行全流水线（逻辑见 GenerateUseCase）。
    * */
    public void generateOne(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole, @NotNull String flutterProjectRootDir) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        GenerateUseCase useCase = new GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter, flrLogConsole);
        useCase.generateOne(flutterProjectRootDir);
    }

    /*
    * 启动一个资源变化监控服务，若检测到有资源变化，就自动执行generate操作
    * */
    public Boolean startMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        if (checkIsFlutterProjectAndShowTips(flrLogConsole) == false) {
            return false;
        }

        String indicatorMessage = "[Flr Start Monitor]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: start monitor failed !!!";

        String flutterMainProjectRootDir = curProject.getBasePath();
        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程
        // - 检测当前flutter主工程根目录是否存在 pubspec.yaml
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterMainProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return false;
        }

        // ----- Step-1 End -----

        // ----- Step-2 Begin -----
        // 对flutter工程进行合法资源目录检测:
        // - 获取主工程的所有子工程根目录，生成工程根目录数组flutter_project_root_dir_array
        // - 遍历flutter_project_root_dir_array，获取每个工程的legal_resource_dir数组：
        //   - 从flr_config中的assets配置获取assets_legal_resource_dir数组；
        //   - 从flr_config中的fonts配置获取fonts_legal_resource_dir数组；
        //   - 合并assets_legal_resource_dir数组和fonts_legal_resource_dir数组到legal_resource_dir数组。
        // - 检测legal_resource_dir数组是否为空，为空则结束运行。
        //
        flrLogConsole.println("", indicatorType);
        indicatorMessage = "get the valid resource directories of all projects now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);

        List<String> flutterProjectRootDirArray = new ArrayList<>();
        flutterProjectRootDirArray.add(flutterMainProjectRootDir);
        flutterProjectRootDirArray.addAll(flutterSubProjectRootDirArray);

        // 合法的资源目录数组
        List<String> legalResourceDirArray = new ArrayList<>();
        // 非法的资源目录数组
        List<String> illegalResourceDirArray = new ArrayList<>();

        for(String flutterProjectRootDir : flutterProjectRootDirArray) {
            try {
                flrLogConsole.println("", indicatorType);
                indicatorMessage = "--------------------------- get info of specified project ----------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = String.format("get the valid resource directories from %s now...", flutterProjectRootDir);
                flrLogConsole.println(indicatorMessage, indicatorType);

                FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterProjectRootDir);

                String pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir);
                File pubspecFile = new File(pubspecFilePath);
                Map<String, Object> pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(flrLogConsole, pubspecFile);

                FlrChecker.checkFlrConfigIsExisted(flrLogConsole, pubspecConfig);
                Map<String, Object> flrConfig = (Map<String, Object>)pubspecConfig.get("flr");

                List<List<String>> resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(flrLogConsole, flrConfig, flutterProjectRootDir);

                // 合法的资源目录数组
                List<String> assetsLegalResourceDirArray = resourceDirResultTuple.get(0);
                List<String> fontsLegalResourceDirArray = resourceDirResultTuple.get(1);
                legalResourceDirArray.addAll(assetsLegalResourceDirArray);
                legalResourceDirArray.addAll(fontsLegalResourceDirArray);
                // 非法的资源目录数组
                illegalResourceDirArray.addAll(resourceDirResultTuple.get(2));

                indicatorMessage = String.format("get the valid resource directories from %s done !!!", flutterProjectRootDir);
                flrLogConsole.println(indicatorMessage, indicatorType);
                indicatorMessage = "--------------------------------------------------------------------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);
            } catch (FlrException e) {
                handleFlrException(flrExceptionTitle, e);

                flrLogConsole.println("", FlrLogConsole.LogType.error);

                indicatorMessage = String.format("[x]: %s has no valid resource directories.", flutterProjectRootDir);
                flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.error);
                indicatorMessage = "--------------------------------------------------------------------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);
            }
        }

        flrLogConsole.println("", indicatorType);

        indicatorMessage = "get the valid resource directories of all projects done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        if(legalResourceDirArray.size() == 0) {
            FlrException e = FlrException.ILLEGAL_ENV;
            handleFlrException(flrExceptionTitle, e);

            flrLogConsole.println("", indicatorType);

            indicatorMessage = "[x]: have no valid resource directories to be monitored";
            flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.error);
            flrLogConsole.println(flrExceptionTitle, FlrLogConsole.LogType.error);
            return false;
        }

        // ----- Step-2 End -----

        if(curFlrListener != null) {
            stopMonitor(actionEvent, flrLogConsole);
        }

        // ----- Step-3 Begin -----
        // 执行一次 flr generate 操作
        //
        flrLogConsole.println("", indicatorType);
        indicatorMessage = "now generate for all projects once before launching the monitoring service ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);
        generateAll(actionEvent, flrLogConsole);
        flrLogConsole.println("", indicatorType);
        indicatorMessage = "did generate for all projects, now is going to launching the monitoring service ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-3 End -----

        // ----- Step-4 Begin -----
        // 启动资源监控服务
        //  - 启动一个文件监控服务，对 legal_resource_dir 数组中的资源目录进行文件监控
        //  - 若服务检测到资源变化（资源目录下的发生增/删/改文件），则执行一次 flr generate 操作
        //
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String nowStr = df.format(new Date());// new Date()为获取当前系统时间，也可使用当前时间戳

        flrLogConsole.println("", indicatorType);
        indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "launch a monitoring service now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "launching ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        FlrListener.AssetChangesEventCallback assetChangesEventCallback = new FlrListener.AssetChangesEventCallback() {
            @Override
            public void run() {
                String contentTitle = "[!]: detect some asset changes !!!";
                String contentMessage = "[*]: invoke Flr-Generate Action now ... ";
                showSuccessMessage(contentTitle, contentMessage, false);

                String indicatorMessage = "";
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String nowStr = df.format(new Date());

                indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "detect some asset changes, run Flr-Generate Action now";
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "generate for all projects now ...";
                flrLogConsole.println(indicatorMessage, indicatorType);

                flrLogConsole.println("", indicatorType);
                generateAll(actionEvent, flrLogConsole);

                flrLogConsole.println("", indicatorType);
                indicatorMessage = "generate for all projects done !!!";
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "---------------------------------------------------------------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);
                flrLogConsole.println("", indicatorType);

                indicatorMessage =
                        "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                                "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
                flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips);

                contentTitle = "[!]: invoke Flr-Generate Action done !!!";
                contentMessage = "[*]: you can get the details from Flr ToolWindow";
                showSuccessMessage(contentTitle, contentMessage, false);
            }
        };
        List<String> legalRelativeResourceDirArray = FlrFileUtil.convertToRelativeResourceDirs(flutterMainProjectRootDir, legalResourceDirArray);
        curFlrListener = new FlrListener(curProject, legalRelativeResourceDirArray, assetChangesEventCallback);
        isMonitoringAssets = true;

        indicatorMessage = "launch a monitoring service done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "the monitoring service is monitoring the following resource directory:";
        flrLogConsole.println(indicatorMessage, indicatorType);

        for(String resourceDir: legalResourceDirArray) {
            indicatorMessage = String.format("  - %s", resourceDir);
            flrLogConsole.println(indicatorMessage, indicatorType);
        }

        if(illegalResourceDirArray.size() > 0) {
            indicatorMessage = "";
            flrLogConsole.println(indicatorMessage, indicatorType);

            indicatorMessage = "[!]: warning, found the following resource directory which is not existed: ";
            flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.warning);

            for (String resourceDir : illegalResourceDirArray) {
                indicatorMessage = String.format("  - %s", resourceDir);
                flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.warning);
            }
        }
        indicatorMessage = "---------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);

        indicatorMessage =
                "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                        "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
        flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips);

        String contentTitle = "[√]: start monitor done !!!";
        String contentMessage = "[*]: you can get the details from Flr ToolWindow";
        showSuccessMessage(contentTitle, contentMessage, false);

        return true;
    }

    /*
    * 停止资源变化监控服务
    * */
    public void stopMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
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

        String contentTitle = "[√]: stop monitor done !!!";
        String contentMessage = "[*]: you can get the details from Flr ToolWindow";
        showSuccessMessage(contentTitle, contentMessage, false);
    }

    /*
    * 显示版本信息，包括工具版本信息和核心逻辑板本信息
    * */
    public void displayVersion(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Version]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrVersion = FlrUtil.getFlrVersion();
        String flrCoreVersion = FlrConstant.CORE_VERSION;

        indicatorMessage = String.format("Flr version %s\n" +
                "CoreLogic version %s\n",
                flrVersion,
                flrCoreVersion);
        flrLogConsole.println(indicatorMessage, indicatorType);
    }

    /*
     * 显示推荐的flutter资源组织结构信息
     * */
    public void displayRecommendedFlutterResourceStructure(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Recommend]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        indicatorMessage = "Flr recommends the following flutter resource structure schemes:\n" +
                "\n" +
                "------------------------------ scheme 1 ------------------------------" +
                "\n" +
                "\n" +
                "  flutter_project_root_dir\n" +
                "  ├── build\n" +
                "  │   ├── ..\n" +
                "  ├── lib\n" +
                "  │   ├── assets\n" +
                "  │   │   ├── images // image resource directory of all modules\n" +
                "  │   │   │   ├── #{module} // image resource directory of a module\n" +
                "  │   │   │   │   ├── #{main_image_asset}\n" +
                "  │   │   │   │   ├── #{variant-dir} // image resource directory of a variant\n" +
                "  │   │   │   │   │   ├── #{image_asset_variant}\n" +
                "  │   │   │   │\n" +
                "  │   │   │   ├── home // image resource directory of home module\n" +
                "  │   │   │   │   ├── home_badge.svg\n" +
                "  │   │   │   │   ├── home_icon.png\n" +
                "  │   │   │   │   ├── 3.0x // image resource directory of a 3.0x-ratio-variant\n" +
                "  │   │   │   │   │   ├── home_icon.png\n" +
                "  │   │   │   │\t\t\n" +
                "  │   │   ├── texts // text resource directory\n" +
                "  │   │   │   │     // (you can also break it down further by module)\n" +
                "  │   │   │   └── test.json\n" +
                "  │   │   │   └── test.yaml\n" +
                "  │   │   │   │\n" +
                "  │   │   ├── fonts // font resource directory of all font-families\n" +
                "  │   │   │   ├── #{font-family} // font resource directory of a font-family\n" +
                "  │   │   │   │   ├── #{font-family}-#{font_weight_or_style}.ttf\n" +
                "  │   │   │   │\n" +
                "  │   │   │   ├── Amiri // font resource directory of Amiri font-family\n" +
                "  │   │   │   │   ├── Amiri-Regular.ttf\n" +
                "  │   │   │   │   ├── Amiri-Bold.ttf\n" +
                "  │   │   │   │   ├── Amiri-Italic.ttf\n" +
                "  │   │   │   │   ├── Amiri-BoldItalic.ttf\n" +
                "  │   ├── ..  \n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String flrCoreVersion = FlrConstant.CORE_VERSION;
        indicatorMessage = String.format(
                "[*]: Then config the resource directories that need to be scanned as follows：\n" +
                "\n" +
                "  flr:\n" +
                "    core_version: %s\n" +
                "    dartfmt_line_length: %d\n" +
                "    # config the image and text resource directories that need to be scanned\n" +
                "    assets:\n" +
                "      - lib/assets/images\n" +
                "      - lib/assets/texts\n" +
                "    # config the font resource directories that need to be scanned\n" +
                "    fonts:\n" +
                "      - lib/assets/fonts\n", flrCoreVersion, FlrConstant.DARTFMT_LINE_LENGTH);
        flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips);

        indicatorMessage = "\n" +
                "------------------------------ scheme 2 ------------------------------" +
                "\n" +
                "\n" +
                "  flutter_project_root_dir\n" +
                "  ├── build\n" +
                "  │   ├── ..\n" +
                "  ├── lib\n" +
                "  │   ├── ..\n" +
                "  ├── assets\n" +
                "  │   ├── images // image resource directory of all modules\n" +
                "  │   │   ├── #{module} // image resource directory of a module\n" +
                "  │   │   │   ├── #{main_image_asset}\n" +
                "  │   │   │   ├── #{variant-dir} // image resource directory of a variant\n" +
                "  │   │   │   │   ├── #{image_asset_variant}\n" +
                "  │   │   │\n" +
                "  │   │   ├── home // image resource directory of home module\n" +
                "  │   │   │   ├── home_badge.svg\n" +
                "  │   │   │   ├── home_icon.png\n" +
                "  │   │   │   ├── 3.0x // image resource directory of a 3.0x-ratio-variant\n" +
                "  │   │   │   │   ├── home_icon.png\n" +
                "  │   │   │\t\t\n" +
                "  │   ├── texts // text resource directory\n" +
                "  │   │   │     // (you can also break it down further by module)\n" +
                "  │   │   └── test.json\n" +
                "  │   │   └── test.yaml\n" +
                "  │   │   │\n" +
                "  │   ├── fonts // font resource directory of all font-families\n" +
                "  │   │   ├── #{font-family} // font resource directory of a font-family\n" +
                "  │   │   │   ├── #{font-family}-#{font_weight_or_style}.ttf\n" +
                "  │   │   │\n" +
                "  │   │   ├── Amiri // font resource directory of Amiri font-family\n" +
                "  │   │   │   ├── Amiri-Regular.ttf\n" +
                "  │   │   │   ├── Amiri-Bold.ttf\n" +
                "  │   │   │   ├── Amiri-Italic.ttf\n" +
                "  │   │   │   ├── Amiri-BoldItalic.ttf\n" +
                "  │   ├── ..  \n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = String.format(
                "[*]: Then config the resource directories that need to be scanned as follows：\n" +
                        "\n" +
                        "  flr:\n" +
                        "    core_version: %s\n" +
                        "    dartfmt_line_length: %d\n" +
                        "    # config the image and text resource directories that need to be scanned\n" +
                        "    assets:\n" +
                        "      - assets/images\n" +
                        "      - assets/texts\n" +
                        "    # config the font resource directories that need to be scanned\n" +
                        "    fonts:\n" +
                        "      - assets/fonts\n", flrCoreVersion, FlrConstant.DARTFMT_LINE_LENGTH);
        flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.tips);
    }

    // MARK: Exception Handler Methods

    private void handleFlrException(@NotNull String contentTitle, FlrException exception) {
        String contentMessage = exception.getMessage();
        showFailureMessage(contentTitle, contentMessage);
    }

    // MARK: MessageBox Show Methods

    private void showSuccessMessage(@NotNull String title, @NotNull String message, Boolean hasWarning) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        if(hasWarning) {
            FlrMessageBox.showWarning(curProject, messageBoxTitle, content);
        } else {
            FlrMessageBox.showInfo(curProject, messageBoxTitle, content);
        }
    }

    private void showFailureMessage(@NotNull String title, @NotNull String message) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        FlrMessageBox.showError(curProject, messageBoxTitle, content);
    }

}

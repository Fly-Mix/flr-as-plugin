package com.flr.core.usecase;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.command.FlrChecker;
import com.flr.command.util.FlrFileUtil;
import com.flr.command.util.FlrUtil;
import com.flr.core.env.FlutterEnvironment;
import com.flr.core.report.FlrReporter;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * init 用例：为工作空间下的 flutter 主工程及其所有子工程写入 flr 配置与 r_dart_library 依赖。
 *
 * 逻辑整段迁移自原 FlrCommand.initAll / initOne，行为不变；日志与通知统一走 {@link FlrReporter}，
 * SDK/版本探测走注入的 {@link FlutterEnvironment}。
 */
@SuppressWarnings("unchecked")
public final class InitUseCase {

    private final Project project;
    private final FlutterEnvironment flutterEnvironment;
    private final FlrReporter reporter;

    public InitUseCase(@NotNull Project project,
                       @NotNull FlutterEnvironment flutterEnvironment,
                       @NotNull FlrReporter reporter) {
        this.project = project;
        this.flutterEnvironment = flutterEnvironment;
        this.reporter = reporter;
    }

    /*
     * 初始化所有工作空间下的 flutter 主工程及其所有子工程
     * */
    public void initAll(@NotNull AnActionEvent actionEvent) {
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;

        // 环境检测：非 flutter 工程则提示并返回
        if (flutterEnvironment.isFlutterProject() == false) {
            reporter.log("[*]: this is not a flutter project", FlrLogConsole.LogType.tips);
            return;
        }

        reporter.log("[Flr Init]", FlrLogConsole.LogType.tips);

        String flrExceptionTitle = "[x]: init failed !!!";
        String flutterMainProjectRootDir = project.getBasePath();

        // ----- Step-1：主工程 pubspec 检测 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterMainProjectRootDir);
        } catch (FlrException e) {
            reporter.failure(flrExceptionTitle, e.getMessage());
            return;
        }

        reporter.log("init all flutter projects now...", indicatorType);

        // ----- Step-2：主工程 + 所有子工程各自 initOne -----
        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);

        reporter.log("", indicatorType);
        initOne(flutterMainProjectRootDir);

        for (String flutterProjectRootDir : flutterSubProjectRootDirArray) {
            reporter.log("", indicatorType);
            initOne(flutterProjectRootDir);
        }

        // ----- Step-3：为所有 flutter 工程获取依赖 -----
        reporter.log("", indicatorType);
        reporter.log("get dependencies for all flutter projects via running \"Flutter Packages Get\" action now ...", indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        reporter.log("[√]: get dependencies for all flutter projects done !!!", indicatorType);

        reporter.log("", indicatorType);
        reporter.log("[√]: init all flutter projects done !!!\n", indicatorType);
        reporter.log("[*]: if you want to know how to make a good resource structure for your flutter project, please click menu \"Tools-Flr-Recommend\" ", FlrLogConsole.LogType.tips);

        reporter.success("[√]: init all flutter projects done !!!", "", false);
    }

    /*
     * 对指定 flutter 工程进行初始化
     * */
    private void initOne(@NotNull String flutterProjectRootDir) {
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        reporter.log("------------------------------- init specified project -------------------------------", indicatorType);

        String flrExceptionTitle = String.format("[x]: init %s failed", flutterProjectRootDir);

        String pubspecFilePath;
        File pubspecFile;
        Map<String, Object> pubspecConfig;

        reporter.log(String.format("init %s now ...", flutterProjectRootDir), indicatorType);

        // ----- Step-1：pubspec 检测与加载 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir);

            pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir);
            pubspecFile = new File(pubspecFilePath);
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile);
        } catch (FlrException e) {
            reporter.failure(flrExceptionTitle, e.getMessage());
            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error);
            reporter.log("--------------------------------------------------------------------------------------", indicatorType);
            return;
        }

        reporter.log(String.format("init %s now ...", project.getBasePath()), indicatorType);

        // ----- Step-2：添加 flr_config 与 r_dart_library 依赖到 pubspec.yaml -----
        int dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH;
        List<String> assetResourceDirArray = new ArrayList<String>();
        List<String> fontResourceDirArray = new ArrayList<String>();
        Map<String, Object> flrConfig = new LinkedHashMap<>();

        String usedFlrCoreLogicVersion = FlrConstant.CORE_VERSION;
        flrConfig.put("core_version", usedFlrCoreLogicVersion);

        Map<String, Object> oldFlrConfig = (Map<String, Object>) pubspecConfig.get("flr");
        if (oldFlrConfig instanceof Map) {
            if (oldFlrConfig.containsKey("dartfmt_line_length")) {
                Object length = oldFlrConfig.get("dartfmt_line_length");
                if (length instanceof Integer) {
                    dartfmtLineLength = (Integer) length;
                    if (dartfmtLineLength < FlrConstant.DARTFMT_LINE_LENGTH) {
                        dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH;
                    }
                }
            }

            if (oldFlrConfig.containsKey("assets")) {
                Object assets = oldFlrConfig.get("assets");
                if (assets instanceof List) {
                    assetResourceDirArray = (List<String>) assets;
                }
            }

            if (oldFlrConfig.containsKey("fonts")) {
                Object fonts = oldFlrConfig.get("fonts");
                if (fonts instanceof List) {
                    fontResourceDirArray = (List<String>) fonts;
                }
            }
        }

        flrConfig.put("dartfmt_line_length", dartfmtLineLength);
        flrConfig.put("assets", assetResourceDirArray);
        flrConfig.put("fonts", fontResourceDirArray);
        pubspecConfig.put("flr", flrConfig);

        reporter.log("add flr configuration into pubspec.yaml done!", indicatorType);

        Map<String, Object> dependenciesMap = (Map<String, Object>) pubspecConfig.get("dependencies");
        String rDartLibraryVersion = flutterEnvironment.getRDartLibraryVersion();
        dependenciesMap.put("r_dart_library", rDartLibraryVersion);
        pubspecConfig.put("dependencies", dependenciesMap);

        reporter.log("add dependency \"r_dart_library\"(https://github.com/YK-Unit/r_dart_library) into pubspec.yaml done!", indicatorType);

        // ----- Step-3：修正 flutter 配置，避免 pub get 失败 -----
        Map<String, Object> flutterConfig = (Map<String, Object>) pubspecConfig.get("flutter");
        if (flutterConfig == null || flrConfig instanceof Map == false) {
            flutterConfig = new LinkedHashMap<>();
        }

        String flutterAssetsKey = "assets";
        Object flutterAssets = flutterConfig.get(flutterAssetsKey);
        Boolean shouldRmFlutterAssetsKey = true;
        if (flutterAssets instanceof List && ((List) flutterAssets).isEmpty() == false) {
            shouldRmFlutterAssetsKey = false;
        }
        if (shouldRmFlutterAssetsKey) {
            flutterConfig.remove(flutterAssetsKey);
        }

        String flutterFontsKey = "fonts";
        Object flutterFonts = flutterConfig.get(flutterFontsKey);
        Boolean shouldRmFlutterFontsKey = true;
        if (flutterFonts instanceof List && ((List) flutterFonts).isEmpty() == false) {
            shouldRmFlutterFontsKey = false;
        }
        if (shouldRmFlutterFontsKey) {
            flutterConfig.remove(flutterFontsKey);
        }

        pubspecConfig.put("flutter", flutterConfig);

        // 保存并刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(pubspecConfig, pubspecFile);

        reporter.success(String.format("[√]: init %s done !!!", flutterProjectRootDir), "", false);

        reporter.log(String.format("[√]: init %s done !!!", flutterProjectRootDir), indicatorType);
        reporter.log("--------------------------------------------------------------------------------------", indicatorType);
    }
}

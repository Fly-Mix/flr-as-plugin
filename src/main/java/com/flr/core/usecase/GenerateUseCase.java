package com.flr.core.usecase;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.command.FlrChecker;
import com.flr.command.util.FlrAssetUtil;
import com.flr.command.util.FlrFileUtil;
import com.flr.core.codegen.RDartGenerator;
import com.flr.core.env.FlutterEnvironment;
import com.flr.core.format.DartFormatter;
import com.flr.core.model.ResourceScanResult;
import com.flr.core.report.FlrReporter;
import com.flr.core.scan.AssetScanner;
import com.flr.logConsole.FlrColoredLogEntity;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * generate 用例：对单个 flutter 工程执行「扫描资源 → 声明到 pubspec.yaml → 生成并格式化 r.g.dart」。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne（Step-1~22），行为不变。三接缝均以构造注入：
 * - {@link FlutterEnvironment}：nullsafety 判定
 * - {@link DartFormatter}：格式化 r.g.dart
 * - {@link FlrReporter}：进度日志与结果通知（取代原先散落的 flrLogConsole/showXxxMessage）
 *
 * 全流程的进度日志与结果通知统一走 {@link FlrReporter}（Step-1 的 {@link FlrChecker} / {@link FlrFileUtil} 也已改用 FlrReporter）。
 */
@SuppressWarnings("unchecked")
public final class GenerateUseCase {

    private final Project project;
    private final FlutterEnvironment flutterEnvironment;
    private final DartFormatter dartFormatter;
    private final FlrReporter reporter;

    public GenerateUseCase(@NotNull Project project,
                           @NotNull FlutterEnvironment flutterEnvironment,
                           @NotNull DartFormatter dartFormatter,
                           @NotNull FlrReporter reporter) {
        this.project = project;
        this.flutterEnvironment = flutterEnvironment;
        this.dartFormatter = dartFormatter;
        this.reporter = reporter;
    }

    /*
     * 扫描资源目录，自动为资源添加声明到 pubspec.yaml 和生成 r.g.dart
     * */
    public void generateOne(@NotNull String flutterProjectRootDir) {
        String indicatorMessage = "--------------------------- generate for specified project ---------------------------";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        reporter.log(indicatorMessage, indicatorType);

        String flrExceptionTitle = String.format("[x]: generate for %s failed", flutterProjectRootDir);

        // 警告日志数组
        List<FlrColoredLogEntity> warningMessages = new ArrayList<FlrColoredLogEntity>();

        String pubspecFilePath;
        File pubspecFile;
        Map<String, Object> pubspecConfig;
        Map<String, Object> flrConfig;
        List<List<String>> resourceDirResultTuple;

        indicatorMessage = String.format("generate for %s now ...", flutterProjectRootDir);
        reporter.log(indicatorMessage, indicatorType);

        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程：
        // - 检测当前flutter工程根目录是否存在pubspec.yaml
        // - 检测当前pubspec.yaml中是否存在Flr的配置
        // - 检测当前flr_config中的resource_dir配置是否合法：
        //    判断合法的标准是：assets配置或者fonts配置了至少1个legal_resource_dir
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir);

            pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir);
            pubspecFile = new File(pubspecFilePath);
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile);

            FlrChecker.checkFlrConfigIsExisted(reporter, pubspecConfig);
            flrConfig = (Map<String, Object>) pubspecConfig.get("flr");

            resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(reporter, flrConfig, flutterProjectRootDir);
        } catch (FlrException e) {
            reporter.failure(flrExceptionTitle, e.getMessage());

            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error);

            indicatorMessage = "--------------------------------------------------------------------------------------";
            reporter.log(indicatorMessage, indicatorType);
            return;
        }

        String packageName = (String) pubspecConfig.get("name");
        boolean isPackageProjectType = FlrFileUtil.isPackageProjectType(reporter, flutterProjectRootDir);

        // ----- Step-1 End -----

        // ----- Step-2 Begin -----
        // 进行核心逻辑版本检测：
        // 检测flr_config中的core_version和当前工具的core_version是否一致；若不一致，则按照以下规则处理：
        //  - 更新flr_config中的core_version的值为当前工具的core_version；
        //  - 生成“核心逻辑版本不一致”的警告日志，存放到警告日志数组。
        //
        String flrCoreVersion = (String) flrConfig.get("core_version");

        if (flrCoreVersion instanceof String == false) {
            flrCoreVersion = "unknown";
        }

        if (flrCoreVersion.equals(FlrConstant.CORE_VERSION) == false) {
            flrConfig.put("core_version", FlrConstant.CORE_VERSION);

            String warningText = String.format("[!]: warning, some team members may be using Flr tool with core_version %s, while you are using Flr tool with core_version %s", flrCoreVersion, FlrConstant.CORE_VERSION);
            String tipsText = "[*]: to fix it, you and your team members should use the Flr tool with same core_version"
                    + "\n"
                    + "[*]: \"core_version\" is the core logic version of Flr tool, you can click menu \"Tools-Flr-Version\" to get it";

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            FlrColoredLogEntity.Item tipsItem = new FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem, tipsItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        // 获取assets_legal_resource_dir数组、fonts_legal_resource_dir数组和illegal_resource_dir数组
        //
        // 合法的资源目录数组
        List<String> assetsLegalResourceDirArray = resourceDirResultTuple.get(0);
        List<String> fontsLegalResourceDirArray = resourceDirResultTuple.get(1);
        // 非法的资源目录数组
        List<String> illegalResourceDirArray = resourceDirResultTuple.get(2);

        if (illegalResourceDirArray.size() > 0) {
            String warningText = "[!]: warning, found the following resource directory which is not existed: ";
            for (String resourceDir : illegalResourceDirArray) {
                warningText += "\n" + String.format("  - %s", resourceDir);
            }

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-3 End -----

        indicatorMessage = "scan assets now ...";
        reporter.log(indicatorMessage, indicatorType);

        // ----- Step-4/5/6 Begin -----
        // 扫描 assets/fonts 合法资源目录，输出有序的 image/non_svg/svg/text 资产、font_family_config 及非法资源文件。
        ResourceScanResult scanResult = AssetScanner.scan(flutterProjectRootDir, packageName, assetsLegalResourceDirArray, fontsLegalResourceDirArray);
        List<String> imageAssetArray = scanResult.getImageAssets();
        List<String> textAssetArray = scanResult.getTextAssets();
        List<Map> fontFamilyConfigArray = scanResult.getFontFamilyConfigs();
        // ----- Step-4/5/6 End -----

        indicatorMessage = "scan assets done !!!";
        reporter.log(indicatorMessage, indicatorType);

        //  ----- Step-7 Begin -----
        //  检测是否存在illegal_resource_file
        List<VirtualFile> illegalResourceFileArray = scanResult.getIllegalResourceFiles();

        if (illegalResourceFileArray.size() > 0) {
            String warningText = "[!]: warning, found the following illegal resource file who's file basename contains illegal characters: ";
            for (VirtualFile resourceFile : illegalResourceFileArray) {
                warningText += "\n" + String.format("  - %s", resourceFile.getPath());
            }

            String tipsText = "[*]: to fix it, you should only use letters (a-z, A-Z), numbers (0-9), and the other legal characters ('_', '+', '-', '.', '·', '!', '@', '&', '$', '￥') to name the file";

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            FlrColoredLogEntity.Item tipsItem = new FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem, tipsItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-7 End -----

        // 添加资源声明到 `pubspec.yaml`
        indicatorMessage = "specify scanned assets in pubspec.yaml now ...";
        reporter.log(indicatorMessage, indicatorType);

        // ----- Step-8 Begin -----
        // 为扫描得到的legal_resource_file添加资源声明到pubspec.yaml
        //
        Map<String, Object> flutterConfig = (Map<String, Object>) pubspecConfig.get("flutter");
        if (flutterConfig == null || flrConfig instanceof Map == false) {
            flutterConfig = new LinkedHashMap<>();
        }

        List<String> newAssetArray = new ArrayList<>();
        newAssetArray.addAll(imageAssetArray);
        newAssetArray.addAll(textAssetArray);

        List<String> oldAssetArray = new ArrayList<>();
        if (flutterConfig.containsKey("assets")) {
            Object assets = flutterConfig.get("assets");
            if (assets instanceof List) {
                oldAssetArray = (List<String>) assets;
            }
        }

        List<String> assetArray = FlrAssetUtil.mergeFlutterAssets(flutterProjectRootDir, packageName, newAssetArray, oldAssetArray);
        if (assetArray.size() > 0) {
            flutterConfig.put("assets", assetArray);
        } else {
            flutterConfig.remove("assets");
        }

        if (fontFamilyConfigArray.size() > 0) {
            flutterConfig.put("fonts", fontFamilyConfigArray);
        } else {
            flutterConfig.remove("fonts");
        }
        pubspecConfig.put("flutter", flutterConfig);

        // 保存刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(pubspecConfig, pubspecFile);

        // ----- Step-8 End -----

        indicatorMessage = "specify scanned assets in pubspec.yaml done !!!";
        reporter.log(indicatorMessage, indicatorType);

        // 创建生成 `r.g.dart`
        indicatorMessage = "generate \"r.g.dart\" now ...";
        reporter.log(indicatorMessage, indicatorType);

        // ----- Step-9~19 Begin -----
        // 生成 asset_id 字典并按模板拼装 r.g.dart 内容（未格式化）
        String r_dart_file_content = RDartGenerator.generate(scanResult, packageName, isPackageProjectType, flutterEnvironment.shouldSupportNullsafety());
        // ----- Step-9~19 End -----

        // ----- Step-20 Begin -----
        // 结束操作，保存 r.g.dart
        //
        String rDartFilePath = flutterProjectRootDir + "/lib/r.g.dart";
        File rDartFile = new File(rDartFilePath);
        try {
            FlrFileUtil.writeContentToFile(project, r_dart_file_content, rDartFile);
        } catch (FlrException e) {
            reporter.failure(flrExceptionTitle, e.getMessage());
            return;
        }
        // ----- Step-20 End -----

        // ----- Step-21 Begin -----
        // 调用 flutter 工具对 r.g.dart 进行格式化操作
        //
        indicatorMessage = "format r.g.dart now ...";
        reporter.log(indicatorMessage, indicatorType);
        dartFormatter.format(rDartFile);
        indicatorMessage = "format r.g.dart done !!!";
        reporter.log(indicatorMessage, indicatorType);

        // ----- Step-21 End -----

        // ----- Step-22 Begin -----
        // 判断警告日志数组是否为空，若不为空，输出所有警告日志
        //
        int warningCount = warningMessages.size();
        if (warningCount > 0) {
            for (FlrColoredLogEntity coloredLogEntity : warningMessages) {
                reporter.log(coloredLogEntity);
            }
        }

        String contentTitle = String.format("[√]: generate for %s done!", flutterProjectRootDir);
        if (warningCount > 0) {
            String warningUnitDesc = "warning";
            if (warningCount > 1) {
                warningUnitDesc = "warnings";
            }
            String warningMessage = String.format("[!]: found %d %s, you can get the details from Flr ToolWindow", warningCount, warningUnitDesc);
            reporter.success(contentTitle, warningMessage, true);
        } else {
            reporter.success(contentTitle, "", false);
        }
        // ----- Step-22 End -----

        indicatorMessage = String.format("[√]: generate for %s done !!!", flutterProjectRootDir);
        reporter.log(indicatorMessage, indicatorType);

        indicatorMessage = "--------------------------------------------------------------------------------------";
        reporter.log(indicatorMessage, indicatorType);
    }
}

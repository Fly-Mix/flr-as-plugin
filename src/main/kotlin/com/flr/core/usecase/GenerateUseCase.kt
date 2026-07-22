package com.flr.core.usecase

import com.flr.FlrConstant
import com.flr.FlrException
import com.flr.FlrPaths
import com.flr.PubspecKeys
import com.flr.command.FlrChecker
import com.flr.command.util.FlrAssetUtil
import com.flr.command.util.FlrFileUtil
import com.flr.command.util.FlrUtil
import com.flr.core.codegen.RDartGenerator
import com.flr.core.env.FlutterEnvironment
import com.flr.core.format.DartFormatter
import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrColoredLogEntity
import com.flr.logConsole.FlrLogConsole
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.io.File

/**
 * generate 用例：对单个 flutter 工程执行「扫描资源 → 声明到 pubspec.yaml → 生成并格式化 r.g.dart」。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne（Step-1~22），行为不变。三接缝均以构造注入：
 * - [FlutterEnvironment]：nullsafety 判定
 * - [DartFormatter]：格式化 r.g.dart
 * - [FlrReporter]：进度日志与结果通知
 */
@Suppress("UNCHECKED_CAST")
class GenerateUseCase(
    private val project: Project,
    private val flutterEnvironment: FlutterEnvironment,
    private val dartFormatter: DartFormatter,
    private val reporter: FlrReporter
) {

    /**
     * 扫描工作空间下的 flutter 主工程及其所有子工程，各自 generate，然后统一获取依赖。
     * 逻辑迁移自原 FlrCommand.generateAll，改用 reporter，行为不变。
     */
    fun generateAll(actionEvent: AnActionEvent) {
        val indicatorType = FlrLogConsole.LogType.normal

        // 环境检测：非 flutter 工程则提示并返回
        if (flutterEnvironment.isFlutterProject() == false) {
            reporter.log("[*]: this is not a flutter project", FlrLogConsole.LogType.tips)
            return
        }

        reporter.log("[Flr Generate]", FlrLogConsole.LogType.tips)

        val flrExceptionTitle = "[x]: generate failed !!!"
        val flutterMainProjectRootDir = project.basePath!!

        // ----- Step-1：主工程 pubspec 检测 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterMainProjectRootDir)
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            return
        }

        reporter.log("generate for all flutter projects now...", indicatorType)

        // ----- Step-2：主工程 + 所有子工程各自 generateOne -----
        val flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir)

        reporter.log("", indicatorType)
        generateOne(flutterMainProjectRootDir)

        for (flutterProjectRootDir in flutterSubProjectRootDirArray) {
            reporter.log("", indicatorType)
            generateOne(flutterProjectRootDir)
        }

        // ----- Step-3：为所有 flutter 工程获取依赖 -----
        reporter.log("", indicatorType)
        reporter.log("get dependencies for all flutter projects via running \"Flutter Packages Get\" action now ...", indicatorType)
        FlrUtil.runFlutterPubGet(actionEvent)
        reporter.log("[√]: get dependencies for all flutter projects done !!!", indicatorType)

        reporter.log("", indicatorType)
        reporter.log("[√]: generate for all flutter projects done !!!\n", indicatorType)

        reporter.success("[√]: generate for all flutter projects done !!!", "", false)
    }

    /** 扫描资源目录，自动为资源添加声明到 pubspec.yaml 和生成 r.g.dart */
    fun generateOne(flutterProjectRootDir: String) {
        var indicatorMessage = "--------------------------- generate for specified project ---------------------------"
        val indicatorType = FlrLogConsole.LogType.normal
        reporter.log(indicatorMessage, indicatorType)

        val flrExceptionTitle = String.format("[x]: generate for %s failed", flutterProjectRootDir)

        // 警告日志数组
        val warningMessages = ArrayList<FlrColoredLogEntity>()

        val pubspecFile: File
        val pubspecConfig: MutableMap<String, Any>
        val flrConfig: MutableMap<String, Any?>
        val resourceDirResultTuple: List<List<String>>

        indicatorMessage = String.format("generate for %s now ...", flutterProjectRootDir)
        reporter.log(indicatorMessage, indicatorType)

        // ----- Step-1 Begin -----
        // 环境检测：pubspec 存在、flr 配置存在、flr 资源目录合法
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir)

            val pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir)
            pubspecFile = File(pubspecFilePath)
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile)!!

            FlrChecker.checkFlrConfigIsExisted(reporter, pubspecConfig)
            flrConfig = pubspecConfig[PubspecKeys.FLR] as MutableMap<String, Any?>

            resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(reporter, flrConfig, flutterProjectRootDir)
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error)
            indicatorMessage = "--------------------------------------------------------------------------------------"
            reporter.log(indicatorMessage, indicatorType)
            return
        }

        val packageName = pubspecConfig["name"] as String
        val isPackageProjectType = FlrFileUtil.isPackageProjectType(reporter, flutterProjectRootDir)
        // ----- Step-1 End -----

        // ----- Step-2 Begin -----
        // 核心逻辑版本检测：若与当前工具不一致，更新并生成警告
        var flrCoreVersion = flrConfig["core_version"] as? String ?: "unknown"

        if (flrCoreVersion != FlrConstant.CORE_VERSION) {
            flrConfig["core_version"] = FlrConstant.CORE_VERSION

            val warningText = String.format(
                "[!]: warning, some team members may be using Flr tool with core_version %s, while you are using Flr tool with core_version %s",
                flrCoreVersion, FlrConstant.CORE_VERSION
            )
            val tipsText = "[*]: to fix it, you and your team members should use the Flr tool with same core_version" +
                "\n" +
                "[*]: \"core_version\" is the core logic version of Flr tool, you can click menu \"Tools-Flr-Version\" to get it"

            val warningItem = FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning)
            val tipsItem = FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips)
            warningMessages.add(FlrColoredLogEntity(mutableListOf(warningItem, tipsItem)))
        }
        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        val assetsLegalResourceDirArray = resourceDirResultTuple[0]
        val fontsLegalResourceDirArray = resourceDirResultTuple[1]
        val illegalResourceDirArray = resourceDirResultTuple[2]

        if (illegalResourceDirArray.size > 0) {
            var warningText = "[!]: warning, found the following resource directory which is not existed: "
            for (resourceDir in illegalResourceDirArray) {
                warningText += "\n" + String.format("  - %s", resourceDir)
            }
            val warningItem = FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning)
            warningMessages.add(FlrColoredLogEntity(mutableListOf(warningItem)))
        }
        // ----- Step-3 End -----

        indicatorMessage = "scan assets now ..."
        reporter.log(indicatorMessage, indicatorType)

        // ----- Step-4/5/6 Begin -----
        var scanResult = com.flr.core.scan.AssetScanner.scan(flutterProjectRootDir, packageName, assetsLegalResourceDirArray, fontsLegalResourceDirArray)

        // ----- Step-4a：扫描 flr.assets（自动检测 lib/flr.assets/）并合并结果 -----
        val flrAssetsScanResult = com.flr.core.scan.AssetScanner.scanFlrAssets(flutterProjectRootDir, packageName)
        if (flrAssetsScanResult != null) {
            scanResult = scanResult.merge(flrAssetsScanResult)
            reporter.log("flr.assets detected, merged its resources into scan result", indicatorType)
        }

        val imageAssetArray = scanResult.imageAssets
        val textAssetArray = scanResult.textAssets
        val fontFamilyConfigArray = scanResult.fontFamilyConfigs
        // ----- Step-4/5/6 End -----

        indicatorMessage = "scan assets done !!!"
        reporter.log(indicatorMessage, indicatorType)

        // ----- Step-7 Begin -----
        val illegalResourceFileArray = scanResult.illegalResourceFiles

        if (illegalResourceFileArray.size > 0) {
            var warningText = "[!]: warning, found the following illegal resource file who's file basename contains illegal characters: "
            for (resourceFile in illegalResourceFileArray) {
                warningText += "\n" + String.format("  - %s", resourceFile.path)
            }
            val tipsText = "[*]: to fix it, you should only use letters (a-z, A-Z), numbers (0-9), and the other legal characters ('_', '+', '-', '.', '·', '!', '@', '&', '$', '￥') to name the file"

            val warningItem = FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning)
            val tipsItem = FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips)
            warningMessages.add(FlrColoredLogEntity(mutableListOf(warningItem, tipsItem)))
        }
        // ----- Step-7 End -----

        indicatorMessage = "specify scanned assets in pubspec.yaml now ..."
        reporter.log(indicatorMessage, indicatorType)

        // ----- Step-8 Begin -----
        var flutterConfig = pubspecConfig[PubspecKeys.FLUTTER] as? MutableMap<String, Any>
        if (flutterConfig == null) {
            flutterConfig = LinkedHashMap()
        }

        val newAssetArray = ArrayList<String>()
        newAssetArray.addAll(imageAssetArray)
        newAssetArray.addAll(textAssetArray)

        var oldAssetArray: MutableList<String> = ArrayList()
        if (flutterConfig.containsKey(PubspecKeys.ASSETS)) {
            val assets = flutterConfig[PubspecKeys.ASSETS]
            if (assets is List<*>) {
                oldAssetArray = assets as MutableList<String>
            }
        }

        val assetArray = FlrAssetUtil.mergeFlutterAssets(flutterProjectRootDir, packageName, newAssetArray, oldAssetArray)
        if (assetArray.size > 0) {
            flutterConfig[PubspecKeys.ASSETS] = assetArray
        } else {
            flutterConfig.remove(PubspecKeys.ASSETS)
        }

        if (fontFamilyConfigArray.size > 0) {
            flutterConfig[PubspecKeys.FONTS] = fontFamilyConfigArray
        } else {
            flutterConfig.remove(PubspecKeys.FONTS)
        }
        pubspecConfig[PubspecKeys.FLUTTER] = flutterConfig

        // 保存刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(project, pubspecConfig, pubspecFile)
        // ----- Step-8 End -----

        indicatorMessage = "specify scanned assets in pubspec.yaml done !!!"
        reporter.log(indicatorMessage, indicatorType)

        indicatorMessage = "generate \"r.g.dart\" now ..."
        reporter.log(indicatorMessage, indicatorType)

        // ----- Step-9~19 Begin -----
        val rDartFileContent = RDartGenerator.generate(scanResult, packageName, isPackageProjectType, flutterEnvironment.shouldSupportNullsafety())
        // ----- Step-9~19 End -----

        // ----- Step-20 Begin -----
        val rDartFilePath = flutterProjectRootDir + "/" + FlrPaths.LIB_DIR + "/" + FlrPaths.R_DART
        val rDartFile = File(rDartFilePath)
        try {
            FlrFileUtil.writeContentToFile(project, rDartFileContent, rDartFile)
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            return
        }
        // ----- Step-20 End -----

        // ----- Step-21 Begin -----
        indicatorMessage = "format r.g.dart now ..."
        reporter.log(indicatorMessage, indicatorType)
        dartFormatter.format(rDartFile)
        indicatorMessage = "format r.g.dart done !!!"
        reporter.log(indicatorMessage, indicatorType)
        // ----- Step-21 End -----

        // ----- Step-22 Begin -----
        val warningCount = warningMessages.size
        if (warningCount > 0) {
            for (coloredLogEntity in warningMessages) {
                reporter.log(coloredLogEntity)
            }
        }

        val contentTitle = String.format("[√]: generate for %s done!", flutterProjectRootDir)
        if (warningCount > 0) {
            var warningUnitDesc = "warning"
            if (warningCount > 1) {
                warningUnitDesc = "warnings"
            }
            val warningMessage = String.format("[!]: found %d %s, you can get the details from Flr ToolWindow", warningCount, warningUnitDesc)
            reporter.success(contentTitle, warningMessage, true)
        } else {
            reporter.success(contentTitle, "", false)
        }
        // ----- Step-22 End -----

        indicatorMessage = String.format("[√]: generate for %s done !!!", flutterProjectRootDir)
        reporter.log(indicatorMessage, indicatorType)

        indicatorMessage = "--------------------------------------------------------------------------------------"
        reporter.log(indicatorMessage, indicatorType)
    }
}

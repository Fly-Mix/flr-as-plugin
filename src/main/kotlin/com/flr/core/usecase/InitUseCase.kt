package com.flr.core.usecase

import com.flr.FlrConstant
import com.flr.FlrException
import com.flr.FlrPaths
import com.flr.PubspecKeys
import com.flr.command.FlrChecker
import com.flr.command.util.FlrFileUtil
import com.flr.command.util.FlrUtil
import com.flr.core.env.FlutterEnvironment
import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrLogConsole
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.io.File

/**
 * init 用例：为工作空间下的 flutter 主工程及其所有子工程写入 flr 配置与 r_dart_library 依赖。
 *
 * 逻辑整段迁移自原 FlrCommand.initAll / initOne，行为不变；日志与通知统一走 [FlrReporter]，
 * SDK/版本探测走注入的 [FlutterEnvironment]。
 */
@Suppress("UNCHECKED_CAST")
class InitUseCase(
    private val project: Project,
    private val flutterEnvironment: FlutterEnvironment,
    private val reporter: FlrReporter
) {

    /** 初始化所有工作空间下的 flutter 主工程及其所有子工程 */
    fun initAll(actionEvent: AnActionEvent) {
        val indicatorType = FlrLogConsole.LogType.normal

        // 环境检测：非 flutter 工程则提示并返回
        if (flutterEnvironment.isFlutterProject() == false) {
            reporter.log("[*]: this is not a flutter project", FlrLogConsole.LogType.tips)
            return
        }

        reporter.log("[Flr Init]", FlrLogConsole.LogType.tips)

        val flrExceptionTitle = "[x]: init failed !!!"
        val flutterMainProjectRootDir = project.basePath!!

        // ----- Step-1：主工程 pubspec 检测 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterMainProjectRootDir)
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            return
        }

        reporter.log("init all flutter projects now...", indicatorType)

        // ----- Step-2：主工程 + 所有子工程各自 initOne -----
        val flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir)

        reporter.log("", indicatorType)
        initOne(flutterMainProjectRootDir)

        for (flutterProjectRootDir in flutterSubProjectRootDirArray) {
            reporter.log("", indicatorType)
            initOne(flutterProjectRootDir)
        }

        // ----- Step-3：为所有 flutter 工程获取依赖 -----
        reporter.log("", indicatorType)
        reporter.log("get dependencies for all flutter projects via running \"Flutter Packages Get\" action now ...", indicatorType)
        FlrUtil.runFlutterPubGet(actionEvent)
        reporter.log("[√]: get dependencies for all flutter projects done !!!", indicatorType)

        reporter.log("", indicatorType)
        reporter.log("[√]: init all flutter projects done !!!\n", indicatorType)
        reporter.log("[*]: if you want to know how to make a good resource structure for your flutter project, please click menu \"Tools-Flr-Recommend\" ", FlrLogConsole.LogType.tips)

        reporter.success("[√]: init all flutter projects done !!!", "", false)
    }

    /** 对指定 flutter 工程进行初始化 */
    private fun initOne(flutterProjectRootDir: String) {
        val indicatorType = FlrLogConsole.LogType.normal
        reporter.log("------------------------------- init specified project -------------------------------", indicatorType)

        val flrExceptionTitle = String.format("[x]: init %s failed", flutterProjectRootDir)

        val pubspecFile: File
        val pubspecConfig: MutableMap<String, Any>

        reporter.log(String.format("init %s now ...", flutterProjectRootDir), indicatorType)

        // ----- Step-1：pubspec 检测与加载 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir)

            val pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir)
            pubspecFile = File(pubspecFilePath)
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile)!!
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error)
            reporter.log("--------------------------------------------------------------------------------------", indicatorType)
            return
        }

        reporter.log(String.format("init %s now ...", project.basePath), indicatorType)

        // ----- Step-2：添加 flr_config 与 r_dart_library 依赖到 pubspec.yaml -----
        var dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH
        var assetResourceDirArray: MutableList<String> = ArrayList()
        var fontResourceDirArray: MutableList<String> = ArrayList()
        val flrConfig = LinkedHashMap<String, Any>()

        val usedFlrCoreLogicVersion = FlrConstant.CORE_VERSION
        flrConfig["core_version"] = usedFlrCoreLogicVersion

        val oldFlrConfig = pubspecConfig[PubspecKeys.FLR] as? Map<String, Any?>
        if (oldFlrConfig != null) {
            if (oldFlrConfig.containsKey("dartfmt_line_length")) {
                val length = oldFlrConfig["dartfmt_line_length"]
                if (length is Int) {
                    dartfmtLineLength = length
                    if (dartfmtLineLength < FlrConstant.DARTFMT_LINE_LENGTH) {
                        dartfmtLineLength = FlrConstant.DARTFMT_LINE_LENGTH
                    }
                }
            }

            if (oldFlrConfig.containsKey(PubspecKeys.ASSETS)) {
                val assets = oldFlrConfig[PubspecKeys.ASSETS]
                if (assets is List<*>) {
                    assetResourceDirArray = assets as MutableList<String>
                }
            }

            if (oldFlrConfig.containsKey(PubspecKeys.FONTS)) {
                val fonts = oldFlrConfig[PubspecKeys.FONTS]
                if (fonts is List<*>) {
                    fontResourceDirArray = fonts as MutableList<String>
                }
            }
        }

        // 自动检测 flr.assets 目录：若存在且尚未在配置中，自动添加其子目录
        if (FlrFileUtil.isFlrAssetsDirExisted(flutterProjectRootDir)) {
            val libDir = "lib/${FlrPaths.FLR_ASSETS}"
            val imagesDir = "$libDir/images"
            val rawfilesDir = "$libDir/rawfiles"
            val fontsDir = "$libDir/fonts"

            if (!assetResourceDirArray.contains(imagesDir)) {
                assetResourceDirArray.add(imagesDir)
            }
            if (!assetResourceDirArray.contains(rawfilesDir)) {
                assetResourceDirArray.add(rawfilesDir)
            }
            if (!fontResourceDirArray.contains(fontsDir)) {
                fontResourceDirArray.add(fontsDir)
            }

            reporter.log("detected flr.assets directory at $libDir, auto-configured its subdirectories", indicatorType)
        }

        flrConfig["dartfmt_line_length"] = dartfmtLineLength
        flrConfig[PubspecKeys.ASSETS] = assetResourceDirArray
        flrConfig[PubspecKeys.FONTS] = fontResourceDirArray
        pubspecConfig[PubspecKeys.FLR] = flrConfig

        reporter.log("add flr configuration into pubspec.yaml done!", indicatorType)

        val dependenciesMap = pubspecConfig["dependencies"] as MutableMap<String, Any>
        val rDartLibraryVersion = flutterEnvironment.getRDartLibraryVersion()
        dependenciesMap["r_dart_library"] = rDartLibraryVersion
        pubspecConfig["dependencies"] = dependenciesMap

        reporter.log("add dependency \"r_dart_library\"(https://github.com/YK-Unit/r_dart_library) into pubspec.yaml done!", indicatorType)

        // ----- Step-3：修正 flutter 配置，避免 pub get 失败 -----
        var flutterConfig = pubspecConfig[PubspecKeys.FLUTTER] as? MutableMap<String, Any>
        if (flutterConfig == null) {
            flutterConfig = LinkedHashMap()
        }

        val flutterAssetsKey = PubspecKeys.ASSETS
        val flutterAssets = flutterConfig[flutterAssetsKey]
        var shouldRmFlutterAssetsKey = true
        if (flutterAssets is List<*> && flutterAssets.isEmpty() == false) {
            shouldRmFlutterAssetsKey = false
        }
        if (shouldRmFlutterAssetsKey) {
            flutterConfig.remove(flutterAssetsKey)
        }

        val flutterFontsKey = PubspecKeys.FONTS
        val flutterFonts = flutterConfig[flutterFontsKey]
        var shouldRmFlutterFontsKey = true
        if (flutterFonts is List<*> && flutterFonts.isEmpty() == false) {
            shouldRmFlutterFontsKey = false
        }
        if (shouldRmFlutterFontsKey) {
            flutterConfig.remove(flutterFontsKey)
        }

        pubspecConfig[PubspecKeys.FLUTTER] = flutterConfig

        // 保存并刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(project, pubspecConfig, pubspecFile)

        reporter.success(String.format("[√]: init %s done !!!", flutterProjectRootDir), "", false)

        reporter.log(String.format("[√]: init %s done !!!", flutterProjectRootDir), indicatorType)
        reporter.log("--------------------------------------------------------------------------------------", indicatorType)
    }
}

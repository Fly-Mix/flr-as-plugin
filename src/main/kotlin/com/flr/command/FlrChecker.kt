package com.flr.command

import com.flr.FlrConstant
import com.flr.FlrException
import com.flr.FlrPaths
import com.flr.PubspecKeys
import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrLogConsole
import java.io.File

/**
 * 条件检测器，提供检测各种条件是否合法的方法。
 *
 * 抛出 FlrException 的方法标注 `@Throws(FlrException::class)`，以便 Java 调用方能 `try/catch(FlrException)`
 * （Kotlin 默认不在字节码声明受检异常）。
 */
object FlrChecker {

    /** 检测当前 flutter 工程目录是否存在 pubspec.yaml，存在返回 true，否则抛异常。 */
    @JvmStatic
    @Throws(FlrException::class)
    fun checkPubspecFileIsExisted(reporter: FlrReporter, flutter_dir: String): Boolean {
        val pubspecFilePath = flutter_dir + "/" + FlrPaths.PUBSPEC_YAML
        val pubspecFile = File(pubspecFilePath)

        if (pubspecFile.exists()) {
            return true
        }

        reporter.log(String.format("[x]: %s not found", pubspecFilePath), FlrLogConsole.LogType.error)
        val tipsStr = String.format("[*]: please make sure %s is existed", pubspecFilePath)
        reporter.log(tipsStr, FlrLogConsole.LogType.tips)

        throw FlrException.ILLEGAL_ENV
    }

    /** 检测 pubspec.yaml 中是否存在 flr 的配置信息 `flr_config`，存在返回 true，否则抛异常。 */
    @JvmStatic
    @Throws(FlrException::class)
    fun checkFlrConfigIsExisted(reporter: FlrReporter, pubspecConfig: Map<String, Any?>): Boolean {
        val flrConfig = pubspecConfig[PubspecKeys.FLR]
        if (flrConfig is Map<*, *>) {
            return true
        }

        reporter.log("[x]: have no flr configuration in pubspec.yaml", FlrLogConsole.LogType.error)
        reporter.log("[*]: please click menu \"Tools-Flr-Init\" to fix it", FlrLogConsole.LogType.tips)

        throw FlrException.ILLEGAL_ENV
    }

    /**
     * 检测当前 flr 配置信息中的 assets 配置是否合法。
     * 合法则返回资源目录结果三元组 [assetsLegalResourceDirArray, fontsLegalResourceDirArray, illegalResourceDirArray]，否则抛异常。
     */
    @JvmStatic
    @JvmSuppressWildcards
    @Throws(FlrException::class)
    fun checkFlrAssetsIsLegal(
        reporter: FlrReporter,
        flrConfig: Map<String, Any?>,
        flutterProjectRootDir: String
    ): List<List<String>> {
        var flrCoreVersion = FlrConstant.CORE_VERSION
        if (flrConfig.containsKey("core_version")) {
            flrCoreVersion = String.format("%s", flrConfig["core_version"])
        }
        var dartfmtLineLengthStr = String.format("%d", FlrConstant.DARTFMT_LINE_LENGTH)
        if (flrConfig.containsKey("dartfmt_line_length")) {
            dartfmtLineLengthStr = String.format("%s", flrConfig["dartfmt_line_length"])
        }

        // 取 assets/fonts 目录配置；移除非法的 resource_dir（nil / 空字符串）并去重
        @Suppress("UNCHECKED_CAST")
        val rawAssets = flrConfig[PubspecKeys.ASSETS] as? List<String?> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val rawFonts = flrConfig[PubspecKeys.FONTS] as? List<String?> ?: emptyList()
        val assetsResourceDirArray = LinkedHashSet(rawAssets.filterNotNull().filter { it.isNotEmpty() }).toList()
        val fontsResourceDirArray = LinkedHashSet(rawFonts.filterNotNull().filter { it.isNotEmpty() }).toList()

        // 筛选合法的和非法的 resource_dir
        val assetsLegalResourceDirArray = ArrayList<String>()
        val fontsLegalResourceDirArray = ArrayList<String>()
        val illegalResourceDirArray = ArrayList<String>()

        for (relativeResourceDir in assetsResourceDirArray) {
            val resourceDir = "$flutterProjectRootDir/$relativeResourceDir"
            val dir = File(resourceDir)
            if (dir.isDirectory && dir.exists()) {
                assetsLegalResourceDirArray.add(resourceDir)
            } else {
                illegalResourceDirArray.add(resourceDir)
            }
        }

        for (relativeResourceDir in fontsResourceDirArray) {
            val resourceDir = "$flutterProjectRootDir/$relativeResourceDir"
            val dir = File(resourceDir)
            if (dir.isDirectory && dir.exists()) {
                fontsLegalResourceDirArray.add(resourceDir)
            } else {
                illegalResourceDirArray.add(resourceDir)
            }
        }

        val legalResourceDirCount = assetsLegalResourceDirArray.size + fontsLegalResourceDirArray.size
        if (legalResourceDirCount > 0) {
            val resourceDirResultTuple = ArrayList<List<String>>()
            resourceDirResultTuple.add(assetsLegalResourceDirArray)
            resourceDirResultTuple.add(fontsLegalResourceDirArray)
            resourceDirResultTuple.add(illegalResourceDirArray)
            return resourceDirResultTuple
        }

        if (illegalResourceDirArray.size > 0) {
            var warningText = "[!]: warning, found the following resource directory which is not existed: "
            for (resourceDir in illegalResourceDirArray) {
                warningText += "\n" + String.format("  - %s", resourceDir)
            }
            warningText += "\n"
            reporter.log(warningText, FlrLogConsole.LogType.warning)
        }

        reporter.log("[x]: have no valid resource directories configuration in pubspec.yaml", FlrLogConsole.LogType.error)
        reporter.log(
            String.format(
                "[*]: please manually configure the resource directories to fix it, for example:\n" +
                    "‭ \n" +
                    "‭     flr:\n" +
                    "‭       core_version: %s\n" +
                    "‭       dartfmt_line_length: %s\n" +
                    "‭       # config the image and text resource directories that need to be scanned\n" +
                    "‭       assets:\n" +
                    "‭         - lib/assets/images\n" +
                    "‭         - lib/assets/texts\n" +
                    "‭       # config the font resource directories that need to be scanned\n" +
                    "‭       fonts:\n" +
                    "‭         - lib/assets/fonts\n",
                flrCoreVersion, dartfmtLineLengthStr
            ), FlrLogConsole.LogType.tips
        )

        throw FlrException.ILLEGAL_ENV
    }
}

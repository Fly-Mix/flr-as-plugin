package com.flr.command

import com.flr.FlrConstant
import com.flr.command.util.FlrUtil
import com.flr.core.env.FlutterEnvironment
import com.flr.core.format.DartFormatter
import com.flr.core.report.FlrReporter
import com.flr.core.usecase.GenerateUseCase
import com.flr.core.usecase.InitUseCase
import com.flr.core.usecase.MonitorUseCase
import com.flr.infra.FlrConsoleReporter
import com.flr.infra.IdeDartFormatter
import com.flr.infra.IdeFlutterEnvironment
import com.flr.logConsole.FlrLogConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * 命令门面：构造 FlrReporter 与三接缝（环境/格式化/通知），委托各用例（Init/Generate/Monitor）执行。
 * 逻辑均已下沉到用例，本类只做薄适配 + 持有长生命周期的 [MonitorUseCase]。
 */
class FlrCommand(private val curProject: Project) : Disposable {

    // Flutter/Dart 环境探测接缝（SDK 探测、nullsafety 判定、r_dart_library 版本）
    private val flutterEnvironment: FlutterEnvironment = IdeFlutterEnvironment(curProject)

    // Dart 代码格式化接缝
    private val dartFormatter: DartFormatter = IdeDartFormatter(curProject)

    // 资源监控用例（长生命周期，承载监控状态与监听器）
    private val monitorUseCase = MonitorUseCase(curProject)

    /** 监控服务是否正在运行（供 action 查询以决定菜单文案/日志行为）。 */
    fun isMonitoringAssets(): Boolean = monitorUseCase.isMonitoring()

    override fun dispose() {
        monitorUseCase.dispose()
    }

    // MARK: Command Action Methods

    /** 薄适配层：委托 InitUseCase.initAll。 */
    fun initAll(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        InitUseCase(curProject, flutterEnvironment, reporter).initAll(actionEvent)
    }

    /** 薄适配层：委托 GenerateUseCase.generateAll。 */
    fun generateAll(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter).generateAll(actionEvent)
    }

    /** 薄适配层：构造 FlrReporter 与三接缝，委托 GenerateUseCase 执行全流水线。 */
    fun generateOne(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole, flutterProjectRootDir: String) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        val useCase = GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter)
        useCase.generateOne(flutterProjectRootDir)
    }

    /** 薄适配层：委托 MonitorUseCase.start。 */
    fun startMonitor(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole): Boolean {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        val generateUseCase = GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter)
        return monitorUseCase.start(actionEvent, reporter, generateUseCase, flutterEnvironment)
    }

    /** 薄适配层：委托 MonitorUseCase.stop。 */
    fun stopMonitor(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        monitorUseCase.stop(reporter)
    }

    /** 显示版本信息，包括工具版本信息和核心逻辑版本信息 */
    fun displayVersion(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        val indicatorType = FlrLogConsole.LogType.normal
        reporter.log("[Flr Version]", FlrLogConsole.LogType.tips)

        val flrVersion = FlrUtil.getFlrVersion()
        val flrCoreVersion = FlrConstant.CORE_VERSION

        val indicatorMessage = String.format(
            "Flr version %s\n" +
                "CoreLogic version %s\n",
            flrVersion,
            flrCoreVersion
        )
        reporter.log(indicatorMessage, indicatorType)
    }

    /** 显示推荐的 flutter 资源组织结构信息 */
    fun displayRecommendedFlutterResourceStructure(actionEvent: AnActionEvent, flrLogConsole: FlrLogConsole) {
        val reporter: FlrReporter = FlrConsoleReporter(curProject, flrLogConsole)
        val indicatorType = FlrLogConsole.LogType.normal
        reporter.log("[Flr Recommend]", FlrLogConsole.LogType.tips)

        var indicatorMessage = "Flr recommends the following flutter resource structure schemes:\n" +
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
            "  │   ├── ..  \n"
        reporter.log(indicatorMessage, indicatorType)

        val flrCoreVersion = FlrConstant.CORE_VERSION
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
                "      - lib/assets/fonts\n", flrCoreVersion, FlrConstant.DARTFMT_LINE_LENGTH
        )
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips)

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
            "  │   ├── ..  \n"
        reporter.log(indicatorMessage, indicatorType)

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
                "      - assets/fonts\n", flrCoreVersion, FlrConstant.DARTFMT_LINE_LENGTH
        )
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips)
    }
}

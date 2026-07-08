package com.flr.command;

import com.flr.FlrConstant;
import com.flr.command.util.FlrUtil;
import com.flr.core.env.FlutterEnvironment;
import com.flr.core.format.DartFormatter;
import com.flr.core.report.FlrReporter;
import com.flr.core.usecase.GenerateUseCase;
import com.flr.core.usecase.InitUseCase;
import com.flr.core.usecase.MonitorUseCase;
import com.flr.infra.FlrConsoleReporter;
import com.flr.infra.IdeDartFormatter;
import com.flr.infra.IdeFlutterEnvironment;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


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

    private final Project curProject;

    // Flutter/Dart 环境探测接缝（SDK 探测、nullsafety 判定、r_dart_library 版本）
    private final FlutterEnvironment flutterEnvironment;

    // Dart 代码格式化接缝
    private final DartFormatter dartFormatter;

    // 资源监控用例（长生命周期，承载监控状态与监听器）
    private final MonitorUseCase monitorUseCase;

    public FlrCommand(Project project) {
        curProject = project;
        flutterEnvironment = new IdeFlutterEnvironment(project);
        dartFormatter = new IdeDartFormatter(project);
        monitorUseCase = new MonitorUseCase(project);
    }

    /*
    * 监控服务是否正在运行（供 action 查询以决定菜单文案/日志行为）。
    * */
    public boolean isMonitoringAssets() {
        return monitorUseCase.isMonitoring();
    }

    @Override
    public void dispose() {
        monitorUseCase.dispose();
    }

    // MARK: Command Action Methods

    /*
    * 薄适配层：委托 InitUseCase.initAll（逻辑见 InitUseCase）。
    * */
    public void initAll(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        new InitUseCase(curProject, flutterEnvironment, reporter).initAll(actionEvent);
    }

    /*
    * 薄适配层：委托 GenerateUseCase.generateAll（逻辑见 GenerateUseCase）。
    * */
    public void generateAll(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        new GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter).generateAll(actionEvent);
    }

    /*
    * 扫描资源目录，自动为资源添加声明到 pubspec.yaml 和生成 r.g.dart
    *
    * 薄适配层：构造 FlrReporter 与三接缝，委托 GenerateUseCase 执行全流水线（逻辑见 GenerateUseCase）。
    * */
    public void generateOne(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole, @NotNull String flutterProjectRootDir) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        GenerateUseCase useCase = new GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter);
        useCase.generateOne(flutterProjectRootDir);
    }

    /*
    * 薄适配层：委托 MonitorUseCase.start（逻辑见 MonitorUseCase）。
    * */
    public Boolean startMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        GenerateUseCase generateUseCase = new GenerateUseCase(curProject, flutterEnvironment, dartFormatter, reporter);
        return monitorUseCase.start(actionEvent, reporter, generateUseCase, flutterEnvironment);
    }

    /*
    * 薄适配层：委托 MonitorUseCase.stop。
    * */
    public void stopMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        monitorUseCase.stop(reporter);
    }

    /*
    * 显示版本信息，包括工具版本信息和核心逻辑板本信息
    * */
    public void displayVersion(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        String indicatorMessage = "[Flr Version]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips);

        String flrVersion = FlrUtil.getFlrVersion();
        String flrCoreVersion = FlrConstant.CORE_VERSION;

        indicatorMessage = String.format("Flr version %s\n" +
                "CoreLogic version %s\n",
                flrVersion,
                flrCoreVersion);
        reporter.log(indicatorMessage, indicatorType);
    }

    /*
     * 显示推荐的flutter资源组织结构信息
     * */
    public void displayRecommendedFlutterResourceStructure(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        FlrReporter reporter = new FlrConsoleReporter(curProject, flrLogConsole);
        String indicatorMessage = "[Flr Recommend]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips);

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
        reporter.log(indicatorMessage, indicatorType);

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
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips);

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
        reporter.log(indicatorMessage, indicatorType);

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
        reporter.log(indicatorMessage, FlrLogConsole.LogType.tips);
    }

}

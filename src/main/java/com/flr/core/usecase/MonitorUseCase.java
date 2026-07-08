package com.flr.core.usecase;

import com.flr.FlrException;
import com.flr.command.FlrChecker;
import com.flr.command.FlrListener;
import com.flr.command.util.FlrFileUtil;
import com.flr.core.env.FlutterEnvironment;
import com.flr.core.report.FlrReporter;
import com.flr.logConsole.FlrLogConsole;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * monitor 用例：启动/停止资源变化监控服务。
 *
 * 长生命周期对象（由 FlrCommand 持有），承载监控状态（是否运行、当前监听器）。逻辑整段迁移自原
 * FlrCommand.startMonitor / stopMonitor，行为不变；日志/通知走 {@link FlrReporter}，生成复用 {@link GenerateUseCase}。
 * 监听器经 Disposer 管理：stop 或工程关闭时释放（含其防抖 Alarm 与消息总线连接）。
 */
@SuppressWarnings("unchecked")
public final class MonitorUseCase implements Disposable {

    private static final String MONITORING_TIP =
            "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                    "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";

    private final Project project;

    private boolean monitoring = false;
    private FlrListener listener;

    public MonitorUseCase(@NotNull Project project) {
        this.project = project;
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    /*
     * 启动资源变化监控服务：检测合法资源目录 -> 先 generate 一次 -> 启动带防抖的文件监听器（变更时再 generate）。
     * */
    public boolean start(@NotNull AnActionEvent actionEvent,
                         @NotNull FlrReporter reporter,
                         @NotNull GenerateUseCase generateUseCase,
                         @NotNull FlutterEnvironment env) {
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;

        if (env.isFlutterProject() == false) {
            reporter.log("[*]: this is not a flutter project", FlrLogConsole.LogType.tips);
            return false;
        }

        reporter.log("[Flr Start Monitor]", FlrLogConsole.LogType.tips);

        String flrExceptionTitle = "[x]: start monitor failed !!!";
        String flutterMainProjectRootDir = project.getBasePath();

        // ----- Step-1：主工程 pubspec 检测 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterMainProjectRootDir);
        } catch (FlrException e) {
            reporter.failure(flrExceptionTitle, e.getMessage());
            return false;
        }

        // ----- Step-2：收集所有工程的合法资源目录 -----
        reporter.log("", indicatorType);
        reporter.log("get the valid resource directories of all projects now ...", indicatorType);

        List<String> flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir);
        List<String> flutterProjectRootDirArray = new ArrayList<>();
        flutterProjectRootDirArray.add(flutterMainProjectRootDir);
        flutterProjectRootDirArray.addAll(flutterSubProjectRootDirArray);

        List<String> legalResourceDirArray = new ArrayList<>();
        List<String> illegalResourceDirArray = new ArrayList<>();

        for (String flutterProjectRootDir : flutterProjectRootDirArray) {
            try {
                reporter.log("", indicatorType);
                reporter.log("--------------------------- get info of specified project ----------------------------", indicatorType);
                reporter.log(String.format("get the valid resource directories from %s now...", flutterProjectRootDir), indicatorType);

                FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir);

                String pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir);
                File pubspecFile = new File(pubspecFilePath);
                Map<String, Object> pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile);

                FlrChecker.checkFlrConfigIsExisted(reporter, pubspecConfig);
                Map<String, Object> flrConfig = (Map<String, Object>) pubspecConfig.get("flr");

                List<List<String>> resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(reporter, flrConfig, flutterProjectRootDir);

                legalResourceDirArray.addAll(resourceDirResultTuple.get(0));
                legalResourceDirArray.addAll(resourceDirResultTuple.get(1));
                illegalResourceDirArray.addAll(resourceDirResultTuple.get(2));

                reporter.log(String.format("get the valid resource directories from %s done !!!", flutterProjectRootDir), indicatorType);
                reporter.log("--------------------------------------------------------------------------------------", indicatorType);
            } catch (FlrException e) {
                reporter.failure(flrExceptionTitle, e.getMessage());

                reporter.log("", FlrLogConsole.LogType.error);
                reporter.log(String.format("[x]: %s has no valid resource directories.", flutterProjectRootDir), FlrLogConsole.LogType.error);
                reporter.log("--------------------------------------------------------------------------------------", indicatorType);
            }
        }

        reporter.log("", indicatorType);
        reporter.log("get the valid resource directories of all projects done !!!", indicatorType);

        if (legalResourceDirArray.size() == 0) {
            reporter.failure(flrExceptionTitle, FlrException.ILLEGAL_ENV.getMessage());

            reporter.log("", indicatorType);
            reporter.log("[x]: have no valid resource directories to be monitored", FlrLogConsole.LogType.error);
            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error);
            return false;
        }

        // 若已有监听器在运行，先停止
        if (listener != null) {
            stop(reporter);
        }

        // ----- Step-3：启动前先 generate 一次 -----
        reporter.log("", indicatorType);
        reporter.log("now generate for all projects once before launching the monitoring service ...", indicatorType);
        reporter.log("", indicatorType);
        generateUseCase.generateAll(actionEvent);
        reporter.log("", indicatorType);
        reporter.log("did generate for all projects, now is going to launching the monitoring service ...", indicatorType);

        // ----- Step-4：启动带防抖的文件监听器 -----
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowStr = df.format(new Date());

        reporter.log("", indicatorType);
        reporter.log(String.format("--------------------------- %s ---------------------------", nowStr), indicatorType);
        reporter.log("launch a monitoring service now ...", indicatorType);
        reporter.log("launching ...", indicatorType);

        FlrListener.AssetChangesEventCallback callback = () -> {
            reporter.success("[!]: detect some asset changes !!!", "[*]: invoke Flr-Generate Action now ... ", false);

            SimpleDateFormat cbDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String cbNowStr = cbDf.format(new Date());
            reporter.log(String.format("--------------------------- %s ---------------------------", cbNowStr), indicatorType);
            reporter.log("detect some asset changes, run Flr-Generate Action now", indicatorType);
            reporter.log("generate for all projects now ...", indicatorType);

            reporter.log("", indicatorType);
            generateUseCase.generateAll(actionEvent);

            reporter.log("", indicatorType);
            reporter.log("generate for all projects done !!!", indicatorType);
            reporter.log("---------------------------------------------------------------------------------", indicatorType);
            reporter.log("", indicatorType);
            reporter.log(MONITORING_TIP, FlrLogConsole.LogType.tips);

            reporter.success("[!]: invoke Flr-Generate Action done !!!", "[*]: you can get the details from Flr ToolWindow", false);
        };

        List<String> legalRelativeResourceDirArray = FlrFileUtil.convertToRelativeResourceDirs(flutterMainProjectRootDir, legalResourceDirArray);
        listener = new FlrListener(project, legalRelativeResourceDirArray, callback);
        // 先注册到 Disposer（以 project 为父），再 startWatching：使监听器的 Alarm/连接以监听器为父，随其释放
        Disposer.register(project, listener);
        listener.startWatching();
        monitoring = true;

        reporter.log("launch a monitoring service done !!!", indicatorType);
        reporter.log("the monitoring service is monitoring the following resource directory:", indicatorType);
        for (String resourceDir : legalResourceDirArray) {
            reporter.log(String.format("  - %s", resourceDir), indicatorType);
        }

        if (illegalResourceDirArray.size() > 0) {
            reporter.log("", indicatorType);
            reporter.log("[!]: warning, found the following resource directory which is not existed: ", FlrLogConsole.LogType.warning);
            for (String resourceDir : illegalResourceDirArray) {
                reporter.log(String.format("  - %s", resourceDir), FlrLogConsole.LogType.warning);
            }
        }
        reporter.log("---------------------------------------------------------------------------------", indicatorType);
        reporter.log("", indicatorType);
        reporter.log(MONITORING_TIP, FlrLogConsole.LogType.tips);

        reporter.success("[√]: start monitor done !!!", "[*]: you can get the details from Flr ToolWindow", false);

        return true;
    }

    /*
     * 停止资源变化监控服务
     * */
    public void stop(@NotNull FlrReporter reporter) {
        reporter.log("[Flr Stop Monitor]", FlrLogConsole.LogType.tips);

        if (listener != null) {
            Disposer.dispose(listener);
            listener = null;
        }
        monitoring = false;

        reporter.log("[√]: terminate the monitoring service done !!!", FlrLogConsole.LogType.normal);
        reporter.success("[√]: stop monitor done !!!", "[*]: you can get the details from Flr ToolWindow", false);
    }

    @Override
    public void dispose() {
        if (listener != null) {
            Disposer.dispose(listener);
            listener = null;
        }
        monitoring = false;
    }
}

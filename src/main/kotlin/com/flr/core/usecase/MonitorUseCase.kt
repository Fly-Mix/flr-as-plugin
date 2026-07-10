package com.flr.core.usecase

import com.flr.FlrException
import com.flr.PubspecKeys
import com.flr.command.FlrChecker
import com.flr.command.FlrListener
import com.flr.command.util.FlrFileUtil
import com.flr.core.env.FlutterEnvironment
import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrLogConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * monitor 用例：启动/停止资源变化监控服务。
 *
 * 长生命周期对象（由 FlrCommand 持有），承载监控状态（是否运行、当前监听器）。逻辑整段迁移自原
 * FlrCommand.startMonitor / stopMonitor，行为不变；日志/通知走 [FlrReporter]，生成复用 [GenerateUseCase]。
 * 监听器经 Disposer 管理：stop 或工程关闭时释放（含其防抖 Alarm 与消息总线连接）。
 */
class MonitorUseCase(private val project: Project) : Disposable {

    private var monitoring = false
    private var listener: FlrListener? = null

    fun isMonitoring(): Boolean = monitoring

    /**
     * 启动资源变化监控服务：检测合法资源目录 -> 先 generate 一次 -> 启动带防抖的文件监听器（变更时再 generate）。
     */
    @Suppress("UNCHECKED_CAST")
    fun start(
        actionEvent: AnActionEvent,
        reporter: FlrReporter,
        generateUseCase: GenerateUseCase,
        env: FlutterEnvironment
    ): Boolean {
        val indicatorType = FlrLogConsole.LogType.normal

        if (env.isFlutterProject() == false) {
            reporter.log("[*]: this is not a flutter project", FlrLogConsole.LogType.tips)
            return false
        }

        reporter.log("[Flr Start Monitor]", FlrLogConsole.LogType.tips)

        val flrExceptionTitle = "[x]: start monitor failed !!!"
        val flutterMainProjectRootDir = project.basePath

        // ----- Step-1：主工程 pubspec 检测 -----
        try {
            FlrChecker.checkPubspecFileIsExisted(reporter, flutterMainProjectRootDir!!)
        } catch (e: FlrException) {
            reporter.failure(flrExceptionTitle, e.message)
            return false
        }

        // ----- Step-2：收集所有工程的合法资源目录 -----
        reporter.log("", indicatorType)
        reporter.log("get the valid resource directories of all projects now ...", indicatorType)

        val flutterSubProjectRootDirArray = FlrFileUtil.getFlutterSubProjectRootDirs(flutterMainProjectRootDir)
        val flutterProjectRootDirArray = ArrayList<String>()
        flutterProjectRootDirArray.add(flutterMainProjectRootDir)
        flutterProjectRootDirArray.addAll(flutterSubProjectRootDirArray)

        val legalResourceDirArray = ArrayList<String>()
        val illegalResourceDirArray = ArrayList<String>()

        for (flutterProjectRootDir in flutterProjectRootDirArray) {
            try {
                reporter.log("", indicatorType)
                reporter.log("--------------------------- get info of specified project ----------------------------", indicatorType)
                reporter.log(String.format("get the valid resource directories from %s now...", flutterProjectRootDir), indicatorType)

                FlrChecker.checkPubspecFileIsExisted(reporter, flutterProjectRootDir)

                val pubspecFilePath = FlrFileUtil.getPubspecFilePath(flutterProjectRootDir)
                val pubspecFile = File(pubspecFilePath)
                val pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(reporter, pubspecFile)!!

                FlrChecker.checkFlrConfigIsExisted(reporter, pubspecConfig)
                val flrConfig = pubspecConfig[PubspecKeys.FLR] as Map<String, Any?>

                val resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(reporter, flrConfig, flutterProjectRootDir)

                legalResourceDirArray.addAll(resourceDirResultTuple[0])
                legalResourceDirArray.addAll(resourceDirResultTuple[1])
                illegalResourceDirArray.addAll(resourceDirResultTuple[2])

                reporter.log(String.format("get the valid resource directories from %s done !!!", flutterProjectRootDir), indicatorType)
                reporter.log("--------------------------------------------------------------------------------------", indicatorType)
            } catch (e: FlrException) {
                reporter.failure(flrExceptionTitle, e.message)

                reporter.log("", FlrLogConsole.LogType.error)
                reporter.log(String.format("[x]: %s has no valid resource directories.", flutterProjectRootDir), FlrLogConsole.LogType.error)
                reporter.log("--------------------------------------------------------------------------------------", indicatorType)
            }
        }

        reporter.log("", indicatorType)
        reporter.log("get the valid resource directories of all projects done !!!", indicatorType)

        if (legalResourceDirArray.size == 0) {
            reporter.failure(flrExceptionTitle, FlrException.ILLEGAL_ENV.message)

            reporter.log("", indicatorType)
            reporter.log("[x]: have no valid resource directories to be monitored", FlrLogConsole.LogType.error)
            reporter.log(flrExceptionTitle, FlrLogConsole.LogType.error)
            return false
        }

        // 若已有监听器在运行，先停止
        if (listener != null) {
            stop(reporter)
        }

        // ----- Step-3：启动前先 generate 一次 -----
        reporter.log("", indicatorType)
        reporter.log("now generate for all projects once before launching the monitoring service ...", indicatorType)
        reporter.log("", indicatorType)
        generateUseCase.generateAll(actionEvent)
        reporter.log("", indicatorType)
        reporter.log("did generate for all projects, now is going to launching the monitoring service ...", indicatorType)

        // ----- Step-4：启动带防抖的文件监听器 -----
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val nowStr = df.format(Date())

        reporter.log("", indicatorType)
        reporter.log(String.format("--------------------------- %s ---------------------------", nowStr), indicatorType)
        reporter.log("launch a monitoring service now ...", indicatorType)
        reporter.log("launching ...", indicatorType)

        val callback = FlrListener.AssetChangesEventCallback {
            reporter.success("[!]: detect some asset changes !!!", "[*]: invoke Flr-Generate Action now ... ", false)

            val cbDf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val cbNowStr = cbDf.format(Date())
            reporter.log(String.format("--------------------------- %s ---------------------------", cbNowStr), indicatorType)
            reporter.log("detect some asset changes, run Flr-Generate Action now", indicatorType)
            reporter.log("generate for all projects now ...", indicatorType)

            reporter.log("", indicatorType)
            generateUseCase.generateAll(actionEvent)

            reporter.log("", indicatorType)
            reporter.log("generate for all projects done !!!", indicatorType)
            reporter.log("---------------------------------------------------------------------------------", indicatorType)
            reporter.log("", indicatorType)
            reporter.log(MONITORING_TIP, FlrLogConsole.LogType.tips)

            reporter.success("[!]: invoke Flr-Generate Action done !!!", "[*]: you can get the details from Flr ToolWindow", false)
        }

        val legalRelativeResourceDirArray = FlrFileUtil.convertToRelativeResourceDirs(flutterMainProjectRootDir, legalResourceDirArray)
        val newListener = FlrListener(project, legalRelativeResourceDirArray, callback)
        listener = newListener
        // 先注册到 Disposer（以 project 为父），再 startWatching：使监听器的 Alarm/连接以监听器为父，随其释放
        Disposer.register(project, newListener)
        newListener.startWatching()
        monitoring = true

        reporter.log("launch a monitoring service done !!!", indicatorType)
        reporter.log("the monitoring service is monitoring the following resource directory:", indicatorType)
        for (resourceDir in legalResourceDirArray) {
            reporter.log(String.format("  - %s", resourceDir), indicatorType)
        }

        if (illegalResourceDirArray.size > 0) {
            reporter.log("", indicatorType)
            reporter.log("[!]: warning, found the following resource directory which is not existed: ", FlrLogConsole.LogType.warning)
            for (resourceDir in illegalResourceDirArray) {
                reporter.log(String.format("  - %s", resourceDir), FlrLogConsole.LogType.warning)
            }
        }
        reporter.log("---------------------------------------------------------------------------------", indicatorType)
        reporter.log("", indicatorType)
        reporter.log(MONITORING_TIP, FlrLogConsole.LogType.tips)

        reporter.success("[√]: start monitor done !!!", "[*]: you can get the details from Flr ToolWindow", false)

        return true
    }

    /** 停止资源变化监控服务 */
    fun stop(reporter: FlrReporter) {
        reporter.log("[Flr Stop Monitor]", FlrLogConsole.LogType.tips)

        listener?.let { Disposer.dispose(it) }
        listener = null
        monitoring = false

        reporter.log("[√]: terminate the monitoring service done !!!", FlrLogConsole.LogType.normal)
        reporter.success("[√]: stop monitor done !!!", "[*]: you can get the details from Flr ToolWindow", false)
    }

    override fun dispose() {
        listener?.let { Disposer.dispose(it) }
        listener = null
        monitoring = false
    }

    companion object {
        private const val MONITORING_TIP =
            "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n"
    }
}

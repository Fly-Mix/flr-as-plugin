package com.flr.command

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection

/**
 * 资源目录文件变化监听器（带防抖）。
 *
 * 生命周期：本监听器应由调用方经 Disposer 注册（如 `Disposer.register(project, listener)`），再调用 [startWatching]；
 * 其消息总线连接与防抖 [Alarm] 均以本监听器为父 disposable，随本监听器（停止监控或工程关闭时）级联释放。
 */
class FlrListener(
    private val curProject: Project,
    monitoredAssetDirs: List<String>,
    private val curAssetChangesEventCallback: AssetChangesEventCallback
) : BulkFileListener, Disposable {

    fun interface AssetChangesEventCallback {
        fun run()
    }

    private val curMonitoredAssetDirFullPaths = ArrayList<String>()

    private var connection: MessageBusConnection? = null
    private var alarm: Alarm? = null

    init {
        val flutterProjectRootDir = curProject.basePath
        for (assetDir in monitoredAssetDirs) {
            curMonitoredAssetDirFullPaths.add("$flutterProjectRootDir/$assetDir")
        }
    }

    /**
     * 启动监听。必须在 `Disposer.register(parent, this)` 之后调用，
     * 以便 Alarm 与消息总线连接以本监听器为父 disposable。
     */
    fun startWatching() {
        // POOLED_THREAD：回调在后台线程执行，不阻塞 EDT
        alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
        val conn = ApplicationManager.getApplication().messageBus.connect(this)
        conn.subscribe(VirtualFileManager.VFS_CHANGES, this)
        connection = conn
    }

    override fun before(events: MutableList<out VFileEvent>) {
    }

    override fun after(events: MutableList<out VFileEvent>) {
        var shouldCallback = false

        for (event in events) {
            // 用 event.path：始终可用（含删除事件），避免 event.file 可能为 null 而 NPE
            val fileFullPath = event.path

            for (monitoredAssetDirFullPath in curMonitoredAssetDirFullPaths) {
                // 精确匹配目录本身或其子路径，避免 ".../images_bak" 命中 ".../images"
                if (fileFullPath == monitoredAssetDirFullPath ||
                    fileFullPath.startsWith("$monitoredAssetDirFullPath/")
                ) {
                    shouldCallback = true
                    break
                }
            }

            if (shouldCallback) {
                break
            }
        }

        val currentAlarm = alarm
        if (shouldCallback && currentAlarm != null) {
            // 防抖：合并连续变更事件，延迟 DEBOUNCE_MS 后只触发一次回调
            currentAlarm.cancelAllRequests()
            currentAlarm.addRequest({ curAssetChangesEventCallback.run() }, DEBOUNCE_MS)
        }
    }

    override fun dispose() {
        // 连接与 Alarm 作为本监听器（Disposer 子节点）的子节点，随本监听器释放而自动释放
    }

    companion object {
        // 防抖延迟：合并该时间窗内的连续变更，只触发一次回调
        private const val DEBOUNCE_MS = 300
    }
}

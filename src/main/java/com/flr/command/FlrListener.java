package com.flr.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源目录文件变化监听器（带防抖）。
 *
 * 生命周期：本监听器应由调用方经 Disposer 注册（如 {@code Disposer.register(project, listener)}），
 * 再调用 {@link #startWatching()}；其消息总线连接与防抖 {@link Alarm} 均以本监听器为父 disposable，
 * 随本监听器（停止监控或工程关闭时）级联释放。
 */
public final class FlrListener implements BulkFileListener, Disposable {

    public interface AssetChangesEventCallback {
        void run();
    }

    // 防抖延迟：合并该时间窗内的连续变更，只触发一次回调
    private static final int DEBOUNCE_MS = 300;

    private final Project curProject;
    private final List<String> curMonitoredAssetDirFullPaths = new ArrayList<String>();
    private final AssetChangesEventCallback curAssetChangesEventCallback;

    private MessageBusConnection connection;
    private Alarm alarm;

    /*
     * @param project
     * @param monitoredAssetDirs 被监控的资源目录（相对路径）
     * @param assetChangesEventCallback 被监控的资源目录有资源变化更新后的回调操作
     * */
    public FlrListener(Project project, List<String> monitoredAssetDirs, AssetChangesEventCallback assetChangesEventCallback) {
        curProject = project;
        String flutterProjectRootDir = curProject.getBasePath();
        for (String assetDir : monitoredAssetDirs) {
            String assetDirFullPath = flutterProjectRootDir + "/" + assetDir;
            curMonitoredAssetDirFullPaths.add(assetDirFullPath);
        }
        curAssetChangesEventCallback = assetChangesEventCallback;
    }

    /**
     * 启动监听。必须在 {@code Disposer.register(parent, this)} 之后调用，
     * 以便 Alarm 与消息总线连接以本监听器为父 disposable。
     */
    public void startWatching() {
        // POOLED_THREAD：回调在后台线程执行，不阻塞 EDT
        alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        connection = ApplicationManager.getApplication().getMessageBus().connect(this);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {

    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        boolean shouldCallback = false;

        for (VFileEvent event : events) {
            // 用 event.getPath()：始终可用（含删除事件），避免 event.getFile() 可能为 null 而 NPE
            String fileFullPath = event.getPath();

            for (String monitoredAssetDirFullPath : curMonitoredAssetDirFullPaths) {
                // 精确匹配目录本身或其子路径，避免 ".../images_bak" 命中 ".../images"
                if (fileFullPath.equals(monitoredAssetDirFullPath)
                        || fileFullPath.startsWith(monitoredAssetDirFullPath + "/")) {
                    shouldCallback = true;
                    break;
                }
            }

            if (shouldCallback) {
                break;
            }
        }

        if (shouldCallback && alarm != null) {
            // 防抖：合并连续变更事件，延迟 DEBOUNCE_MS 后只触发一次回调
            alarm.cancelAllRequests();
            alarm.addRequest(curAssetChangesEventCallback::run, DEBOUNCE_MS);
        }
    }

    @Override
    public void dispose() {
        // 连接与 Alarm 作为本监听器（Disposer 子节点）的子节点，随本监听器释放而自动释放
    }
}

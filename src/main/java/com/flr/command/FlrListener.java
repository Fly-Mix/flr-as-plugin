package com.flr.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FlrListener implements BulkFileListener, Disposable {

    public interface AssetChangesEventCallback {
        void run();
    }

    private MessageBusConnection connection;
    private final Project curProject;
    private List<String> curMonitoredAssetDirFullPaths = new ArrayList<String>();
    private AssetChangesEventCallback curAssetChangesEventCallback;

    /*
    * 生成资源文件监控者
    *
    * @param project
    * @param monitoredAssetDirs 被监控的资源目录（相对路径）
    * @param assetChangesEventCallback 被监控的资源目录有资源变化更新后的回调操作
    * @return com.fly_mix.flr.FlrListener
    * */
    public FlrListener(Project project, List<String> monitoredAssetDirs, AssetChangesEventCallback assetChangesEventCallback) {
        curProject = project;
        String flutterProjectRootDir = curProject.getBasePath();
        for(String assetDir: monitoredAssetDirs) {
            String assetDirFullPath = flutterProjectRootDir + "/" + assetDir;
            curMonitoredAssetDirFullPaths.add(assetDirFullPath);
        }
        curAssetChangesEventCallback = assetChangesEventCallback;
        connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    @Override
    public void dispose() {
        connection.dispose();
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

        if (shouldCallback) {
            curAssetChangesEventCallback.run();
        }
    }
}


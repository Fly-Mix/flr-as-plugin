package com.flr.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
        Boolean shouldCallback = false;

        System.out.println("\nFlr events"+ events);

        for (VFileEvent event: events) {
            VirtualFile eventFile = event.getFile();
            String fileFullPath = eventFile.getPath();

            System.out.println("\nVFileEvent"+ "\ngetPath:"+event.getPath()+ "\ngetFile:"+event.getFile());

            for(String monitoredAssetDirFullPath: curMonitoredAssetDirFullPaths) {
                if(fileFullPath.contains(monitoredAssetDirFullPath)) {
                    shouldCallback = true;
                    break;
                }
            }

            if(shouldCallback) {
                break;
            }
        }

        if(shouldCallback) {
            curAssetChangesEventCallback.run();
        }
    }
}


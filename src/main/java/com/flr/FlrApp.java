package com.flr;

import com.flr.command.FlrCommand;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 工程级服务：持有该工程的 {@link FlrCommand}。
 *
 * 作为 {@link Service.Level#PROJECT} 轻量服务，平台按工程各创建一个实例并管理其生命周期；
 * 实现 {@link Disposable}，工程关闭时由平台调用 {@link #dispose()} 级联释放 FlrCommand（及其监控监听器）。
 *
 * 注意：不要再手写 static 单例——那会让多个打开的工程共享同一实例而互相串号。
 */
@Service(Service.Level.PROJECT)
public final class FlrApp implements Disposable {

    private final FlrCommand flrCommand;

    public FlrApp(@NotNull Project project) {
        flrCommand = new FlrCommand(project);
    }

    public static FlrApp getInstance(@NotNull Project project) {
        return project.getService(FlrApp.class);
    }

    @Override
    public void dispose() {
        flrCommand.dispose();
    }

    public FlrCommand getFlrCommand() {
        return flrCommand;
    }
}

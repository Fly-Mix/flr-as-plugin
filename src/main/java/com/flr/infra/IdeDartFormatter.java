package com.flr.infra;

import com.flr.command.util.FlrUtil;
import com.flr.core.format.DartFormatter;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * {@link DartFormatter} 的 IDE 实现：委托现有 {@link FlrUtil#formatDartFile} 调用 DartStyleAction。
 */
public final class IdeDartFormatter implements DartFormatter {

    private final Project project;

    public IdeDartFormatter(Project project) {
        this.project = project;
    }

    @Override
    public void format(File dartFile) {
        FlrUtil.formatDartFile(project, dartFile);
    }
}

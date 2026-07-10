package com.flr.infra

import com.flr.command.util.FlrUtil
import com.flr.core.format.DartFormatter
import com.intellij.openapi.project.Project
import java.io.File

/**
 * [DartFormatter] 的 IDE 实现：委托现有 [FlrUtil.formatDartFile] 调用 DartStyleAction。
 */
class IdeDartFormatter(private val project: Project) : DartFormatter {

    override fun format(dartFile: File) {
        FlrUtil.formatDartFile(project, dartFile)
    }
}

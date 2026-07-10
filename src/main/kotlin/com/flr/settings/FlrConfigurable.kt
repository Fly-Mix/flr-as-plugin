package com.flr.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

/**
 * Flr 设置页（Settings→Tools→Flr）：用 UI DSL v2 表单查看/编辑当前工程 pubspec.yaml 的 flr 配置。
 *
 * 读写走 [FlrConfigIO]；表单用共享的 [flrConfigRows]。`model` 为在内存中被 DSL panel 双向同步的中间态：
 * - [reset]：先从 pubspec 重载入 model，再 `panel.reset()`（model→UI）
 * - [apply]：`panel.apply()`（UI→model）后 [FlrConfigIO.save] 落盘
 * - isModified 由 DSL panel 自动跟踪（含列表 cell 的 onIsModified）
 */
class FlrConfigurable(private val project: Project) :
    BoundSearchableConfigurable("Flr", "com.flr.settings.FlrConfigurable", "com.flr.settings.FlrConfigurable") {

    private val loaded: FlrConfig? = FlrConfigIO.load(project)
    private val hasPubspec: Boolean = loaded != null
    private val model: FlrConfig = loaded ?: FlrConfig()

    override fun createPanel(): DialogPanel {
        return panel {
            if (!hasPubspec) {
                row {
                    label("当前工程没有可读的 pubspec.yaml，无法编辑 flr 配置。")
                }
            } else {
                flrConfigRows(project, model)
            }
        }
    }

    override fun apply() {
        super.apply()
        if (hasPubspec) {
            FlrConfigIO.save(project, model)
        }
    }

    override fun reset() {
        if (hasPubspec) {
            val fresh = FlrConfigIO.load(project)
            if (fresh != null) {
                model.assetDirs.clear()
                model.assetDirs.addAll(fresh.assetDirs)
                model.fontDirs.clear()
                model.fontDirs.addAll(fresh.fontDirs)
                model.dartfmtLineLength = fresh.dartfmtLineLength
                model.coreVersion = fresh.coreVersion
            }
        }
        super.reset()
    }
}

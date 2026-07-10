package com.flr

/**
 * Flr 全局常量。
 *
 * `const val`（字符串/整型）与 `@JvmField val`（List）均编译为静态字段，Java 端以
 * `FlrConstant.PLUGIN_ID` / `FlrConstant.IMAGE_FILE_TYPES` 原样访问（无 `.INSTANCE`）。
 */
object FlrConstant {

    /** 插件 ID */
    const val PLUGIN_ID = "com.fly-mix.flr"

    /** Flr 的核心逻辑版本 */
    const val CORE_VERSION = "3.2.0"

    /** 插件对外展示的名称 */
    const val PLUGIN_DISPLAY_NAME = "Flr"

    /** Flr 支持的非 SVG 类图片文件类型 */
    @JvmField
    val NON_SVG_IMAGE_FILE_TYPES: List<String> =
        listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".icon", ".bmp", ".wbmp")

    /** Flr 支持的 SVG 类图片文件类型 */
    @JvmField
    val SVG_IMAGE_FILE_TYPES: List<String> = listOf(".svg")

    /** Flr 支持的图片文件类型（非 SVG + SVG，顺序与原实现一致） */
    @JvmField
    val IMAGE_FILE_TYPES: List<String> = NON_SVG_IMAGE_FILE_TYPES + SVG_IMAGE_FILE_TYPES

    /** Flr 支持的文本文件类型 */
    @JvmField
    val TEXT_FILE_TYPES: List<String> = listOf(".txt", ".json", ".yaml", ".xml")

    /** Flr 支持的字体文件类型 */
    @JvmField
    val FONT_FILE_TYPES: List<String> = listOf(".ttf", ".otf", ".ttc")

    /** Flr 优先考虑的非 SVG 类图片文件类型 */
    const val PRIOR_NON_SVG_IMAGE_FILE_TYPE = ".png"

    /** Flr 优先考虑的 SVG 类图片文件类型 */
    const val PRIOR_SVG_IMAGE_FILE_TYPE = ".svg"

    /** Flr 优先考虑的文本文件类型；".*" 意味所有文本文件类型优先级相同 */
    const val PRIOR_TEXT_FILE_TYPE = ".*"

    /** Flr 优先考虑的字体文件类型；".*" 意味所有字体文件类型优先级相同 */
    const val PRIOR_FONT_FILE_TYPE = ".*"

    /** dartfmt 工具的默认行长（仅用于 flr-cli） */
    const val DARTFMT_LINE_LENGTH = 80
}

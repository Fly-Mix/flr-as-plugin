package com.flr.core.model;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Map;

/**
 * 资源扫描结果领域模型，取代原 generateOne 中散落的多个局部数组与 {@code List<List<...>>} 元组。
 *
 * 各字段语义与原 generateOne 的同名局部变量一致（均已按原算法去重/排序）：
 * - {@link #imageAssets}：合并去重并排序后的全部图片资产（non-svg + svg）
 * - {@link #nonSvgImageAssets} / {@link #svgImageAssets}：按 SVG 分类并排序后的图片资产
 * - {@link #textAssets}：去重排序后的文本资产
 * - {@link #fontFamilyConfigs}：排序后的字体家族配置（{@code List<Map>}，每项含 family / fonts）
 * - {@link #illegalResourceFiles}：非法命名的资源文件（image + text + font 合并）
 */
public final class ResourceScanResult {

    private final List<String> imageAssets;
    private final List<String> nonSvgImageAssets;
    private final List<String> svgImageAssets;
    private final List<String> textAssets;
    private final List<Map> fontFamilyConfigs;
    private final List<VirtualFile> illegalResourceFiles;

    public ResourceScanResult(List<String> imageAssets,
                              List<String> nonSvgImageAssets,
                              List<String> svgImageAssets,
                              List<String> textAssets,
                              List<Map> fontFamilyConfigs,
                              List<VirtualFile> illegalResourceFiles) {
        this.imageAssets = imageAssets;
        this.nonSvgImageAssets = nonSvgImageAssets;
        this.svgImageAssets = svgImageAssets;
        this.textAssets = textAssets;
        this.fontFamilyConfigs = fontFamilyConfigs;
        this.illegalResourceFiles = illegalResourceFiles;
    }

    public List<String> getImageAssets() {
        return imageAssets;
    }

    public List<String> getNonSvgImageAssets() {
        return nonSvgImageAssets;
    }

    public List<String> getSvgImageAssets() {
        return svgImageAssets;
    }

    public List<String> getTextAssets() {
        return textAssets;
    }

    public List<Map> getFontFamilyConfigs() {
        return fontFamilyConfigs;
    }

    public List<VirtualFile> getIllegalResourceFiles() {
        return illegalResourceFiles;
    }
}

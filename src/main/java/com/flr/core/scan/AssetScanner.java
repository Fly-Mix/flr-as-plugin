package com.flr.core.scan;

import com.flr.PubspecKeys;
import com.flr.command.util.FlrAssetUtil;
import com.flr.command.util.FlrFileUtil;
import com.flr.core.model.ResourceScanResult;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源扫描器：扫描 assets/fonts 合法资源目录，产出 {@link ResourceScanResult}。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne 的 Step-4/5/6 及 Step-7 的非法资源文件合并，行为不变
 * （保留 HashSet 去重后 Collections.sort、Comparator 排序等一切细节）。仅使用 VFS，可在
 * BasePlatformTestCase 下运行。
 */
public final class AssetScanner {

    private AssetScanner() {
    }

    public static ResourceScanResult scan(@NotNull String flutterProjectRootDir,
                                          @NotNull String packageName,
                                          @NotNull List<String> assetsLegalResourceDirArray,
                                          @NotNull List<String> fontsLegalResourceDirArray) {
        // ----- Step-4：扫描图片资源，输出有序的 image/non_svg/svg 资产与非法图片文件 -----
        List<String> imageAssetArray = new ArrayList<String>();
        List<VirtualFile> illegalImageFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : assetsLegalResourceDirArray) {
            List<List<VirtualFile>> imageFileResultTuple = FlrFileUtil.findImageFiles(resourceDir);
            List<VirtualFile> legalImageFileSubArray = imageFileResultTuple.get(0);
            List<VirtualFile> illegalImageFileSubArray = imageFileResultTuple.get(1);

            illegalImageFileArray.addAll(illegalImageFileSubArray);

            List<String> imageAssetSubArray = FlrAssetUtil.generateImageAssets(flutterProjectRootDir, packageName, legalImageFileSubArray);
            imageAssetArray.addAll(imageAssetSubArray);
        }

        // uniq
        imageAssetArray = new ArrayList<String>(new HashSet<String>(imageAssetArray));
        // sort
        Collections.sort(imageAssetArray);

        List<String> nonSvgImageAssetArray = new ArrayList<String>();
        List<String> svgImageAssetArray = new ArrayList<String>();

        for (String asset : imageAssetArray) {
            File assetFile = new File(asset);
            if (FlrFileUtil.isSvgImageResourceFile(assetFile)) {
                svgImageAssetArray.add(asset);
            } else {
                nonSvgImageAssetArray.add(asset);
            }
        }

        Collections.sort(nonSvgImageAssetArray);
        Collections.sort(svgImageAssetArray);

        // ----- Step-5：扫描文本资源，输出去重排序的 text 资产与非法文本文件 -----
        List<String> textAssetArray = new ArrayList<String>();
        List<VirtualFile> illegalTextFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : assetsLegalResourceDirArray) {
            List<List<VirtualFile>> textFileResultTuple = FlrFileUtil.findTextFiles(resourceDir);
            List<VirtualFile> legalTextFileSubArray = textFileResultTuple.get(0);
            List<VirtualFile> illegalTextFileSubArray = textFileResultTuple.get(1);

            illegalTextFileArray.addAll(illegalTextFileSubArray);

            List<String> textAssetSubArray = FlrAssetUtil.generateTextAssets(flutterProjectRootDir, packageName, legalTextFileSubArray);
            textAssetArray.addAll(textAssetSubArray);
        }

        // uniq
        textAssetArray = new ArrayList<String>(new HashSet<String>(textAssetArray));
        // sort
        Collections.sort(textAssetArray);

        // ----- Step-6：扫描字体资源，输出排序的 font_family_config 与非法字体文件 -----
        List<Map> fontFamilyConfigArray = new ArrayList<Map>();
        List<VirtualFile> illegalFontFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : fontsLegalResourceDirArray) {
            List<VirtualFile> fontFamilyDirArray = FlrFileUtil.findTopChildDirs(resourceDir);

            for (VirtualFile fontFamilyDirFile : fontFamilyDirArray) {
                String fontFamilyName = fontFamilyDirFile.getName();

                List<List<VirtualFile>> fontFileResultTuple = FlrFileUtil.findFontFilesInFontFamilyDir(fontFamilyDirFile);
                List<VirtualFile> legalFontFileArray = fontFileResultTuple.get(0);
                List<VirtualFile> illegalFontFileSubArray = fontFileResultTuple.get(1);

                illegalFontFileArray.addAll(illegalFontFileSubArray);

                if (legalFontFileArray.size() <= 0) {
                    continue;
                }

                List<Map> fontAssetConfigArray = FlrAssetUtil.generateFontAssetConfigs(flutterProjectRootDir, packageName, legalFontFileArray);
                fontAssetConfigArray.sort(new Comparator<Map>() {
                    @Override
                    public int compare(Map o1, Map o2) {
                        String assetValue1 = (String) o1.get("asset");
                        String assetValue2 = (String) o2.get("asset");
                        return assetValue1.compareTo(assetValue2);
                    }
                });

                Map<String, Object> fontFamilyConfig = new LinkedHashMap<String, Object>();
                fontFamilyConfig.put("family", fontFamilyName);
                fontFamilyConfig.put(PubspecKeys.FONTS, fontAssetConfigArray);

                fontFamilyConfigArray.add(fontFamilyConfig);
            }
        }

        fontFamilyConfigArray.sort(new Comparator<Map>() {
            @Override
            public int compare(Map o1, Map o2) {
                String familyValue1 = (String) o1.get("family");
                String familyValue2 = (String) o2.get("family");
                return familyValue1.compareTo(familyValue2);
            }
        });

        // ----- Step-7（非法资源文件合并）：image + text + font -----
        List<VirtualFile> illegalResourceFileArray = new ArrayList<VirtualFile>();
        illegalResourceFileArray.addAll(illegalImageFileArray);
        illegalResourceFileArray.addAll(illegalTextFileArray);
        illegalResourceFileArray.addAll(illegalFontFileArray);

        return new ResourceScanResult(
                imageAssetArray,
                nonSvgImageAssetArray,
                svgImageAssetArray,
                textAssetArray,
                fontFamilyConfigArray,
                illegalResourceFileArray);
    }
}

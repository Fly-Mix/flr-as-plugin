package com.flr.core.scan

import com.flr.PubspecKeys
import com.flr.command.util.FlrAssetUtil
import com.flr.command.util.FlrFileUtil
import com.flr.core.model.ResourceScanResult
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * 资源扫描器：扫描 assets/fonts 合法资源目录，产出 [ResourceScanResult]。
 *
 * 逻辑整段迁移自原 FlrCommand.generateOne 的 Step-4/5/6 及 Step-7 的非法资源文件合并，行为不变
 * （保留 HashSet 去重后自然序排序、Comparator 排序等一切细节）。仅使用 VFS，可在 BasePlatformTestCase 下运行。
 */
object AssetScanner {

    @JvmStatic
    fun scan(
        flutterProjectRootDir: String,
        packageName: String,
        assetsLegalResourceDirArray: List<String>,
        fontsLegalResourceDirArray: List<String>
    ): ResourceScanResult {
        // ----- Step-4：扫描图片资源，输出有序的 image/non_svg/svg 资产与非法图片文件 -----
        var imageAssetArray = ArrayList<String>()
        val illegalImageFileArray = ArrayList<VirtualFile>()

        for (resourceDir in assetsLegalResourceDirArray) {
            val imageFileResultTuple = FlrFileUtil.findImageFiles(resourceDir)
            val legalImageFileSubArray = imageFileResultTuple[0]
            val illegalImageFileSubArray = imageFileResultTuple[1]

            illegalImageFileArray.addAll(illegalImageFileSubArray)

            val imageAssetSubArray = FlrAssetUtil.generateImageAssets(flutterProjectRootDir, packageName, legalImageFileSubArray)
            imageAssetArray.addAll(imageAssetSubArray)
        }

        // uniq
        imageAssetArray = ArrayList(HashSet(imageAssetArray))
        // sort
        imageAssetArray.sort()

        val nonSvgImageAssetArray = ArrayList<String>()
        val svgImageAssetArray = ArrayList<String>()

        for (asset in imageAssetArray) {
            val assetFile = File(asset)
            if (FlrFileUtil.isSvgImageResourceFile(assetFile)) {
                svgImageAssetArray.add(asset)
            } else {
                nonSvgImageAssetArray.add(asset)
            }
        }

        nonSvgImageAssetArray.sort()
        svgImageAssetArray.sort()

        // ----- Step-5：扫描文本资源，输出去重排序的 text 资产与非法文本文件 -----
        var textAssetArray = ArrayList<String>()
        val illegalTextFileArray = ArrayList<VirtualFile>()

        for (resourceDir in assetsLegalResourceDirArray) {
            val textFileResultTuple = FlrFileUtil.findTextFiles(resourceDir)
            val legalTextFileSubArray = textFileResultTuple[0]
            val illegalTextFileSubArray = textFileResultTuple[1]

            illegalTextFileArray.addAll(illegalTextFileSubArray)

            val textAssetSubArray = FlrAssetUtil.generateTextAssets(flutterProjectRootDir, packageName, legalTextFileSubArray)
            textAssetArray.addAll(textAssetSubArray)
        }

        // uniq
        textAssetArray = ArrayList(HashSet(textAssetArray))
        // sort
        textAssetArray.sort()

        // ----- Step-6：扫描字体资源，输出排序的 font_family_config 与非法字体文件 -----
        val fontFamilyConfigArray = ArrayList<Map<String, Any>>()
        val illegalFontFileArray = ArrayList<VirtualFile>()

        for (resourceDir in fontsLegalResourceDirArray) {
            val fontFamilyDirArray = FlrFileUtil.findTopChildDirs(resourceDir)

            for (fontFamilyDirFile in fontFamilyDirArray) {
                val fontFamilyName = fontFamilyDirFile.name

                val fontFileResultTuple = FlrFileUtil.findFontFilesInFontFamilyDir(fontFamilyDirFile)
                val legalFontFileArray = fontFileResultTuple[0]
                val illegalFontFileSubArray = fontFileResultTuple[1]

                illegalFontFileArray.addAll(illegalFontFileSubArray)

                if (legalFontFileArray.size <= 0) {
                    continue
                }

                // FlrAssetUtil.generateFontAssetConfigs（Java，原始 List<Map>）返回 String 键、Object 值的 map 列表
                @Suppress("UNCHECKED_CAST")
                val fontAssetConfigArray =
                    FlrAssetUtil.generateFontAssetConfigs(flutterProjectRootDir, packageName, legalFontFileArray)
                        as MutableList<MutableMap<String, Any>>
                fontAssetConfigArray.sortWith(Comparator { o1, o2 ->
                    val assetValue1 = o1["asset"] as String
                    val assetValue2 = o2["asset"] as String
                    assetValue1.compareTo(assetValue2)
                })

                val fontFamilyConfig = LinkedHashMap<String, Any>()
                fontFamilyConfig["family"] = fontFamilyName
                fontFamilyConfig[PubspecKeys.FONTS] = fontAssetConfigArray

                fontFamilyConfigArray.add(fontFamilyConfig)
            }
        }

        fontFamilyConfigArray.sortWith(Comparator { o1, o2 ->
            val familyValue1 = o1["family"] as String
            val familyValue2 = o2["family"] as String
            familyValue1.compareTo(familyValue2)
        })

        // ----- Step-7（非法资源文件合并）：image + text + font -----
        val illegalResourceFileArray = ArrayList<VirtualFile>()
        illegalResourceFileArray.addAll(illegalImageFileArray)
        illegalResourceFileArray.addAll(illegalTextFileArray)
        illegalResourceFileArray.addAll(illegalFontFileArray)

        return ResourceScanResult(
            imageAssetArray,
            nonSvgImageAssetArray,
            svgImageAssetArray,
            textAssetArray,
            fontFamilyConfigArray,
            illegalResourceFileArray
        )
    }
}

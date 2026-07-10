package com.flr.command.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.regex.Pattern

/**
 * 资产相关的工具类方法。逻辑与原 Java 版一致（含 mergeFlutterAssets 对入参的原地修改语义）。
 */
object FlrAssetUtil {

    /**
     * 判断当前的资源文件是不是资产变体（asset_variant）类型。
     * 倍率变体目录特征正则：`^((0\.[0-9]+)|([1-9]+[0-9]*(\.[0-9]+)?))[x]$`（如 0.5x/1.5x/2.0x/3x）。
     */
    @JvmStatic
    fun isAssetVariant(legalResourceFile: VirtualFile): Boolean {
        if (FlrFileUtil.isNonSvgImageResourceFile(legalResourceFile)) {
            val parentDirFile = legalResourceFile.parent
            val parentDirName = parentDirFile.name

            val ratioRegex = "^((0\\.[0-9]+)|([1-9]+[0-9]*(\\.[0-9]+)?))[x]\$"
            val pattern = Pattern.compile(ratioRegex)
            if (pattern.matcher(parentDirName).matches()) {
                return true
            }
        }
        return false
    }

    /** 判断当前资产是不是图片类资产 */
    @JvmStatic
    fun isImageAsset(asset: String): Boolean {
        val file = File(asset)
        return FlrFileUtil.isImageResourceFile(file)
    }

    /** 判断当前资产是不是 package 类资产 */
    @JvmStatic
    fun isPackageAsset(asset: String): Boolean {
        val packagePrefix = "packages/"
        return asset.startsWith(packagePrefix)
    }

    /** 判断当前资产是不是指定 package 的资产 */
    @JvmStatic
    fun isSpecifiedPackageAsset(packageName: String, asset: String): Boolean {
        val specifiedPackagePrefix = "packages/$packageName/"
        return asset.startsWith(specifiedPackagePrefix)
    }

    /**
     * 获取指定 flutter 工程的 asset 对应的主资源文件（主资源文件不一定存在，如图片资产可能只存在变体资源文件）。
     */
    @JvmStatic
    fun getMainResourceFile(flutterProjectDir: String, packageName: String, asset: String): File {
        return if (isSpecifiedPackageAsset(packageName, asset)) {
            val specifiedPackagePrefix = "packages/$packageName/"

            // asset: packages/flutter_r_demo/assets/images/test.png -> impliedRelativeResourceFile: lib/assets/images/test.png
            var impliedRelativeResourceFile = asset.replaceFirst(specifiedPackagePrefix, "")
            impliedRelativeResourceFile = "lib/$impliedRelativeResourceFile"

            val mainResourceFilePath = "$flutterProjectDir/$impliedRelativeResourceFile"
            File(mainResourceFilePath)
        } else {
            val mainResourceFilePath = "$flutterProjectDir/$asset"
            File(mainResourceFilePath)
        }
    }

    /**
     * 判断指定 flutter 工程的 asset 是不是存在；存在的标准是：asset 需要存在对应的资源文件。
     */
    @JvmStatic
    fun isAssetExisted(flutterProjectDir: String, packageName: String, asset: String): Boolean {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<Boolean, RuntimeException> {
            val mainResourceFile = getMainResourceFile(flutterProjectDir, packageName, asset)
            if (FlrFileUtil.isNonSvgImageResourceFile(mainResourceFile)) {
                if (mainResourceFile.exists()) {
                    return@compute true
                }

                val fileBaseName = mainResourceFile.name
                val fileDir = mainResourceFile.parent
                var didFindVariantResourceFile = false
                val resourceDirFile = File(fileDir)

                val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
                    ?: return@compute false
                val resourceDirChildren = resourceDirVirtualFile.children
                for (resourceDirChild in resourceDirChildren) {
                    if (resourceDirChild.isDirectory) {
                        val subResourceDirChildren = resourceDirChild.children
                        for (subResourceDirChild in subResourceDirChildren) {
                            if (subResourceDirChild.isDirectory) {
                                continue
                            }
                            if (!isAssetVariant(subResourceDirChild)) {
                                continue
                            }
                            val variantResourceFileBaseName = subResourceDirChild.name
                            if (fileBaseName == variantResourceFileBaseName) {
                                didFindVariantResourceFile = true
                            }
                        }
                    }
                }

                if (didFindVariantResourceFile) {
                    return@compute true
                }
            } else {
                if (mainResourceFile.exists()) {
                    return@compute true
                }
            }
            false
        }
    }

    /**
     * 为当前资源文件生成 main_asset。
     * 例：lib/assets/images/3.0x/test.png -> packages/<pkg>/assets/images/test.png（剥离倍率变体目录 + lib/ 前缀）。
     */
    @JvmStatic
    fun generateMainAsset(flutterProjectDir: String, packageName: String, legalResourceFile: VirtualFile): String {
        var mainResourceFile = legalResourceFile.path
        if (isAssetVariant(legalResourceFile)) {
            val fileBasename = legalResourceFile.name
            val parentDirVirtualFile = legalResourceFile.parent
            val mainResourceFileDirVirtualFile = parentDirVirtualFile.parent
            val mainResourceFileDir = mainResourceFileDirVirtualFile.path
            mainResourceFile = "$mainResourceFileDir/$fileBasename"
        }

        // mainResourceFile -> mainRelativeResourceFile（去掉工程根目录前缀）
        val mainRelativeResourceFile = mainResourceFile.replaceFirst("$flutterProjectDir/", "")

        // 前缀为 "lib/" 的是 impliedResourceFile：mainAsset = "packages/#{pkg}/#{assetName}"；否则 mainAsset = "#{assetName}"
        val libPrefix = "lib/"
        return if (mainRelativeResourceFile.startsWith(libPrefix)) {
            val assetName = mainRelativeResourceFile.replaceFirst(libPrefix, "")
            "packages/$packageName/$assetName"
        } else {
            mainRelativeResourceFile
        }
    }

    /** 遍历 legalImageFile 数组生成 imageAsset 数组 */
    @JvmStatic
    fun generateImageAssets(flutterProjectDir: String, packageName: String, legalImageFileArray: List<VirtualFile>): List<String> {
        val imageAssetSet = LinkedHashSet<String>()
        for (imageVirtualFile in legalImageFileArray) {
            imageAssetSet.add(generateMainAsset(flutterProjectDir, packageName, imageVirtualFile))
        }
        return ArrayList(imageAssetSet)
    }

    /** 遍历 legalTextFile 数组生成 textAsset 数组 */
    @JvmStatic
    fun generateTextAssets(flutterProjectDir: String, packageName: String, legalTextFileArray: List<VirtualFile>): List<String> {
        val textAssetSet = LinkedHashSet<String>()
        for (textVirtualFile in legalTextFileArray) {
            textAssetSet.add(generateMainAsset(flutterProjectDir, packageName, textVirtualFile))
        }
        return ArrayList(textAssetSet)
    }

    /**
     * 遍历 legalFontFile 数组生成 fontAssetConfig 数组。
     * fontAssetConfig = {"asset": "packages/<pkg>/assets/fonts/Amiri/Amiri-Regular.ttf"}
     */
    @JvmStatic
    fun generateFontAssetConfigs(
        flutterProjectDir: String,
        packageName: String,
        legalFontFileArray: List<VirtualFile>
    ): MutableList<MutableMap<String, String>> {
        val fontAssetConfigArray = ArrayList<MutableMap<String, String>>()
        for (fontVirtualFile in legalFontFileArray) {
            val fontAsset = generateMainAsset(flutterProjectDir, packageName, fontVirtualFile)
            val fontAssetConfig = LinkedHashMap<String, String>()
            fontAssetConfig["asset"] = fontAsset
            fontAssetConfigArray.add(fontAssetConfig)
        }
        return fontAssetConfigArray
    }

    /**
     * 合并新旧 2 个 asset 数组（保留原实现对入参的原地修改语义）：
     * - old - new = diff；筛选 diff 中合法的 asset（非本工程 package 资源，或存在对应资源文件）得 legalOld；
     * - legalOld 字典序排序后追加到 new，返回合并结果。
     */
    @JvmStatic
    fun mergeFlutterAssets(
        flutterProjectDir: String,
        packageName: String,
        newAssetArray: MutableList<String>,
        oldAssetArray: MutableList<String>
    ): List<String> {
        val legalOldAssetArray = ArrayList<String>()

        val diffAssetArray = oldAssetArray
        diffAssetArray.removeAll(newAssetArray)
        for (asset in diffAssetArray) {
            // 第三方 package 的资源：引用第三方 package 资源的推荐做法是通过其 R 类访问
            if (isPackageAsset(asset)) {
                if (!isSpecifiedPackageAsset(packageName, asset)) {
                    legalOldAssetArray.add(asset)
                    continue
                }
            }
            // 指定 flutter 工程的 asset：存在则合并
            if (isAssetExisted(flutterProjectDir, packageName, asset)) {
                legalOldAssetArray.add(asset)
            }
        }

        legalOldAssetArray.sort()
        val mergedAssetArray = newAssetArray
        mergedAssetArray.addAll(legalOldAssetArray)
        return mergedAssetArray
    }
}

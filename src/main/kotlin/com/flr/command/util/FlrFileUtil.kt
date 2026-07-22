package com.flr.command.util

import com.flr.FlrConstant
import com.flr.FlrException
import com.flr.FlrPaths
import com.flr.core.report.FlrReporter
import com.flr.logConsole.FlrLogConsole
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.StringWriter
import java.util.regex.Pattern

/**
 * 资源文件相关的工具类方法。逻辑与原 Java 版一致。
 */
object FlrFileUtil {

    private val LOG = Logger.getInstance(FlrFileUtil::class.java)

    /** 获取 flutter 主工程的所有子工程的根目录 */
    @JvmStatic
    fun getFlutterSubProjectRootDirs(flutterMainProjectRootDir: String): List<String> {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<List<String>, RuntimeException> {
            val flutterSubProjectRootDirArray = ArrayList<String>()

            val resourceDirFile = File(flutterMainProjectRootDir)
            val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
                ?: return@compute flutterSubProjectRootDirArray

            val pubspecFileName = FlrPaths.PUBSPEC_YAML
            val resourceDirChildren = resourceDirVirtualFile.children
            for (resourceDirChild in resourceDirChildren) {
                if (resourceDirChild.isDirectory) {
                    val subResourceDirChildren = resourceDirChild.children
                    for (subResourceDirChild in subResourceDirChildren) {
                        if (subResourceDirChild.isDirectory) {
                            continue
                        }
                        val fileBaseName = subResourceDirChild.name
                        if (fileBaseName == pubspecFileName) {
                            flutterSubProjectRootDirArray.add(subResourceDirChild.parent.path)
                            break
                        }
                    }
                }
            }
            flutterSubProjectRootDirArray
        }
    }

    /** 获取指定 flutter 工程的 pubspec.yaml 文件的路径 */
    @JvmStatic
    fun getPubspecFilePath(flutterProjectDir: String): String {
        return flutterProjectDir + "/" + FlrPaths.PUBSPEC_YAML
    }

    /**
     * 判断指定 flutter 工程的工程类型是不是 Package 工程类型。
     * 从 .metadata 读取 project_type；不存在 .metadata 时，判断 pubspec.yaml 是否存在 author 配置。
     */
    @JvmStatic
    fun isPackageProjectType(reporter: FlrReporter, flutterProjectDir: String): Boolean {
        val metadataFilePath = "$flutterProjectDir/.metadata"
        val metadataFile = File(metadataFilePath)

        if (metadataFile.exists()) {
            try {
                val yaml = Yaml()
                val inputStream = FileInputStream(metadataFile)
                val itr = yaml.loadAll(inputStream)
                var metadataConfig: Map<String, Any?>? = null
                for (obj in itr) {
                    if (obj is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        metadataConfig = obj as Map<String, Any?>
                        break
                    }
                }

                var projectType = metadataConfig?.get("project_type") as? String
                if (projectType == null) {
                    projectType = "unknown"
                }

                if (projectType == "package" || projectType == "plugin") {
                    return true
                }
            } catch (e: Exception) {
                LOG.warn(e)
                reporter.log(e.message ?: "", FlrLogConsole.LogType.normal)
                reporter.log("", FlrLogConsole.LogType.normal)
            }
        } else {
            val warningStr = "[!]: warning, metadata file is missed, flr can not make sure to get a right project type of this flutter project" +
                "\n" +
                "[!]: then flr maybe generate buggy r.g.dart"
            reporter.log(warningStr, FlrLogConsole.LogType.warning)
            val tipsStr = String.format(
                "[*]: to fix it, you can manually copy the metadata file of a flutter project with same project type to %s\n",
                metadataFilePath
            )
            reporter.log(tipsStr, FlrLogConsole.LogType.tips)

            val pubspecFilePath = getPubspecFilePath(flutterProjectDir)
            val pubspecFile = File(pubspecFilePath)
            try {
                val pubspecConfig = loadPubspecConfigFromFile(reporter, pubspecFile)
                if (pubspecConfig?.containsKey("author") == true) {
                    return true
                }
            } catch (e: FlrException) {
                LOG.warn(e)
                reporter.log(e.message ?: "", FlrLogConsole.LogType.normal)
                reporter.log("", FlrLogConsole.LogType.normal)
            }
        }

        return false
    }

    @JvmStatic
    fun getFileBasename(file: File): String {
        return file.name
    }

    @JvmStatic
    fun getFileBasenameWithoutExtension(file: File): String {
        val fileBasename = file.name
        val lastIndexOf = fileBasename.lastIndexOf(".")
        if (lastIndexOf == -1) {
            return fileBasename
        }
        return fileBasename.substring(0, lastIndexOf)
    }

    @JvmStatic
    fun getFileExtension(file: File): String {
        val fileBasename = file.name
        val lastIndexOf = fileBasename.lastIndexOf(".")
        if (lastIndexOf == -1) {
            return "" // empty extension
        }
        return fileBasename.substring(lastIndexOf)
    }

    /** 把 resourceDir 转换为 relativeResourceDir（去掉工程根目录前缀） */
    @JvmStatic
    fun convertToRelativeResourceDir(flutterProjectDir: String, resourceDir: String): String {
        return resourceDir.replaceFirst("$flutterProjectDir/", "")
    }

    /** 把 resourceDir 数组转换为 relativeResourceDir 数组 */
    @JvmStatic
    fun convertToRelativeResourceDirs(flutterProjectDir: String, resourceDirs: List<String>): List<String> {
        val relativeResourceDirArray = ArrayList<String>()
        for (resourceDir in resourceDirs) {
            relativeResourceDirArray.add(convertToRelativeResourceDir(flutterProjectDir, resourceDir))
        }
        return relativeResourceDirArray
    }

    /** 写文件（经 VFS，走 WriteCommandAction） */
    @JvmStatic
    @Throws(FlrException::class)
    fun writeContentToFile(project: Project, content: String, file: File) {
        writeTextViaVfs(project, file, content)
    }

    /**
     * 在 EDT + WriteCommandAction 内经 VFS 写入文本：写入走 VFS/Document 层，自动同步编辑器、可撤销、无需手动 refresh。
     *
     * 调用方在 Task.Backgroundable 后台线程（无写锁）执行，故用 invokeAndWait 切到 EDT 并保持同步语义
     * （返回即写完、失败即抛 FlrException）；写动作内的异常经 holder 回抛到调用线程。
     */
    @Throws(FlrException::class)
    private fun writeTextViaVfs(project: Project, file: File, content: String) {
        // 父目录先在后台线程做同步刷新（不持写锁，此处允许 sync refresh），确保 VFS 已感知磁盘现状
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.parentFile)

        val thrown = arrayOfNulls<FlrException>(1)
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val parent = VfsUtil.createDirectoryIfMissing(file.parentFile.path)
                    if (parent == null) {
                        thrown[0] = FlrException("can not create directory: " + file.parent)
                        return@runWriteCommandAction
                    }
                    var vFile = parent.findChild(file.name)
                    if (vFile == null) {
                        vFile = parent.createChildData(FlrFileUtil::class.java, file.name)
                    }
                    VfsUtil.saveText(vFile, content)
                } catch (e: IOException) {
                    LOG.warn(e)
                    thrown[0] = FlrException(e.message)
                }
            }
        }
        thrown[0]?.let { throw it }
    }

    /**
     * 读取 pubspec.yaml 到 pubspecConfig；读取成功返回字典对象，读取失败抛异常，无 Map 文档返回 null。
     */
    @JvmStatic
    @Throws(FlrException::class)
    fun loadPubspecConfigFromFile(reporter: FlrReporter, pubspecFile: File): MutableMap<String, Any>? {
        try {
            val yaml = Yaml()
            val inputStream = FileInputStream(pubspecFile)
            val itr = yaml.loadAll(inputStream)
            var pubspecConfig: MutableMap<String, Any>? = null
            for (obj in itr) {
                if (obj is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    pubspecConfig = obj as MutableMap<String, Any>
                    break
                }
            }
            return pubspecConfig
        } catch (e: Exception) {
            LOG.warn(e)
            reporter.log(e.message ?: "", FlrLogConsole.LogType.normal)
            reporter.log("", FlrLogConsole.LogType.normal)
            reporter.log("[x]: pubspec.yaml is damaged, maybe it has some syntax errors", FlrLogConsole.LogType.error)
            reporter.log(String.format("[*]: please correct the pubspec.yaml file at %s", pubspecFile), FlrLogConsole.LogType.tips)
            throw FlrException.ILLEGAL_ENV
        }
    }

    /**
     * 保存 pubspecConfig 到 pubspec.yaml。
     *
     * 优先用 [PubspecPsiWriter] 经 YAML PSI **定点写入受管键**（保留注释/格式/键序）；若 PSI 不可用则**回退**到
     * 全量 snakeyaml dump（旧行为，会丢注释）作兜底。两条路径都经 VFS/WriteCommandAction。
     */
    @JvmStatic
    fun dumpPubspecConfigToFile(project: Project, pubspecConfig: Map<String, Any>, pubspecFile: File) {
        try {
            // 优先：YAML PSI 定点写入（保留注释）
            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(pubspecFile)
            if (vFile != null && PubspecPsiWriter.applyManagedKeys(project, vFile, pubspecConfig)) {
                return
            }

            // 回退：全量 dump（旧行为）
            val dumperOptions = DumperOptions()
            dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            dumperOptions.indent = 2
            dumperOptions.indicatorIndent = 0
            val yaml = Yaml(dumperOptions)
            val writer = StringWriter()
            yaml.dump(pubspecConfig, writer)

            writeTextViaVfs(project, pubspecFile, writer.toString())
        } catch (e: Exception) {
            LOG.warn(e)
        }
    }

    /**
     * 判断当前资源文件是否合法：其 file_basename_no_extension 由字母、数字、其他合法字符
     * （'_', '+', '-', '.', '·', '!', '@', '&', '$', '￥'）组成。
     */
    @JvmStatic
    fun isLegalResourceFile(virtualFile: VirtualFile): Boolean {
        val fileBasenameWithoutExtension = virtualFile.nameWithoutExtension
        val pattern = Pattern.compile("^[a-zA-Z0-9_\\+\\-\\.·!@&\$￥]+\$")
        return pattern.matcher(fileBasenameWithoutExtension).matches()
    }

    @JvmStatic
    fun isLegalResourceFile(file: File): Boolean {
        val fileBasenameWithoutExtension = getFileBasenameWithoutExtension(file)
        val pattern = Pattern.compile("^[a-zA-Z0-9_\\+\\-\\.·!@&\$￥]+\$")
        return pattern.matcher(fileBasenameWithoutExtension).matches()
    }

    @JvmStatic
    fun isNonSvgImageResourceFile(virtualFile: VirtualFile): Boolean {
        val fullVirtualFileExtension = ".${virtualFile.extension}".lowercase()
        return FlrConstant.NON_SVG_IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)
    }

    @JvmStatic
    fun isNonSvgImageResourceFile(file: File): Boolean {
        val fileExtName = getFileExtension(file).lowercase()
        return FlrConstant.NON_SVG_IMAGE_FILE_TYPES.contains(fileExtName)
    }

    @JvmStatic
    fun isSvgImageResourceFile(virtualFile: VirtualFile): Boolean {
        val fullVirtualFileExtension = ".${virtualFile.extension}".lowercase()
        return FlrConstant.SVG_IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)
    }

    @JvmStatic
    fun isSvgImageResourceFile(file: File): Boolean {
        val fileExtName = getFileExtension(file).lowercase()
        return FlrConstant.SVG_IMAGE_FILE_TYPES.contains(fileExtName)
    }

    @JvmStatic
    fun isImageResourceFile(virtualFile: VirtualFile): Boolean {
        val fullVirtualFileExtension = ".${virtualFile.extension}".lowercase()
        return FlrConstant.IMAGE_FILE_TYPES.contains(fullVirtualFileExtension)
    }

    @JvmStatic
    fun isImageResourceFile(file: File): Boolean {
        val fileExtName = getFileExtension(file).lowercase()
        return FlrConstant.IMAGE_FILE_TYPES.contains(fileExtName)
    }

    @JvmStatic
    fun isTextResourceFile(virtualFile: VirtualFile): Boolean {
        val fullVirtualFileExtension = ".${virtualFile.extension}".lowercase()
        return FlrConstant.TEXT_FILE_TYPES.contains(fullVirtualFileExtension)
    }

    @JvmStatic
    fun isTextResourceFile(file: File): Boolean {
        val fileExtName = getFileExtension(file).lowercase()
        return FlrConstant.TEXT_FILE_TYPES.contains(fileExtName)
    }

    @JvmStatic
    fun isFontResourceFile(virtualFile: VirtualFile): Boolean {
        val fullVirtualFileExtension = ".${virtualFile.extension}".lowercase()
        return FlrConstant.FONT_FILE_TYPES.contains(fullVirtualFileExtension)
    }

    @JvmStatic
    fun isFontResourceFile(file: File): Boolean {
        val fileExtName = getFileExtension(file).lowercase()
        return FlrConstant.FONT_FILE_TYPES.contains(fileExtName)
    }

    /**
     * 扫描指定的资源目录和其所有层级的子目录，查找所有图片文件。
     * 返回 [legalImageFileArray, illegalImageFileArray]。
     */
    @JvmStatic
    fun findImageFiles(resourceDir: String): List<List<VirtualFile>> {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<List<List<VirtualFile>>, RuntimeException> {
            val legalImageFileArray = ArrayList<VirtualFile>()
            val illegalImageFileArray = ArrayList<VirtualFile>()

            val resourceDirFile = File(resourceDir)
            val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
            VfsUtilCore.visitChildrenRecursively(resourceDirVirtualFile!!, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && isImageResourceFile(file)) {
                        if (isLegalResourceFile(file)) {
                            legalImageFileArray.add(file)
                        } else {
                            illegalImageFileArray.add(file)
                        }
                        return true
                    }
                    return super.visitFile(file)
                }
            })

            val imageFileResultTuple = ArrayList<List<VirtualFile>>()
            imageFileResultTuple.add(legalImageFileArray)
            imageFileResultTuple.add(illegalImageFileArray)
            imageFileResultTuple
        }
    }

    /**
     * 扫描指定的资源目录和其所有层级的子目录，查找所有文本文件。
     * 返回 [legalTextFileArray, illegalTextFileArray]。
     */
    @JvmStatic
    fun findTextFiles(resourceDir: String): List<List<VirtualFile>> {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<List<List<VirtualFile>>, RuntimeException> {
            val legalTextFileArray = ArrayList<VirtualFile>()
            val illegalTextFileArray = ArrayList<VirtualFile>()

            val resourceDirFile = File(resourceDir)
            val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
            VfsUtilCore.visitChildrenRecursively(resourceDirVirtualFile!!, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && isTextResourceFile(file)) {
                        if (isLegalResourceFile(file)) {
                            legalTextFileArray.add(file)
                        } else {
                            illegalTextFileArray.add(file)
                        }
                        return true
                    }
                    return super.visitFile(file)
                }
            })

            val textFileResultTuple = ArrayList<List<VirtualFile>>()
            textFileResultTuple.add(legalTextFileArray)
            textFileResultTuple.add(illegalTextFileArray)
            textFileResultTuple
        }
    }

    /** 扫描指定的资源目录，返回其所有第一级子目录 */
    @JvmStatic
    fun findTopChildDirs(resourceDir: String): List<VirtualFile> {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<List<VirtualFile>, RuntimeException> {
            val topChildDirArray = ArrayList<VirtualFile>()

            val resourceDirFile = File(resourceDir)
            val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
            if (resourceDirVirtualFile == null) {
                return@compute topChildDirArray
            }
            val resourceDirChildren = resourceDirVirtualFile.children
            for (resourceDirChild in resourceDirChildren) {
                if (resourceDirChild.isDirectory) {
                    topChildDirArray.add(resourceDirChild)
                }
            }
            topChildDirArray
        }
    }

    /**
     * 扫描 flr.assets 某类资源（images/rawfiles）下所有子目录，每个子目录代表一个资源实例。
     * 返回每个子目录的内部文件列表（[resourceDirVirtualFile, fileList] 组成的 pair 列表）。
     */
    @JvmStatic
    fun findFlrAssetsResourceDirs(resourceCategoryDir: String): List<Pair<VirtualFile, List<VirtualFile>>> {
        return ReadAction.compute<List<Pair<VirtualFile, List<VirtualFile>>>, RuntimeException> {
            val result = ArrayList<Pair<VirtualFile, List<VirtualFile>>>()

            val resourceDirFile = File(resourceCategoryDir)
            val resourceDirVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(resourceDirFile)
            if (resourceDirVirtualFile == null) {
                return@compute result
            }

            val subDirArray = resourceDirVirtualFile.children.filter { it.isDirectory }
            for (subDir in subDirArray) {
                val filesInSubDir = ArrayList<VirtualFile>()
                VfsUtilCore.visitChildrenRecursively(subDir, object : VirtualFileVisitor<Any>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (!file.isDirectory) {
                            filesInSubDir.add(file)
                        }
                        return true
                    }
                })
                if (filesInSubDir.isNotEmpty()) {
                    result.add(Pair(subDir, filesInSubDir))
                }
            }
            result
        }
    }

    /** 判断 flr.assets 目录是否存在（位于 lib/flr.assets/） */
    @JvmStatic
    fun isFlrAssetsDirExisted(flutterProjectRootDir: String): Boolean {
        val flrAssetsDir = File("$flutterProjectRootDir/lib/${FlrPaths.FLR_ASSETS}")
        return flrAssetsDir.isDirectory && flrAssetsDir.exists()
    }

    /**
     * 扫描指定的字体家族目录和其所有层级的子目录，查找所有字体文件。
     * 返回 [legalFontFileArray, illegalFontFileArray]。
     */
    @JvmStatic
    fun findFontFilesInFontFamilyDir(fontFamilyDirFile: VirtualFile): List<List<VirtualFile>> {
        // 后台线程读取 VFS 需持读锁
        return ReadAction.compute<List<List<VirtualFile>>, RuntimeException> {
            val legalFontFileArray = ArrayList<VirtualFile>()
            val illegalFontFileArray = ArrayList<VirtualFile>()

            VfsUtilCore.visitChildrenRecursively(fontFamilyDirFile, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && isFontResourceFile(file)) {
                        if (isLegalResourceFile(file)) {
                            legalFontFileArray.add(file)
                        } else {
                            illegalFontFileArray.add(file)
                        }
                        return true
                    }
                    return super.visitFile(file)
                }
            })

            val fontFileResultTuple = ArrayList<List<VirtualFile>>()
            fontFileResultTuple.add(legalFontFileArray)
            fontFileResultTuple.add(illegalFontFileArray)
            fontFileResultTuple
        }
    }
}

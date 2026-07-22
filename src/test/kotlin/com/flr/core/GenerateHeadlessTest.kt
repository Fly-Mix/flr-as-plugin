package com.flr.core

import com.flr.command.util.FlrAssetUtil
import com.flr.core.codegen.RDartGenerator
import com.flr.core.model.ResourceScanResult
import com.flr.core.scan.AssetScanner
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * generate 流水线的 headless 全流程基准回归测试（走真实 VFS 扫描）。
 *
 * 与纯 codegen 的 [GenerateGoldenTest] 不同，本测试真正跑 [AssetScanner] 扫描基准工程 flutter_r_demo 的磁盘资源，
 * 再断言两条验收标准；覆盖扫描器（Step-4~6）+ pubspec 合并这段 pure test 覆盖不到的逻辑。
 *
 * 需要平台测试框架（BasePlatformTestCase 启动 Application + VFS）。
 * 经三接缝解耦，本测试不依赖 Dart/Flutter 插件与真实 SDK：nullsafety 用固定值 true；格式化用外部 dart format 归一化比较。
 * 全程内存计算，绝不写入基准工程。运行参数：flr.demo.path（默认同级 flutter_r_demo）、flr.dart.path。缺失则跳过。
 */
class GenerateHeadlessTest : BasePlatformTestCase() {

    private val packageName = "flutter_r_demo"
    private val isPackageProjectType = false // 基准工程为 app 工程
    private val shouldSupportNullsafety = true
    private val normalizeLineLength = 100

    private fun demoPath(): String = System.getProperty("flr.demo.path", "")
    private fun dartPath(): String = System.getProperty("flr.dart.path", "")

    fun testGenerateReproducesGolden() {
        val rawDemoDir = File(demoPath())
        if (demoPath().isEmpty() || !rawDemoDir.isDirectory) {
            println("[skip] flr.demo.path 未提供或不存在，跳过 headless 全流程回归：" + demoPath())
            return
        }
        // 规范化去掉 ".."：VFS 文件路径是规范化的，flutterProjectDir 必须一致，否则资产前缀剥离失败导致绝对路径泄漏
        val demoDir = rawDemoDir.canonicalFile
        val root = demoDir.path

        // 从基准工程 flr 配置得到合法资源目录（相对 -> 绝对，过滤不存在）
        val pubspec = loadYaml(String(Files.readAllBytes(File(demoDir, "pubspec.yaml").toPath()), StandardCharsets.UTF_8))!!
        @Suppress("UNCHECKED_CAST")
        val flrConfig = pubspec["flr"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val assetsLegalDirs = toLegalDirs(root, flrConfig["assets"] as? List<String?>)
        @Suppress("UNCHECKED_CAST")
        val fontsLegalDirs = toLegalDirs(root, flrConfig["fonts"] as? List<String?>)

        // 刷新 VFS，使扫描器（LocalFileSystem/VfsUtilCore）能看到磁盘上的资源
        refreshVfs(assetsLegalDirs)
        refreshVfs(fontsLegalDirs)

        // 真实扫描（主扫描 + flr.assets 自动检测）
        var scan = AssetScanner.scan(root, packageName, assetsLegalDirs, fontsLegalDirs)
        val flrAssetsScan = AssetScanner.scanFlrAssets(root, packageName)
        if (flrAssetsScan != null) {
            scan = scan.merge(flrAssetsScan)
        }
        val rDart = RDartGenerator.generate(scan, packageName, isPackageProjectType, shouldSupportNullsafety)

        // ---- 标准 1：r.g.dart 归一化比较（脱离 formatter 版本）----
        val goldenRDart = gitShow(demoDir, "HEAD:lib/r.g.dart")
        if (dartPath().isEmpty() || !File(dartPath()).isFile) {
            println("[skip] flr.dart.path 未提供，跳过标准1（r.g.dart 归一化比较）：" + dartPath())
        } else {
            assertEquals(
                "标准1：扫描+生成的 r.g.dart 归一化后应与基线一致",
                dartFormat(goldenRDart), dartFormat(rDart)
            )
        }

        // ---- 标准 2：flutter 节点（assets + fonts）YAML 对象比较 ----
        val goldenPubspec = loadYaml(gitShow(demoDir, "HEAD:pubspec.yaml"))!!
        @Suppress("UNCHECKED_CAST")
        val goldenFlutter = goldenPubspec["flutter"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val goldenAssets = ArrayList(goldenFlutter["assets"] as List<String>)
        val goldenFonts = goldenFlutter["fonts"]

        val newAssets = ArrayList<String>()
        newAssets.addAll(scan.imageAssets)
        newAssets.addAll(scan.textAssets)
        // mergeFlutterAssets 会修改入参，故用副本
        val mergedAssets = FlrAssetUtil.mergeFlutterAssets(root, packageName, newAssets, ArrayList(goldenAssets))

        assertEquals("标准2：flutter.assets 应与基线一致", goldenAssets, mergedAssets)
        assertEquals("标准2：flutter.fonts 应与基线一致", goldenFonts, scan.fontFamilyConfigs)
    }

    // ---- helpers ----

    private fun loadYaml(content: String): Map<String, Any?>? {
        val yaml = Yaml()
        for (doc in yaml.loadAll(content)) {
            if (doc is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return doc as Map<String, Any?>
            }
        }
        return null
    }

    private fun toLegalDirs(root: String, relativeDirs: List<String?>?): List<String> {
        val legal = ArrayList<String>()
        if (relativeDirs == null) {
            return legal
        }
        for (rel in relativeDirs) {
            if (rel == null || rel.trim().isEmpty()) {
                continue
            }
            val dir = File("$root/$rel")
            if (dir.isDirectory) {
                legal.add(dir.path)
            }
        }
        return legal
    }

    private fun refreshVfs(dirs: List<String>) {
        for (dir in dirs) {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir))
            vf?.refresh(false, true) // 递归刷新
        }
    }

    private fun gitShow(repoDir: File, ref: String): String {
        val p = ProcessBuilder("git", "-C", repoDir.path, "show", ref).start()
        val out = readStream(p.inputStream)
        val code = p.waitFor()
        if (code != 0) {
            throw IllegalStateException("git show $ref 失败（code=$code）")
        }
        return out
    }

    private fun dartFormat(content: String): String {
        val tmp = File.createTempFile("flr_rg_", ".dart")
        try {
            Files.write(tmp.toPath(), content.toByteArray(StandardCharsets.UTF_8))
            val p = ProcessBuilder(
                dartPath(), "format", "--line-length", normalizeLineLength.toString(), tmp.path
            ).redirectErrorStream(true).start()
            val out = readStream(p.inputStream)
            val code = p.waitFor()
            if (code != 0) {
                throw IllegalStateException("dart format 失败（code=$code）：$out")
            }
            return String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8)
        } finally {
            tmp.delete()
        }
    }

    private fun readStream(input: InputStream): String {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n == -1) {
                break
            }
            bos.write(buf, 0, n)
        }
        return String(bos.toByteArray(), StandardCharsets.UTF_8)
    }
}

package com.flr.core

import com.flr.command.util.FlrFileUtil
import com.flr.core.codegen.RDartGenerator
import com.flr.core.model.ResourceScanResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * r.g.dart 代码生成的黄金回归测试（纯 JUnit，不启动 IDE 平台，绕开 VFS 与平台测试 bootstrap）。
 *
 * 思路：从基准工程 flutter_r_demo 的黄金 pubspec（git HEAD）取出已声明的 flutter.assets / fonts，
 * 按扩展名重建出与扫描器等价的 [ResourceScanResult]（分类用 FlrFileUtil 的纯 File 方法 + 升序排序），
 * 喂给 [RDartGenerator]，再与黄金 lib/r.g.dart 做归一化比较（同一 dart format 抹平 formatter 版本差异）。
 *
 * 运行参数（build.gradle 注入）：flr.demo.path（默认同级 flutter_r_demo）、flr.dart.path。缺失则跳过。
 */
class GenerateGoldenTest {

    private val packageName = "flutter_r_demo"
    private val isPackageProjectType = false // 基准工程为 app 工程
    private val shouldSupportNullsafety = true
    private val normalizeLineLength = 100

    private fun demoPath(): String = System.getProperty("flr.demo.path", "")
    private fun dartPath(): String = System.getProperty("flr.dart.path", "")

    @Test
    fun rDartMatchesGoldenAfterNormalization() {
        val demoDir = File(demoPath())
        if (demoPath().isEmpty() || !demoDir.isDirectory) {
            println("[skip] flr.demo.path 未提供或不存在，跳过黄金回归：" + demoPath())
            return
        }
        if (dartPath().isEmpty() || !File(dartPath()).isFile) {
            println("[skip] flr.dart.path 未提供，跳过黄金回归（需 dart 做归一化）：" + dartPath())
            return
        }

        // 从黄金 pubspec 取 flutter.assets / fonts
        val pubspec = loadYaml(gitShow(demoDir, "HEAD:pubspec.yaml"))!!
        @Suppress("UNCHECKED_CAST")
        val flutter = pubspec["flutter"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val assets = flutter["assets"] as List<String>
        @Suppress("UNCHECKED_CAST")
        val fontFamilyConfigs = flutter["fonts"] as List<Map<String, Any>>

        // 按扩展名重建 non_svg / svg / text 资产并升序排序（复现扫描器的分类与排序）
        val nonSvg = ArrayList<String>()
        val svg = ArrayList<String>()
        val text = ArrayList<String>()
        for (asset in assets) {
            val assetFile = File(asset)
            when {
                FlrFileUtil.isSvgImageResourceFile(assetFile) -> svg.add(asset)
                FlrFileUtil.isNonSvgImageResourceFile(assetFile) -> nonSvg.add(asset)
                FlrFileUtil.isTextResourceFile(assetFile) -> text.add(asset)
            }
        }
        nonSvg.sort()
        svg.sort()
        text.sort()
        val images = ArrayList(nonSvg)
        images.addAll(svg)
        images.sort()

        val scan = ResourceScanResult(images, nonSvg, svg, text, fontFamilyConfigs, emptyList())
        val rDart = RDartGenerator.generate(scan, packageName, isPackageProjectType, shouldSupportNullsafety)

        val goldenRDart = gitShow(demoDir, "HEAD:lib/r.g.dart")
        assertEquals(
            "r.g.dart 归一化后应与基线逐字节一致",
            dartFormat(goldenRDart), dartFormat(rDart)
        )
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

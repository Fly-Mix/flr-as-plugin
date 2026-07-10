package com.flr.command.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * [PubspecPsiWriter] 的 headless 单测：验证注释保留写入的核心不变量——
 * 注释/无关键/键序逐字保留、受管键正确更新、幂等、首次 init（flr 节点新建）。
 */
class PubspecPsiWriterTest : BasePlatformTestCase() {

    private val sampleWithComments = """
        name: flutter_r_demo
        description: A demo project.  # inline desc comment
        version: 1.0.0

        # --- dependencies section ---
        dependencies:
          flutter:
            sdk: flutter
          # our http client
          http: ^0.13.0

        dev_dependencies:
          flutter_test:
            sdk: flutter

        # flutter config below
        flutter:
          uses-material-design: true  # keep material design on
    """.trimIndent() + "\n"

    private fun initTarget(): Map<String, Any?> = mapOf(
        "flr" to linkedMapOf(
            "core_version" to "3.2.0",
            "dartfmt_line_length" to 80,
            "assets" to listOf("lib/assets/images"),
            "fonts" to listOf("lib/assets/fonts"),
        ),
        "flutter" to linkedMapOf(
            "assets" to listOf("packages/flutter_r_demo/assets/images/test.png"),
            "fonts" to listOf(
                linkedMapOf(
                    "family" to "Amiri",
                    "fonts" to listOf(linkedMapOf("asset" to "packages/flutter_r_demo/assets/fonts/Amiri/Amiri-Regular.ttf")),
                )
            ),
        ),
        "dependencies" to linkedMapOf("r_dart_library" to "0.4.0-nullsafety.0"),
    )

    fun testPreservesCommentsAndOtherKeysOnFirstInit() {
        val psiFile = myFixture.configureByText("pubspec.yaml", sampleWithComments)
        val vFile = psiFile.virtualFile

        val ok = PubspecPsiWriter.applyManagedKeys(project, vFile, initTarget())
        assertTrue("applyManagedKeys 应成功", ok)

        val text = myFixture.file.text

        // 注释逐字保留
        assertTrue("保留行内 desc 注释", text.contains("# inline desc comment"))
        assertTrue("保留 dependencies 区注释", text.contains("# --- dependencies section ---"))
        assertTrue("保留 http 依赖注释", text.contains("# our http client"))
        assertTrue("保留 flutter 区注释", text.contains("# flutter config below"))
        assertTrue("保留 material 行内注释", text.contains("# keep material design on"))

        // 无关键/其它依赖/其它 flutter 键保留
        assertTrue(text.contains("name: flutter_r_demo"))
        assertTrue(text.contains("version: 1.0.0"))
        assertTrue("保留其它依赖 http", text.contains("http: ^0.13.0"))
        assertTrue("保留 dev_dependencies", text.contains("dev_dependencies:"))
        assertTrue("保留 flutter 其它键", text.contains("uses-material-design: true"))

        // 受管键写入
        assertTrue("新建 flr 节点", text.contains("flr:"))
        assertTrue(text.contains("core_version:") && text.contains("3.2.0"))
        assertTrue(text.contains("dartfmt_line_length: 80"))
        assertTrue("flr.assets 写入", text.contains("lib/assets/images"))
        assertTrue("dependencies 增加 r_dart_library", text.contains("r_dart_library:") && text.contains("0.4.0-nullsafety.0"))
        assertTrue("flutter.assets 写入", text.contains("packages/flutter_r_demo/assets/images/test.png"))
        assertTrue("flutter.fonts 写入", text.contains("family: Amiri"))
    }

    fun testIdempotent() {
        val psiFile = myFixture.configureByText("pubspec.yaml", sampleWithComments)
        val vFile = psiFile.virtualFile

        assertTrue(PubspecPsiWriter.applyManagedKeys(project, vFile, initTarget()))
        val once = myFixture.file.text
        assertTrue(PubspecPsiWriter.applyManagedKeys(project, vFile, initTarget()))
        val twice = myFixture.file.text

        assertEquals("同一 target 重复写入内容应稳定（幂等）", once, twice)
    }

    fun testUpdatesExistingFlutterAssetsOnly() {
        // 已有 flutter.assets（含注释），更新后应替换 assets、保留其它键与注释
        val content = """
            name: demo
            dependencies:
              r_dart_library: 0.1.0
            flutter:
              uses-material-design: true
              # old assets below
              assets:
                - lib/old/a.png
        """.trimIndent() + "\n"
        val psiFile = myFixture.configureByText("pubspec.yaml", content)
        val vFile = psiFile.virtualFile

        val target = mapOf(
            "flutter" to linkedMapOf("assets" to listOf("lib/new/b.png")),
            "dependencies" to linkedMapOf("r_dart_library" to "0.4.0-nullsafety.0"),
        )
        assertTrue(PubspecPsiWriter.applyManagedKeys(project, vFile, target))
        val text = myFixture.file.text

        assertTrue("保留 flutter 其它键", text.contains("uses-material-design: true"))
        assertTrue("保留 assets 上方注释", text.contains("# old assets below"))
        assertTrue("assets 已更新为新值", text.contains("lib/new/b.png"))
        assertFalse("旧 assets 值应被替换", text.contains("lib/old/a.png"))
        assertTrue("r_dart_library 就地更新", text.contains("0.4.0-nullsafety.0"))
        assertFalse(text.contains("0.1.0"))
    }
}

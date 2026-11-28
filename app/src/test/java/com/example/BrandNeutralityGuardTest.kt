package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Brand Neutrality Guard Test.
 *
 * Prevents any school-specific name, emblem, location, personal name, or old package identifier
 * from sneaking back into the neutral "School Report Card" product.
 *
 * Edit [FORBIDDEN_TERMS] below whenever you want to extend or adjust the blocklist.
 */
class BrandNeutralityGuardTest {

    companion object {
        /**
         * Case-insensitive forbidden terms (label -> regex).
         * Short acronyms use word boundaries so unrelated English/code identifiers
         * (e.g. EXISTS_WITH, descriptions) do not trigger false positives.
         */
        val FORBIDDEN_TERMS: List<Pair<String, Regex>> = listOf(
            "C.P.S" to Regex("""\bc\.p\.s\.?(?:\b|(?=\s|[^a-zA-Z0-9]))""", RegexOption.IGNORE_CASE),
            "CPS" to Regex("""\bcps\b""", RegexOption.IGNORE_CASE),
            "C.P.I" to Regex("""\bc\.p\.i\.?(?:\b|(?=\s|[^a-zA-Z0-9]))""", RegexOption.IGNORE_CASE),
            "CPI" to Regex("""\bcpi\b""", RegexOption.IGNORE_CASE),
            "S.T.S" to Regex("""\bs\.t\.s\.?(?:\b|(?=\s|[^a-zA-Z0-9]))""", RegexOption.IGNORE_CASE),
            "STS" to Regex("""\bsts\b""", RegexOption.IGNORE_CASE),
            "Harikaranpur" to Regex("""harikaranpur""", RegexOption.IGNORE_CASE),
            "Bijaura" to Regex("""bijaura""", RegexOption.IGNORE_CASE),
            "Ghazipur" to Regex("""ghazipur""", RegexOption.IGNORE_CASE),
            "Vishawkarma" to Regex("""vishawkarma""", RegexOption.IGNORE_CASE),
            "cpireportcard" to Regex("""cpireportcard""", RegexOption.IGNORE_CASE),
            "ghzpkr" to Regex("""ghzpkr""", RegexOption.IGNORE_CASE)
        )

        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "ico",
            "pdf", "jks", "keystore", "jar", "class",
            "ttf", "otf", "woff", "woff2", "zip", "apk", "aab"
        )

        private val SELF_RELATIVE_PATH =
            "app/src/test/java/com/example/BrandNeutralityGuardTest.kt"
    }

    private fun resolveRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "app/build.gradle.kts").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not locate repository root from ${File(".").canonicalPath}")
    }

    private fun collectTextFiles(root: File, relativeTargets: List<String>): List<File> {
        val result = mutableListOf<File>()
        for (target in relativeTargets) {
            val f = File(root, target)
            if (!f.exists()) continue
            if (f.isFile) {
                result.add(f)
            } else {
                f.walkTopDown()
                    .filter { it.isFile }
                    .filter { it.extension.lowercase() !in BINARY_EXTENSIONS }
                    .forEach { result.add(it) }
            }
        }
        return result
    }

    @Test
    fun repositoryContainsNoForbiddenSchoolSpecificTerms() {
        val root = resolveRepoRoot()
        val filesToScan = collectTextFiles(
            root = root,
            relativeTargets = listOf(
                "app/src/main",
                "app/src/test",
                "app/src/androidTest",
                "app/build.gradle.kts",
                "public",
                "store_listing",
                "functions/src",
                "functions/test",
                "metadata.json",
                "README.md",
                "PLAY_STORE_STEPS.md",
                "PLAY_DATA_SAFETY.md",
                "firebase-blueprint.json",
                "firestore.test.js",
                "firestore.phase19.test.js",
                "firestore.transport.test.js",
                ".github/workflows"
            )
        )

        val violations = mutableListOf<String>()
        for (file in filesToScan) {
            val relPath = file.relativeTo(root).invariantSeparatorsPath
            if (relPath == SELF_RELATIVE_PATH) continue

            val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: continue
            lines.forEachIndexed { index, line ->
                for ((label, regex) in FORBIDDEN_TERMS) {
                    if (regex.containsMatchIn(line)) {
                        violations.add("$relPath:${index + 1} matched [$label] -> ${line.trim().take(120)}")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "Brand neutrality check failed! Remove school-specific terms from:\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun neutralBrandConfigurationAndAssetsAreInPlace() {
        val root = resolveRepoRoot()

        // 1. New neutral package name configured
        val appGradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(
            "app/build.gradle.kts must set applicationId = \"com.vivek.schoolreportcard\"",
            appGradle.contains("applicationId = \"com.vivek.schoolreportcard\"")
        )

        // 2. Neutral app name in strings.xml
        val stringsXml = File(root, "app/src/main/res/values/strings.xml").readText()
        assertTrue(
            "strings.xml must set app_name to School Report Card",
            stringsXml.contains("<string name=\"app_name\">School Report Card</string>")
        )

        // 3. Old private assets and school emblems are deleted
        assertFalse(
            "public/assets/aistudio must be deleted",
            File(root, "public/assets/aistudio").exists()
        )
        listOf(
            "app/src/main/res/drawable/img_school_emblem.png",
            "app/src/main/res/drawable/logo_cpi.png",
            "app/src/main/res/drawable/logo_cps.png",
            "app/src/main/res/drawable/ic_school_crest.xml"
        ).forEach { forbiddenAsset ->
            assertFalse("Removed emblem asset must not exist: $forbiddenAsset", File(root, forbiddenAsset).exists())
        }

        // 4. Adaptive icons include monochrome layer for Android 13+ themed icons
        listOf(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
        ).forEach { adaptiveXml ->
            val content = File(root, adaptiveXml).readText()
            assertTrue(
                "$adaptiveXml must declare a monochrome layer",
                content.contains("<monochrome") && content.contains("ic_launcher_monochrome")
            )
        }
    }
}

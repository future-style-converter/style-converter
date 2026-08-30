package com.styleconverter.runtime.core.renderer

// Wave-49 lane F2 — the SEAM REACHABILITY pin.
//
// WHY THIS EXISTS: skeptic S5 found that lane A2 shipped a new runtime module
// (content/PseudoBoxFold.kt) with NO call site, while the same lane's other
// change removed the bucket's old render — so the wave looked like a fix and
// silently dropped content. A `grep` for the symbol is not proof of the
// opposite: a reference that sits inside a file nothing else reaches is still
// dead. This test answers the real question — is the module reachable from the
// entry point the device gate actually drives? — by building a file-level
// reference graph over BOTH Kotlin trees and doing a BFS from the harness's
// capture screen.
//
// It is deliberately a SOURCE-level test: `runtimes/compose` unit tests are
// plain JVM JUnit4 (no Robolectric, no Compose UI test — see
// runtimes/compose/build.gradle.kts), so a @Composable entry cannot be
// executed here. Reading source to pin a structural invariant is the idiom
// this module already uses (columns/FragmentGeometryTest reads
// MultiColumnApplier.kt the same way).
//
// NEGATIVE CONTROL, EXECUTED (wave-49 F2): with ComponentRenderer.kt and
// ComponentHost.kt restored to their pre-seam bytes in the working tree and
// this class run alone, both assertions below FAIL —
//   `every module … has a live call site`        AssertionError (line 175)
//   `the four … seams are still spliced …`       AssertionError (line 204)
// naming content/PseudoBoxFold.kt and color/BackgroundColorInheritance.kt as
// the unreachable modules. Re-applying the seams turns both green. So the pin
// has teeth — it is not a tautology over a graph that reaches everything.

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamReachabilityTest {

    /**
     * The repo root, found by walking up from the JVM's working directory
     * until a file only this repository has is visible. Same walk-up pattern
     * as columns/FragmentGeometryTest / schema/SchemaConformanceTest, because
     * this module's Gradle build has rootDir = apps/android-harness (the
     * out-of-tree include) rather than the repo root.
     */
    private val repoRoot: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        requireNotNull(dir) { "repo root not found above ${System.getProperty("user.dir")}" }
    }

    /** The runtime library tree — the product code whose modules are audited. */
    private val runtimeTree =
        File(repoRoot, "runtimes/compose/src/main/java/com/styleconverter/runtime")

    /**
     * The harness tree. The BFS root lives HERE, not in the runtime: the
     * device gate captures through the harness's ScreenshotCaptureScreen, so
     * "reachable" must mean reachable from what the gate actually runs — and
     * some runtime modules (effects/clip/RootCanvasClip.kt) are called by the
     * harness canvas rather than by ComponentHost.
     */
    private val harnessTree =
        File(repoRoot, "apps/android-harness/app/src/main/java/com/styleconverter/test")

    /** Block comments — stripped so a module named only in prose never counts. */
    private val blockComment = Regex("""/\*[\s\S]*?\*/""")

    /** Line comments — the exact trap S5 hit: three of four PseudoBoxFold
     *  "references" were `//` lines. */
    private val lineComment = Regex("""//[^\n]*""")

    /** String literals (raw and quoted) — a module name inside a log message
     *  or an embedded JSON payload is not a call either. */
    private val stringLiteral = Regex(""""{3}[\s\S]*?"{3}|"(?:[^"\\\n]|\\.)*"""")

    /** Declared types: `object X` / `class X` / `interface X`. */
    private val typeDecl = Regex("""\b(?:object|class|interface)\s+([A-Z][A-Za-z0-9_]*)""")

    /**
     * TOP-LEVEL functions, including extension functions — required, because
     * a Kotlin file's public surface is often NOT its basename: for example
     * layout/position/PercentInsetPositioned.kt declares only the extension
     * `Modifier.percentInsetPositioned`, and effects/clip/RootCanvasClip.kt
     * only `rootCanvasClipShape`/`rootCanvasClipConfig`. Grepping the file
     * name would call both dead; they are not.
     */
    private val topLevelFun = Regex(
        """(?m)^(?:@\w+\s+)?(?:internal |public |private )?fun\s+(?:<[^>]*>\s*)?""" +
            """(?:[A-Za-z0-9_.<>?]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\("""
    )

    /** Top-level properties (CompositionLocals live here). */
    private val topLevelVal = Regex(
        """(?m)^(?:internal |public |private )?(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )

    /** Any identifier — the reference side of an edge. */
    private val identifier = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    /** Every .kt file of a tree, keyed by absolute path. */
    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Source with comments and string literals removed — see the three
     *  regexes above for why each removal is load-bearing. */
    private fun code(f: File): String =
        stringLiteral.replace(lineComment.replace(blockComment.replace(f.readText(), " "), " "), " ")

    /**
     * The set of runtime+harness files reachable from the capture screen by
     * following "file A names a symbol file B declares" edges.
     */
    private fun reachableFromCaptureScreen(): Set<File> {
        // The two trees the gate compiles together into one APK.
        val files = kotlinFiles(runtimeTree) + kotlinFiles(harnessTree)
        // Strip once — the graph build reads each file several times.
        val codeOf = files.associateWith { code(it) }
        // symbol → the files that DECLARE it. A symbol declared twice maps to
        // both owners; that over-connects rather than under-connects, so it
        // can only make this test more permissive, never falsely red.
        val owners = HashMap<String, MutableList<File>>()
        for (f in files) {
            val src = codeOf.getValue(f)
            val declared = typeDecl.findAll(src).map { it.groupValues[1] } +
                topLevelFun.findAll(src).map { it.groupValues[1] } +
                topLevelVal.findAll(src).map { it.groupValues[1] }
            for (s in declared) owners.getOrPut(s) { mutableListOf() }.add(f)
        }
        // Edges: every identifier a file mentions in CODE, resolved to owners.
        val edges = files.associateWith { f ->
            identifier.findAll(codeOf.getValue(f))
                .flatMap { owners[it.value].orEmpty() }
                .filter { it != f }
                .toSet()
        }
        // The BFS root: the harness screen the WPT capture run drives.
        val root = files.single { it.name == "ScreenshotCaptureScreen.kt" }
        val seen = linkedSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            for (next in edges.getValue(queue.removeFirst())) {
                if (seen.add(next)) queue.addLast(next)
            }
        }
        return seen
    }

    @Test
    fun `every module wave 49 added to the compose runtime has a live call site`() {
        val reachable = reachableFromCaptureScreen()
        // The wave-49 native modules, by their canonical mirror paths. Each
        // must be reachable from the capture entry or its predicted flip
        // cannot happen — which is exactly the A2 failure this pin closes.
        val modules = listOf(
            // A2 — the generated-BOX path (fold + its pure claim half).
            "content/PseudoBoxFold.kt",
            "content/PseudoGeneratedBox.kt",
            // A5 — currentcolor / background-color inheritance / color-mix.
            "color/CurrentColorBackground.kt",
            "color/BackgroundColorInheritance.kt",
            "color/StaticColorMix.kt",
            // A7 — the multicol spanner containing-block channel.
            "columns/MulticolSpannerContainingBlock.kt",
            // A3 — the css-images-4 §2.5 candidate walk.
            "images/ImageCandidateChain.kt",
            // A7 — percentage inset resolution against the containing block.
            "layout/position/PercentInsetResolve.kt",
            "layout/position/PercentInsetPositioned.kt",
            // A6 — the orthographic (flat) projection of a 3D rotation.
            "transforms/OrthographicFlatten.kt",
            // A4 — the root/document-element clip and its subtree scope.
            "effects/clip/ClipPathSubtreeScope.kt",
            "effects/clip/RootCanvasClip.kt",
        )
        val dead = modules.filterNot { File(runtimeTree, it) in reachable }
        assertTrue(
            "unreachable from ScreenshotCaptureScreen (no live call site): $dead", dead.isEmpty()
        )
    }

    @Test
    fun `a module named only in prose is not counted as referenced`() {
        // The teeth-check for the strip step, on the EXACT files that fooled a
        // grep: `grep -rn PseudoBoxFold runtimes/compose/src/main` returned
        // three hits outside the module itself, and all three were `//` prose.
        val extractorFile = File(runtimeTree, "content/PseudoBucketExtractor.kt")
        val boxFile = File(runtimeTree, "content/PseudoGeneratedBox.kt")
        // Raw text names the module — this is what a grep sees…
        assertTrue(extractorFile.readText().contains("PseudoBoxFold"))
        assertTrue(boxFile.readText().contains("PseudoBoxFold"))
        // …and after the strip neither file references it, so neither
        // contributes an edge. The only real edge is ComponentRenderer's.
        assertFalse(code(extractorFile).contains("PseudoBoxFold"))
        assertFalse(code(boxFile).contains("PseudoBoxFold"))
    }

    @Test
    fun `the four wave 49 composition seams are still spliced into the renderer`() {
        // Reachability alone would survive a call site moved into a helper
        // nothing composes, so pin the seams themselves at their entry points.
        val renderer = code(File(runtimeTree, "core/renderer/ComponentRenderer.kt"))
        val host = code(File(runtimeTree, "core/renderer/ComponentHost.kt"))
        // A2: the generated box is folded in at the renderer's single
        // per-component entry, right after `display: contents` unboxing.
        assertTrue(
            "PseudoBoxFold not called at the renderer entry",
            renderer.contains("PseudoBoxFold.resolve(") &&
                renderer.contains("ContentsUnboxing.resolve(component)")
        )
        // A5: `background-color: inherit` is resolved once at the root shim,
        // before ItemPlacementExtractor reads the prepared component.
        assertTrue(
            "BackgroundColorInheritance not called at the host shim",
            host.contains("BackgroundColorInheritance.resolve(component)")
        )
        // A7: BOTH halves of the multicol channel — the decision the
        // composition reads, and the stamp it publishes to its subtree. The
        // pure walks in CanvasRootHoist already called the helper before this
        // wave, so only these two lines prove the COMPOSITION half is live.
        assertTrue(
            "multicol spanner containing-block decision missing from composition",
            renderer.contains("MulticolSpannerContainingBlock.childPositionedAncestor(") &&
                renderer.contains("MulticolSpannerContainingBlock.childPositionedAtMulticol(")
        )
        assertTrue(
            "LocalPositionedAncestorAtMulticol never provided — the stamp cannot be read",
            renderer.contains("LocalPositionedAncestorAtMulticol provides")
        )
    }
}

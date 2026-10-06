package com.styleconverter.test.screenshot

// wave-52 lane L2 (T3) — SOURCE-SCAN pins that the composed canvas paints
// the RC1 static-position root ABOVE the in-flow roots of its stack.
//
// THE DEFECT. CSS 2.1 Appendix E step 8 paints positioned descendants with
// `z-index: auto` AFTER (above) in-flow content, but the composed Column
// mounted the RC1 slot (an absolute root with no inset, zeroFlowAnchor) in
// slot order, so a LATER in-flow root painted over it:
// css-flexbox/align-items-007's red `<img>` (root 2) covered the abspos green
// (root 1) — wave51-fix android f 0.9966, colour-vetoed (novel red 10 000 px).
// The fix wraps a LIFTED RC1 root's node in `Box(Modifier.zIndex(1f))`,
// which reorders the Column's DRAW without moving any slot (the Box wraps a
// 0×0 report). The decision itself (`composedRootsPaintingAboveFlow`: no
// declared z-index, a single painted box, no later step-8 root — the skeptic
// fix after the first cut lifted 11 tests' red `z-index: -1` boxes) is
// pinned on the verbatim IR in UaBlockMarginsTest u3/u10–u12; this file pins the wiring,
// which plain junit:4.13.2 cannot run (no Robolectric / Compose UI test —
// the same technique as ComposedCanvasIcbClipSourceTest).
//
// EXECUTED MUTATIONS (applied to ScreenshotCaptureScreen.kt, this class run
// alone, source restored byte-exact — sha256 checked; recorded in
// tools/titan/results/wave52-composed-canvas/mutations.log + _note.md):
//   ZO-M1 `Modifier.zIndex(1f)` → `Modifier` (the wrap kept, the order gone)
//         → rc1Root_isWrappedInAZIndexAboveTheFlow red.
//   ZO-M2 the decision fed `hostActive = false` (`composedRootsPaintingAboveFlow(roots, false)`)
//         → rc1Decision_readsTheSameHostActivationAsTheFold red.
//   ZO-M4 the wiring reverted to the first cut (`roots.map { root ->
//         isComposedStaticPositionRoot(root, hostActive) }`, every RC1 root
//         lifted) → rc1Decision_isTheLiftRuleNotTheBareRc1Flag red.
//   ZO-M3 the T6 map dropped from the composed-roots rewrite
//         → t6Rewrite_isWiredIntoTheComposedRoots red.
//
// The third pin rides here because it guards the SAME composed-roots
// rewrite the RC1 flag reads: wave-52 L2 T6 (a hoisted root's UA block
// margin, withUaBlockMarginOnHoistedRoot — pinned pure in UaBlockMarginsTest u9).

// Exact-count pins.
import org.junit.Assert.assertEquals
// Presence pins on the sliced source.
import org.junit.Assert.assertTrue
// JUnit 4's test marker — the only test API this module has.
import org.junit.Test
// The scanned source is read from disk, located by a repo-root walk-up.
import java.io.File

class ComposedCanvasRc1ZOrderSourceTest {
    // Repo root by walk-up (this build's rootDir is apps/android-harness).
    private val repoRoot: File by lazy {
        // The file whose presence proves a directory is the repo root.
        val anchor = "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
        // Start at the JVM's working directory and climb.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Stop at the first ancestor that contains the anchor.
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        // A missing root is a test-setup error, named loudly.
        requireNotNull(dir) { "repo root ($anchor) not found above ${System.getProperty("user.dir")}" }
    }

    // CODE LINES ONLY of the composed canvas (declaration → CompleteView):
    // `//` lines dropped so prose that mentions a call cannot satisfy a pin.
    private val composedCode: String by lazy {
        // The whole harness screen source.
        val source = File(repoRoot,
            "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt").readText()
        // Both anchors must exist or the slice would silently be the whole file.
        assertTrue("ComposedCaptureCanvas declaration missing", source.contains("private fun ComposedCaptureCanvas("))
        assertTrue("CompleteView declaration missing", source.contains("private fun CompleteView("))
        source.substringAfter("private fun ComposedCaptureCanvas(").substringBefore("private fun CompleteView(")
            .lines().filter { !it.trim().startsWith("//") }.joinToString("\n")
    }

    @Test
    fun rc1Root_isWrappedInAZIndexAboveTheFlow() {
        // Exactly one zIndex wrap in the composed canvas, on the RC1 branch.
        assertEquals(1, composedCode.split("Modifier.zIndex(1f)").size - 1)
        // The branch reads the per-root RC1 flag and wraps the in-flow node.
        val branch = composedCode.substringAfter("if (rootAboveFlow[i]) {").substringBefore("} else {")
        assertTrue("RC1 branch missing", composedCode.contains("if (rootAboveFlow[i]) {"))
        assertTrue("RC1 node not wrapped in the zIndex Box",
            branch.contains("Box(modifier = Modifier.zIndex(1f)) { inFlow() }"))
    }

    @Test
    fun rc1Decision_readsTheSameHostActivationAsTheFold() {
        // One decision, two consumers: the RC1 flag and the root-stack plan
        // both read the Host's own activation (CanvasRootHoist.hostActivates).
        assertTrue(composedCode.contains("CanvasRootHoist.hostActivates(roots)"))
        assertTrue(composedCode.contains("composedRootsPaintingAboveFlow(roots, hostActive)"))
        assertTrue(composedCode.contains("roots.map { root -> composedRootStackPlan(root, hostActive) }"))
    }

    @Test
    fun rc1Decision_isTheLiftRuleNotTheBareRc1Flag() {
        // The wrap's flag is the whole-list lift rule — never the bare RC1
        // predicate, which lifted `z-index: -1` boxes above the flow.
        assertTrue(composedCode.contains("val rootAboveFlow = androidx.compose.runtime.remember(roots) {"))
        assertEquals(1, composedCode.split("composedRootsPaintingAboveFlow(").size - 1)
        assertTrue("the bare RC1 flag feeds the wrap again",
            !composedCode.contains("isComposedStaticPositionRoot(root, hostActive)"))
    }
    @Test
    fun t6Rewrite_isWiredIntoTheComposedRoots() {
        // The roots every consumer below reads are the M1 one-owner strip
        // followed by the T6 UA-margin synthesis — one rewrite, one place.
        assertTrue(composedCode.contains(
            "withCanvasOwnedBodyMargin(roots, canvasMargin).map(::withUaBlockMarginOnHoistedRoot)"))
    }
}

package com.styleconverter.runtime.core.renderer

// Wave 51 (PR 2, BACKLOG queue 9(g)) — `align-items` on a NON-flex block Box.
//
// THE DEFECT (retro A11#8, measured on fixtures/fidelity/layout.combos.json):
// ComponentRenderer's block-flow fallback wrapped a component's content in
// `Box(contentAlignment = displayConfig.alignItems.toBoxAlignment())`, so a
// declared `align-items: end` placed a block's content bottom-RIGHT
// (Layout_C01_AlignContent, three-way-odd 0.9018) and `align-items: center`
// centred it (Layout_TextBlock, Android-odd 0.8911) — on a display type where
// css-align-3 ("Self-Alignment") gives `align-items` NO layout effect: it sets
// the default `align-self` of flex/grid ITEMS, and a block container's in-flow
// children are not items. Web and iOS ignore it there; Compose alone moved the
// content. The fix is the CSS default for block flow — `Alignment.TopStart`,
// unconditionally — and the now-callerless `AlignItems.toBoxAlignment()` is
// deleted rather than left to be re-used by mistake. The flex path keeps its
// own alignment mapping (layout/flexbox/FlexboxApplier.toBoxAlignment), which
// this pin deliberately does not touch.
//
// WHY A SOURCE SCAN: `runtimes/compose` unit tests are plain JVM JUnit4 (no
// Robolectric, no Compose UI test — runtimes/compose/build.gradle.kts), so the
// composable cannot be executed here; reading source to pin a structural
// invariant is this module's idiom (SeamReachabilityTest, columns/
// FragmentGeometryTest). The device measurement is the wave-51 PR-2 record in
// docs/BACKLOG.md 9(g).
//
// NEGATIVE CONTROL, EXECUTED (wave 51): with the block Box's
// `contentAlignment = Alignment.TopStart` mutated to `Alignment.Center`, the
// first assertion below FAILS; with the deleted `toBoxAlignment()` restored
// and called again, the second FAILS. Both green on the shipped tree.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockBoxAlignItemsTest {

    /** Repo root by walk-up (this module's Gradle rootDir is apps/android-harness). */
    private val repoRoot: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        requireNotNull(dir) { "repo root not found above ${System.getProperty("user.dir")}" }
    }

    private val renderer: String by lazy {
        File(repoRoot, "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt").readText()
    }

    /** The block-flow fallback Box, located by the RenderContent call it wraps. */
    private fun blockBoxCall(): String {
        // The ONLY `Box(` whose contentAlignment line is immediately followed by
        // the block-content render — the flex/grid paths use Row/Column/Grid.
        val re = Regex("""Box\(\s*modifier = modifier,\s*contentAlignment = ([^\n]+)\n\s*\) \{\s*RenderContent\(component, textColor, displayConfig\)""")
        val hits = re.findAll(renderer).toList()
        assertEquals("exactly one block-flow fallback Box wraps RenderContent", 1, hits.size)
        return hits.single().groupValues[1].trim()
    }

    @Test fun `the block-flow fallback Box aligns its content TopStart regardless of align-items`() {
        // CSS block flow: content starts at the top-left of the content box;
        // `align-items` is inert on a non-flex, non-grid container.
        assertEquals("Alignment.TopStart", blockBoxCall())
    }

    @Test fun `align-items no longer reaches a non-flex Box - the renderer's toBoxAlignment is gone`() {
        // The mapping that turned `align-items: end` into BottomEnd had one
        // caller; it is deleted, not merely bypassed, so nothing can re-wire it.
        assertFalse(renderer.contains("alignItems.toBoxAlignment()"))
        assertFalse(renderer.contains("fun AlignItems.toBoxAlignment()"))
        // The flex path's own mapping is a different function in a different
        // file and stays — this pin must not be read as banning it.
        val flexbox = File(repoRoot, "runtimes/compose/src/main/java/com/styleconverter/runtime/layout/flexbox/FlexboxApplier.kt").readText()
        assertTrue(flexbox.contains("fun toBoxAlignment("))
    }
}

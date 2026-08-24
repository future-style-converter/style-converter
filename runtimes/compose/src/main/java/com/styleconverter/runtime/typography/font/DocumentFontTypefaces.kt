package com.styleconverter.runtime.typography.font

// DocumentFontTypefaces — wave-47 lane Z4 (SEAM 1 of the monospace pin).
//
// [DocumentFontRegistry] hands Compose one FontFamily per registered CSS
// family, but Compose's `Font(File)` keeps its android.graphics.Typeface
// PRIVATE (`AndroidPreloadedFont.typeface` is internal to ui-text), so the
// spacing engine could never MEASURE the face it renders:
// spacing/ChUnitMetrics keyed `ch` on the five generic singletons and fell
// to Typeface.DEFAULT for every document family. MEASURED (wave-46 Y7
// pilot, css-text hyphens-manual-inline-010): a `width: 10ch` box at 32px
// resolved against Roboto's '0' (box 184px) while the label PAINTED the
// pinned DejaVu Sans Mono (frozen-ref box: 197px). This object closes that
// seam the way wave-46 skeptic S5 adjudicated: the registry — which knows
// the FILES — keeps a per-family measuring handle at register time, and
// ChUnitMetrics measures THE REGISTERED face, recovered from here rather
// than from the Compose Font it cannot see into.
//
// Split out of DocumentFontRegistry to hold the ≤200-line file target; the
// registry stays the SINGLE WRITER ([rebuild] is internal and called only
// from its register()/clear()), so the handles and the resolvable families
// can never drift apart.

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlin.math.abs

object DocumentFontTypefaces {

    /** One registered face of a family: the on-device FILE plus the §4.4/§4.5
     *  descriptors the registry parsed — everything the measuring-face pick
     *  below and a lazy Typeface load need. */
    data class Face(val file: File, val weight: FontWeight, val style: FontStyle)

    /** The measuring face per registered [FontFamily] — keyed by the exact
     *  instances [DocumentFontRegistry.resolve] answers, which are the
     *  instances TypographyExtractor stores in the style config and
     *  StyleApplier hands to ChUnitMetrics, so lookup needs no name plumbing.
     *  Volatile swap, same reader/writer stance as the registry's map. */
    @Volatile
    private var measuring: Map<FontFamily, Face> = emptyMap()

    /** Registration generation — bumped by every [rebuild] (register AND
     *  clear). ChUnitMetrics folds it into its memo key so an advance cached
     *  for document N can never answer for document N+1: the registry
     *  REPLACES its table wholesale per document, and two documents may bind
     *  the same family name to different files. */
    @Volatile
    var generation: Long = 0L
        private set

    /** Memoized Typeface per family for the CURRENT generation. Cleared on
     *  [rebuild]. null results are cached too: a file the platform cannot
     *  parse (or the JVM test environment, where android.graphics is an
     *  unmocked stub) must not retry the disk load on every recomposition. */
    private val typefaces = HashMap<FontFamily, android.graphics.Typeface?>()

    /** How a measuring FILE becomes a Typeface. A seam for the same measured
     *  reason as [DocumentFontRegistry.fontLoader]: Typeface.createFromFile
     *  is unmocked on the JVM, so with the call inlined no unit test could
     *  pin the memoization or the face pick. runCatching: a broken font
     *  answers null and ChUnitMetrics falls to the css-values-4 §6.1.3 0.5em
     *  fallback — never a crash over a metric. `internal` + restored by the
     *  test's tearDown, exactly like the registry's own loader seam. */
    internal var typefaceLoader: (File) -> android.graphics.Typeface? = { file ->
        runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()
    }

    /** Replace the handle table for a new document. INTERNAL: only the
     *  registry's register()/clear() call this, keeping one writer. */
    internal fun rebuild(families: Map<FontFamily, List<Face>>) {
        // Build the next table fully, then swap once — a reader mid-rebuild
        // sees a consistent OLD generation, mirroring the registry's own
        // wholesale volatile swap rather than a half-mutated map.
        val next = HashMap<FontFamily, Face>(families.size)
        for ((family, faces) in families) {
            // Defensive: the registry never emits an empty face list for a
            // built family, but a silent NPE here would take down a render.
            val face = measuringFaceOf(faces) ?: continue
            next[family] = face
        }
        measuring = next
        // Drop the previous document's memoized Typefaces with the handles.
        synchronized(typefaces) { typefaces.clear() }
        // Bump LAST: worst case mid-swap a reader pairs the NEW map with the
        // OLD number, which only costs one redundant re-measure — pairing an
        // old map with a new number could serve a stale advance forever.
        generation += 1
    }

    /** css-values-4 §6.1.3 measures '0' "in the element's font"; for a family
     *  declared by several faces the plain-text face is the family's
     *  canonical advance source (the face a weightless upright run renders
     *  in): normal style outranks italic (§4.5), then nearest-to-400 weight
     *  (§4.4's initial value — the pilot's Regular face wins over its Bold
     *  sibling whatever the wire order), then declaration order
     *  (minWithOrNull returns the FIRST minimum, so ties keep wire order). */
    private fun measuringFaceOf(faces: List<Face>): Face? =
        faces.minWithOrNull(compareBy(
            // Upright first: an italic '0' advance is not what a normal run
            // lays out with.
            { if (it.style == FontStyle.Normal) 0 else 1 },
            // Then the weight closest to the 400 initial.
            { abs(it.weight.weight - 400) },
        ))

    /** The measuring face for a registered family, or null when [family] is
     *  not a document family (the generics, bundled Inter, null). Pure map
     *  read — also the existence probe ChUnitMetrics keys its cache on. */
    fun measuringFace(family: FontFamily?): Face? =
        family?.let { measuring[it] }

    /** The android.graphics.Typeface of the family's measuring face, or null
     *  when the family is not registered or the platform cannot parse the
     *  file (JVM unit tests land on null by construction and keep the spec
     *  fallback deterministic). Memoized per generation: one createFromFile
     *  per family per document, not one per recomposition. */
    fun typefaceFor(family: FontFamily?): android.graphics.Typeface? {
        // Non-null family that carries a handle, else not ours to answer —
        // ChUnitMetrics keeps its generic/DEFAULT mapping for that case.
        val fam = family ?: return null
        val face = measuring[fam] ?: return null
        // Memo hit (including a cached null) — no second disk load.
        synchronized(typefaces) { if (typefaces.containsKey(fam)) return typefaces[fam] }
        // Load OUTSIDE the lock: createFromFile does real file I/O.
        val loaded = typefaceLoader(face.file)
        // Publish (last write wins; racing loads of one file are identical).
        synchronized(typefaces) { typefaces[fam] = loaded }
        return loaded
    }
}

package com.styleconverter.runtime.typography.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.styleconverter.runtime.core.ir.IRFontFace
import java.io.File

/**
 * DocumentFontRegistry — runtime registration of the document's `@font-face`
 * declarations (schema/spec/01-envelope.md §5; wave-35 lane B2).
 *
 * ## Why this exists, and why it is not a property triplet
 *
 * Everything under this runtime's category folders is a per-PROPERTY triplet:
 * one IR property in, one Compose modifier out. A font FACE is neither. It is
 * a document-level RESOURCE that must be registered with the text stack before
 * any element referencing it measures, and registering it means knowing where
 * the HOST put the file. So the registry is a document-scoped object the host
 * fills in (the harness's inbox path does it right after
 * `IRDocumentDecoder.decode`), and the per-property resolver
 * [com.styleconverter.runtime.typography.CssFontFamilyResolver] merely
 * CONSULTS it — exactly the split the web side uses between
 * `apps/web-harness/src/sdui/useFontFaces.ts` (mount) and the engine (read).
 *
 * ## What it closes
 *
 * Until wave 35 both natives DECODED `fontFaces` and ignored it: a test
 * declaring `@font-face { font-family: test; src: url(X.woff) }` plus
 * `body { font: 36px test }` shaped with the bundled Inter while the browser
 * ref painted the author's outlines. css-text/boundary-shaping-001…008 are the
 * measured case — they assert on "fi"/"ffi" LIGATURES that only the declared
 * LinLibertine face carries, so the assertion was unobservable on the native
 * side of the diff (wpt-not-applicable.mjs Rule 15's closing move, step 1).
 *
 * ## The file channel
 *
 * The wire carries a corpus-relative PATH, never a payload. The feeder
 * (tools/titan/feed-android.mjs) copies each referenced file into the app's
 * own sandbox at `<externalFilesDir>/fonts/<src>`, preserving the relative
 * path VERBATIM — so resolution here is a plain [File] join with no name
 * mangling and no escaping rule that could drift from the host's.
 *
 * ## Degradation is loud, never silent
 *
 * A face whose file is absent, unreadable or unparseable is DECLINED: the
 * family is simply not registered, [resolve] answers null, and the existing
 * resolver walk continues to the fallback exactly as it did in wave 34. Each
 * decline is logged at WARN and counted in [lastReport], because the failure
 * mode this guards against — rendering plausible text in the WRONG face — is
 * invisible in a screenshot.
 */
object DocumentFontRegistry {

    private const val TAG = "DocFontRegistry"

    /** Logging seam. `runCatching` guards `android.util.Log` for JVM callers —
     *  the module's unit suite runs without `returnDefaultValues`, so an
     *  unguarded Log call throws "not mocked" and would make this whole
     *  registry untestable off-device. Same guard InlineRunPlan uses. */
    private fun logw(message: String, e: Throwable? = null) {
        runCatching { android.util.Log.w(TAG, message, e) }
    }

    private fun logi(message: String) {
        runCatching { android.util.Log.i(TAG, message) }
    }

    /** How a readable file becomes a Compose [Font].
     *
     *  A SEAM, and it exists for one measured reason:
     *  `androidx.compose.ui.text.font.Font(File, …)` calls
     *  `android.graphics.Typeface.createFromFile` EAGERLY, and that method is
     *  not mocked in a JVM unit test — so with the call inlined, every
     *  registration in the unit suite fell into the decline branch and the
     *  grouping/matching rules below could not be pinned at all. Swapping the
     *  loader lets the suite exercise everything this file OWNS (family
     *  identity, §4.1 grouping, decline handling, the resolver hook) while the
     *  device gate measures the one thing it does not own — whether Android
     *  can rasterise the bytes.
     *
     *  `internal` + restored by the test's tearDown: production has exactly
     *  one implementation and no way to reach this from outside the module. */
    internal var fontLoader: (File, FontWeight, FontStyle) -> Font =
        { file, weight, style -> Font(file = file, weight = weight, style = style) }

    /** Font container formats `android.graphics.Typeface.createFromFile` can
     *  actually parse — and, critically, NOT the web-only ones.
     *
     *  MEASURED (wave-35 device gate, emulator API 36, the css-text
     *  boundary-shaping face `LinLibertine_Re-4.7.5.woff`): handing a WOFF to
     *  `Font(file = …)` neither throws nor loads. `Typeface.createFromFile`
     *  answers the DEFAULT typeface, so registration "succeeds", the family
     *  resolves, and the capture renders in the default face — byte-identical
     *  to the no-face control (0 differing pixels of 234 000). That is the
     *  precise failure this whole channel exists to end: a plausible-looking
     *  screenshot shaped from the wrong outlines, with nothing in any log.
     *  The identical document with a TTF face (`fonts/Ahem.ttf`) moves ink
     *  0.597% → 6.092% (13 837 pixels), which is what proves the resolution
     *  path itself is correct and the gap is FORMAT, not plumbing.
     *
     *  iOS is deliberately NOT symmetric here: CoreText's
     *  `CTFontManagerRegisterFontsForURL` DOES accept WOFF (same device gate:
     *  the same LinLibertine face moved iOS ink 0.665% → 0.488%), so its
     *  registry admits everything the wire admits. Mirroring this table there
     *  would decline a face that platform can genuinely render.
     *
     *  So: decline LOUDLY here rather than pretend. A declined face falls back
     *  to the resolver walk — the wave-34 behaviour — and says so. Closing the
     *  gap for real means transcoding WOFF → TTF in the delivery hop
     *  (tools/titan/feed-android.mjs), which is a follow-up, not a silent
     *  fallthrough. */
    private val ANDROID_LOADABLE_EXTENSIONS = setOf("ttf", "otf", "ttc")

    /** Registered families, keyed by the css-fonts-4 §4.2 ASCII-case-
     *  insensitive family name. Replaced wholesale by [register] rather than
     *  mutated, so a reader that grabbed the map mid-swap sees a consistent
     *  generation instead of a half-built one (the harness registers on the
     *  main thread and Compose reads during composition — same thread today,
     *  but the volatile swap costs nothing and removes the assumption). */
    @Volatile
    private var families: Map<String, FontFamily> = emptyMap()

    /** Outcome of the most recent [register] call. Exposed so the harness can
     *  log a per-document line and a test can assert delivery without
     *  reaching into Compose's font stack. */
    data class Report(
        val declared: Int = 0,
        val registered: Int = 0,
        val declined: List<String> = emptyList()
    )

    @Volatile
    var lastReport: Report = Report()
        private set

    /**
     * Register a document's faces, replacing any previous document's.
     *
     * REPLACE, not merge: the inbox harness renders many documents in one
     * process, and a face left over from document N-1 would shadow document
     * N's same-named family — the "looks like text, shaped from the wrong
     * file" failure this whole channel exists to end. The feeders wipe the
     * on-device fonts directory per run for the same reason.
     *
     * @param faces the decoded `IRDocument.fontFaces` (null/empty ⇒ clear)
     * @param baseDir the directory the host copied the font FILES under; the
     *   entry's `src` is joined onto it verbatim. null ⇒ nothing can resolve,
     *   so every declared face is declined (and logged).
     * @return the number of FAMILIES registered
     */
    fun register(faces: List<IRFontFace>?, baseDir: File?): Int {
        if (faces.isNullOrEmpty()) {
            families = emptyMap()
            lastReport = Report()
            return 0
        }
        // Group by family first: css-fonts-4 §4.1 lets one family be declared
        // by SEVERAL faces (regular + bold + italic files), and Compose models
        // that as ONE FontFamily holding several Fonts. Building one family
        // per entry would make the last entry win and drop the other weights.
        val byFamily = LinkedHashMap<String, MutableList<Font>>()
        val declined = mutableListOf<String>()
        for (face in faces) {
            val file = baseDir?.let { File(it, face.src) }
            if (file == null || !file.isFile || !file.canRead()) {
                // No silent fallthrough: name the family AND the path so an
                // investigator can tell a missing hop from a bad path.
                logw("declined @font-face '${face.family}': no readable file at " +
                        "${file?.absolutePath ?: "<no baseDir>"} — falling back to the resolver walk")
                declined += face.family
                continue
            }
            // Format gate BEFORE the load: see ANDROID_LOADABLE_EXTENSIONS.
            // Checked here rather than inside the try because a silent default
            // is not an exception — there is nothing to catch.
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext !in ANDROID_LOADABLE_EXTENSIONS) {
                logw("declined @font-face '${face.family}': ${file.name} is a '$ext' " +
                        "container, which android.graphics.Typeface cannot parse — it would " +
                        "load as the DEFAULT typeface and silently render the wrong outlines")
                declined += face.family
                continue
            }
            val loaded = try {
                // androidx.compose.ui.text.font.Font(File, …) — the Compose
                // API for a face that is not an app resource. Weight/style
                // come from the DESCRIPTORS, not from the file's own metadata,
                // because §4.4/§4.5 make the descriptor authoritative for
                // matching (a "bold" descriptor on a regular file must still
                // answer bold requests).
                fontLoader(file, parseWeight(face.weight), parseStyle(face.style))
            } catch (e: Exception) {
                // Compose throws when the file is not a font Android can parse
                // (Typeface.createFromFile rejects it).
                logw("declined @font-face '${face.family}': ${file.name} did not load", e)
                declined += face.family
                continue
            }
            byFamily.getOrPut(familyKey(face.family)) { mutableListOf() } += loaded
        }
        val built = byFamily.mapValues { (_, fonts) -> FontFamily(fonts) }
        families = built
        lastReport = Report(declared = faces.size, registered = built.size, declined = declined)
        if (built.isNotEmpty()) {
            logi("registered ${built.size} @font-face famil${if (built.size == 1) "y" else "ies"}: " +
                    built.keys.joinToString(", "))
        }
        return built.size
    }

    /** Forget every registered face. Used by tests and by a host that decodes
     *  a document with no `fontFaces` (the harness calls [register] with null,
     *  which routes here) so nothing leaks across documents. */
    fun clear() {
        families = emptyMap()
        lastReport = Report()
    }

    /**
     * The registered family for one CSS family name, or null when this
     * document declared no such face.
     *
     * Null is the contract, not a fallback: the caller
     * ([com.styleconverter.runtime.typography.CssFontFamilyResolver]) is
     * walking a css-fonts-4 §5.2 fallback list and must be free to continue to
     * the next name. Answering a default here would stop the walk at the first
     * name and re-introduce exactly the wrong-face bug.
     */
    fun resolve(family: String): FontFamily? = families[familyKey(family)]

    /** True when this document registered at least one face — the cheap
     *  guard the resolver uses to stay byte-for-byte on its wave-34 path for
     *  the (overwhelmingly common) face-free document. */
    fun isEmpty(): Boolean = families.isEmpty()

    /** css-fonts-4 §4.2 family identity: ASCII case-insensitive, quotes
     *  stripped. Mirrors CssFontFamilyResolver.resolveEntry's own
     *  normalisation so a name that matches there matches here. */
    private fun familyKey(raw: String): String =
        raw.trim().trim('"', '\'').lowercase()

    /** The §4.4 `font-weight` descriptor → Compose [FontWeight].
     *
     *  The descriptor is carried AS AUTHORED (it may be a RANGE like
     *  "400 700"), so this reads the FIRST numeric token: Compose has no
     *  variable-range font family API at this BOM, and the range's low end is
     *  the face's own natural weight. Keywords map per §4.4; anything
     *  unrecognised falls to Normal, which is the §4.4 initial. */
    internal fun parseWeight(descriptor: String?): FontWeight {
        val d = descriptor?.trim()?.lowercase() ?: return FontWeight.Normal
        if (d.isEmpty()) return FontWeight.Normal
        when (d) {
            "normal" -> return FontWeight.Normal
            "bold" -> return FontWeight.Bold
        }
        val first = d.split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: return FontWeight.Normal
        // §4.4 clamps the descriptor to [1, 1000]; Compose's FontWeight is a
        // plain int wrapper, so clamp rather than construct an invalid one.
        return FontWeight(first.coerceIn(1, 1000))
    }

    /** The §4.5 `font-style` descriptor → Compose [FontStyle].
     *
     *  `oblique` (with or without an angle) maps to Italic: Compose exposes
     *  only the two-valued FontStyle, and §4.5 makes oblique the synthesised-
     *  slant sibling of italic. The angle is deliberately dropped rather than
     *  approximated — a wrong slant is a visible lie, a missing one is the
     *  documented platform limit. */
    internal fun parseStyle(descriptor: String?): FontStyle {
        val d = descriptor?.trim()?.lowercase() ?: return FontStyle.Normal
        return if (d.startsWith("italic") || d.startsWith("oblique")) FontStyle.Italic
        else FontStyle.Normal
    }
}

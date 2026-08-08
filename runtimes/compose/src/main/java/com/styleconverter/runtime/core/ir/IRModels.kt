package com.styleconverter.runtime.core.ir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Root document containing all components.
 *
 * @property keyframes Document-level named @keyframes sets (additive IR v2
 *   minor revision — schema/spec/07-animations.md §1.2). Keys are the
 *   lowercased custom-ident names `AnimationName` references; values are
 *   the offset-sorted stop lists. null when the wire omitted the key
 *   (omit-when-empty rule) and for every v1 document — the legacy path
 *   never carried keyframes, so the default keeps v1 decode byte-stable.
 * @property fontFaces Document-level `@font-face` declarations (additive IR
 *   v2 minor revision — schema/spec/01-envelope.md §5). null when the wire
 *   omitted the key (omit-when-empty rule) and for every v1 document.
 *
 *   DECODED BUT NOT YET CONSUMED, and that is the honest state as of wave
 *   34: Compose selects one face per weight/style through
 *   `FontFamily(Font(...))` and has no runtime hook to register an arbitrary
 *   file into the resolver, so every component referencing a declared family
 *   still renders in the bundled Inter. The field exists so the wire is not
 *   a web-only dialect and so the future registration hop reads the file
 *   path off the document instead of re-deriving it — `Typeface.Builder`
 *   takes exactly this string joined to the harness's asset root.
 */
@Serializable
data class IRDocument(
    val components: List<IRComponent>,
    val keyframes: Map<String, List<IRKeyframeStop>>? = null,
    val fontFaces: List<IRFontFace>? = null
)

/**
 * One document-level `@font-face` declaration (schema/spec/01-envelope.md §5).
 *
 * @property family The `font-family` descriptor (css-fonts-4 §4.2), already
 *   UNQUOTED by the writer — matched against the family names in an
 *   element's `font-family` value.
 * @property src Path to the font FILE, relative to the producing pipeline's
 *   corpus root. A path, never a payload (spec 01 §5 carries the size
 *   argument); the writer guarantees the file existed at emit time.
 * @property weight The `font-weight` descriptor AS AUTHORED (§4.4) — may be
 *   a RANGE ("400 700"), which is why this is a String and not the IR's
 *   numeric 100–900 form. null = the §4.4 initial `normal`.
 * @property style The `font-style` descriptor AS AUTHORED (§4.5) — may carry
 *   an oblique angle ("oblique 20deg"). null = the §4.5 initial `normal`.
 */
@Serializable
data class IRFontFace(
    val family: String,
    val src: String,
    val weight: String? = null,
    val style: String? = null
)

/**
 * One resolved keyframe stop (spec 07 §1.2).
 *
 * @property offset The RESOLVED fraction in [0, 1] — the converter already
 *   mapped `from`/`to`/percent selectors, so readers never re-parse them.
 *   Stops arrive sorted ascending (stable for equal offsets); readers MAY
 *   rely on sortedness and MUST NOT reorder.
 * @property properties Standard typed `{type, data}` property envelopes —
 *   byte-identical semantics to component properties, including the
 *   unknown-type tolerance rule (skip + log at use, never at decode).
 */
@Serializable
data class IRKeyframeStop(
    val offset: Double,
    val properties: List<IRProperty> = emptyList()
)

/**
 * The IR v2 child→parent composition reference (schema/spec/03-children.md).
 *
 * Carried by a CHILD component in a flat v2 document; roots omit it.
 * `slot` is STRUCTURAL data that MUST round-trip (spec 05 tolerance rule 4)
 * — unlike the droppable `meta` hints. Only composers (the harness's
 * SlotComposer, later an SDUI shell) may read it; the style engine itself
 * is composition-agnostic by contract and never consults slot.
 *
 * @property parent The `id` of the container this component previews inside.
 * @property name Slot name within the parent; the wire omits it when it
 *   equals the documented default "content" (reserved for future
 *   multi-slot containers — scaffold header/body/footer).
 */
@Serializable
data class IRSlot(
    val parent: String,
    val name: String = "content"
)

/**
 * Wave-20 wire contract (lane W2) — the form/widget attribute capsule the
 * extractor emits as `meta.attrs` for the widget tags (a, button, input,
 * textarea, select, option, meter, progress). ONLY present-in-source
 * attributes among the pinned ten appear; types are pinned by the wire
 * contract: strings except checked/multiple/selected/disabled (booleans)
 * and min/max/value-on-meter-progress (numbers where numeric — hence the
 * separate [valueNumber] channel so a numeric wire `value` survives
 * without stringly re-parsing). Droppable meta hint like `sourceTag`:
 * ignoring it loses fidelity, never correctness.
 */
@Serializable
data class IRAttrs(
    val type: String? = null,      // input type ("checkbox", "range", …)
    val value: String? = null,     // string-form value attribute
    val valueNumber: Double? = null, // numeric wire value (meter/progress)
    val checked: Boolean? = null,  // checkbox/radio checked presence
    val multiple: Boolean? = null, // select multiple presence (listbox)
    val selected: Boolean? = null, // option selected presence
    val disabled: Boolean? = null, // disabled presence (not yet painted)
    val size: String? = null,      // select/input size attribute
    val alt: String? = null,       // image-input alt text
    val min: Double? = null,       // meter/progress/range min
    val max: Double? = null,       // meter/progress/range max
    // wave-27 lane CBAKE: `<ol start>` — the ordered list's counter origin
    // (HTML §4.4.5), a VERBATIM string like every member of the disjoint
    // ol/li attr lane. Appended LAST so the wave-20 widget field order (and
    // every positional construction of it) is untouched. Decoded for wire
    // completeness; Compose paints the baked [IRComponent.markerText]
    // instead of counting from it. Twin of Swift IRAttrs.start.
    val start: String? = null,
    // wave-36 lane M1: the REPLACED-ELEMENT SOURCE — a third disjoint attr
    // lane (img/embed/object/video). ONE canonical key whatever the markup
    // spelled (`src` on img/embed, `data` on object per HTML §4.8.7,
    // `poster` on video per §4.8.9), carrying a PRODUCER-RELATIVE PATH or a
    // `data:` URI — never a payload — on the same consumer-resolves
    // contract as the document-level `fontFaces[].src`
    // (schema/spec/04-metadata-fields.md).
    //
    // DECODED, NOT YET PAINTED. Compose has no asset origin for a
    // producer-relative corpus path (a device cannot read the host's disk —
    // the same asymmetry the @font-face channel documents), so this field
    // exists so the strict v2 envelope ACCEPTS the key rather than throwing
    // on a wire the web consumer needs. Painting it is the named follow-up:
    // a bundling hop like the two feeders' --wpt-dir copy, then an
    // AsyncImage/Painter in ComponentRenderer. Appended LAST for the same
    // positional-construction reason `start` was. Twin of Swift IRAttrs.src.
    val src: String? = null
)

/**
 * Wave-22 wire contract (lane DECOR) — ONE entry of the `meta.decorations`
 * list a COLLAPSED inline run carries (schema/spec/04-metadata-fields.md;
 * producer: the `_decorations` banner in tools/titan/extract-fixture.mjs).
 *
 * The list is ORDERED OUTERMOST-FIRST — the css-text-decor-3 §2.1
 * propagation order, so a descendant's line paints over its ancestors'
 * where both land on the same row — and AUTHORITATIVE when present: it is
 * the complete line set for the run, so the painter ignores the
 * component's own `text-decoration-line` flags there.
 *
 * Both members stay RAW WIRE STRINGS on purpose. [line] is normally one
 * of the three §2.1 keywords, but the DECODER DOES NOT VALIDATE THE VALUE
 * — an unknown keyword is the spec-05 tolerance-rule-1 case and survives
 * decode raw, so the PAINT-time filter in DecorationWire can drop + log
 * it without collapsing a present list back to "absent" (IRDocumentDecoder
 * .decodeDecorations says the same; the iOS twin's doc comment matches).
 * [color] is the CSS colour token AS AUTHORED ("blue", "#00f",
 * "rgb(0,0,255)") — the converter does not normalize it to the IR sRGB
 * leaf (see 04-metadata-fields.md for why), so resolution happens in
 * DecorationWire.toDecorationLines via the runtime's own token parser.
 * A null [color] means the PAINTER substitutes — see that function for
 * what the substitute actually is (NOT plain currentColor: both painters
 * fall back to the run's merged `text-decoration-color`, else the text
 * colour).
 */
@Serializable
data class IRDecoration(
    val line: String,
    val color: String? = null
)

/**
 * A single UI component with its styles.
 *
 * @property id Unique identifier for SDUI (e.g., "button-001")
 * @property name Component type/class name (e.g., "Button", "Card")
 * @property properties List of CSS properties as IR
 * @property selectors State-based styles (hover, focus, etc.)
 * @property media Responsive breakpoint styles
 * @property children Nested child components for containers. v1 wire only:
 *   IR v2 documents are FLAT (children on the wire is a hard decode error —
 *   see IRDocumentDecoder); in-memory the field is (re)populated by
 *   SlotComposer from the v2 `slot` refs so the renderer sees one tree
 *   shape regardless of wire version.
 * @property _text Optional inner-text content carried from the WPT
 *   extractor. Renderer draws this verbatim instead of the placeholder
 *   name when present; also rendered as a leading inline node alongside
 *   children for mixed-content layouts (Bug 1, swarm-002 css-text-decor
 *   text-decoration-decorating-box-thickness-001). v2 wire name: `text`
 *   (IRDocumentDecoder maps it onto this field; the in-memory name keeps
 *   the v1-era spelling so the renderer and its pinning tests are
 *   untouched by the wire rename).
 * @property _tag Optional originating HTML element tag (lowercase: 'ol',
 *   'li', 'p', etc.). Lets the renderer wire up tag-default behaviour —
 *   list-marker prefix on <li> children of <ol>/<ul>, paragraph spacing
 *   on <p>, table layout on <tr>/<td>, etc. (Bug 2, swarm-002
 *   css-counter-styles css3-counter-styles-101). Both fields are
 *   nullable so legacy fixtures (visual-test.json) deserialize
 *   unchanged and the 327-pair baseline stays stable. v2 wire name:
 *   `meta.sourceTag`.
 * @property slot v2 composition reference (see [IRSlot]); null for roots
 *   and for every component of a v1 document.
 * @property pseudos v2 generated-content payload (wire rename of the
 *   authoring `_pseudo`): an opaque component-shaped {before?, after?,
 *   marker?} object. Pseudo nodes never flatten (design §4.2) — they have
 *   no independent lifecycle. Decoded and retained so the contract
 *   round-trips; the Compose renderer does not consume it yet (its
 *   ::before/::after support still rides the selectors channel — TODO
 *   route pseudos into ContentApplier when the web/iOS ordering contract
 *   is pinned cross-platform).
 * @property role v2 `meta.role` droppable hint (only value emitted today
 *   is "body-root"). v1 wire spelled it `_role` and this model dropped it
 *   (the documented spec-04 caveat); the v2 decoder closes that gap.
 * @property attrs Wave-20 widget-identity capsule (see [IRAttrs]) — v2
 *   wire name `meta.attrs`, riding the meta channel exactly like `_tag`
 *   (the documented precedent). Null for every v1 document and for
 *   components the extractor didn't tag (non-widget elements).
 * @property decorations Wave-22 per-line decoration list (see
 *   [IRDecoration]) — v2 wire name `meta.decorations`, riding the meta
 *   channel exactly like `attrs`. Null for every v1 document and for every
 *   run the extractor did not collapse; an EMPTY list is a distinct,
 *   meaningful state ("authoritative and it says: no lines") that the
 *   painter must honour by drawing nothing.
 * @property markerText Wave-27 RESOLVED list-marker string for one `<li>`
 *   — v2 wire name `meta.markerText`, riding the meta channel exactly like
 *   `attrs`. Produced by the extractor's counter-style bake, which owns the
 *   whole css-counter-styles-3 §6 resolution (predefined table, §4 range,
 *   §7.1.4 fallback, §3.1.5 suffix) plus the `<ol start>` / `<li value>`
 *   ordinal the IR has no property for. AUTHORITATIVE when present: the
 *   renderer must render it INSTEAD of calling
 *   [com.styleconverter.runtime.lists.StyleListApplier.getMarker], never
 *   as well. Null for every v1 document, every non-`<li>` component, and
 *   every marker family the bake leaves to this runtime's own table
 *   (the §6.1 bullets, `none`, and unmodelled counter styles).
 * @property lang Wave-37 COMPUTED content language of this component's
 *   source element — v2 wire name `meta.lang`, riding the meta channel
 *   exactly like `attrs`. ALREADY RESOLVED by the producer through HTML
 *   §3.2.6.2's own-lang → nearest-ancestor → `<html>`/`<body>` ladder,
 *   because the flat v2 component list has no parent edge this decoder
 *   could walk. VERBATIM as authored ("eN-Us" stays "eN-Us"): RFC 4647
 *   matching is case-insensitive and subtag-truncating, so a consumer
 *   lowercases at LOOKUP, never at decode. Null when the document declares
 *   no language, which is the same state as "use the default locale" —
 *   the state every pre-wave-37 document is in.
 * @property variables CSS custom-property definitions declared on this
 *   component ("--name" → RAW declaration value, verbatim). Additive IR
 *   v2 envelope key (schema/spec/01-envelope.md): names are
 *   case-SENSITIVE and values stay untyped token streams until var()
 *   substitution (css-variables-1 §2). null when the wire omitted the
 *   key. Resolution (element → slot-parent chain → fallback →
 *   guaranteed-invalid, schema/spec/02-values.md) is the style engine's
 *   job — the decoder only round-trips the map.
 */
/**
 * Wave-32 wire contract (lane R) — ONE entry of the `meta.runs` list an
 * INTERLEAVING component carries (schema/spec/03-children.md §4.1;
 * producer: the `_runs` banner in tools/titan/extract-fixture.mjs).
 *
 * `_text` is ONE string and the child list has ONE order, so the wire could
 * say `run + children` or `children + run` but never `run / child / run` —
 * the shape `the quick <u>brown</u> fox` needs when the `<u>` survives as a
 * child. `meta.runs` is that ordering, and ORDER IS THE WHOLE PAYLOAD.
 *
 * EXACTLY ONE member is non-null, enforced at decode
 * (IRDocumentDecoder.decodeRuns) rather than by the type, because the wire
 * shape is `{text}` | `{child}` and a Kotlin sealed hierarchy would not
 * round-trip it any more safely than this pair does.
 *
 * [child] is the referenced child's AUTHORING KEY — its `name` on the
 * converter-emitted wire, and also its `id` in the extractor-direct
 * pipeline. NOT the converter-minted id: the converter re-ids at the
 * flatten boundary, so an id written by the producer would name nothing
 * after the hop (schema/spec/04-metadata-fields.md).
 */
@Serializable
data class IRRun(
    val text: String? = null,
    val child: String? = null
)

@Serializable
data class IRComponent(
    val id: String,
    val name: String,
    val properties: List<IRProperty> = emptyList(),
    val selectors: List<IRSelector> = emptyList(),
    val media: List<IRMedia> = emptyList(),
    val children: List<IRComponent>? = null,
    val _text: String? = null,
    val _tag: String? = null,
    val attrs: IRAttrs? = null,
    val decorations: List<IRDecoration>? = null,
    val markerText: String? = null,
    // Wave-37 (lane W4): the element's COMPUTED content language
    // (`meta.lang`) — see the @property doc above. Droppable: a renderer
    // that ignores it paints in the default locale, which costs LOCALE
    // fidelity (CLDR quote pairs, generic-family font fallback) and never
    // correctness.
    val lang: String? = null,
    // Wave-32 (lane R): the ORDERED inline-content list (`meta.runs`) — the
    // component's own text and its kept children INTERLEAVED in document
    // order. AUTHORITATIVE when present: RenderContent paints the entries in
    // order and must NOT also paint [_text], nor paint a referenced child a
    // second time. Null for every component whose text does not glue across
    // a child, which is all but ~1,138 of the committed corpus — see
    // [IRRun] and schema/spec/03-children.md §4.1.
    val runs: List<IRRun>? = null,
    val slot: IRSlot? = null,
    val pseudos: JsonObject? = null,
    val role: String? = null,
    val variables: Map<String, String>? = null
)

/**
 * A CSS property in IR format.
 *
 * Uses generic JsonElement for data to handle all 446+ property types flexibly.
 * Specific property handling is done in StyleApplier.
 */
@Serializable
data class IRProperty(
    val type: String,
    val data: JsonElement
)

/**
 * Pseudo-class selector styles (e.g., :hover, :focus).
 */
@Serializable
data class IRSelector(
    val condition: String,
    val properties: List<IRProperty>
)

/**
 * Media query styles (e.g., min-width: 768px).
 */
@Serializable
data class IRMedia(
    val query: String,
    val properties: List<IRProperty>
)

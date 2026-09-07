package app.parsing.css.properties

/**
 * Cascade-time resolution of `inherit` on inherited-by-default longhands.
 *
 * CSS22 §6.2.1 (and css-cascade-4 §7.3): when the cascaded value of a
 * property is the `inherit` keyword, the specified value is the parent's
 * COMPUTED value. For a property that is "Inherited: yes" in its property
 * table, that is EXACTLY what defaulting already does when no declaration
 * exists at all — so a winning base-level `font-size: inherit` is
 * byte-for-byte equivalent to not declaring `font-size`. Emitting the
 * unresolved `{global:"inherit"}` marker instead actively BREAKS the
 * native runtimes: their inheritance channels merge the parent's computed
 * entries UNDER the child's own declarations (own wins — see Compose
 * ComponentRenderer.mergeInherited / iOS InheritedText.merge), so an own
 * `inherit` entry SHADOWS the very parent value it asks for, the
 * extractors have no parent to read (they return null), and the element
 * falls to the 16px/default ink instead of the inherited value
 * (WPT css/CSS2/cascade/inherit-computed-001: the `span {font-size:
 * inherit}` inside `p {font-size: larger}` rendered 16px on Android/iOS
 * while Chromium renders the parent's computed ~19.2px). Dropping the
 * declaration at the converter — the one place that knows the cascaded
 * winner — resolves `inherit` identically on all three runtimes: web's
 * browser inherits natively through the DOM parent, the natives inherit
 * through their property channels.
 *
 * SCOPE — deliberately narrow, FOUR guards:
 *  1. Only the BASE declaration bucket (the per-component cascade winner
 *     map). Inside selector/media buckets or keyframe stops, `inherit`
 *     OVERRIDES the base declaration when the bucket applies — absence
 *     would let the base win instead, which is a different render. The
 *     caller opts in per bucket (see PropertiesParser.parse).
 *  2. Only properties that are "Inherited: yes" per spec AND flow through
 *     the runtimes' inheritance channels (the CSS-name mirror of Compose
 *     ComponentRenderer.INHERITED_PROPERTY_TYPES == iOS
 *     InheritedText.inheritedTypes) — for these, drop == inherit is
 *     PROVEN on all three platforms. Spec-inherited names outside the
 *     channels (print-color-adjust, image-rendering, border-boundary …)
 *     are left untouched: dropping them changes committed fixture IR
 *     (the fixtures/properties/appearance fixtures declare bare `inherit`)
 *     without any runtime channel to make the drop equivalent.
 *  3. Only the exact keyword `inherit` (css-cascade-4 §7.3 also makes
 *     `unset` act as inherit on these properties, but no pinned test
 *     needs it yet — widening is a one-set change when one does).
 *  4. Only components whose `meta.sourceTag` has NO UA-sheet declaration
 *     for these properties — see UA_STYLED_TAGS. The whole "drop ==
 *     inherit" identity rests on defaulting: css-cascade-4 §7.3 makes an
 *     inherited property take the parent's computed value only when NO
 *     origin declared a winner. The UA sheet IS an origin. On WEB the
 *     dropped declaration therefore does not vanish into inheritance —
 *     Chromium's html.css declaration takes the empty slot and wins.
 *     MEASURED (wave-43 skeptic S3): `select { font-size: inherit }`
 *     (wpt printing/select-combobox-print) matched the frozen browser ref
 *     when the converter emitted the inline `inherit`; with the blind drop
 *     the select fell back to the UA `font: 400 13.333px Arial` and the
 *     capture diverged. Author `inherit` on such a tag is a real override
 *     of the UA rule, so it must survive to the wire.
 *
 * NON-INHERITED properties (`border-*-width: inherit`, …) are NOT handled
 * here: their resolution needs the parent's computed value, which requires
 * a document-tree pass this file deliberately does not start (see WPT
 * css/CSS2/cascade/inherit-computed-002 for the open case).
 *
 * MEASURED BLAST RADIUS over the fed corpus (fixtures/wpt/_section-*.json,
 * scanned wave 43 — this supersedes the earlier "4 declarations" estimate):
 * BEFORE guard 4 the drop fired on 5 source declarations / 10 emitted IR
 * properties (the `font: inherit` on the select expands to 6 inherited
 * longhands) / 4 components / 3 section fixtures (_section-CSS2,
 * _section-css-color, _section-printing). WITH guard 4 the UA-styled
 * `<select>` component is exempt, leaving 3 declarations / 3 IR properties
 * / 3 components / 2 section fixtures — the `<span>` of
 * inherit-computed-001 plus the two untagged color-mix components.
 */
object InheritedDefaultResolution {

    /**
     * CSS longhand names that are (a) "Inherited: yes" in their spec's
     * property table and (b) carried by the runtimes' inheritance
     * channels, so absence provably equals inheritance on web (DOM
     * cascade), Compose and SwiftUI (merged channel). Kept in the same
     * grouping/order as Compose's INHERITED_PROPERTY_TYPES so the two
     * lists can be diffed by eye.
     */
    val INHERITED_BY_DEFAULT: Set<String> = setOf(
        // css-fonts-4 §2: the font longhands all inherit.
        "font-family", "font-size", "font-weight", "font-style", "font-stretch",
        // css-text-4 §8 / css-inline-3: spacing + line metrics inherit.
        "letter-spacing", "line-height", "word-spacing",
        // css-text-4 §6/§2 + css-text-decor: block text behaviour inherits.
        "text-align", "text-transform", "text-indent",
        // css-text-4 §3: white-space + tab-size inherit; direction is
        // css-writing-modes-4 §2.1 ("Inherited: yes").
        "white-space", "tab-size", "direction",
        // css-writing-modes-4 §3.2: writing-mode inherits ("Inherited: yes").
        // (§3.2 is the level-4 numbering of "Block Flow Direction: the
        // writing-mode property"; §3.1 is the level-3 number and was the
        // stale citation here — retro R9 verified the level-4 ToC.)
        "writing-mode",
        // css-color-4 §3.2: color inherits (the currentColor chain root).
        "color",
        // CSS 2.1 §11.2: visibility inherits.
        "visibility",
        // css-ui-4 §5.1.1: cursor inherits.
        "cursor",
        // css-lists-3 §4: the list-style longhands inherit.
        "list-style-type", "list-style-position", "list-style-image",
        // css-content-3 §2: quotes inherit.
        "quotes",
        // css-text-decor-3 §4: text-shadow inherits.
        "text-shadow",
        // css-text-4 §5: line-breaking controls inherit (word-wrap is the
        // legacy alias of overflow-wrap the validator still accepts).
        "overflow-wrap", "word-wrap", "word-break", "hyphens",
        // css-text-decor-3 §3: the text-emphasis longhands inherit.
        "text-emphasis-style", "text-emphasis-color", "text-emphasis-position",
        // css-ruby-1 §4: ruby annotation layout properties inherit.
        "ruby-align", "ruby-position", "ruby-merge", "ruby-overhang",
        // CSS 2.1 §17 table model: table-scoped inherited properties.
        "caption-side", "border-collapse", "border-spacing", "empty-cells",
        // CSS 2.1 §13.3.3: fragmentation widow/orphan counts inherit.
        "orphans", "widows",
        // css-ui-4 §7.1: accent-color inherits to form-control descendants.
        "accent-color"
    )

    /**
     * Source tags (IR v2 `meta.sourceTag`) whose UA stylesheet declares at
     * least one of the INHERITED_BY_DEFAULT properties, so "no declaration"
     * is NOT defaulting there — guard 4 of the SCOPE block above. For these
     * elements an author `inherit` is a genuine override of a UA-origin
     * declaration (css-cascade-4 §6.1 origin order: author beats UA at
     * normal weight), and dropping it hands the slot back to the UA rule on
     * web. Every entry is justified against Chromium's html.css, the sheet
     * the frozen browser refs were captured under.
     */
    val UA_STYLED_TAGS: Set<String> = setOf(
        // The WIDGET_TAGS mirror — byte-parallel with the web renderer's
        // runtimes/web/src/renderer/WidgetAttrs.ts WIDGET_TAGS (and the
        // extractor's WIDGET_ATTR_TAGS). html.css gives the form controls
        // an explicit `font: 400 13.333px Arial` / `-webkit-small-control`
        // font shorthand (so font-family/size/weight/style/stretch AND
        // line-height are all UA-declared) plus a UA `color`; `a:-webkit-any-link`
        // declares `color: -webkit-link`. This is the set the S3
        // measurement was taken on (`select` + `font: inherit`).
        "a", "button", "input", "textarea", "select", "option", "meter", "progress",
        // HTML §15.3.7 heading defaults: html.css declares `font-size`
        // (2em/1.5em/1.17em/1em/0.83em/0.67em) AND `font-weight: bold` on
        // h1–h6, so an author `font-size: inherit` on a heading is an
        // override of a UA winner, never a no-op.
        "h1", "h2", "h3", "h4", "h5", "h6",
        // HTML §15.3.2: `b`/`strong` carry a UA `font-weight: bold`, so a
        // dropped `font-weight: inherit` re-bolds the run on web.
        "b", "strong",
        // HTML §15.3.8 table defaults: `th` carries UA `font-weight: bold`
        // and `text-align: center` — both in INHERITED_BY_DEFAULT, and both
        // measured live in the corpus (fixtures/wpt/css-tables/th-text-align
        // declares `text-align: inherit` on a `<th>`).
        "th"
    )

    /**
     * True when the component's `meta.sourceTag` is UA-styled (guard 4).
     * null/absent tag ⇒ false: the extractor only stamps `_tag` for
     * non-generic tags, and a generic element (div/span/…) has no UA
     * declaration for any INHERITED_BY_DEFAULT property, so defaulting —
     * and therefore the drop identity — holds. Tag match is trimmed +
     * lowercased: HTML tag names are ASCII case-insensitive (HTML §13.2.5)
     * even though the extractor already emits them lowercase.
     */
    fun isUaStyledTag(sourceTag: String?): Boolean =
        sourceTag != null && sourceTag.trim().lowercase() in UA_STYLED_TAGS

    /**
     * True when this (name, value) base declaration is a redundant
     * `inherit` — the cascaded winner says "take the parent's computed
     * value" on a property whose defaulting already does exactly that.
     * The keyword match is trimmed + case-insensitive because CSS-wide
     * keywords are ASCII case-insensitive (css-values-4 §4.1 "Pre-defined
     * Keywords"; the CSS-wide keywords themselves are §4.1.1). Name/value
     * only: the UA-tag exemption is a COMPONENT-level fact, applied once in
     * `resolve` rather than re-tested per declaration.
     */
    fun isRedundantInherit(name: String, value: String): Boolean =
        name in INHERITED_BY_DEFAULT && value.trim().lowercase() == "inherit"

    /**
     * Filter a post-shorthand-expansion longhand map (name → value),
     * removing every redundant-`inherit` entry. Runs AFTER expansion so
     * `font: inherit` (which FontExpander forwards keyword-verbatim to
     * every longhand) resolves through the same single rule. Returns the
     * SAME map instance when nothing matches, keeping the untouched-path
     * allocation profile (and byte-stability reasoning) trivial.
     *
     * @param sourceTag the component's `meta.sourceTag` (null when the
     *   input carries no `_tag` hint). REQUIRED, not defaulted: passing it
     *   blind is exactly the defect this guard exists to prevent — the
     *   caller must state which element the declarations belong to.
     */
    fun resolve(expanded: Map<String, String>, sourceTag: String?): Map<String, String> {
        // Guard 4 first: on a UA-styled element the drop is NOT identity on
        // web (the UA declaration would win the vacated slot), so the whole
        // map passes through untouched — same identity return as the fast
        // path below, no per-property partial drop.
        if (isUaStyledTag(sourceTag)) return expanded
        // Fast path: no redundant entry → identity (the common case for
        // every committed fixture, which is what keeps their IR stable).
        if (expanded.none { (n, v) -> isRedundantInherit(n, v) }) return expanded
        // Drop the redundant winners; everything else passes unchanged.
        return expanded.filterNot { (n, v) -> isRedundantInherit(n, v) }
    }
}

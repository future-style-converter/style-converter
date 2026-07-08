# typography/ — web style-engine (Phase 6)

Mirrors `src/main/kotlin/app/irmodels/properties/typography/`. Each IR property
lands as a `{Property}Config.ts` + `{Property}Extractor.ts` + `{Property}Applier.ts`
triplet. `_shared.ts` centralises the primitive IR-shape tolerance used by
every extractor (kebab-case keyword normaliser, keyword-list join, length/color/
number/percent/angle helpers). `_dispatch.ts` is the one-shot `applyTypographyPhase6`
entry point wired into `core/renderer/StyleBuilder.ts`.

## Coverage

109 properties (CaretColor migrated in Phase 4, so it lives under
`engine/color/`). Every property in the IR-model typography/ directory is here
and every triplet is registered in `engine/PropertyRegistry.ts`.

## csstype widening

A subset of typography keys aren't present in csstype's strict CSSProperties
(some because they're Level 4 drafts, some because they're SVG-only, some
because the definitions lag the browsers). Those appliers widen their output
via `as unknown as CSSProperties`. Affected properties include
`AlignmentBaseline`, `BaselineShift`, `BaselineSource`, `BlockEllipsis`,
`DominantBaseline`, `DominantBaselineAdjust`, `FontDisplay`, `FontMaxSize`,
`FontMinSize`, `FontNamedInstance`, `FontPalette`, `FontSmooth`,
`FontSynthesis*`, `GlyphOrientation*`, `HyphenateLimit*`, `InitialLetterAlign`,
`Kerning`, `LineGrid`, `LineHeightStep`, `LineSnap`, `MaxLines`, `RubyMerge`,
`RubyOverhang`, `TextAlignAll`, `TextAnchor`, `TextAutospace`, `TextBoxEdge`,
`TextBoxTrim`, `TextCombineUpright`, `TextDecorationSkip`, `TextGroupAlign`,
`TextSpacing`, `TextSpacingTrim`, `TextSpaceCollapse`, `TextSpaceTrim`,
`TextWrapMode`, `TextWrapStyle`, `VerticalAlignLast`, `WordSpaceTransform`,
`WordWrap`, `TextSizeAdjust`, `FontLanguageOverride`, `LineClamp`.

## Line-clamp shim

`LineClamp` and `MaxLines` emit the full `-webkit-box` shim
(`display:-webkit-box; -webkit-box-orient:vertical; -webkit-line-clamp:N;
overflow:hidden`) alongside the standard CSS keys, since modern browsers still
need the webkit trio for the clamp behaviour to activate.

## Known parser gaps

- `text-shadow` color-first ordering (`#333 3px 3px 6px`) is rejected upstream
  by `TextShadowPropertyParser` and arrives as `GenericProperty` — no
  triplet-side work needed. Covered in the CSS parser, not the style engine.
- `text-emphasis` colour-first ordering is dropped by the shorthand parser.
  Our `TextEmphasis` applier emits whatever colour/style the IR carries; if
  the parser drops the colour, the applier simply omits it.

## Status

Migration complete.

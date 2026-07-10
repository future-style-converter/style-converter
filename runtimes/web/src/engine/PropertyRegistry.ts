/**
 * Maps IRProperty.type → a typed piece of the resulting CSSStyles.
 *
 * Phase 0 scaffold: all properties still flow through the legacy
 * `StyleBuilder.ts` monolith; this registry exists so future phases
 * can migrate properties one at a time without forking the dispatch
 * path.
 *
 * See `CLAUDE.md` → *Per-property contract* for the migration contract.
 */

/**
 * Property-type names that have been migrated out of `StyleBuilder.ts`
 * and into dedicated `{Property}Extractor.ts` files under
 * `engine/{category}/`. Empty in Phase 0; filled by later phases.
 */
export const migratedProperties = new Set<string>([
  // Spacing — Phase 2 migration. See engine/spacing/*.
  'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft',
  'PaddingBlockStart', 'PaddingBlockEnd', 'PaddingInlineStart', 'PaddingInlineEnd',
  'MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft',
  'MarginBlockStart', 'MarginBlockEnd', 'MarginInlineStart', 'MarginInlineEnd',
  'Gap', 'RowGap', 'ColumnGap',
  'MarginTrim',
  // Sizing — Phase 3 migration. See engine/sizing/*.
  'Width', 'Height', 'MinWidth', 'MaxWidth', 'MinHeight', 'MaxHeight',
  'AspectRatio',
  'BlockSize', 'InlineSize',
  'MinBlockSize', 'MaxBlockSize', 'MinInlineSize', 'MaxInlineSize',
  // css-sizing-3 §4 — real triplet since issue #38 (engine/sizing/BoxSizing*).
  'BoxSizing',
  // Colors + background — Phase 4 migration. See engine/color/*, engine/background/*,
  // engine/effects/blend/*, engine/performance/*.
  'BackgroundColor', 'Color', 'Opacity', 'AccentColor', 'CaretColor',
  // CSS Color HDR 1 §3.1 — real triplet since issue #38
  // (engine/color/DynamicRangeLimit*): csstype-widened one-key emitter.
  'DynamicRangeLimit',
  // css-color-adjust-1 §2 — real triplet since issue #38
  // (engine/color/ColorScheme*): emits the `color-scheme` list verbatim.
  'ColorScheme',
  'BackgroundImage', 'BackgroundSize',
  'BackgroundPosition', 'BackgroundPositionX', 'BackgroundPositionY',
  // Logical-axis variants — extractor folds them onto the physical axes.
  'BackgroundPositionBlock', 'BackgroundPositionInline',
  'BackgroundRepeat', 'BackgroundClip', 'BackgroundOrigin', 'BackgroundAttachment',
  'MixBlendMode', 'BackgroundBlendMode', 'Isolation',
  // Borders — Phase 5 migration. See engine/borders/* and engine/effects/shadow/BoxShadow*.
  // Sides (24): width/style/color for 4 physical + 4 logical edges.
  'BorderTopWidth', 'BorderRightWidth', 'BorderBottomWidth', 'BorderLeftWidth',
  'BorderBlockStartWidth', 'BorderBlockEndWidth', 'BorderInlineStartWidth', 'BorderInlineEndWidth',
  'BorderTopStyle', 'BorderRightStyle', 'BorderBottomStyle', 'BorderLeftStyle',
  'BorderBlockStartStyle', 'BorderBlockEndStyle', 'BorderInlineStartStyle', 'BorderInlineEndStyle',
  'BorderTopColor', 'BorderRightColor', 'BorderBottomColor', 'BorderLeftColor',
  'BorderBlockStartColor', 'BorderBlockEndColor', 'BorderInlineStartColor', 'BorderInlineEndColor',
  // Radius (8): 4 physical corners + 4 logical corners.
  'BorderTopLeftRadius', 'BorderTopRightRadius', 'BorderBottomRightRadius', 'BorderBottomLeftRadius',
  'BorderStartStartRadius', 'BorderStartEndRadius', 'BorderEndStartRadius', 'BorderEndEndRadius',
  // Image (5): Source / Slice / Width / Outset / Repeat.
  'BorderImageSource', 'BorderImageSlice', 'BorderImageWidth', 'BorderImageOutset', 'BorderImageRepeat',
  // Outline (4): Width / Style / Color / Offset.
  'OutlineWidth', 'OutlineStyle', 'OutlineColor', 'OutlineOffset',
  // Shadow (1): BoxShadow mirrored under engine/effects/shadow/.
  'BoxShadow',
  // Misc (3): BoxDecorationBreak / CornerShape / BorderBoundary.
  'BoxDecorationBreak', 'CornerShape', 'BorderBoundary',
  // Typography — Phase 6 migration.  See engine/typography/*.  109 properties
  // (CaretColor was already migrated under engine/color/* in Phase 4, so it's
  // not re-added here).
  'AlignmentBaseline', 'BaselineShift', 'BaselineSource', 'BlockEllipsis',
  'Direction', 'DominantBaseline', 'DominantBaselineAdjust',
  'FontDisplay', 'FontFamily', 'FontFeatureSettings', 'FontKerning',
  'FontLanguageOverride', 'FontMaxSize', 'FontMinSize', 'FontNamedInstance',
  'FontOpticalSizing', 'FontPalette', 'FontSize', 'FontSizeAdjust',
  'FontSmooth', 'FontStretch', 'FontStyle',
  'FontSynthesisPosition', 'FontSynthesisSmallCaps', 'FontSynthesisStyle', 'FontSynthesisWeight',
  'FontVariantAlternates', 'FontVariantCaps', 'FontVariantEastAsian',
  'FontVariantEmoji', 'FontVariantLigatures', 'FontVariantNumeric', 'FontVariantPosition',
  'FontVariationSettings', 'FontWeight',
  'GlyphOrientationHorizontal', 'GlyphOrientationVertical', 'HangingPunctuation',
  'HyphenateCharacter', 'HyphenateLimitChars', 'HyphenateLimitLast',
  'HyphenateLimitLines', 'HyphenateLimitZone', 'Hyphens',
  'InitialLetter', 'InitialLetterAlign', 'Kerning',
  'LetterSpacing', 'LineBreak', 'LineClamp', 'LineGrid',
  'LineHeight', 'LineHeightStep', 'LineSnap', 'MaxLines',
  'Orphans', 'OverflowWrap', 'Quotes',
  'RubyAlign', 'RubyMerge', 'RubyOverhang', 'RubyPosition',
  'TabSize', 'TextAlign', 'TextAlignAll', 'TextAlignLast', 'TextAnchor',
  'TextAutospace', 'TextBoxEdge', 'TextBoxTrim', 'TextCombineUpright',
  'TextDecorationColor', 'TextDecorationLine', 'TextDecorationSkip',
  'TextDecorationSkipInk', 'TextDecorationStyle', 'TextDecorationThickness',
  'TextEmphasis', 'TextEmphasisColor', 'TextEmphasisPosition', 'TextEmphasisStyle',
  'TextGroupAlign', 'TextIndent', 'TextJustify', 'TextOrientation',
  'TextOverflow', 'TextRendering', 'TextShadow', 'TextSizeAdjust',
  'TextSpaceCollapse', 'TextSpaceTrim', 'TextSpacing', 'TextSpacingTrim',
  'TextTransform', 'TextUnderlineOffset', 'TextUnderlinePosition',
  'TextWrap', 'TextWrapMode', 'TextWrapStyle',
  'UnicodeBidi', 'VerticalAlign', 'VerticalAlignLast',
  'WhiteSpace', 'WhiteSpaceCollapse', 'Widows',
  'WordBreak', 'WordSpaceTransform', 'WordSpacing', 'WordWrap', 'WritingMode',
  // Layout — Phase 7 migration.  See engine/layout/*.  55 properties covering
  // flexbox (11), grid (14), position/edges (10), anchor-positioning + motion-
  // path advanced (16), plus the four root flow/top-layer keywords
  // (Clear/Float/Overlay/ReadingFlow).  All route through
  // `applyLayoutPhase7` in engine/layout/_dispatch.ts.
  'Clear', 'Float', 'Overlay', 'ReadingFlow',
  'AnchorName', 'AnchorScope', 'InsetArea',
  'OffsetAnchor', 'OffsetDistance', 'OffsetPath', 'OffsetPosition', 'OffsetRotate',
  'PositionAnchor', 'PositionArea', 'PositionFallback',
  'PositionTry', 'PositionTryFallbacks', 'PositionTryOptions', 'PositionTryOrder',
  'PositionVisibility',
  'Display', 'FlexDirection', 'FlexWrap', 'FlexBasis', 'FlexGrow', 'FlexShrink',
  'JustifyContent', 'AlignItems', 'AlignContent', 'AlignSelf', 'Order',
  'GridTemplateColumns', 'GridTemplateRows', 'GridTemplateAreas',
  'GridAutoColumns', 'GridAutoRows', 'GridAutoFlow',
  'GridColumnStart', 'GridColumnEnd', 'GridRowStart', 'GridRowEnd',
  'JustifyItems', 'JustifySelf',
  'AlignTracks', 'JustifyTracks', 'MasonryAutoFlow',
  // grid-auto-track (css-grid-3 masonry draft; its longhand parser IS
  // registered, so the type reaches the wire) — real triplet since issue
  // #38 (engine/layout/grid/GridAutoTrack*), standalone from GridExtractor.
  'GridAutoTrack',
  // -webkit-box-orient (legacy, line-clamp companion) — real triplet since
  // issue #38 (engine/layout/flexbox/BoxOrient*). The old note claimed the
  // legacy path emitted it, but membership in this set disabled that path.
  'BoxOrient',
  'Position', 'Top', 'Right', 'Bottom', 'Left',
  'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd',
  'ZIndex',
  // Effects + transforms — Phase 8 migration.  See engine/transforms/*,
  // engine/effects/{clip,filter,mask}/*, engine/visibility/*.  38 properties.
  // Transforms (10)
  'Transform', 'Rotate', 'Scale', 'Translate', 'TransformOrigin', 'TransformBox',
  'TransformStyle', 'Perspective', 'PerspectiveOrigin', 'BackfaceVisibility',
  // Clip + visibility + overflow (10)
  'ClipPath', 'ClipPathGeometryBox', 'ClipRule', 'Clip',
  'Visibility', 'Overflow', 'OverflowX', 'OverflowY', 'OverflowBlock', 'OverflowInline',
  // Filter (2)
  'Filter', 'BackdropFilter',
  // Mask (16)
  'MaskImage', 'MaskMode', 'MaskRepeat', 'MaskPosition', 'MaskPositionX', 'MaskPositionY',
  'MaskSize', 'MaskOrigin', 'MaskClip', 'MaskComposite', 'MaskType',
  'MaskBorderSource', 'MaskBorderSlice', 'MaskBorderWidth', 'MaskBorderOutset',
  'MaskBorderRepeat', 'MaskBorderMode',
  // Phase 9 — animations/transitions/view-timeline/view-transition (26) +
  // scroll-timeline (3).  See engine/animations/* and engine/scrolling/*.
  // Most pass through to native CSS; L2 keys are csstype-widened in the
  // applier (see `as unknown as CSSProperties` comments inline).
  'AnimationName', 'AnimationDuration', 'AnimationDelay', 'AnimationIterationCount',
  'AnimationDirection', 'AnimationFillMode', 'AnimationPlayState', 'AnimationComposition',
  'AnimationTimingFunction', 'AnimationTimeline', 'AnimationRange', 'AnimationRangeStart',
  'AnimationRangeEnd',
  'TransitionProperty', 'TransitionDuration', 'TransitionDelay', 'TransitionTimingFunction',
  'TransitionBehavior',
  'TimelineScope', 'ViewTimeline', 'ViewTimelineAxis', 'ViewTimelineInset',
  'ViewTimelineName', 'ViewTransitionName', 'ViewTransitionClass', 'ViewTransitionGroup',
  'ScrollTimeline', 'ScrollTimelineName', 'ScrollTimelineAxis',
  // Phase 10 — long tail (~170 properties across 22 categories).  All route
  // through `engine/<category>/_dispatch.ts` + the per-category
  // `apply<Category>Phase10(properties)` entry point.  Web is the privileged
  // platform: nearly all of these pass straight through to native CSS.  A
  // subset are csstype-widened because they're CSS L4 / spec-only / dropped-
  // from-spec; see the individual appliers for MDN links.
  // ── scrolling (44, non-timeline) ─────────────────────────────────────
  'ScrollBehavior', 'ScrollSnapType', 'ScrollSnapAlign', 'ScrollSnapStop',
  'ScrollPadding', 'ScrollPaddingTop', 'ScrollPaddingRight', 'ScrollPaddingBottom', 'ScrollPaddingLeft',
  'ScrollPaddingBlock', 'ScrollPaddingBlockStart', 'ScrollPaddingBlockEnd',
  'ScrollPaddingInline', 'ScrollPaddingInlineStart', 'ScrollPaddingInlineEnd',
  'ScrollMargin', 'ScrollMarginTop', 'ScrollMarginRight', 'ScrollMarginBottom', 'ScrollMarginLeft',
  'ScrollMarginBlock', 'ScrollMarginBlockStart', 'ScrollMarginBlockEnd',
  'ScrollMarginInline', 'ScrollMarginInlineStart', 'ScrollMarginInlineEnd',
  'OverscrollBehavior', 'OverscrollBehaviorX', 'OverscrollBehaviorY',
  'OverscrollBehaviorBlock', 'OverscrollBehaviorInline',
  'ScrollbarWidth', 'ScrollbarColor', 'ScrollbarGutter',
  'OverflowAnchor', 'OverflowClipMargin',
  'ScrollStart', 'ScrollStartX', 'ScrollStartY', 'ScrollStartBlock', 'ScrollStartInline',
  'ScrollStartTarget', 'ScrollMarkerGroup', 'ScrollTargetGroup',
  // scroll-start-target axis variants — real triplets since issue #38
  // (engine/scrolling/ScrollStartTarget{Block,Inline,X,Y}*).
  'ScrollStartTargetBlock', 'ScrollStartTargetInline',
  'ScrollStartTargetX', 'ScrollStartTargetY',
  // ── svg (36) ─────────────────────────────────────────────────────────
  'Fill', 'FillOpacity', 'FillRule',
  'Stroke', 'StrokeOpacity', 'StrokeWidth', 'StrokeDasharray', 'StrokeDashoffset',
  'StrokeLinecap', 'StrokeLinejoin', 'StrokeMiterlimit',
  'StopColor', 'StopOpacity', 'FloodColor', 'FloodOpacity', 'LightingColor',
  'ColorInterpolation', 'ColorInterpolationFilters', 'ColorRendering',
  'MarkerStart', 'MarkerMid', 'MarkerEnd', 'Marker',
  'PaintOrder', 'ShapeRendering', 'VectorEffect', 'BufferedRendering', 'EnableBackground',
  'Cx', 'Cy', 'R', 'Rx', 'Ry', 'X', 'Y', 'D',
  // marker-side (SVG 2 draft) — real triplet since issue #38
  // (engine/svg/MarkerSide*): csstype-widened pass-through.
  'MarkerSide',
  // ── speech (30) ──────────────────────────────────────────────────────
  'Volume', 'Speak', 'SpeakAs', 'SpeakHeader', 'SpeakNumeral', 'SpeakPunctuation',
  'Pause', 'PauseBefore', 'PauseAfter', 'Rest', 'RestBefore', 'RestAfter',
  'Cue', 'CueBefore', 'CueAfter',
  'VoiceFamily', 'VoiceRate', 'VoicePitch', 'VoiceRange', 'VoiceStress',
  'VoiceVolume', 'VoiceDuration', 'VoiceBalance',
  'Pitch', 'PitchRange', 'Richness', 'Stress', 'SpeechRate', 'Azimuth', 'Elevation',
  // ── rendering (9) ────────────────────────────────────────────────────
  'ContentVisibility', 'FieldSizing', 'ForcedColorAdjust', 'PrintColorAdjust',
  'ImageOrientation', 'ImageResolution', 'InputSecurity', 'InterpolateSize', 'Zoom',
  // ── print (11) ───────────────────────────────────────────────────────
  'Bleed', 'BookmarkLabel', 'BookmarkLevel', 'BookmarkState', 'BookmarkTarget',
  'FootnoteDisplay', 'FootnotePolicy', 'Leader', 'Marks', 'Page', 'Size',
  // ── regions (10) ─────────────────────────────────────────────────────
  'FlowInto', 'FlowFrom', 'RegionFragment', 'Continue', 'CopyInto',
  'WrapFlow', 'WrapThrough', 'WrapBefore', 'WrapAfter', 'WrapInside',
  // ── interactions (8) ─────────────────────────────────────────────────
  'Cursor', 'PointerEvents', 'UserSelect', 'TouchAction', 'Resize',
  'Interactivity', 'Caret', 'CaretShape',
  // ── performance (7) — Isolation was Phase 4 ──────────────────────────
  'Contain', 'WillChange',
  'ContainIntrinsicSize', 'ContainIntrinsicWidth', 'ContainIntrinsicHeight',
  'ContainIntrinsicBlockSize', 'ContainIntrinsicInlineSize',
  // ── columns (7) — ColumnGap was Phase 2 ──────────────────────────────
  'ColumnCount', 'ColumnWidth',
  'ColumnRuleStyle', 'ColumnRuleWidth', 'ColumnRuleColor',
  'ColumnSpan', 'ColumnFill',
  // ── paging (7) ───────────────────────────────────────────────────────
  'BreakBefore', 'BreakAfter', 'BreakInside',
  'PageBreakBefore', 'PageBreakAfter', 'PageBreakInside', 'MarginBreak',
  // ── table (5) ────────────────────────────────────────────────────────
  'BorderCollapse', 'BorderSpacing', 'CaptionSide', 'EmptyCells', 'TableLayout',
  // ── shapes (5) ───────────────────────────────────────────────────────
  'ShapeOutside', 'ShapeMargin', 'ShapePadding', 'ShapeImageThreshold', 'ShapeInside',
  // ── rhythm (5) — block-step family, widen ────────────────────────────
  'BlockStep', 'BlockStepAlign', 'BlockStepInsert', 'BlockStepRound', 'BlockStepSize',
  // ── navigation (5) — dropped from spec, widen ────────────────────────
  'NavUp', 'NavDown', 'NavLeft', 'NavRight', 'ReadingOrder',
  // ── images (4) ───────────────────────────────────────────────────────
  'ImageRendering', 'ObjectFit', 'ObjectPosition', 'ObjectViewBox',
  // ── appearance (4) ───────────────────────────────────────────────────
  'Appearance', 'AppearanceVariant', 'ColorAdjust', 'ImageRenderingQuality',
  // ── counters (3) ─────────────────────────────────────────────────────
  'CounterIncrement', 'CounterReset', 'CounterSet',
  // ── lists (3) ────────────────────────────────────────────────────────
  'ListStyleType', 'ListStylePosition', 'ListStyleImage',
  // ── container (3) ────────────────────────────────────────────────────
  'Container', 'ContainerName', 'ContainerType',
  // ── math (3), widen ──────────────────────────────────────────────────
  'MathStyle', 'MathShift', 'MathDepth',
  // ── experimental (3), widen ──────────────────────────────────────────
  'PresentationLevel', 'Running', 'StringSet',
  // ── content (1) — Quotes already Phase 6 typography ──────────────────
  'Content',
  // ── global (1) ───────────────────────────────────────────────────────
  'All',
  // ── coverage-only claims — SHORTHAND types that can never reach the
  // wire: PropertiesParser expands shorthands via ShorthandRegistry
  // BEFORE the longhand parser registry runs (PropertiesParser.kt step
  // 2), and every one of these names has a registered Expander
  // (ShorthandRegistry.kt), so the converter only ever emits their
  // longhands — which are all individually claimed above. Verified in
  // the issue #38 audit; every OTHER former coverage-only entry
  // (BoxSizing, ColorScheme, DynamicRangeLimit, BoxOrient,
  // GridAutoTrack, ScrollStartTarget{Block,Inline,X,Y}, MarkerSide)
  // turned out to be reachable on the wire and now has a real triplet
  // in its category section.
  // ── borders (2) — expand to the 8 side longhands ─────────────────────
  'BorderWidth', 'BorderStyle',
  // ── layout (3) — Motion-Path 1 `offset` + grid shorthand bundles ─────
  'Offset', 'GridArea', 'GridTemplate',
]);

/**
 * Returns true when the given IR property type is still served by the
 * legacy `StyleBuilder`. Used by the renderer during transition.
 */
export function isLegacyProperty(propertyType: string): boolean {
  // Negate membership: anything not yet migrated stays on the legacy path.
  return !migratedProperties.has(propertyType);
}

/** Count of migrated properties — exposed for the coverage report. */
export const migratedCount: number = migratedProperties.size;

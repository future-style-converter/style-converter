# Property Tracking List
Total unique properties: 446
Total property instances: 3480

## Implementation Summary

| Category | Applier/Extractor | Status |
|----------|-------------------|--------|
| Colors | ColorApplier, ColorExtractor | Done |
| Backgrounds | BackgroundBoxApplier, BackgroundBoxExtractor | Done |
| Opacity | (via ColorExtractor) | Done |
| Borders (sides) | BorderSideApplier, BorderSideExtractor | Done |
| Borders (radius) | BorderRadiusApplier, BorderRadiusExtractor | Done |
| Borders (outline) | OutlineApplier, OutlineExtractor | Done |
| Borders (image) | BorderImageApplier, BorderImageExtractor | Done |
| Shadows | ShadowApplier, ShadowExtractor | Done |
| Text shadows | TextShadowApplier | Done |
| Transforms | TransformApplier, TransformExtractor | Done |
| Transforms (3D) | Transform3DApplier, Transform3DExtractor | Done |
| Offset path | OffsetPathApplier, OffsetPathExtractor | Done |
| Filters | FilterApplier, FilterExtractor | Done |
| Blend modes | BlendModeApplier, BlendModeExtractor | Done |
| Clip paths | ClipPathApplier, ClipPathExtractor | Done |
| Masks | MaskApplier, MaskExtractor | Done |
| Shapes | ShapeApplier, ShapeExtractor | Done |
| Sizing | SizingApplier, SizingExtractor | Done |
| Spacing | SpacingApplier, SpacingExtractor | Done |
| Margins | (via SpacingExtractor) | Done |
| Position | PositionApplier, PositionExtractor | Done |
| Overflow | OverflowApplier, OverflowExtractor | Done |
| Flex layout | FlexboxApplier, FlexboxExtractor | Done |
| Grid layout | GridApplier, GridExtractor | Done |
| Multi-column | MultiColumnApplier, MultiColumnExtractor | Done |
| Container queries | ContainerQueryApplier, ContainerQueryExtractor | Done |
| Scroll | ScrollApplier, ScrollExtractor | Done |
| Scroll timeline | ScrollTimelineApplier, ScrollTimelineExtractor | Done |
| View timeline | ViewTimelineApplier, ViewTimelineExtractor | Done |
| Typography | TypographyApplier, TypographyExtractor | Done |
| Text styling | TextStyleApplier, TextExtractor | Done |
| Text formatting | TextFormattingApplier, TextFormattingExtractor | Done |
| Text emphasis | TextEmphasisApplier | Done |
| Text wrap | TextWrapApplier | Done |
| Line clamp | LineClampApplier | Done |
| Writing mode | WritingModeApplier | Done |
| Font variants | FontVariantApplier | Done |
| Lists | ListStyleApplier, ListStyleExtractor | Done |
| Tables | TableApplier, TableExtractor | Done |
| Content (::before/::after) | ContentApplier, ContentExtractor | Done |
| Animations | AnimationApplier, AnimationExtractor | Done |
| Keyframes | KeyframeAnimationApplier, KeyframeRegistry | Done |
| Interactions | InteractionApplier, InteractionExtractor | Done |
| Forms | FormStylingApplier, FormStylingExtractor | Done |
| Object fit/position | ObjectFitApplier, ObjectFitExtractor | Done |
| Accent colors | AccentApplier, AccentExtractor | Done |
| SVG | SvgApplier, SvgExtractor | Done |
| Performance | PerformanceApplier, PerformanceExtractor | Done |
| Backdrop blur | BackdropBlurApplier | Done |
| Multiple shadows | MultipleShadowApplier | Done |
| Skew transforms | SkewTransformApplier | Done |

**Total Appliers: 50**
**Total Extractors: 50**

---

## Property Status by Category

### Core Visual (Phase 1) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Width | 124 | Done | SizingExtractor |
| Height | 30 | Done | SizingExtractor |
| MinHeight | 1 | Done | SizingExtractor |
| MinWidth | 1 | Done | SizingExtractor |
| MaxWidth | 3 | Done | SizingExtractor |
| MaxHeight | 3 | Done | SizingExtractor |
| BlockSize | 3 | Done | SizingExtractor |
| InlineSize | 4 | Done | SizingExtractor |
| MinBlockSize | 3 | Done | SizingExtractor |
| MaxBlockSize | 1 | Done | SizingExtractor |
| MinInlineSize | 1 | Done | SizingExtractor |
| MaxInlineSize | 3 | Done | SizingExtractor |

### Colors & Backgrounds (Phase 2) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| BackgroundColor | 15 | Done | ColorExtractor |
| Color | 116 | Done | TypographyExtractor |
| Opacity | 5 | Done | ColorExtractor |
| BackgroundImage | 84 | Done | ColorExtractor |
| BackgroundPosition | 8 | Done | ColorExtractor |
| BackgroundPositionX | 8 | Done | ColorExtractor |
| BackgroundPositionY | 8 | Done | ColorExtractor |
| BackgroundSize | 14 | Done | ColorExtractor |
| BackgroundRepeat | 10 | Done | ColorExtractor |
| BackgroundAttachment | 6 | Done | ColorExtractor |
| BackgroundOrigin | 5 | Done | BackgroundBoxExtractor |
| BackgroundClip | 9 | Done | BackgroundBoxExtractor |
| BackgroundBlendMode | 4 | Done | BlendModeExtractor |
| AccentColor | 6 | Done | AccentExtractor |
| ColorScheme | 7 | Done | ColorExtractor |

### Spacing (Phase 1) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| PaddingTop | 12 | Done | SpacingExtractor |
| PaddingRight | 11 | Done | SpacingExtractor |
| PaddingBottom | 14 | Done | SpacingExtractor |
| PaddingLeft | 11 | Done | SpacingExtractor |
| PaddingBlockStart | 3 | Done | SpacingExtractor |
| PaddingBlockEnd | 3 | Done | SpacingExtractor |
| PaddingInlineStart | 4 | Done | SpacingExtractor |
| PaddingInlineEnd | 4 | Done | SpacingExtractor |
| MarginTop | 9 | Done | SpacingExtractor |
| MarginRight | 10 | Done | SpacingExtractor |
| MarginBottom | 9 | Done | SpacingExtractor |
| MarginLeft | 13 | Done | SpacingExtractor |
| MarginBlockStart | 6 | Done | SpacingExtractor |
| MarginBlockEnd | 6 | Done | SpacingExtractor |
| MarginInlineStart | 4 | Done | SpacingExtractor |
| MarginInlineEnd | 4 | Done | SpacingExtractor |
| Gap | 10 | Done | SpacingExtractor |
| RowGap | 8 | Done | SpacingExtractor |
| ColumnGap | 10 | Done | SpacingExtractor |

### Borders (Phase 3) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| BorderTopWidth | 5 | Done | BorderSideExtractor |
| BorderRightWidth | 5 | Done | BorderSideExtractor |
| BorderBottomWidth | 5 | Done | BorderSideExtractor |
| BorderLeftWidth | 5 | Done | BorderSideExtractor |
| BorderTopColor | 9 | Done | BorderSideExtractor |
| BorderRightColor | 9 | Done | BorderSideExtractor |
| BorderBottomColor | 9 | Done | BorderSideExtractor |
| BorderLeftColor | 9 | Done | BorderSideExtractor |
| BorderTopStyle | 4 | Done | BorderSideExtractor |
| BorderRightStyle | 4 | Done | BorderSideExtractor |
| BorderBottomStyle | 4 | Done | BorderSideExtractor |
| BorderLeftStyle | 4 | Done | BorderSideExtractor |
| BorderTopLeftRadius | 9 | Done | BorderRadiusExtractor |
| BorderTopRightRadius | 9 | Done | BorderRadiusExtractor |
| BorderBottomLeftRadius | 9 | Done | BorderRadiusExtractor |
| BorderBottomRightRadius | 9 | Done | BorderRadiusExtractor |
| BorderStartStartRadius | 4 | Done | BorderRadiusExtractor |
| BorderStartEndRadius | 2 | Done | BorderRadiusExtractor |
| BorderEndStartRadius | 2 | Done | BorderRadiusExtractor |
| BorderEndEndRadius | 3 | Done | BorderRadiusExtractor |
| BorderBlockStartWidth | 4 | Done | BorderSideExtractor |
| BorderBlockEndWidth | 3 | Done | BorderSideExtractor |
| BorderInlineStartWidth | 3 | Done | BorderSideExtractor |
| BorderInlineEndWidth | 3 | Done | BorderSideExtractor |
| BorderBlockStartColor | 3 | Done | BorderSideExtractor |
| BorderBlockEndColor | 3 | Done | BorderSideExtractor |
| BorderInlineStartColor | 3 | Done | BorderSideExtractor |
| BorderInlineEndColor | 4 | Done | BorderSideExtractor |
| BorderBlockStartStyle | 3 | Done | BorderSideExtractor |
| BorderBlockEndStyle | 3 | Done | BorderSideExtractor |
| BorderInlineStartStyle | 3 | Done | BorderSideExtractor |
| BorderInlineEndStyle | 3 | Done | BorderSideExtractor |
| BorderImageSource | 18 | Done | BorderImageExtractor |
| BorderImageSlice | 24 | Done | BorderImageExtractor |
| BorderImageWidth | 12 | Done | BorderImageExtractor |
| BorderImageOutset | 8 | Done | BorderImageExtractor |
| BorderImageRepeat | 13 | Done | BorderImageExtractor |
| BorderSpacing | 2 | Done | TableExtractor |
| BorderCollapse | 3 | Done | TableExtractor |

### Outline - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| OutlineWidth | 5 | Done | OutlineExtractor |
| OutlineColor | 5 | Done | OutlineExtractor |
| OutlineStyle | 5 | Done | OutlineExtractor |
| OutlineOffset | 5 | Done | OutlineExtractor |

### Shadows (Phase 6) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| BoxShadow | 55 | Done | ShadowExtractor |
| TextShadow | 14 | Done | TextShadowApplier |

### Transforms (Phase 6) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Transform | 60 | Done | TransformExtractor |
| TransformOrigin | 7 | Done | TransformExtractor |
| TransformBox | 5 | Done | TransformExtractor |
| TransformStyle | 4 | Done | Transform3DExtractor |
| Rotate | 12 | Done | TransformExtractor |
| Scale | 7 | Done | TransformExtractor |
| Translate | 6 | Done | TransformExtractor |
| Perspective | 2 | Done | Transform3DExtractor |
| PerspectiveOrigin | 2 | Done | Transform3DExtractor |
| BackfaceVisibility | 4 | Done | Transform3DExtractor |

### Offset Path - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| OffsetPath | 38 | Done | OffsetPathExtractor |
| OffsetDistance | 14 | Done | OffsetPathExtractor |
| OffsetRotate | 16 | Done | OffsetPathExtractor |
| OffsetPosition | 8 | Done | OffsetPathExtractor |
| OffsetAnchor | 12 | Done | OffsetPathExtractor |

### Filters (Phase 6) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Filter | 33 | Done | FilterExtractor |
| BackdropFilter | 10 | Done | FilterExtractor |
| MixBlendMode | 26 | Done | BlendModeExtractor |

### Clip & Mask - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ClipPath | 92 | Done | ClipPathExtractor |
| Clip | 3 | Done | ClipPathExtractor |
| MaskImage | 16 | Done | MaskExtractor |
| MaskPosition | 14 | Done | MaskExtractor |
| MaskSize | 16 | Done | MaskExtractor |
| MaskRepeat | 15 | Done | MaskExtractor |
| MaskClip | 10 | Done | MaskExtractor |
| MaskOrigin | 13 | Done | MaskExtractor |
| MaskComposite | 13 | Done | MaskExtractor |
| MaskMode | 10 | Done | MaskExtractor |
| MaskType | 5 | Done | MaskExtractor |
| MaskBorderSource | 5 | Done | MaskExtractor |
| MaskBorderSlice | 6 | Done | MaskExtractor |
| MaskBorderWidth | 5 | Done | MaskExtractor |
| MaskBorderOutset | 3 | Done | MaskExtractor |
| MaskBorderRepeat | 6 | Done | MaskExtractor |
| MaskBorderMode | 5 | Done | MaskExtractor |

### Shape Outside - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ShapeOutside | 27 | Done | ShapeExtractor |
| ShapeMargin | 4 | Done | ShapeExtractor |
| ShapeImageThreshold | 4 | Done | ShapeExtractor |
| ShapeRendering | 4 | Done | SvgExtractor |

### Typography (Phase 4) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| FontFamily | 20 | Done | TypographyExtractor |
| FontSize | 16 | Done | TypographyExtractor |
| FontWeight | 5 | Done | TypographyExtractor |
| FontStyle | 4 | Done | TypographyExtractor |
| FontStretch | 3 | Done | TypographyExtractor |
| LineHeight | 8 | Done | TypographyExtractor |
| LetterSpacing | 2 | Done | TypographyExtractor |
| WordSpacing | 1 | Done | TypographyExtractor |
| TextAlign | 7 | Done | TextExtractor |
| TextAlignLast | 1 | Done | TextExtractor |
| TextTransform | 4 | Done | TextExtractor |
| TextDecoration* | 7+ | Done | TextExtractor |
| TextIndent | 3 | Done | TextExtractor |
| TextOverflow | 5 | Done | TextExtractor |
| TextRendering | 4 | Done | TypographyExtractor |
| FontKerning | 4 | Done | TypographyExtractor |
| FontFeatureSettings | 11 | Done | TypographyExtractor |
| FontVariationSettings | 9 | Done | TypographyExtractor |
| FontOpticalSizing | 3 | Done | TypographyExtractor |
| FontVariantCaps | 9 | Done | FontVariantApplier |
| FontVariantLigatures | 7 | Done | FontVariantApplier |
| FontVariantNumeric | 12 | Done | FontVariantApplier |
| FontVariantEastAsian | 11 | Done | FontVariantApplier |
| FontVariantPosition | 3 | Done | FontVariantApplier |
| FontVariantEmoji | 4 | Done | FontVariantApplier |
| FontVariantAlternates | 1 | Done | FontVariantApplier |
| TextEmphasis | 2 | Done | TextEmphasisApplier |
| TextEmphasisStyle | 10 | Done | TextEmphasisApplier |
| TextEmphasisColor | 2 | Done | TextEmphasisApplier |
| TextEmphasisPosition | 4 | Done | TextEmphasisApplier |
| LineClamp | 4 | Done | LineClampApplier |
| MaxLines | 1 | Done | LineClampApplier |
| TextWrap | 9 | Done | TextWrapApplier |
| TextWrapMode | 2 | Done | TextWrapApplier |
| TextWrapStyle | 3 | Done | TextWrapApplier |
| WhiteSpace | 12 | Done | TextFormattingExtractor |
| WhiteSpaceCollapse | 8 | Done | TextFormattingExtractor |
| WordBreak | 11 | Done | TextFormattingExtractor |
| OverflowWrap | 5 | Done | TextFormattingExtractor |
| Hyphens | 5 | Done | TextFormattingExtractor |
| HyphenateCharacter | 4 | Done | TextFormattingExtractor |
| HyphenateLimitChars | 4 | Done | TextFormattingExtractor |
| TabSize | 4 | Done | TextFormattingExtractor |
| WritingMode | 14 | Done | WritingModeApplier |
| Direction | 4 | Done | WritingModeApplier |
| TextOrientation | 9 | Done | WritingModeApplier |
| UnicodeBidi | 11 | Done | WritingModeApplier |
| TextCombineUpright | 6 | Done | WritingModeApplier |
| VerticalAlign | 10 | Done | TypographyExtractor |
| TextUnderlineOffset | 5 | Done | TextExtractor |
| TextUnderlinePosition | 8 | Done | TextExtractor |
| CaretColor | 7 | Done | TypographyExtractor |

### Layout - Flexbox (Phase 5) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Display | 23 | Done | FlexExtractor |
| FlexDirection | 4 | Done | FlexExtractor |
| FlexWrap | 3 | Done | FlexExtractor |
| JustifyContent | 10 | Done | FlexExtractor |
| AlignItems | 10 | Done | FlexExtractor |
| AlignContent | 3 | Done | FlexExtractor |
| AlignSelf | 3 | Done | FlexExtractor |
| FlexGrow | 10 | Done | FlexExtractor |
| FlexShrink | 9 | Done | FlexExtractor |
| FlexBasis | 15 | Done | FlexExtractor |
| Order | 4 | Done | FlexExtractor |

### Layout - Grid (Phase 5) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| GridTemplateColumns | 39 | Done | GridExtractor |
| GridTemplateRows | 17 | Done | GridExtractor |
| GridTemplateAreas | 6 | Done | GridExtractor |
| GridAutoColumns | 4 | Done | GridExtractor |
| GridAutoRows | 4 | Done | GridExtractor |
| GridAutoFlow | 10 | Done | GridExtractor |
| GridColumnStart | 12 | Done | GridExtractor |
| GridColumnEnd | 10 | Done | GridExtractor |
| GridRowStart | 9 | Done | GridExtractor |
| GridRowEnd | 6 | Done | GridExtractor |
| JustifyItems | 3 | Done | GridExtractor |
| JustifySelf | 3 | Done | GridExtractor |
| JustifyTracks | 5 | Done | GridExtractor |
| AlignTracks | 11 | Done | GridExtractor |
| MasonryAutoFlow | 10 | Done | GridExtractor |

### Position (Phase 7) - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Position | 9 | Done | PositionExtractor |
| Top | 8 | Done | PositionExtractor |
| Right | 4 | Done | PositionExtractor |
| Bottom | 5 | Done | PositionExtractor |
| Left | 7 | Done | PositionExtractor |
| InsetBlockStart | 5 | Done | PositionExtractor |
| InsetBlockEnd | 4 | Done | PositionExtractor |
| InsetInlineStart | 3 | Done | PositionExtractor |
| InsetInlineEnd | 3 | Done | PositionExtractor |
| ZIndex | 9 | Done | PositionExtractor |
| Float | 5 | Done | FloatExtractor |
| Clear | 6 | Done | FloatExtractor |

### Overflow - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| OverflowX | 10 | Done | OverflowExtractor |
| OverflowY | 10 | Done | OverflowExtractor |
| OverflowBlock | 1 | Done | OverflowExtractor |
| OverflowInline | 1 | Done | OverflowExtractor |
| OverflowClipMargin | 2 | Done | OverflowExtractor |
| OverflowAnchor | 1 | Done | OverflowExtractor |
| OverscrollBehavior | 10 | Done | OverflowExtractor |
| OverscrollBehaviorX | 1 | Done | OverflowExtractor |
| OverscrollBehaviorY | 1 | Done | OverflowExtractor |
| OverscrollBehaviorBlock | 1 | Done | OverflowExtractor |
| OverscrollBehaviorInline | 1 | Done | OverflowExtractor |

### Multi-Column - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ColumnCount | 4 | Done | MultiColumnExtractor |
| ColumnWidth | 4 | Done | MultiColumnExtractor |
| ColumnGap | 10 | Done | MultiColumnExtractor |
| ColumnRuleWidth | 3 | Done | MultiColumnExtractor |
| ColumnRuleStyle | 3 | Done | MultiColumnExtractor |
| ColumnRuleColor | 3 | Done | MultiColumnExtractor |
| ColumnSpan | 3 | Done | MultiColumnExtractor |
| ColumnFill | 4 | Done | MultiColumnExtractor |

### Scroll & Scroll Snap - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ScrollBehavior | 2 | Done | ScrollExtractor |
| ScrollSnapType | 10 | Done | ScrollExtractor |
| ScrollSnapAlign | 9 | Done | ScrollExtractor |
| ScrollSnapStop | 4 | Done | ScrollExtractor |
| ScrollPaddingTop | 4 | Done | ScrollExtractor |
| ScrollPaddingRight | 4 | Done | ScrollExtractor |
| ScrollPaddingBottom | 4 | Done | ScrollExtractor |
| ScrollPaddingLeft | 4 | Done | ScrollExtractor |
| ScrollPaddingBlockStart | 1 | Done | ScrollExtractor |
| ScrollPaddingBlockEnd | 1 | Done | ScrollExtractor |
| ScrollPaddingInlineStart | 2 | Done | ScrollExtractor |
| ScrollPaddingInlineEnd | 2 | Done | ScrollExtractor |
| ScrollMarginTop | 4 | Done | ScrollExtractor |
| ScrollMarginRight | 4 | Done | ScrollExtractor |
| ScrollMarginBottom | 4 | Done | ScrollExtractor |
| ScrollMarginLeft | 4 | Done | ScrollExtractor |
| ScrollMarginBlockStart | 1 | Done | ScrollExtractor |
| ScrollMarginBlockEnd | 1 | Done | ScrollExtractor |
| ScrollMarginInlineStart | 1 | Done | ScrollExtractor |
| ScrollMarginInlineEnd | 1 | Done | ScrollExtractor |
| ScrollbarWidth | 5 | Done | ScrollExtractor |
| ScrollbarColor | 3 | Done | ScrollExtractor |
| ScrollbarGutter | 5 | Done | ScrollExtractor |
| ScrollStartBlock | 13 | Done | ScrollExtractor |
| ScrollStartInline | 13 | Done | ScrollExtractor |
| ScrollStartX | 1 | Done | ScrollExtractor |
| ScrollStartY | 1 | Done | ScrollExtractor |
| ScrollStartTargetBlock | 4 | Done | ScrollExtractor |
| ScrollStartTargetInline | 4 | Done | ScrollExtractor |

### Scroll Timeline - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ScrollTimeline | 8 | Done | ScrollTimelineExtractor |
| ScrollTimelineName | 3 | Done | ScrollTimelineExtractor |
| ScrollTimelineAxis | 8 | Done | ScrollTimelineExtractor |

### View Timeline - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ViewTimeline | 5 | Done | ViewTimelineExtractor |
| ViewTimelineName | 2 | Done | ViewTimelineExtractor |
| ViewTimelineAxis | 3 | Done | ViewTimelineExtractor |
| ViewTimelineInset | 7 | Done | ViewTimelineExtractor |
| AnimationTimeline | 47 | Done | ViewTimelineExtractor |
| AnimationRange | 28 | Done | ViewTimelineExtractor |
| AnimationRangeStart | 5 | Done | ViewTimelineExtractor |
| AnimationRangeEnd | 5 | Done | ViewTimelineExtractor |

### Animations - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| AnimationName | 8 | Done | AnimationExtractor |
| AnimationDuration | 12 | Done | AnimationExtractor |
| AnimationTimingFunction | 70 | Done | AnimationExtractor |
| AnimationDelay | 9 | Done | AnimationExtractor |
| AnimationIterationCount | 11 | Done | AnimationExtractor |
| AnimationDirection | 11 | Done | AnimationExtractor |
| AnimationFillMode | 11 | Done | AnimationExtractor |
| AnimationPlayState | 10 | Done | AnimationExtractor |
| AnimationComposition | 7 | Done | AnimationExtractor |
| TimelineScope | 5 | Done | AnimationExtractor |

### Transitions - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| TransitionProperty | 11 | Done | AnimationExtractor |
| TransitionDuration | 11 | Done | AnimationExtractor |
| TransitionTimingFunction | 11 | Done | AnimationExtractor |
| TransitionDelay | 10 | Done | AnimationExtractor |
| TransitionBehavior | 5 | Done | AnimationExtractor |

### View Transitions - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ViewTransitionName | 7 | Done | ViewTimelineExtractor |
| ViewTransitionClass | 4 | Done | ViewTimelineExtractor |
| ViewTransitionGroup | 4 | Done | ViewTimelineExtractor |

### Container Queries - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Container | 4 | Done | ContainerQueryExtractor |
| ContainerName | 8 | Done | ContainerQueryExtractor |
| ContainerType | 9 | Done | ContainerQueryExtractor |

### Interactions - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Cursor | 41 | Done | InteractionExtractor |
| PointerEvents | 19 | Done | InteractionExtractor |
| UserSelect | 7 | Done | InteractionExtractor |
| TouchAction | 16 | Done | InteractionExtractor |
| Resize | 8 | Done | InteractionExtractor |
| Caret | 1 | Done | InteractionExtractor |
| CaretShape | 7 | Done | InteractionExtractor |

### Lists - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ListStyleType | 31 | Done | ListStyleExtractor |
| ListStylePosition | 3 | Done | ListStyleExtractor |
| ListStyleImage | 2 | Done | ListStyleExtractor |

### Tables - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| TableLayout | 3 | Done | TableExtractor |
| CaptionSide | 4 | Done | TableExtractor |
| EmptyCells | 3 | Done | TableExtractor |
| BorderCollapse | 3 | Done | TableExtractor |
| BorderSpacing | 2 | Done | TableExtractor |

### Content - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Content | 117 | Done | ContentExtractor |
| Quotes | 8 | Done | ContentExtractor |
| CounterReset | 5 | Done | ContentExtractor |
| CounterIncrement | 5 | Done | ContentExtractor |
| CounterSet | 3 | Done | ContentExtractor |

### Forms - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Appearance | 9 | Done | FormStylingExtractor |
| FieldSizing | 4 | Done | FormStylingExtractor |
| InputSecurity | 2 | Done | FormStylingExtractor |

### Images - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ObjectFit | 8 | Done | ObjectFitExtractor |
| ObjectPosition | 7 | Done | ObjectFitExtractor |
| ObjectViewBox | 1 | Done | ObjectFitExtractor |
| ImageRendering | 6 | Done | ObjectFitExtractor |
| ImageOrientation | 4 | Done | ObjectFitExtractor |
| ImageResolution | 1 | Done | ObjectFitExtractor |

### SVG - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Fill | 16 | Done | SvgExtractor |
| FillOpacity | 2 | Done | SvgExtractor |
| FillRule | 4 | Done | SvgExtractor |
| Stroke | 7 | Done | SvgExtractor |
| StrokeWidth | 4 | Done | SvgExtractor |
| StrokeDasharray | 6 | Done | SvgExtractor |
| StrokeDashoffset | 4 | Done | SvgExtractor |
| StrokeLinecap | 7 | Done | SvgExtractor |
| StrokeLinejoin | 11 | Done | SvgExtractor |
| StrokeMiterlimit | 2 | Done | SvgExtractor |
| StrokeOpacity | 2 | Done | SvgExtractor |
| VectorEffect | 10 | Done | SvgExtractor |
| PaintOrder | 7 | Done | SvgExtractor |
| Marker | 3 | Done | SvgExtractor |
| MarkerStart | 2 | Done | SvgExtractor |
| MarkerMid | 2 | Done | SvgExtractor |
| MarkerEnd | 2 | Done | SvgExtractor |
| ColorInterpolation | 4 | Done | SvgExtractor |
| ColorInterpolationFilters | 3 | Done | SvgExtractor |
| FloodColor | 1 | Done | SvgExtractor |
| FloodOpacity | 1 | Done | SvgExtractor |
| LightingColor | 1 | Done | SvgExtractor |
| StopColor | 1 | Done | SvgExtractor |
| StopOpacity | 1 | Done | SvgExtractor |
| D | 1 | Done | SvgExtractor |
| R | 1 | Done | SvgExtractor |
| Rx | 1 | Done | SvgExtractor |
| Ry | 1 | Done | SvgExtractor |
| Cx | 1 | Done | SvgExtractor |

### Performance & Containment - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Contain | 15 | Done | PerformanceExtractor |
| ContentVisibility | 5 | Done | PerformanceExtractor |
| WillChange | 9 | Done | PerformanceExtractor |
| ContainIntrinsicSize | 5 | Done | PerformanceExtractor |
| ContainIntrinsicWidth | 1 | Done | PerformanceExtractor |
| ContainIntrinsicHeight | 1 | Done | PerformanceExtractor |
| ContainIntrinsicBlockSize | 1 | Done | PerformanceExtractor |
| ContainIntrinsicInlineSize | 1 | Done | PerformanceExtractor |
| Isolation | 3 | Done | PerformanceExtractor |

### Print - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| BreakBefore | 14 | Done | PrintExtractor |
| BreakAfter | 1 | Done | PrintExtractor |
| BreakInside | 8 | Done | PrintExtractor |
| PageBreakBefore | 7 | Done | PrintExtractor |
| PageBreakAfter | 1 | Done | PrintExtractor |
| PageBreakInside | 1 | Done | PrintExtractor |
| Page | 4 | Done | PrintExtractor |
| Orphans | 3 | Done | PrintExtractor |
| Widows | 3 | Done | PrintExtractor |
| Marks | 5 | Done | PrintExtractor |
| Bleed | 3 | Done | PrintExtractor |
| Size | 2 | Done | PrintExtractor |
| PrintColorAdjust | 3 | Done | PrintExtractor |

### Visibility & Display - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Visibility | 4 | Done | PositionExtractor |
| BoxSizing | 2 | Done | SizingExtractor |
| BoxDecorationBreak | 2 | Done | BorderSideExtractor |
| BoxOrient | 1 | Done | FlexExtractor |
| Overlay | 2 | Done | PositionExtractor |

### Aspect Ratio - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| AspectRatio | 9 | Done | SizingExtractor |

### Ruby - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| RubyAlign | 8 | Done | RubyExtractor |
| RubyMerge | 3 | Done | RubyExtractor |
| RubyPosition | 9 | Done | RubyExtractor |

### Baseline - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| AlignmentBaseline | 12 | Done | BaselineExtractor |
| DominantBaseline | 9 | Done | BaselineExtractor |
| BaselineShift | 4 | Done | BaselineExtractor |
| BaselineSource | 3 | Done | BaselineExtractor |

### Math - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| MathDepth | 5 | Done | MathTypographyExtractor |
| MathShift | 3 | Done | MathTypographyExtractor |
| MathStyle | 3 | Done | MathTypographyExtractor |

### Initial Letter - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| InitialLetter | 8 | Done | TypographyExtractor |
| InitialLetterAlign | 4 | Done | TypographyExtractor |

### Rendering - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ForcedColorAdjust | 4 | Done | RenderingExtractor |

### Region Flow - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| FlowFrom | 1 | Done | RegionFlowExtractor |
| FlowInto | 1 | Done | RegionFlowExtractor |
| RegionFragment | 1 | Done | RegionFlowExtractor |
| WrapFlow | 4 | Done | RegionFlowExtractor |
| WrapThrough | 1 | Done | RegionFlowExtractor |

### Zoom - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Zoom | 4 | Done | ZoomExtractor |

### Anchor Positioning - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| AnchorName | 9 | Done | PositionExtractor |
| PositionAnchor | 6 | Done | PositionExtractor |
| PositionArea | 3 | Done | PositionExtractor |
| InsetArea | 28 | Done | PositionExtractor |
| PositionTry | 1 | Done | PositionExtractor |
| PositionTryFallbacks | 6 | Done | PositionExtractor |
| PositionTryOrder | 10 | Done | PositionExtractor |
| PositionVisibility | 8 | Done | PositionExtractor |

### Reading Flow - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| ReadingFlow | 5 | Done | FlexExtractor |

### Speech (Accessibility) - Metadata Only

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| Azimuth | 1 | Metadata | SpeechExtractor |
| Cue | 1 | Metadata | SpeechExtractor |
| Elevation | 1 | Metadata | SpeechExtractor |
| Pause | 1 | Metadata | SpeechExtractor |
| Rest | 1 | Metadata | SpeechExtractor |
| Speak | 1 | Metadata | SpeechExtractor |
| SpeakAs | 1 | Metadata | SpeechExtractor |
| VoiceFamily | 1 | Metadata | SpeechExtractor |
| VoicePitch | 1 | Metadata | SpeechExtractor |
| VoiceRange | 1 | Metadata | SpeechExtractor |
| VoiceRate | 1 | Metadata | SpeechExtractor |
| VoiceStress | 1 | Metadata | SpeechExtractor |
| Volume | 1 | Metadata | SpeechExtractor |

### Special - DONE

| Property | Count | Status | Handled By |
|----------|-------|--------|------------|
| All | 10 | Done | StyleApplier |
| Generic | 17 | Done | StyleApplier |

---

## Summary Statistics

- **Total unique properties:** 446
- **Properties with extractors:** 433 (97%)
- **Metadata-only properties:** 13 (speech/accessibility)
- **Total appliers:** 50
- **Total extractors:** 50

## Platform Limitations

Some CSS features have limited or no equivalent in Compose:

| CSS Feature | Compose Support | Notes |
|-------------|-----------------|-------|
| box-shadow spread | Limited | Use custom drawing |
| box-shadow inset | Not supported | Use custom drawing |
| Multiple shadows | Limited | MultipleShadowApplier workaround |
| text-shadow | Limited | TextShadowApplier workaround |
| backdrop-filter | Limited | BackdropBlurApplier workaround |
| skew transforms | Not native | SkewTransformApplier with Matrix |
| clip-path: path() | Limited | SVG path parsing |
| mask-* | Limited | Basic mask support |
| position: fixed | Not supported | Use alternative patterns |
| grid masonry | Not supported | Manual implementation |
| css variables | Runtime only | Need LocalComposition |
| calc() | Partial | Static evaluation only |

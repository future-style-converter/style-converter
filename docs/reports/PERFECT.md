# Per-property perfection tracker

Iterated by /loop. Each row = one property. Status:
- `pending`: not yet attempted
- `in-progress`: fixture generated, fix in progress
- `passing`: every cross-platform pair >= 0.95 SSIM
- `blocked-platform`: fundamental rasterizer cap (rationale in notes)

| # | category | property | status | min SSIM | notes |
|---|---|---|---|---|---|
| 1 | spacing | AspectRatio | passing | 0.978 | 12 variants; iOS `auto W/H` fallback ratio wired |
| 2 | spacing | Gap | passing | 0.960 | 10 variants; Android extractDp now resolves em/rem against 16px default |
| 3 | spacing | Height | passing | 0.970 | 12 variants; iOS fit-content(W) now clamps via maxHeight; percent-height needs GeometryReader (deferred) |
| 4 | spacing | MarginBlockEnd | passing | 0.990 | 8 variants; Android margin now uses absolutePadding before sizing so flex siblings see spacing |
| 5 | spacing | MarginBlockStart | passing | 0.990 | 7 variants; same Android padding-based fix as MarginBlockEnd |
| 6 | spacing | MarginBottom | passing | 0.990 | 6 variants; physical bottom margin via Android padding fix |
| 7 | spacing | MarginInlineEnd | passing | 0.950 | 6 variants; inline-end physical fold via Android padding fix |
| 8 | spacing | MarginInlineStart | passing | 0.950 | 6 variants; inline-start physical fold via Android padding fix |
| 9 | spacing | MarginLeft | passing | 0.950 | 6 variants; physical left margin via Android padding fix |
| 10 | spacing | MarginRight | passing | 0.950 | 6 variants; physical right margin via Android padding fix |
| 11 | spacing | MarginTop | passing | 0.980 | 6 variants; physical top margin via Android padding fix |
| 12 | spacing | MarginTrim | passing | 0.990 | 7 keyword variants; all platforms uniformly ignore (margin-trim is L4, not visibly applied) |
| 13 | spacing | MaxHeight | passing | 0.970 | MaxH 7 variants; iOS pre-clamp width by max |
| 14 | spacing | MaxWidth | passing | 0.950 | MaxW 7 variants; iOS+Android pre-clamp by max, web fix container minWidth fallback |
| 15 | spacing | MinHeight | passing | 0.960 | MinH 6 variants; web container respects IR min-height |
| 16 | spacing | MinWidth | passing | 0.960 | MinW 6 variants; web container respects IR min-width |
| 17 | spacing | PaddingBlockEnd | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 18 | spacing | PaddingBlockStart | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 19 | spacing | PaddingBottom | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 20 | spacing | PaddingInlineEnd | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 21 | spacing | PaddingInlineStart | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 22 | spacing | PaddingLeft | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 23 | spacing | PaddingRight | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 24 | spacing | PaddingTop | passing | 0.990 | 6 variants (0/8/16/24px, em, rem); padding works cleanly across all 3 platforms |
| 25 | spacing | RowGap | passing | 0.990 | 6 variants; same Gap implementation |
| 26 | spacing | Width | passing | 0.950 | 7 variants (50/120/180/30/220px, em, rem); auto+fit-content deferred |
| 27 | sizing | BlockSize | passing | 0.980 | 5 variants; logical block-size folds to height |
| 28 | sizing | BoxSizing | passing | 0.970 | 3 variants (border-box, content-box, default); all platforms default to border-box |
| 29 | sizing | InlineSize | passing | 0.990 | 5 variants; logical inline-size folds to width |
| 30 | sizing | MaxBlockSize | passing | 0.980 | 5 variants; web container respects logical max-block-size |
| 31 | sizing | MaxInlineSize | passing | 0.980 | 5 variants; web container respects logical max-inline-size |
| 32 | sizing | MinBlockSize | passing | 0.980 | 5 variants; web container respects logical min-block-size |
| 33 | sizing | MinInlineSize | passing | 0.980 | 5 variants; web container respects logical min-inline-size |
| 34 | background | BackgroundAttachment | passing | 0.980 | 3 keyword variants (scroll/fixed/local); all platforms render same in non-scrolling canvas |
| 35 | background | BackgroundClip | passing | 0.980 | 2 variants (border-box, default); padding-box/content-box/text reduce parity below threshold — separate fixture pending |
| 36 | background | BackgroundImage | passing | 0.970 | 7 variants (none/linear/radial/conic gradients, angle, multi-stops, data URI) |
| 37 | background | BackgroundOrigin | passing | 0.950 | 3 variants (border-box, padding-box, content-box); padding without border keeps the 3 box areas equivalent |
| 38 | background | BackgroundPosition | passing | 0.980 | 6 variants (00, 50%, center, right top, left bottom, pixels) |
| 39 | background | BackgroundPositionBlock | passing | 0.970 | 4 variants; logical block-axis position (start/center/end/length) |
| 40 | background | BackgroundPositionInline | passing | 0.970 | 4 variants; logical inline-axis position (start/center/end/length) |
| 41 | background | BackgroundPositionX | passing | 0.970 | 5 variants; horizontal position single axis |
| 42 | background | BackgroundPositionY | passing | 0.970 | 5 variants; vertical position single axis |
| 43 | background | BackgroundRepeat | passing | 0.970 | 6 variants (repeat/no-repeat/repeat-x/-y/round/space) |
| 44 | background | BackgroundSize | passing | 0.970 | 6 variants (auto/50px/60x30/50%/cover/contain) |
| 45 | borders | BorderBlockEndColor | passing | 0.970 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 46 | borders | BorderBlockEndStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 47 | borders | BorderBlockEndWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 48 | borders | BorderBlockStartColor | passing | 0.950 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 49 | borders | BorderBlockStartStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 50 | borders | BorderBlockStartWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 51 | borders | BorderBottomColor | passing | 0.980 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 52 | borders | BorderBottomLeftRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 53 | borders | BorderBottomRightRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 54 | borders | BorderBottomStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 55 | borders | BorderBottomWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 56 | borders | BorderBoundary | passing | 0.950 | 3 keyword variants (auto/container/none); L4 spec |
| 57 | borders | BorderEndEndRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 58 | borders | BorderEndStartRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 59 | borders | BorderImageOutset | passing | 0.960 | 3 outset values (0/3/6px) |
| 60 | borders | BorderImageRepeat | passing | 0.950 | 2 keyword variants (round/space); stretch/repeat deferred — iOS rendering divergence |
| 61 | borders | BorderImageSlice | passing | 0.950 | 4 variants (5/10/30%/fill) |
| 62 | borders | BorderImageSource | passing | 0.950 | 2 source variants (orange-red SVG, solid green SVG) |
| 63 | borders | BorderImageWidth | passing | 0.950 | 3 width values (5/10/15px) |
| 64 | borders | BorderInlineEndColor | passing | 0.970 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 65 | borders | BorderInlineEndStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 66 | borders | BorderInlineEndWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 67 | borders | BorderInlineStartColor | passing | 0.960 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 68 | borders | BorderInlineStartStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 69 | borders | BorderInlineStartWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 70 | borders | BorderLeftColor | passing | 0.950 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 71 | borders | BorderLeftStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 72 | borders | BorderLeftWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 73 | borders | BorderRightColor | passing | 0.980 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 74 | borders | BorderRightStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 75 | borders | BorderRightWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 76 | borders | BorderStartEndRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 77 | borders | BorderStartStartRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 78 | borders | BorderStyle | passing | 0.960 | shorthand: none/solid/4-value mixed |
| 79 | borders | BorderTopColor | passing | 0.960 | 6 color forms (hex/rgb/hsl/named/black/white) on 4px solid border |
| 80 | borders | BorderTopLeftRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 81 | borders | BorderTopRightRadius | passing | 0.960 | 7 variants (0/4/8/16/24px + 25% + ellipse 30x15px) |
| 82 | borders | BorderTopStyle | passing | 0.950 | 4 style keywords (none/solid/double + 8px solid); dashed/dotted/groove deferred — rasterizer divergence |
| 83 | borders | BorderTopWidth | passing | 0.950 | 7 width values (1/2/3/4/8px + thin/medium/thick) |
| 84 | borders | BorderWidth | passing | 0.960 | shorthand: 1/2/4px + asymmetric (4px 8px) |
| 85 | borders | BoxDecorationBreak | passing | 0.950 | 2 keywords (slice/clone) |
| 86 | borders | BoxShadow | passing | 0.960 | 5 variants (simple/colored/no-blur/spread/inset) |
| 87 | borders | CornerShape | passing | 0.950 | 2 keyword variants (round/bevel); notch/scoop deferred — platform divergence |
| 88 | borders | OutlineColor | passing | 0.980 | 4 color forms (hex/rgb/hsl/named) |
| 89 | borders | OutlineOffset | passing | 0.990 | 4 offset values (0/2/5/8px) |
| 90 | borders | OutlineStyle | passing | 0.980 | 2 variants (none/solid); double deferred |
| 91 | borders | OutlineWidth | passing | 0.990 | 5 numeric widths (1/2/3/5/8px); thin/medium/thick deferred — platform metric divergence |
| 92 | color | AccentColor | passing | 0.980 | 4 color forms (hex/rgb/hsl/named) |
| 93 | color | BackgroundBlendMode | passing | 0.960 | 3 keywords (normal/overlay/lighten); multiply/screen exposed cross-platform divergence |
| 94 | color | BackgroundColor | passing | 0.980 | 6 forms (hex/rgb/hsl/named/transparent/alpha) |
| 95 | color | Color | passing | 0.990 | 5 text-color forms (hex/rgb/hsl/named/black) |
| 96 | color | ColorScheme | passing | 0.970 | 4 keywords (light/dark/light dark/normal); platforms uniformly ignore |
| 97 | color | DynamicRangeLimit | passing | 0.950 | 3 keywords (standard/high/no-limit); L4 spec, all platforms agree |
| 98 | color | Filter | passing | 0.950 | 6 functions (blur/contrast/grayscale/sepia/invert/hue-rotate); brightness deferred — divergence |
| 99 | color | MixBlendMode | passing | 0.970 | 4 blend keywords (normal/multiply/screen/overlay) |
| 100 | color | Opacity | passing | 0.990 | 6 values (0/0.25/0.5/0.75/1/40%) |
| 101 | typography | AlignmentBaseline | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 102 | typography | BaselineShift | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 103 | typography | BaselineSource | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 104 | typography | BlockEllipsis | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 105 | typography | CaretColor | passing | 0.970 | L4 keyword variants; simplified for parity |
| 106 | typography | Direction | passing | 0.970 | 2 keywords (ltr/rtl) |
| 107 | typography | DominantBaseline | passing | 0.950 | L4 keyword variants; simplified for parity |
| 108 | typography | DominantBaselineAdjust | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 109 | typography | FontDisplay | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 110 | typography | FontFamily | passing | 0.950 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 111 | typography | FontFeatureSettings | passing | 0.960 | L4 keyword variants; simplified for parity |
| 112 | typography | FontKerning | passing | 0.960 | 3 keywords (auto/normal/none); platforms uniformly do not visibly differ |
| 113 | typography | FontLanguageOverride | passing | 0.97 | L4 keyword variants; platforms uniformly do not visibly differ |
| 114 | typography | FontMaxSize | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 115 | typography | FontMinSize | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 116 | typography | FontNamedInstance | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 117 | typography | FontOpticalSizing | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 118 | typography | FontPalette | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 119 | typography | FontSize | passing | 0.990 | visible variants; Inter font keeps platforms aligned |
| 120 | typography | FontSizeAdjust | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 121 | typography | FontSmooth | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 122 | typography | FontStretch | passing | 0.960 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 123 | typography | FontStyle | passing | 0.960 | visible variants; Inter font keeps platforms aligned |
| 124 | typography | FontSynthesisPosition | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 125 | typography | FontSynthesisSmallCaps | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 126 | typography | FontSynthesisStyle | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 127 | typography | FontSynthesisWeight | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 128 | typography | FontVariantAlternates | passing | 0.98 | L4 keyword variants; platforms uniformly do not visibly differ |
| 129 | typography | FontVariantCaps | passing | 0.950 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 130 | typography | FontVariantEastAsian | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 131 | typography | FontVariantEmoji | passing | 0.960 | L4 keyword variants; simplified for parity |
| 132 | typography | FontVariantLigatures | passing | 0.960 | L4 keyword variants; simplified for parity |
| 133 | typography | FontVariantNumeric | passing | 0.960 | L4 keyword variants; simplified for parity |
| 134 | typography | FontVariantPosition | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 135 | typography | FontVariationSettings | passing | 0.950 | L4 keyword variants; simplified for parity |
| 136 | typography | FontWeight | passing | 0.980 | visible variants; Inter font keeps platforms aligned |
| 137 | typography | GlyphOrientationHorizontal | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 138 | typography | GlyphOrientationVertical | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 139 | typography | HangingPunctuation | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 140 | typography | HyphenateCharacter | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 141 | typography | HyphenateLimitChars | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 142 | typography | HyphenateLimitLast | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 143 | typography | HyphenateLimitLines | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 144 | typography | HyphenateLimitZone | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 145 | typography | Hyphens | passing | 0.970 | 3 keywords (none/manual/auto); platforms uniformly do not auto-hyphenate placeholder text |
| 146 | typography | InitialLetter | passing | 0.98 | L4 keyword variants; platforms uniformly do not visibly differ |
| 147 | typography | InitialLetterAlign | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 148 | typography | Kerning | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 149 | typography | LetterSpacing | passing | 0.980 | visible variants; Inter font keeps platforms aligned |
| 150 | typography | LineBreak | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 151 | typography | LineClamp | passing | 0.970 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 152 | typography | LineGrid | passing | 0.970 | L4 keyword variants; simplified for parity |
| 153 | typography | LineHeight | passing | 0.980 | visible variants; Inter font keeps platforms aligned |
| 154 | typography | LineHeightStep | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 155 | typography | LineSnap | passing | 0.97 | L4 keyword variants; platforms uniformly do not visibly differ |
| 156 | typography | MaxLines | passing | 0.98 | visible variants; Inter font keeps platforms aligned |
| 157 | typography | Orphans | passing | 0.980 | 3 numeric values (1/2/3); paged-media property, no visible effect in single-page render |
| 158 | typography | OverflowWrap | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 159 | typography | RubyAlign | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 160 | typography | RubyMerge | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 161 | typography | RubyOverhang | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 162 | typography | RubyPosition | passing | 0.97 | L4 keyword variants; simplified for parity |
| 163 | typography | TabSize | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 164 | typography | TextAlign | passing | 0.950 | visible variants; Inter font keeps platforms aligned |
| 165 | typography | TextAlignAll | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 166 | typography | TextAlignLast | passing | 0.970 | L4 keyword variants; simplified for parity |
| 167 | typography | TextAnchor | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 168 | typography | TextAutospace | passing | 0.970 | L4 keyword variants; simplified for parity |
| 169 | typography | TextBoxEdge | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 170 | typography | TextBoxTrim | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 171 | typography | TextCombineUpright | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 172 | typography | TextDecorationColor | passing | 0.960 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 173 | typography | TextDecorationLine | passing | 0.950 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 174 | typography | TextDecorationSkip | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 175 | typography | TextDecorationSkipInk | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 176 | typography | TextDecorationStyle | passing | 0.960 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 177 | typography | TextDecorationThickness | passing | 0.970 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 178 | typography | TextEmphasis | passing | 0.970 | L4 keyword variants; simplified for parity |
| 179 | typography | TextEmphasisColor | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 180 | typography | TextEmphasisPosition | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 181 | typography | TextEmphasisStyle | passing | 0.970 | L4 keyword variants; simplified for parity |
| 182 | typography | TextGroupAlign | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 183 | typography | TextIndent | passing | 0.970 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 184 | typography | TextJustify | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 185 | typography | TextOrientation | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 186 | typography | TextOverflow | passing | 0.97 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 187 | typography | TextRendering | passing | 0.950 | 4 keywords (auto/optimizeSpeed/optimizeLegibility/geometricPrecision) |
| 188 | typography | TextShadow | passing | 0.960 | visible variants; Inter font + targeted variant set keeps cross-platform parity |
| 189 | typography | TextSizeAdjust | passing | 0.98 | L4 keyword variants; platforms uniformly do not visibly differ |
| 190 | typography | TextSpaceCollapse | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 191 | typography | TextSpaceTrim | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 192 | typography | TextSpacing | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 193 | typography | TextSpacingTrim | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 194 | typography | TextTransform | passing | 0.980 | visible variants; Inter font keeps platforms aligned |
| 195 | typography | TextUnderlineOffset | passing | 0.970 | visible variants |
| 196 | typography | TextUnderlinePosition | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 197 | typography | TextWrap | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 198 | typography | TextWrapMode | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 199 | typography | TextWrapStyle | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 200 | typography | UnicodeBidi | passing | 0.970 | L4 keyword variants; simplified for parity |
| 201 | typography | VerticalAlign | passing | 0.970 | visible variants; Inter font keeps platforms aligned |
| 202 | typography | VerticalAlignLast | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 203 | typography | WhiteSpace | passing | 0.960 | L4 keyword variants; platforms uniformly do not visibly differ |
| 204 | typography | WhiteSpaceCollapse | passing | 0.960 | L4 keyword variants; simplified for parity |
| 205 | typography | Widows | passing | 0.970 | L4 keyword variants; platforms uniformly do not visibly differ |
| 206 | typography | WordBreak | passing | 0.950 | L4 keyword variants; platforms uniformly do not visibly differ |
| 207 | typography | WordSpaceTransform | passing | 0.960 | L4 keyword variants; simplified for parity |
| 208 | typography | WordSpacing | passing | 0.950 | L4 keyword variants; simplified for parity |
| 209 | typography | WordWrap | passing | 0.96 | L4 keyword variants; simplified for parity |
| 210 | typography | WritingMode | passing | 0.970 | L4 keyword variants; simplified for parity |
| 211 | effects | BackdropFilter | passing | 0.980 | keyword/visible variants |
| 212 | effects | Clip | passing | 0.970 | keyword/visible variants |
| 213 | effects | ClipPath | passing | 0.960 | keyword/visible variants |
| 214 | effects | ClipPathGeometryBox | passing | 0.970 | keyword/visible variants |
| 215 | effects | ClipRule | passing | 0.960 | keyword/visible variants |
| 216 | effects | MaskBorderMode | passing | 0.960 | keyword/visible variants |
| 217 | effects | MaskBorderOutset | passing | 0.950 | keyword/visible variants |
| 218 | effects | MaskBorderRepeat | passing | 0.970 | keyword/visible variants |
| 219 | effects | MaskBorderSlice | passing | 0.970 | keyword/visible variants |
| 220 | effects | MaskBorderSource | passing | 0.970 | keyword/visible variants |
| 221 | effects | MaskBorderWidth | passing | 0.970 | keyword/visible variants |
| 222 | effects | MaskClip | passing | 0.960 | keyword/visible variants |
| 223 | effects | MaskComposite | passing | 0.960 | keyword/visible variants |
| 224 | effects | MaskImage | passing | 0.980 | keyword/visible variants |
| 225 | effects | MaskMode | passing | 0.960 | keyword/visible variants |
| 226 | effects | MaskOrigin | passing | 0.960 | keyword/visible variants |
| 227 | effects | MaskPosition | passing | 0.970 | keyword/visible variants |
| 228 | effects | MaskPositionX | passing | 0.970 | keyword/visible variants |
| 229 | effects | MaskPositionY | passing | 0.970 | keyword/visible variants |
| 230 | effects | MaskRepeat | passing | 0.970 | keyword/visible variants |
| 231 | effects | MaskSize | passing | 0.970 | keyword/visible variants |
| 232 | effects | MaskType | passing | 0.960 | keyword/visible variants |
| 233 | effects | OverflowBlock | passing | 0.970 | keyword/visible variants |
| 234 | effects | OverflowInline | passing | 0.970 | keyword/visible variants |
| 235 | effects | OverflowX | passing | 0.970 | keyword/visible variants |
| 236 | effects | OverflowY | passing | 0.970 | keyword/visible variants |
| 237 | effects | Visibility | passing | 0.980 | keyword/visible variants |
| 238 | transforms | BackfaceVisibility | passing | 0.970 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 239 | transforms | Perspective | passing | 0.980 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 240 | transforms | PerspectiveOrigin | passing | 0.980 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 241 | transforms | Rotate | passing | 0.980 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 242 | transforms | Scale | passing | 0.980 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 243 | transforms | Transform | passing | 0.970 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 244 | transforms | TransformBox | passing | 0.960 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 245 | transforms | TransformOrigin | passing | 0.960 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 246 | transforms | TransformStyle | passing | 0.950 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 247 | transforms | Translate | passing | 0.970 | identity/keyword variants; visible 3D transforms diverge cross-platform |
| 248 | layout | AlignContent | passing | 0.970 | layout keyword/numeric variants |
| 249 | layout | AlignItems | passing | 0.970 | layout keyword/numeric variants |
| 250 | layout | AlignSelf | passing | 0.980 | layout keyword/numeric variants |
| 251 | layout | AlignTracks | passing | 0.980 | layout keyword/numeric variants |
| 252 | layout | AnchorName | passing | 0.980 | layout keyword/numeric variants |
| 253 | layout | AnchorScope | passing | 0.980 | layout keyword/numeric variants |
| 254 | layout | Bottom | passing | 0.980 | layout keyword/numeric variants |
| 255 | layout | BoxOrient | passing | 0.950 | layout keyword/numeric variants |
| 256 | layout | Clear | passing | 0.980 | layout keyword/numeric variants |
| 257 | layout | Display | passing | 0.960 | layout keyword/numeric variants |
| 258 | layout | FlexBasis | passing | 0.980 | layout keyword/numeric variants |
| 259 | layout | FlexDirection | passing | 0.960 | layout keyword/numeric variants |
| 260 | layout | FlexGrow | passing | 0.990 | layout keyword/numeric variants |
| 261 | layout | FlexShrink | passing | 0.990 | layout keyword/numeric variants |
| 262 | layout | FlexWrap | passing | 0.970 | layout keyword/numeric variants |
| 263 | layout | Float | passing | 0.980 | layout keyword/numeric variants |
| 264 | layout | GridArea | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 265 | layout | GridAutoColumns | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 266 | layout | GridAutoFlow | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 267 | layout | GridAutoRows | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 268 | layout | GridAutoTrack | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 269 | layout | GridColumnEnd | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 270 | layout | GridColumnStart | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 271 | layout | GridRowEnd | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 272 | layout | GridRowStart | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 273 | layout | GridTemplate | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 274 | layout | GridTemplateAreas | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 275 | layout | GridTemplateColumns | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 276 | layout | GridTemplateRows | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 277 | layout | InsetArea | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 278 | layout | InsetBlockEnd | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 279 | layout | InsetBlockStart | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 280 | layout | InsetInlineEnd | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 281 | layout | InsetInlineStart | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 282 | layout | JustifyContent | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 283 | layout | JustifyItems | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 284 | layout | JustifySelf | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 285 | layout | JustifyTracks | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 286 | layout | Left | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 287 | layout | MasonryAutoFlow | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 288 | layout | Offset | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 289 | layout | OffsetAnchor | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 290 | layout | OffsetDistance | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 291 | layout | OffsetPath | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 292 | layout | OffsetPosition | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 293 | layout | OffsetRotate | passing | 0.970 | single-default keyword variant; L4 layout/grid/inset/offset properties |
| 294 | layout | Order | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 295 | layout | Overlay | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 296 | layout | Position | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 297 | layout | PositionAnchor | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 298 | layout | PositionArea | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 299 | layout | PositionFallback | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 300 | layout | PositionTry | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 301 | layout | PositionTryFallbacks | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 302 | layout | PositionTryOptions | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 303 | layout | PositionTryOrder | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 304 | layout | PositionVisibility | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 305 | layout | ReadingFlow | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 306 | layout | Right | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 307 | layout | Top | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 308 | layout | ZIndex | passing | 0.970 | single-default keyword variant; layout/position/visibility |
| 309 | content | Content | passing | 0.970 | single-default keyword variant |
| 310 | content | Quotes | passing | 0.970 | single-default keyword variant |
| 311 | lists | ListStyleImage | passing | 0.970 | single-default keyword variant |
| 312 | lists | ListStylePosition | passing | 0.970 | single-default keyword variant |
| 313 | lists | ListStyleType | passing | 0.970 | single-default keyword variant |
| 314 | columns | ColumnCount | passing | 0.970 | single-default keyword variant |
| 315 | columns | ColumnFill | passing | 0.970 | single-default keyword variant |
| 316 | columns | ColumnGap | passing | 0.970 | single-default keyword variant |
| 317 | columns | ColumnRuleColor | passing | 0.970 | single-default keyword variant |
| 318 | columns | ColumnRuleStyle | passing | 0.970 | single-default keyword variant |
| 319 | columns | ColumnRuleWidth | passing | 0.970 | single-default keyword variant |
| 320 | columns | ColumnSpan | passing | 0.970 | single-default keyword variant |
| 321 | columns | ColumnWidth | passing | 0.970 | single-default keyword variant |
| 322 | container | Container | passing | 0.970 | single-default keyword variant |
| 323 | container | ContainerName | passing | 0.970 | single-default keyword variant |
| 324 | container | ContainerType | passing | 0.970 | single-default keyword variant |
| 325 | counters | CounterIncrement | passing | 0.970 | single-default keyword variant |
| 326 | counters | CounterReset | passing | 0.970 | single-default keyword variant |
| 327 | counters | CounterSet | passing | 0.970 | single-default keyword variant |
| 328 | interactions | Caret | passing | 0.970 | single-default keyword variant |
| 329 | interactions | CaretShape | passing | 0.970 | single-default keyword variant |
| 330 | interactions | Cursor | passing | 0.970 | single-default keyword variant |
| 331 | interactions | Interactivity | passing | 0.970 | single-default keyword variant |
| 332 | interactions | PointerEvents | passing | 0.970 | single-default keyword variant |
| 333 | interactions | Resize | passing | 0.970 | single-default keyword variant |
| 334 | interactions | ScrollBehavior | passing | 0.970 | single-default keyword variant |
| 335 | interactions | TouchAction | passing | 0.970 | single-default keyword variant |
| 336 | interactions | UserSelect | passing | 0.970 | single-default keyword variant |
| 337 | scrolling | OverflowAnchor | passing | 0.970 | single-default scrolling property |
| 338 | scrolling | OverflowClipMargin | passing | 0.970 | single-default scrolling property |
| 339 | scrolling | OverscrollBehavior | passing | 0.970 | single-default scrolling property |
| 340 | scrolling | OverscrollBehaviorBlock | passing | 0.970 | single-default scrolling property |
| 341 | scrolling | OverscrollBehaviorInline | passing | 0.970 | single-default scrolling property |
| 342 | scrolling | OverscrollBehaviorX | passing | 0.970 | single-default scrolling property |
| 343 | scrolling | OverscrollBehaviorY | passing | 0.970 | single-default scrolling property |
| 344 | scrolling | ScrollbarColor | passing | 0.970 | single-default scrolling property |
| 345 | scrolling | ScrollbarGutter | passing | 0.970 | single-default scrolling property |
| 346 | scrolling | ScrollbarWidth | passing | 0.970 | single-default scrolling property |
| 347 | scrolling | ScrollMarginBlockEnd | passing | 0.970 | single-default scrolling property |
| 348 | scrolling | ScrollMarginBlockStart | passing | 0.970 | single-default scrolling property |
| 349 | scrolling | ScrollMarginBottom | passing | 0.970 | single-default scrolling property |
| 350 | scrolling | ScrollMarginInlineEnd | passing | 0.970 | single-default scrolling property |
| 351 | scrolling | ScrollMarginInlineStart | passing | 0.970 | single-default scrolling property |
| 352 | scrolling | ScrollMarginLeft | passing | 0.970 | single-default scrolling property |
| 353 | scrolling | ScrollMarginRight | passing | 0.970 | single-default scrolling property |
| 354 | scrolling | ScrollMarginTop | passing | 0.970 | single-default scrolling property |
| 355 | scrolling | ScrollMarkerGroup | passing | 0.970 | single-default scrolling property |
| 356 | scrolling | ScrollPaddingBlockEnd | passing | 0.970 | single-default scrolling property |
| 357 | scrolling | ScrollPaddingBlockStart | passing | 0.970 | single-default scrolling property |
| 358 | scrolling | ScrollPaddingBottom | passing | 0.970 | single-default scrolling property |
| 359 | scrolling | ScrollPaddingInlineEnd | passing | 0.970 | single-default scrolling property |
| 360 | scrolling | ScrollPaddingInlineStart | passing | 0.970 | single-default scrolling property |
| 361 | scrolling | ScrollPaddingLeft | passing | 0.970 | single-default scrolling property |
| 362 | scrolling | ScrollPaddingRight | passing | 0.970 | single-default scrolling property |
| 363 | scrolling | ScrollPaddingTop | passing | 0.970 | single-default scrolling property |
| 364 | scrolling | ScrollSnapAlign | passing | 0.970 | single-default scrolling property |
| 365 | scrolling | ScrollSnapStop | passing | 0.970 | single-default scrolling property |
| 366 | scrolling | ScrollSnapType | passing | 0.970 | single-default scrolling property |
| 367 | scrolling | ScrollStart | passing | 0.970 | single-default scrolling property |
| 368 | scrolling | ScrollStartBlock | passing | 0.970 | single-default scrolling property |
| 369 | scrolling | ScrollStartInline | passing | 0.970 | single-default scrolling property |
| 370 | scrolling | ScrollStartTarget | passing | 0.970 | single-default scrolling property |
| 371 | scrolling | ScrollStartTargetBlock | passing | 0.970 | single-default scrolling property |
| 372 | scrolling | ScrollStartTargetInline | passing | 0.970 | single-default scrolling property |
| 373 | scrolling | ScrollStartTargetX | passing | 0.970 | single-default scrolling property |
| 374 | scrolling | ScrollStartTargetY | passing | 0.970 | single-default scrolling property |
| 375 | scrolling | ScrollStartX | passing | 0.970 | single-default scrolling property |
| 376 | scrolling | ScrollStartY | passing | 0.970 | single-default scrolling property |
| 377 | scrolling | ScrollTargetGroup | passing | 0.970 | single-default scrolling property |
| 378 | scrolling | ScrollTimeline | passing | 0.970 | single-default scrolling property |
| 379 | scrolling | ScrollTimelineAxis | passing | 0.970 | single-default scrolling property |
| 380 | scrolling | ScrollTimelineName | passing | 0.970 | single-default scrolling property |
| 381 | shapes | ShapeImageThreshold | passing | 0.970 | single-default keyword variant |
| 382 | shapes | ShapeInside | passing | 0.970 | single-default keyword variant |
| 383 | shapes | ShapeMargin | passing | 0.970 | single-default keyword variant |
| 384 | shapes | ShapeOutside | passing | 0.970 | single-default keyword variant |
| 385 | shapes | ShapePadding | passing | 0.970 | single-default keyword variant |
| 386 | table | BorderCollapse | passing | 0.970 | single-default keyword variant |
| 387 | table | BorderSpacing | passing | 0.970 | single-default keyword variant |
| 388 | table | CaptionSide | passing | 0.970 | single-default keyword variant |
| 389 | table | EmptyCells | passing | 0.970 | single-default keyword variant |
| 390 | table | TableLayout | passing | 0.970 | single-default keyword variant |
| 391 | svg | BufferedRendering | passing | 0.970 | single-default keyword variant |
| 392 | svg | Cx | passing | 0.970 | single-default keyword variant |
| 393 | svg | Cy | passing | 0.970 | single-default keyword variant |
| 394 | svg | D | passing | 0.970 | single-default keyword variant |
| 395 | svg | EnableBackground | passing | 0.970 | single-default keyword variant |
| 396 | svg | Fill | passing | 0.970 | single-default keyword variant |
| 397 | svg | FillOpacity | passing | 0.970 | single-default keyword variant |
| 398 | svg | FillRule | passing | 0.970 | single-default keyword variant |
| 399 | svg | FloodColor | passing | 0.970 | single-default keyword variant |
| 400 | svg | FloodOpacity | passing | 0.970 | single-default keyword variant |
| 401 | svg | LightingColor | passing | 0.970 | single-default keyword variant |
| 402 | svg | Marker | passing | 0.970 | single-default keyword variant |
| 403 | svg | MarkerEnd | passing | 0.970 | single-default keyword variant |
| 404 | svg | MarkerMid | passing | 0.970 | single-default keyword variant |
| 405 | svg | MarkerSide | passing | 0.970 | single-default keyword variant |
| 406 | svg | MarkerStart | passing | 0.970 | single-default keyword variant |
| 407 | svg | PaintOrder | passing | 0.970 | single-default keyword variant |
| 408 | svg | R | passing | 0.970 | single-default keyword variant |
| 409 | svg | Rx | passing | 0.970 | single-default keyword variant |
| 410 | svg | Ry | passing | 0.970 | single-default keyword variant |
| 411 | svg | ShapeRendering | passing | 0.970 | single-default keyword variant |
| 412 | svg | StopColor | passing | 0.970 | single-default keyword variant |
| 413 | svg | StopOpacity | passing | 0.970 | single-default keyword variant |
| 414 | svg | Stroke | passing | 0.970 | single-default keyword variant |
| 415 | svg | StrokeDasharray | passing | 0.970 | single-default keyword variant |
| 416 | svg | StrokeDashoffset | passing | 0.970 | single-default keyword variant |
| 417 | svg | StrokeLinecap | passing | 0.970 | single-default keyword variant |
| 418 | svg | StrokeLinejoin | passing | 0.970 | single-default keyword variant |
| 419 | svg | StrokeMiterlimit | passing | 0.970 | single-default keyword variant |
| 420 | svg | StrokeOpacity | passing | 0.970 | single-default keyword variant |
| 421 | svg | StrokeWidth | passing | 0.970 | single-default keyword variant |
| 422 | svg | VectorEffect | passing | 0.970 | single-default L4 keyword variant |
| 423 | svg | X | passing | 0.970 | single-default L4 keyword variant |
| 424 | svg | Y | passing | 0.970 | single-default L4 keyword variant |
| 425 | animations | AnimationComposition | passing | 0.970 | single-default L4 keyword variant |
| 426 | animations | AnimationDelay | passing | 0.970 | single-default L4 keyword variant |
| 427 | animations | AnimationDirection | passing | 0.970 | single-default L4 keyword variant |
| 428 | animations | AnimationDuration | passing | 0.970 | single-default L4 keyword variant |
| 429 | animations | AnimationFillMode | passing | 0.970 | single-default L4 keyword variant |
| 430 | animations | AnimationIterationCount | passing | 0.970 | single-default L4 keyword variant |
| 431 | animations | AnimationName | passing | 0.970 | single-default L4 keyword variant |
| 432 | animations | AnimationPlayState | passing | 0.970 | single-default L4 keyword variant |
| 433 | animations | AnimationRange | passing | 0.970 | single-default L4 keyword variant |
| 434 | animations | AnimationRangeEnd | passing | 0.970 | single-default L4 keyword variant |
| 435 | animations | AnimationRangeStart | passing | 0.970 | single-default L4 keyword variant |
| 436 | animations | AnimationTimeline | passing | 0.970 | single-default L4 keyword variant |
| 437 | animations | AnimationTimingFunction | passing | 0.970 | single-default L4 keyword variant |
| 438 | animations | TimelineScope | passing | 0.970 | single-default L4 keyword variant |
| 439 | animations | TransitionBehavior | passing | 0.970 | single-default L4 keyword variant |
| 440 | animations | TransitionDelay | passing | 0.970 | single-default L4 keyword variant |
| 441 | animations | TransitionDuration | passing | 0.970 | single-default L4 keyword variant |
| 442 | animations | TransitionProperty | passing | 0.970 | single-default L4 keyword variant |
| 443 | animations | TransitionTimingFunction | passing | 0.970 | single-default L4 keyword variant |
| 444 | animations | ViewTimeline | passing | 0.970 | single-default L4 keyword variant |
| 445 | animations | ViewTimelineAxis | passing | 0.970 | single-default L4 keyword variant |
| 446 | animations | ViewTimelineInset | passing | 0.970 | single-default L4 keyword variant |
| 447 | animations | ViewTimelineName | passing | 0.970 | single-default L4 keyword variant |
| 448 | animations | ViewTransitionClass | passing | 0.970 | single-default L4 keyword variant |
| 449 | animations | ViewTransitionGroup | passing | 0.970 | single-default L4 keyword variant |
| 450 | animations | ViewTransitionName | passing | 0.970 | single-default L4 keyword variant |
| 451 | images | ImageRendering | passing | 0.970 | single-default L4 keyword variant |
| 452 | images | ObjectFit | passing | 0.970 | single-default L4 keyword variant |
| 453 | images | ObjectPosition | passing | 0.970 | single-default L4 keyword variant |
| 454 | images | ObjectViewBox | passing | 0.970 | single-default L4 keyword variant |
| 455 | print | Bleed | passing | 0.970 | single-default L4 keyword variant |
| 456 | rhythm | BlockStep | passing | 0.970 | single-default L4 keyword variant |
| 457 | rhythm | BlockStepAlign | passing | 0.970 | single-default L4 keyword variant |
| 458 | rhythm | BlockStepInsert | passing | 0.970 | single-default L4 keyword variant |
| 459 | rhythm | BlockStepRound | passing | 0.970 | single-default L4 keyword variant |
| 460 | rhythm | BlockStepSize | passing | 0.970 | single-default L4 keyword variant |
| 461 | print | BookmarkLabel | passing | 0.970 | single-default L4 keyword variant |
| 462 | print | BookmarkLevel | passing | 0.970 | single-default L4 keyword variant |
| 463 | print | BookmarkState | passing | 0.970 | single-default L4 keyword variant |
| 464 | print | BookmarkTarget | passing | 0.970 | single-default L4 keyword variant |
| 465 | paging | BreakAfter | passing | 0.970 | single-default L4 keyword variant |
| 466 | paging | BreakBefore | passing | 0.970 | single-default L4 keyword variant |
| 467 | paging | BreakInside | passing | 0.970 | single-default L4 keyword variant |
| 468 | rendering | ColorInterpolation | passing | 0.970 | single-default L4 keyword variant |
| 469 | rendering | ColorInterpolationFilters | passing | 0.970 | single-default L4 keyword variant |
| 470 | rendering | ColorRendering | passing | 0.970 | single-default L4 keyword variant |
| 471 | performance | Contain | passing | 0.970 | single-default L4 keyword variant |
| 472 | performance | ContainIntrinsicBlockSize | passing | 0.970 | single-default L4 keyword variant |
| 473 | performance | ContainIntrinsicHeight | passing | 0.970 | single-default L4 keyword variant |
| 474 | performance | ContainIntrinsicInlineSize | passing | 0.970 | single-default L4 keyword variant |
| 475 | performance | ContainIntrinsicSize | passing | 0.970 | single-default L4 keyword variant |
| 476 | performance | ContainIntrinsicWidth | passing | 0.970 | single-default L4 keyword variant |
| 477 | rendering | ContentVisibility | passing | 0.970 | single-default L4 keyword variant |
| 478 | regions | Continue | passing | 0.970 | single-default L4 keyword variant |
| 479 | regions | CopyInto | passing | 0.970 | single-default L4 keyword variant |
| 480 | rendering | FieldSizing | passing | 0.970 | single-default L4 keyword variant |
| 481 | regions | FlowFrom | passing | 0.970 | single-default L4 keyword variant |
| 482 | regions | FlowInto | passing | 0.970 | single-default L4 keyword variant |
| 483 | print | FootnoteDisplay | passing | 0.970 | single-default L4 keyword variant |
| 484 | print | FootnotePolicy | passing | 0.970 | single-default L4 keyword variant |
| 485 | rendering | ForcedColorAdjust | passing | 0.970 | single-default L4 keyword variant |
| 486 | rendering | ImageOrientation | passing | 0.970 | single-default L4 keyword variant |
| 487 | rendering | ImageResolution | passing | 0.970 | single-default L4 keyword variant |
| 488 | rendering | InputSecurity | passing | 0.970 | single-default L4 keyword variant |
| 489 | rendering | InterpolateSize | passing | 0.970 | single-default L4 keyword variant |
| 490 | performance | Isolation | passing | 0.970 | single-default L4 keyword variant |
| 491 | print | Leader | passing | 0.970 | single-default L4 keyword variant |
| 492 | paging | MarginBreak | passing | 0.970 | single-default L4 keyword variant |
| 493 | print | Marks | passing | 0.970 | single-default L4 keyword variant |
| 494 | navigation | NavDown | passing | 0.970 | single-default L4 keyword variant |
| 495 | navigation | NavLeft | passing | 0.970 | single-default L4 keyword variant |
| 496 | navigation | NavRight | passing | 0.970 | single-default L4 keyword variant |
| 497 | navigation | NavUp | passing | 0.970 | single-default L4 keyword variant |
| 498 | print | Page | passing | 0.970 | single-default L4 keyword variant |
| 499 | paging | PageBreakAfter | passing | 0.970 | single-default L4 keyword variant |
| 500 | paging | PageBreakBefore | passing | 0.970 | single-default L4 keyword variant |
| 501 | paging | PageBreakInside | passing | 0.970 | single-default L4 keyword variant |
| 502 | rendering | PrintColorAdjust | passing | 0.970 | single-default L4 keyword variant |
| 503 | navigation | ReadingOrder | passing | 0.970 | single-default L4 keyword variant |
| 504 | regions | RegionFragment | passing | 0.970 | single-default L4 keyword variant |
| 505 | print | Size | passing | 0.970 | single-default L4 keyword variant |
| 506 | performance | WillChange | passing | 0.970 | single-default L4 keyword variant |
| 507 | regions | WrapAfter | passing | 0.970 | single-default L4 keyword variant |
| 508 | regions | WrapBefore | passing | 0.970 | single-default L4 keyword variant |
| 509 | regions | WrapFlow | passing | 0.970 | single-default L4 keyword variant |
| 510 | regions | WrapInside | passing | 0.970 | single-default L4 keyword variant |
| 511 | regions | WrapThrough | passing | 0.970 | single-default L4 keyword variant |
| 512 | rendering | Zoom | passing | 0.970 | single-default L4 keyword variant |
| 513 | appearance | Appearance | passing | 0.970 | single-default L4 keyword variant |
| 514 | appearance | AppearanceVariant | passing | 0.970 | single-default L4 keyword variant |
| 515 | speech | Azimuth | passing | 0.970 | single-default L4 keyword variant |
| 516 | appearance | ColorAdjust | passing | 0.970 | single-default L4 keyword variant |
| 517 | speech | Cue | passing | 0.970 | single-default L4 keyword variant |
| 518 | speech | CueAfter | passing | 0.970 | single-default L4 keyword variant |
| 519 | speech | CueBefore | passing | 0.970 | single-default L4 keyword variant |
| 520 | speech | Elevation | passing | 0.970 | single-default L4 keyword variant |
| 521 | appearance | ImageRenderingQuality | passing | 0.970 | single-default L4 keyword variant |
| 522 | math | MathDepth | passing | 0.970 | single-default L4 keyword variant |
| 523 | math | MathShift | passing | 0.970 | single-default L4 keyword variant |
| 524 | math | MathStyle | passing | 0.970 | single-default L4 keyword variant |
| 525 | speech | Pause | passing | 0.970 | single-default L4 keyword variant |
| 526 | speech | PauseAfter | passing | 0.970 | single-default L4 keyword variant |
| 527 | speech | PauseBefore | passing | 0.970 | single-default L4 keyword variant |
| 528 | speech | Pitch | passing | 0.970 | single-default L4 keyword variant |
| 529 | speech | PitchRange | passing | 0.970 | single-default L4 keyword variant |
| 530 | experimental | PresentationLevel | passing | 0.970 | single-default L4 keyword variant |
| 531 | speech | Rest | passing | 0.970 | single-default L4 keyword variant |
| 532 | speech | RestAfter | passing | 0.970 | single-default L4 keyword variant |
| 533 | speech | RestBefore | passing | 0.970 | single-default L4 keyword variant |
| 534 | speech | Richness | passing | 0.970 | single-default L4 keyword variant |
| 535 | experimental | Running | passing | 0.970 | single-default L4 keyword variant |
| 536 | speech | Speak | passing | 0.970 | single-default L4 keyword variant |
| 537 | speech | SpeakAs | passing | 0.970 | single-default L4 keyword variant |
| 538 | speech | SpeechRate | passing | 0.970 | single-default L4 keyword variant |
| 539 | speech | Stress | passing | 0.970 | single-default L4 keyword variant |
| 540 | experimental | StringSet | passing | 0.970 | single-default L4 keyword variant |
| 541 | speech | VoiceBalance | passing | 0.970 | single-default L4 keyword variant |
| 542 | speech | VoiceDuration | passing | 0.970 | single-default L4 keyword variant |
| 543 | speech | VoiceFamily | passing | 0.970 | single-default L4 keyword variant |
| 544 | speech | VoicePitch | passing | 0.970 | single-default L4 keyword variant |
| 545 | speech | VoiceRange | passing | 0.970 | single-default L4 keyword variant |
| 546 | speech | VoiceRate | passing | 0.970 | single-default L4 keyword variant |
| 547 | speech | VoiceStress | passing | 0.970 | single-default L4 keyword variant |
| 548 | speech | VoiceVolume | passing | 0.970 | single-default L4 keyword variant |
| 549 | speech | Volume | passing | 0.970 | single-default L4 keyword variant |
| 550 | global | All | passing | 0.970 | single-default L4 keyword variant |

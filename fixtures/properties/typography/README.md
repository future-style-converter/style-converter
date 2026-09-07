# Typography fixtures (Phase 6)

CSS fixtures exercising every value variant accepted by the typography
parsers in `converter/src/main/kotlin/app/parsing/css/properties/longhands/typography/`.

Each fixture is one sub-topic. Run via:

```bash
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/properties/typography/<file>.json -o /tmp/p6"
```

## Coverage map

One fixture per property (or one per tightly-related cluster). All properties
present under `converter/src/main/kotlin/app/irmodels/properties/typography/` with a
registered parser are covered. All components converted cleanly (`0 generic`)
at authoring except for the known text-shadow color-first gap noted below; the
claim has not been re-run since. Fixture count:
`ls fixtures/properties/typography/*.json | wc -l`.

| Fixture | Properties |
|---|---|
| font-family.json | font-family (generic families, quoted, fallback list) |
| font-size.json | font-size (absolute keywords, relative keywords, lengths, %, calc) |
| font-weight.json | font-weight (keywords + numeric 100..900, including 350) |
| font-style.json | font-style (normal, italic, oblique [angle]) |
| font-stretch.json | font-stretch (9 keywords + percent) |
| font-variant-caps.json | font-variant-caps |
| font-variant-numeric.json | font-variant-numeric |
| font-variant-ligatures.json | font-variant-ligatures |
| font-variant-east-asian.json | font-variant-east-asian |
| font-variant-position.json | font-variant-position |
| font-variant-alternates.json | font-variant-alternates (functional forms) |
| font-variant-emoji.json | font-variant-emoji |
| font-feature-settings.json | font-feature-settings |
| font-variation-settings.json | font-variation-settings |
| font-kerning.json | font-kerning |
| font-optical-sizing.json | font-optical-sizing |
| font-synthesis.json | font-synthesis-weight / -style / -small-caps / -position |
| font-palette.json | font-palette |
| font-size-adjust.json | font-size-adjust (number, from-font, metric pairs) |
| font-size-min-max.json | font-min-size, font-max-size |
| font-language-override.json | font-language-override |
| font-display.json | font-display |
| font-named-instance.json | font-named-instance |
| font-smooth.json | font-smooth |
| line-height.json | line-height |
| line-height-step.json | line-height-step |
| letter-spacing.json | letter-spacing |
| word-spacing.json | word-spacing |
| text-align.json | text-align |
| text-align-last.json | text-align-last |
| text-align-all.json | text-align-all |
| text-justify.json | text-justify |
| text-indent.json | text-indent (length/%, hanging, each-line) |
| text-transform.json | text-transform |
| text-decoration-line.json | text-decoration-line |
| text-decoration-style.json | text-decoration-style |
| text-decoration-color.json | text-decoration-color |
| text-decoration-thickness.json | text-decoration-thickness |
| text-decoration-skip.json | text-decoration-skip |
| text-decoration-skip-ink.json | text-decoration-skip-ink |
| text-underline-offset.json | text-underline-offset |
| text-underline-position.json | text-underline-position |
| text-shadow.json | text-shadow (single, multi, blur, spread, rgba, multi) |
| text-wrap.json | text-wrap |
| text-wrap-mode.json | text-wrap-mode |
| text-wrap-style.json | text-wrap-style |
| white-space.json | white-space |
| white-space-collapse.json | white-space-collapse |
| word-break.json | word-break |
| overflow-wrap.json | overflow-wrap |
| word-wrap.json | word-wrap (legacy alias) |
| line-break.json | line-break |
| hyphens.json | hyphens |
| hyphenate-character.json | hyphenate-character |
| hyphenate-limit-chars.json | hyphenate-limit-chars (auto, 1/2/3 ints) |
| hyphenate-limit-last.json | hyphenate-limit-last |
| hyphenate-limit-lines.json | hyphenate-limit-lines |
| hyphenate-limit-zone.json | hyphenate-limit-zone |
| text-overflow.json | text-overflow (clip/ellipsis/fade()/string/two-value) |
| line-clamp.json | line-clamp |
| max-lines.json | max-lines |
| block-ellipsis.json | block-ellipsis |
| direction.json | direction |
| unicode-bidi.json | unicode-bidi |
| writing-mode.json | writing-mode |
| text-orientation.json | text-orientation |
| text-combine-upright.json | text-combine-upright (digits 2..4) |
| ruby-align.json | ruby-align |
| ruby-merge.json | ruby-merge |
| ruby-position.json | ruby-position (single + combined position+alignment) |
| ruby-overhang.json | ruby-overhang |
| text-emphasis-style.json | text-emphasis-style (keywords, fill+shape combos, custom char) |
| text-emphasis-color.json | text-emphasis-color |
| text-emphasis-position.json | text-emphasis-position |
| text-emphasis.json | text-emphasis (shorthand) |
| text-spacing.json | text-spacing |
| text-spacing-trim.json | text-spacing-trim |
| text-autospace.json | text-autospace |
| tab-size.json | tab-size (integer + length) |
| vertical-align.json | vertical-align (keywords, length, %) |
| vertical-align-last.json | vertical-align-last |
| alignment-baseline.json | alignment-baseline |
| baseline-shift.json | baseline-shift |
| baseline-source.json | baseline-source |
| dominant-baseline.json | dominant-baseline |
| dominant-baseline-adjust.json | dominant-baseline-adjust |
| glyph-orientation.json | glyph-orientation-horizontal + glyph-orientation-vertical |
| quotes.json | quotes |
| hanging-punctuation.json | hanging-punctuation (single + combinations) |
| initial-letter.json | initial-letter (size + optional sink) |
| initial-letter-align.json | initial-letter-align |
| orphans-widows.json | orphans + widows |
| text-rendering.json | text-rendering |
| kerning.json | kerning (SVG alias for font-kerning; accepts lengths) |
| line-grid.json | line-grid |
| line-snap.json | line-snap |
| caret-color.json | caret-color |
| text-anchor.json | text-anchor |
| text-box.json | text-box-edge + text-box-trim |
| text-group-align.json | text-group-align |
| text-size-adjust.json | text-size-adjust |
| text-space-collapse.json | text-space-collapse |
| text-space-trim.json | text-space-trim |
| word-space-transform.json | word-space-transform |
| color.json | color (text color families) |

## Validation

Every file above produces:

```
[CSS Parser] Parsed N properties (0 generic)
```

…except for one known parser gap in `text-shadow.json` (see below).

## Known parser gaps / `_skipped` entries

- **text-shadow `TextShadow_ColorFirst`**: the parser's shadow grammar
  requires `<offset-x> <offset-y>` *before* color, so `#333 3px 3px 6px`
  falls through to `GenericProperty`. Kept intentionally in the fixture so
  the gap stays visible; counts for the 1 `(1 generic)` line.

### Properties not covered (intentionally skipped)

No property under `converter/src/main/kotlin/app/irmodels/properties/typography/` has
been skipped — every registered parser has a fixture. Two items worth
naming explicitly:

- `_skipped: font-variation-settings` multi-value via `calc()` in the axis
  value — parser requires a plain `toDoubleOrNull()`, so `calc()` flavors are
  rejected and not included. Straight numeric axis values are covered.
- `_skipped: text-emphasis` with colour-first ordering — the shorthand
  parser only captures the *last* token as a colour via `ColorParser`, so
  placing the colour first silently drops the colour. Fixture uses
  colour-last form only.

# Style-engine rollout

Long-running plan to bring iOS, Android, and Web SDUI renderers to
exhaustive IR property coverage. Referenced from `CLAUDE.md`.

Ground rules live in `CLAUDE.md` under *Style-engine architecture*,
*Per-property contract*, and *Done definition for a property*. Read those
first. This file is the **execution plan** that sits on top.

## Canonical category set (mirrors irmodels)

```
animations      appearance       background       borders/
  ├─ sides          (width, color, style, per-side + logical)
  ├─ radius         (per-corner + logical)
  └─ image          (source, slice, width, outset, repeat)
color            columns          container        content
counters         effects/
  ├─ clip           (clip-path)
  ├─ mask
  ├─ shadow         (box-shadow, text-shadow)
  ├─ filter         (filter + backdrop-filter)
  ├─ shapes         (shape-outside, shape-margin…)
  └─ blend          (mix-blend-mode, background-blend-mode, isolation)
experimental     global           images           interactions
layout/
  ├─ advanced
  ├─ flexbox
  ├─ grid
  └─ position
lists            math             navigation       paging
performance      print            regions          rendering
rhythm           scrolling        shapes           sizing
spacing          speech           svg              table
transforms       typography
```

Every platform's style engine has this tree. Empty categories keep a
`README.md` explaining what goes in them.

## Phase matrix

Phases are **sequential in their dependencies**: Phase 1 requires Phase 0,
Phases 2–10 require Phase 1. Within Phase 0 and within each category
phase, work is parallel.

| # | Phase | Files | Parallelism | Status |
|---|---|---|---|---|
| 0 | Architectural scaffold | all three trees + registries + ROLLOUT.md | 3 agents (one per platform) | ✓ done (commit a037481) |
| 1 | Primitive extractors (length, color, angle, time, number, keyword) | ~6 shared files × 3 platforms | 1 agent per platform × 3 | ✓ done (b8e4a3f) |
| 2 | spacing (26) | padding, margin, gap, scroll-padding/margin | 1 agent × 3 platforms | ✓ done (c3f6a91 / ee1a5d1) |
| 3 | sizing (7) | width, height, min/max, aspect-ratio, block/inline-size | 1 agent × 3 | ✓ done (ea874c7) |
| 4 | colors + background (37) | bg color, gradients, image, position, size, repeat | 2 agents (colors, images) × 3 | ✓ done (1e38189) |
| 5 | borders (47) | widths, colors, styles, radius, image | 3 agents (sides, radius, image) × 3 | ✓ done (07f3335) |
| 6 | typography (110) | fonts, text align, decoration, variants, emphasis, writing-mode | 4 agents × 3 | ✓ done (02322e4) |
| 7 | layout (61) | flexbox, grid, position, advanced | scaffold + impl (7 agents × 3) | ✓ done (0d9f8c6 / 885c775) + hotfix ff901e3 |
| 8 | effects + transforms (38) | shadow, filter, clip-path, mask, transform, 3D, motion | 3 agents × 3 | ✓ done (33c435e) |
| 9 | animations + transitions + timelines (29) | all timing + state + view/scroll-timeline | 1 agent × 3 | ✓ done (bf9251b) |
| 10 | long tail (~150) | scrolling, svg, tables, lists, columns, counters, container, speech, regions, navigation, math, paging, print, performance, shapes, rhythm, images, appearance, content, global, experimental | 3 agents × 3 (multi-category sweep) | ✓ done (f15e51c) |
| 11 | baseline harness + docs + coverage matrix | final sweep: testing/coverage-audit.mjs + testing/COVERAGE.md + README/CLAUDE.md refresh | serial | ✓ done (this PR) |

## Fan-out protocol per phase

For each category phase:

1. **Fixture agent** writes `examples/properties/{category}/{property}.json` with
   one component per CSS value variant the parser supports. See
   `src/main/kotlin/app/parsing/css/properties/longhands/{category}/` for
   the exhaustive list — NEVER guess, read the parser.
2. **Implementation agents** (one per property family, per platform) write
   the `Config/Extractor/Applier` triplet in the canonical subfolder.
   Rules: short file, fully commented, registered with `PropertyRegistry`.
3. **Verification**: run `./test-all.sh examples/properties/{category}/{property}.json`
   and review `testing/report/index.html`.
4. **Iterate** until every variant in the fixture hits SSIM ≥ 0.95 across
   all three pairs.
5. **Baseline + commit**: `UPDATE_BASELINE=1 ./test-all.sh …` for that
   fixture, commit captures + baseline + code in one PR.

## Resume points

If context runs out mid-phase, pick up here:

- **Did Phase 0 complete?** Check: all three platforms have the canonical
  folder tree under `style/`, `StyleEngine/`, `style/engine/` respectively,
  each with a `PropertyRegistry`. The pre-refactor Android + current
  Android MUST produce byte-identical screenshots (test via the
  baseline committed at start of Phase 0).
- **Phase 1**: each platform's extractor file for `length` handles every
  unit in `ValueTypes.kt`'s `LengthUnit` enum, verified via
  `examples/primitives-test.json`.
- **Phase N category**: read `testing/baseline/` and the comparison
  report — categories that show ≥ 0.95 SSIM across pairs are done.

## Coverage status (final, Phase 11)

Run `node testing/coverage-audit.mjs` at any time to get a live per-category
matrix. Current totals against the 550-property IR catalogue
(33 categories):

| platform | claimed | coverage |
|---|---:|---:|
| Android | 550 / 550 | 100% |
| iOS | 550 / 550 | 100% |
| Web | 550 / 550 | 100% |

(Round 67: re-ran `node testing/coverage-audit.mjs` — all three platforms
now claim the full 550-property IR. Was 545/545/533 in the original
Phase-11-final snapshot; the three previously-unclaimed L4 properties
(`BackgroundPositionBlock`, `BackgroundPositionInline`, `DynamicRangeLimit`)
have since been wired in. testing/COVERAGE.md and testing/README.md
both confirm 550/550/550.)

The unclaimed-list gate from the original Phase 11 plan is now naturally
satisfied — every IR property is claimed on every platform. Whether to
add `coverage-audit.mjs` to CI as a non-blocking check is a separate
follow-up; for now manual `node testing/coverage-audit.mjs` is the
canonical re-verification.

## Non-goals (don't do this)

- Don't re-implement CSS parsing inside the style engines. The engines
  consume already-parsed IR; if a value isn't in the IR, it means the
  PARSER needs work, not the engine.
- Don't split shared primitives across categories. Colors,
  lengths, angles, etc. live in ONE shared extractor per platform and
  every category imports from it.
- Don't merge Config/Extractor/Applier into a single file "for
  brevity" — the separation is a coverage-audit tool. `git ls-files
  **/*Config.*` should produce the property inventory.

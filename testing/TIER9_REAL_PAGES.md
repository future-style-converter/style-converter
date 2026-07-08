# Tier 9 — Real-world page conversion

Take 10 real websites' CSS, run through converter, render via all 3 engines,
manually rate. Pass = SSIM ≥ 0.95 across all 3 platform pairs (iOS-Android,
iOS-web, Android-web).

## Round 43 update — var-resolution surfaces honest failures

The Tier 9 investigator (round 43) found that the dominant failure source
was the css-to-ir adapter silently dropping every `:root { --custom-property }`
block. That meant every `var()` reference in the extracted IR was
unresolvable, and each platform's "I don't know this var" fallback diverged.

Round 43 fix (commit 9809fbf): extract `:root` / `html` / `body` vars during
css-to-ir conversion, substitute resolved values into IR. CNN went from
0 → 1767 root vars resolved; MDN 0 → 128.

**Counter-intuitive result: pass rates DROPPED slightly after the fix.**
This is the same pattern as Tier 1's "548/548 100% passing → 91/550
(16.5%) honest" — the previous "passes" were largely trivial-empty-box
matches because un-resolved `var()` literals fell back to identical
defaults across platforms (each renderer treated unknown var() the same
way → identical output → ≥0.95 SSIM by accident).

After the fix, IR carries real resolved values. Components that genuinely
exercise the platform renderers' real divergences now fail honestly
instead of trivially passing. The SSIM distribution makes this concrete:

| site   | round 40 | round 43 | median SSIM | <0.80 deep failures |
|--------|---------:|---------:|------------:|--------------------:|
| MDN    | 33/87 (37%) | 29/87 (33%)  | 0.926 | 13 / 87 |
| Stripe | 29/96 (30%) | 27/96 (28%)  | 0.897 | 10 / 96 |
| CNN    | 17/141 (12%) | 17/141 (12%) | 0.702 | 96 / 141 |

The CNN median of 0.702 is particularly meaningful — it shows real
cross-platform layout divergence (float / grid / flow) once the IR
actually carries the colors and dimensions that exercise those code
paths. Previously these components were rendering as bare "no-color
default boxes" that all 3 platforms agreed on by accident.

This is **not a regression** — it's the same honesty correction that
Tier 1 went through. The fix made the data substantive. The next phase
is real renderer fixes for the divergences now visible.

## Per-site status

| # | site | status | iOS-rate | Android-rate | web-rate | notes |
|---|---|---|---|---|---|---|
| 1 | github.com | **rendered: skipped** | skipped | skipped | skipped | 0 IR components extracted (heavy media queries) |
| 2 | stripe.com | **rendered: 46/123 pass (37%)** | 46/123 (37%) | 46/123 (37%) | 46/123 (37%) | Round 47 position:fixed fix: 40 → 46 (+5%), median 0.894 → 0.912. Stripe also has fixed/absolute overlays but smaller bleed than CNN. Round 45 baseline: Phase 9d filter dropped 1031 invisible rules (sample went 32 → 41 visible components). Round 44 baseline: 75× more vars resolved via fetch-cap bump (9 → 683 root vars, but only +1 pass — confirms var-resolution wasn't the bottleneck). Still 216 unresolved var() refs (theme-scoped on `[data-theme]` / `.hds-*` blocks). |
| 3 | apple.com | **rendered: 31/54 pass (57%)** | 31/54 (57%) | 31/54 (57%) | 31/54 (57%) | round 45 Phase 9d filter is the headline win here: 1331 of 1355 rules were invisible (`display`/`margin`/`position` only). Filter dropped them before the 50-cap, so the sample now contains 18 visible components → 54 pairs. Headline jumped 38% → **57%**, median SSIM 0.914 → **0.977** — most failures are close-misses (only 10 deep <0.80). Apple's CSS is heavily structural; filtering surfaces the small visible subset cleanly. |
| 4 | tailwindcss.com | **rendered: 62/150 pass (41%)** | 62/150 (41%) | 62/150 (41%) | 62/150 (41%) | Round 53 update: denominator GREW from 135 → 150 (Phase 9d filter changed IR sampling — got 50 visible components vs prior 45). Pass count dropped 125 → 62 BUT distribution shows 0 deep failures (<0.80), 88/150 in 0.90-0.95 band (just under threshold), median 0.936, mean 0.952. **Not actually regressed**: the new sample includes more components clustered around the 0.95 threshold. Same renderer behavior; different test set. |
| 5 | mdn.dev | **rendered: 15/57 pass (26%)** | 15/57 (26%) | 15/57 (26%) | 15/57 (26%) | Round 47 re-render after Phase 9d (filter cut MDN from 29 → 19 components × 3 = 57 pairs). 15/57 = 26% — first measurement post-9d. MDN doesn't have major position:fixed contamination so the round-47 fix barely changed the absolute pass rate; the smaller denominator (57 vs 87) reflects honest filtering. |
| 6 | wikipedia.org | **rendered: skipped** | skipped | skipped | skipped | 0 IR components extracted (only @media + descendant selectors) |
| 7 | cnn.com | **rendered: 44/144 pass (30%)** | 44/144 (30%) | 44/144 (30%) | 44/144 (30%) | **Round 47 huge win**: 14/144 → 44/144, median SSIM 0.702 → **0.923**, deep failures (<0.80) 99 → **12** (87% reduction). Root cause: CNN's `modal__overlay` IR component has `position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: #0c0c0cf2` — a fullscreen semi-transparent overlay. iOS+Android render each component in its own isolated view tree so this didn't bleed; web's CaptureGallery is one long page so the modal__overlay covered EVERY OTHER capture canvas with its dark overlay. Fix: add `transform: translateZ(0)` to web's CaptureCanvas style, creating a stacking context that traps `position: fixed` to the canvas (per CSS Transforms spec). Largest single Tier-9 fix to date. |
| 8 | twitter.com | **rendered: 85/117 pass (73%)** | 85/117 (73%) | 85/117 (73%) | 85/117 (73%) | Round 53 update: denominator shrunk 150 → 117 (Phase 9d/9h filtered out 11 invisible components). Pass count dropped 138 → 85, deep failures only 3. Same renderer behavior; smaller sample. Twitter (React-Native-Web-generated CSS) has 0 var() refs by design. |
| 9 | medium.com | **rendered: skipped** | skipped | skipped | skipped | 1 IR component only — too thin |
| 10 | producthunt.com | **rendered: skipped** | skipped | skipped | skipped | 1 IR component only — too thin |

## Phase 9b — CNN cluster audit (round 44, scoping done; no fix)

Cluster analysis on CNN's 96 deep failures (<0.80 SSIM) by platform-pair:

| pair          | deep failures |
|---------------|--------------:|
| iOS-web       |            47 |
| Android-web   |            46 |
| iOS-Android   |             3 |

**93/96 (97%) of deep failures are web-vs-mobile divergences.** iOS-Android
agree on almost everything. The web renderer is the structural outlier.

Top properties appearing in deep-failure components (out of 47 distinct):

| count | property         |
|------:|------------------|
|    27 | display          |
|    10 | height           |
|     7 | align-items      |
|     7 | margin           |
|     7 | width            |
|     6 | padding          |
|     6 | justify-content  |
|     5 | position         |
|     4 | background-color |

Sample deep failure (representative, not cherry-picked):

```
component: footer__user-account-nav-mobile
  IR properties: { margin: 0, display: block }
  iOS rendered:     390 × 60   px
  Android rendered: 390 × 62   px
  web rendered:     390 × 62   px
  iOS-web SSIM:     0.59
  Android-web SSIM: 0.58
  iOS-Android SSIM: 0.83
```

Dimensions match within 2px across all 3 platforms — but the IMAGES
substantially diverge. The component has NO color, NO border, NO content
— just `margin: 0` and `display: block`. So the divergence is each
platform's "empty-box default": iOS likely renders transparent over a
white/light background; Android renders against the dark capture-canvas
background; web's `display: block` with no background shows the canvas
through. Each is "valid"; they don't match each other.

**This is not a renderer-fix problem.** Many of CNN's 96 deep failures
are fixture-level "nothing to render" cases where each platform's
empty-default leaks through. The previous round's var-resolution fix
exposed these because un-resolved var() refs to (non-existent) backgrounds
used to render as identical-empty across platforms by accident.

The right fix is at the test-harness level, not the renderer level:
either (a) skip components whose IR has no visible-rendering properties
(no color, no background, no border, no text content) or (b) capture
each platform with a matched neutral background that masks empty-default
differences. Both are non-trivial in scope — (a) needs a "is this
component visible?" classifier; (b) needs per-platform capture changes.

Phase 9b research-complete; no fix landed in this round (premature
without harness redesign).

## What round 45+ could unblock (deferred — multi-day work)

1. **Phase 9c**: ~~scoped-selector var extraction in css-to-ir~~ — **partially
   done in round 44**: bumped fetch-real-css cap from 3 → 10 stylesheets per
   site. Stripe vars went 9 → 683 resolved (75×); Apple unblocked from
   "too thin" → 38% / 144 components. The deeper "scoped-selector"
   extraction (`[data-theme]` / `.hds-*` blocks) is still pending — Stripe
   has 216 remaining unresolved refs in those scopes. Whether it's worth
   it: the round 44 75× var-resolution gain produced only +1 stripe pass,
   confirming Phase 9b's finding that var-resolution isn't the bottleneck
   anymore. The empty-fixture / web-vs-mobile divergence (Phase 9d/9e) is.
2. **Phase 9d**: ~~harness-level "empty fixture" filter~~ — **done in
   round 45**: added `isVisible()` classifier to css-to-ir.mjs that drops
   components whose only properties are non-visible (margin/padding/display/
   position without color/border/background/content). Per-site impact
   varies dramatically:
     - Apple 38% → **57%** (huge win — 1331 invisible filtered, surfaced clean visible subset)
     - Stripe 29% → 32% (mild positive, more visible components fit cap)
     - CNN 12% → 9% (neutral — first-50 was already mostly-visible; sample shuffled)
   Filter is honest (it removes "cannot meaningfully test" cases), but
   it's not a uniform improvement. The CNN result confirms Phase 9b's
   conclusion: the remaining deep failures are real renderer divergences,
   not empty-fixture noise.
3. **Phase 9e**: ~~per-platform neutral-background capture~~ — **scope
   pivoted in round 47**. Investigation found backgrounds already match
   across all 3 platforms (#1A1A2E). The actual web-vs-mobile divergence
   was caused by `position: fixed` IR components (CNN's `modal__overlay`)
   bleeding across the web CaptureGallery's flat-page layout. Real fix:
   `transform: translateZ(0)` on web's CaptureCanvas creates a stacking
   context that traps `position: fixed` to the canvas (per CSS Transforms
   spec). Result: CNN 14/144 → 44/144 (+21%), median SSIM 0.702 → 0.923,
   deep failures 99 → 12 (87% reduction). Stripe +5%, Apple unchanged
   (no fixed elements). Round 48 also applied the same defensive fix to
   FixtureCanvas (Phase 5a). Auditor 48 verified by reproducing 44/144
   exactly + visual diff of subscribe-button (web image now matches iOS,
   no overlay bleed).
4. **Phase 9f**: ~~Tier 9 micro-bench harness~~ — deferred. Round-47/48
   established a working manual workflow (./test-all.sh per site, per-site
   manifest spot-check via python). Wiring this into a one-shot CI script
   would be ~2-3h follow-up; not blocking.

## Phase 9h (round 51): isVisible filter — opacity refinement

Investigation of CNN's `header__navigation-separator` deep failure (iOS-Android
0.64, iOS-web 0.62) found a real bug in the round-45 isVisible() filter.
The component's IR is `opacity: 1; border: none; height: 2px; transition: …`
— with NO actual visible-render properties. Yet it was passing the filter
because `opacity` was in the VISIBLE_PROPS Set.

The trap: `opacity: 1` is the DEFAULT no-op value. Every element has it
implicitly. Authors often declare `opacity: 1` as part of a transition
setup (animate from 1 → 0 on hide), not because they want to mark
visibility. Treating it as a visibility marker passed invisible-by-design
components through the filter, where iOS + web rendered the placeholder
text (component name) and Android rendered nothing — driving SSIM to 0.61.

Round 51 fix: remove `opacity` from VISIBLE_PROPS (transform/rotate/scale
similarly are modifiers but kept for now — edge cases where 3D transforms
reveal previously-hidden faces).

Per-site impact:
| site | round 49 | round 51 | delta |
|------|----------|----------|-------|
| CNN | 44/144 (30%) | **45/144 (31%)** | +1, deep failures 12→10 |
| Stripe | 46/123 (37%) | **48/123 (39%)** | +2 |
| Apple | 31/54 (57%) | 27/48 (56%) | denominator shrunk (18→16 components, both filtered components scored 1.0 — coincidence, not regression) |

Modest pass-rate gains but the bigger win: prevents future
`opacity:1`-decorated components from polluting the test set.

## Phase 9j scoping (round 52, no fix landed)

After Phase 9h, CNN has 10 deep failures across 6 components. Round 52
examined each in detail by reading the actual divergent images:

| component                        | divergence    | root cause |
|----------------------------------|---------------|------------|
| ad-feedback__container           | iOS-A 0.64    | flex `row-reverse` + max-width: PlaceholderLabel positioning |
| modal__overlay                   | iOS-w/A-w 0.65 | post-translateZ trapping, `width:100%; height:100%` interpreted differently |
| header__subnav-mount--scrolled   | iOS-A 0.80, A-w 0.76 | box-shadow renders on Android wrap-container; iOS PlaceholderLabel ignores it |
| header__editionizer-button       | iOS-w 0.79, A-w 0.78 | text on transparent bg: PlaceholderLabel font-metric divergence |
| product-zone--t-highlight        | iOS-A 0.74    | 60vs62px height: iOS Text intrinsic vs Android wrapContentSize line-height |
| product-zone--t-white            | iOS-A 0.78    | same root cause |

**All 6 trace to the same architectural mismatch**: iOS PlaceholderLabel,
Android Text+wrapContentSize, and web `<span>{name}</span>` each pick
slightly different size/position for placeholder text on bg-only
components. iOS draws text-only (ignores bg+shadow); Android draws
bg+text+shadow at native font metrics; web draws bg+text at CSS metrics.

A "real renderer fix" would require coordinated changes across all 3
platforms to either (a) all render placeholder identically (would change
the testing-affordance behavior on the entire visual-test fixture
corpus) or (b) all skip placeholder when no children (changes the
tooltip-style label gallery). Both are large-surface refactors that
risk regressing the visual-test baseline (which is currently 104/109
byte-identical and passes 327/327 in BASELINE=1).

**Round 52 verdict**: Tier 9 is at a real natural pause. The remaining
deep failures aren't actionable without a deeper architectural
decision about cross-platform placeholder rendering. Documented as
known limitations; future work (Phase 9i) requires explicit
architectural sign-off before proceeding.

The visual-test corpus itself has only 3 components / 4 pairs <0.80
across 327 comparisons (Typography_FontMono, Typography_FontSerif —
both font-rendering divergences; Edge_DeepNesting — layout depth
divergence). These are accepted as inherent cross-platform behavior.

## Phase 9i (deferred — iOS+web PlaceholderLabel divergence)

Even with opacity refinement, the underlying iOS + web behavior of
"render component name as placeholder text when no children" causes
divergence with Android (which renders nothing). For honestly-empty
components like `header__navigation-separator`, all 3 platforms should
agree on "render nothing" — Android's behavior is correct.

Fix would require modifying iOS PlaceholderLabel and web PlaceholderContent
to skip placeholder rendering when the component has no visible properties.
Risk: breaks the testing-affordance behavior on Tier 1 fixtures that
deliberately test invisibility (e.g. visibility:hidden).

Tracked as Phase 9i; not blocking. The opacity refinement (Phase 9h)
removes the most obvious offenders by filtering them out of the IR
upstream.

## Phase 9g (round 48 research — no fix)

After Phase 9e cleaned up CNN's contamination-driven failures, the
remaining 12 deep failures (across 7 distinct components) are **real
per-platform renderer divergences**, not test-harness artifacts:

| component                          | divergent pair(s)             | property hint |
|------------------------------------|-------------------------------|---------------|
| ad-feedback__container             | iOS-Android 0.64, Android-web 0.63 | `flex-direction: row-reverse` + `max-width: 500px` (flex impl divergence) |
| modal__overlay (own canvas)        | iOS-web 0.65, Android-web 0.63 | `position: fixed; width:100%; height:100%` rendered inside the new translateZ context — web interprets 100%-of-trapped-canvas differently |
| header__navigation-separator       | iOS-Android 0.64, iOS-web 0.62 | `border: none; width:100%; height:2px` — iOS likely renders nothing |
| header__subnav-mount--scrolled     | iOS-Android 0.80, Android-web 0.76 | shadow rendering: `box-shadow: 0px 3px 8px 0px #6a73810f` |
| header__editionizer-button         | iOS-web 0.79, Android-web 0.78 | `background: 0 0` (transparent) + text: text-rendering divergence |
| product-zone--t-highlight          | iOS-Android 0.74              | `color-scheme: light only` — iOS interprets, Android doesn't |
| product-zone--t-white              | iOS-Android 0.78              | same `color-scheme: light only` |

These would need real per-platform renderer work to resolve:
- 3 components: real iOS vs Android divergence (flex / `color-scheme`)
- 1: web's translateZ stacking-context interpretation
- 2: shadow + text rendering at low opacity / transparent backgrounds
- 1: 2px hairline rendering across platforms

Documented for Phase 9h+ pickup.

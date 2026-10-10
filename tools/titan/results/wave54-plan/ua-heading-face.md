# ua-heading-face — wave-54 family brief (BACKLOG 0(g); supersedes the 4(b′) mechanism for inset-005/-006/-014)

Evidence run: `tools/titan/runs/wave53-final` (dev tip 7cce3b22). Every score is `node tools/titan/results/wave52-gate/cells.mjs
'<terms>' wave53-open wave53-final` (the two runs agree on every cell quoted here). Every PNG named below was opened
(ref + web + iOS + Android side by side) and its heading ink was measured with `ua-heading-face.geometry.py` (dark-ink row
bands, `geometry_common.bands`); the output is `ua-heading-face.geometry.out.txt`. The census is
`ua-heading-face.census.mjs` → `ua-heading-face.census.json` / `.census.out.txt` (all 1435 per-test IR documents of
wave53-final, joined to the scored cells through `score-gate.mjs`'s own loader). Nothing was built, run or captured: the
`wave54-open` gate was live.

**Summary.** The natives paint an author-unsized `<h1>`/`<h2>`/`<h3>` at the 16 px (13 px monospace) REGULAR body face
where the ref paints the UA `2em/1.5em/1.17em bold` face:
- **Android, everywhere.** There is no Compose twin of `UAElementFontRule.swift`; BACKLOG 0(g) has queued it as "STILL
  DEFERRED" since the retro.
- **iOS, whenever the heading hosts a child box.** `UAElementFontRule.headingAppliesTo(hasElementChildren:)` stands the
  heading half down for any heading with element children. Wave 40 measured that gate when a heading's runs were painted
  as a STACK of labels (`inset-014 0.9317 → 0.9150 WORSE`). Since the wave-44/45 inline fold, the same hosts paint as ONE
  paragraph, and the gate now withholds the face from exactly the hosts it would fix. The gate's own doc says "when it
  lands, this gate should be deleted, not widened".
- **The extractor bakes the face only for collapsed runs.** `collapseInlineRun` writes `UA_H1_PROPS` (FontSize 32,
  FontWeight 700, MarginTop/Bottom 21.44; lossy `ua-heading-defaults`) only on an `<h1>` whose inline chain COLLAPSED.
  That is why `text-decoration-inset-001…004` pass on both natives, while `-005/-006/-011/-014` (the chain did not
  collapse, so the h1 is a `meta.runs` host) carry no face at all.

## 1. Target cells (wave53-final)

| cell | ios | android | web | role |
|---|---|---|---|---|
| css-break/block-in-inline-015-print | **P 0.9894** | **f 0.9489** | P 1 | leaf `<h1>` ×4. iOS already has the rule (the anchor); Android is the target |
| css-text-decor/text-decoration-inset-005 | **f 0.8995** | **f 0.9** | P 0.9983 | folded `<h1>` host (u > sup/sub) |
| css-text-decor/text-decoration-inset-006 | **f 0.8987** | **f 0.8993** | P 0.9985 | folded `<h1>` host |
| css-text-decor/text-decoration-inset-014 | **f 0.9238** | **f 0.923** | P 0.9975 | folded monospace `<h1>`, `width: 16ch` |
| css-text-decor/text-decoration-inset-011 | f 0.6999 | f 0.6852 | P 0.9948 | **CONTROL**: runs host whose fold BAILS (see §4) |

**Anchors for "correct" (same abspos 2em-bold `<h1>`, face baked by the extractor):**

| cell | ios | android |
|---|---|---|
| text-decoration-inset-001 | P 0.9707 | P 0.9848 |
| text-decoration-inset-002 | P 0.9706 | P 0.9858 |
| text-decoration-inset-003 | P 0.9706 | P 0.984 |
| text-decoration-inset-004 | P 0.9634 | P 0.9859 |

## 2. The picture (390×600; bands = dark-ink rows below the instruction prose)

- **block-in-inline-015-print.** Ref and web: four bold lines "one / two / three / four".
  - Ref bands: y29–46 (x17–72), y65–86 (x16–73), y103–126 (x16–96), y142–166 (x16–79).
  - iOS bands: y30–47 x17–72, y66–87 x16–72, y105–127 x16–96, y144–167 x16–79. Geometry OK.
  - **Android** paints four 16 px REGULAR lines: y23–31 x17–42, y42–51 x17–42, y61–71 x17–53, y80–91 x17–45. The band
    heights are 9–12 px against the ref's 18–25 px, and the right edges sit at x42–53 against x72–96.
- **text-decoration-inset-005 / -006.**
  - Ref and web: the heading wraps onto TWO lines, "the ultra-quick b(row)n" in band y134–173 (x16–336, sup raised, sub
    lowered) and "fox" in band y188–212 (x16–64).
  - Both natives: ONE 16 px regular line, band y110–131, x16–191 (-005) / x16–194 (-006). The sup is raised and the sub
    lowered, inside ONE paragraph. So the fold IS engaged on both natives; what is missing is the face.
- **text-decoration-inset-014.**
  - Ref and web: "the ultra-quick / brown fox" in 26 px bold DejaVu-class monospace, bands y129–154 (x17–258) and y163–186
    (x16–155).
  - Natives: the SAME two-line wrap at 13 px regular, bands y111–121/122 (x17–133) and y127/128–137/138 (x16–84). The
    natives wrap the folded paragraph at 16ch exactly like the ref, but at half the face.
- **text-decoration-inset-011 (control).** The natives paint the Arabic runs as STACKED lines (bands at y137, 169, 208,
  265, 297) with no underline colours. This is the fold's refusal, not the face. See §4.

## 3. The wire (`wave53-final/sections/css-text-decor/per-test-ir/`)

- **inset-001 `<h1>`** (collapsed chain; lossy `body-inherited-baked`, `ua-heading-defaults`, `inline-chain-collapsed`):
  - `FontSize {"px":32}`, `FontWeight {"weight":700}`, `MarginTop/Bottom {"px":21.44}`;
  - `meta.decorations [{line: underline, color: black}]`;
  - no `runs`.
- **inset-005 `<h1>`** (lossy `body-inherited-baked`, `em/rem` only):
  - `Position ABSOLUTE` and nothing else;
  - `meta.runs [{text:"the "},{child:u},{text:" fox"}]`;
  - the `u` member is itself a runs host `[ultra-, sup, " b", sub, n]` carrying `TextDecorationColor black`.
- **inset-014 `<h1>`:** `Position ABSOLUTE`, `FontFamily ["monospace"]`, `Width {v:16,u:CH}`, same runs shape.
- **inset-011 `<h1>`:**
  - `LineHeight ×2`, `Width {v:10,u:EM}`, `Direction RTL`;
  - runs `[u.a, " —", br, " ", u.b]`;
  - members carry `TextDecorationColor` blue / green and `TextUnderlineOffset 20px`.
- **block-in-inline-015-print:** four leaf `<h1>` ("one" … "four") with author `margin: 0`, no size.

## 4. Mechanism (traced; file:line under 7cce3b22)

**A. Extractor — why some headings carry the face and others do not.**
- `tools/titan/extract-fixture.mjs` `collapseInlineRun` (:3166-3172) sets
  `uaHeading = { ...UA_H1_PROPS }` only on an `h1` collapse root that declares none of `UA_H1_GUARD_PROPS`.
- The node is stamped at :4477 (`node.uaHeadingProps`), merged into the props at :11276, and marked lossy
  `ua-heading-defaults` at :11365.
- A heading whose inline chain does NOT collapse never reaches that branch. It keeps `meta.runs` and gets nothing. The
  extractor otherwise "models no UA sheet". That split is the 14 face-carrying vs 8 face-less heading documents in §6.

**B. iOS — the stand-down.** The path, in order:
1. `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` `mergedProperties(now:)`
   (:298-356) calls `UAElementFontRule.apply(sourceTag:own:merged:hasElementChildren: component.children?.isEmpty == false)`.
2. `StyleEngine/typography/UAElementFontRule.swift` `apply` asks `headingAppliesTo(hasElementChildren:)`, which returns
   `!hasElementChildren`. The `<h1>` hosts a `u` child, so the heading half stands down.
3. The fold (`ComponentRenderer.swift` :3383-3392, `InlineRunFlow.fold(runs:children:totalChildCount:containerProperties:
   resolvedProperties, …)`) is ENGAGED: the picture shows one paragraph with a shifted sup and sub.
4. It paints through `PlaceholderLabel` with `foldTextConfig = style.text` (:3446-3453). That is the 16 px regular config,
   because step 2 withheld the face.
5. Leaf headings (block-in-inline-015-print) take the rule. That is why iOS is P 0.9894 there.

**C. Compose — no twin.** The path, in order:
1. `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` :1141 runs the only
   tag-keyed UA cascade step on Compose: `rawProperties = ListStyleUaRule.apply(component._tag, allReset.own,
   mergeInherited(allReset.own, allReset.inherited))`.
2. There is no font step, so `effectiveProperties` has no FontSize or FontWeight for an unsized heading.
3. `mergedComponent = component.copy(properties = effectiveProperties)` (:1841) is what `RenderContent` receives.
4. The fold (`InlineRunFold.fold(entries, children, hostProperties = component.properties, …)`, :3839) therefore sees
   the merged list:
   - the host's inherited `Color black` is the ink base that admits the `u` member's `TextDecorationColor black`;
   - the paragraph's TextStyle is 16 sp regular.
5. The leaf `<h1>`s of block-in-inline-015-print get the same 16 sp regular.

**D. Why gating on "the fold engages" is sound.** The fold's admission never reads the host's FontSize or FontWeight:
- Compose `InlineRunFold.fold` reads the host's `TextTransform` (:313), writing mode (:318), `Hyphens` (:334),
  `FontVariantCaps` (:555) and, through `InlineSpanRing.admit`, `Color` (:280).
- iOS `InlineRunFlow.fold` reads the same set (:298 / :305 / :312 / :468) and `InlineSpanRing` reads `Color` (:251).

So the verdict computed on the PRE-UA merged list equals the verdict on the post-UA list, and no circularity exists.

**E. Correction to BACKLOG 4(b′).** 4(b′) says the inset-005/-006/-014 members "refuse ONLY because neither the member nor
the fold host declares `color`".
- At wave53-final the div above each `<h1>` carries `Color black` (lossy `body-inherited-baked`).
- Both natives fold with the inherited ink as the base. The evidence is the pictures (§2) and the merged host list
  (Compose :1841, iOS `resolvedProperties`).
- The residual defect on these three tests is the heading face, not the decoration colour.
- The lane's first JVM and Catalyst pins (§8) re-establish the fold verdict on the verbatim wire before any face is
  applied. If a pin says `Bailed`, this brief's iOS/Android target rows for 005/006/014 are void and the lane stops at U1.

## 5. The fix

**U1 — Compose twin, leaf AND folded hosts.**
- New `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (≤ 200 lines), the
  twin of the Swift rule:
  - the `h1…h6` multiplier `2 / 1.5 / 1.17 / 1 / .83 / .67` with bold;
  - `sub` / `sup` at `1/1.2`;
  - the `em` base is the inherited FontSize px, else `MonospaceUAFontSize.resolveSpFromPairs(…) ?: 16`. That is why the
    monospace `<h1>` is 26 px in the ref, not 32;
  - the author's own `FontSize` / `Font` / `FontWeight` wins (css-cascade-4 §6.1);
  - in-place substitution (first entry replaced, later ones dropped).
- New `typography/UAHeadingFoldGate.kt` (≤ 120 lines): `standsDown(component, mergedPreUa, lang)`. It is true iff the
  heading has element children AND (`meta.runs` is absent OR `InlineRunFold.fold(…)` on `InlineRunPlan` entries returns
  `Bailed`).
- Seam hunk `seam-1.patch` (Compose `ComponentRenderer.kt` :1141): wrap the `ListStyleUaRule.apply(…)` result in
  `UAElementFontRule.apply(component._tag, allReset.own, <that>, standsDown = UAHeadingFoldGate.standsDown(…))`.
  - This sits BEFORE `DynamicValueResolver` (:1206), so `16ch` and `em` resolve against the UA face, exactly as in the ref.
- Spec:
  - HTML §15.3.7 / Chromium `html.css` (`h1 { font-size: 2em; font-weight: bold }`; `sub, sup { font-size: smaller }`);
  - css-cascade-4 §4.3 (a UA declaration beats inheritance) and §6.1 (it loses to the author);
  - css-fonts-4 §2.5 (`smaller`).

**U2 — iOS: retire the stacked-era stand-down for folded hosts.**
- New `StyleEngine/typography/UAHeadingFoldGate.swift` (≤ 120 lines) with the same predicate, calling
  `InlineRunFlow.fold(…)` with the call-site's inputs.
- Seam hunk `seam-2.patch` (`ComponentRenderer.swift` `mergedProperties(now:)` :343-356): the `hasElementChildren:`
  argument becomes `UAHeadingFoldGate.standsDown(component:inFlowChildren:mergedPreUA:)`, where `inFlowChildren` is
  :781.
- `UAElementFontRule.swift` (274 lines, over budget) gets doc-only edits: the `headingAppliesTo` measurement table is
  re-dated, and TWIN STATUS points at the Kotlin file.

**Rejected: baking the face in the extractor for every runs-host heading.**
- It is a seam-heavy change that edits the IR bytes of 4 documents. It would also hand the 2em face to `inset-011`'s
  STACKED fallback, the measured wave-40 WORSE case.
- And it leaves the runtime gap in place for every future, non-WPT SDUI heading. The runtimes are the product.

## 6. Corpus radius (census over 1435 per-test IR documents, `ua-heading-face.census.out.txt`)

**24 documents carry `h1–h6` or `sub`/`sup`.** They break down as follows.

**8 carry an author-UNSIZED heading.** These are the lane's carriers.

| document | shape | web / ios / android |
|---|---|---|
| css-break/block-in-inline-015-print | h1 leaf ×4 | P 1 / P 0.9894 / f 0.9489 |
| css-lists/counter-list-item | h2 leaf | P 0.9975 / f 0.7864 / f 0.7307 |
| css-lists/counter-reset-increment-overflow-underflow | h3 leaf ×6 | P 0.997 / f 0.857 / f 0.8699 |
| css-text-decor/text-decoration-color | h3 leaf, plus sup/sub leaves | P 0.9898 / f 0.674 / f 0.6164 |
| css-text-decor/text-decoration-inset-005 | h1 folded | P 0.9983 / f 0.8995 / f 0.9 |
| css-text-decor/text-decoration-inset-006 | h1 folded | P 0.9985 / f 0.8987 / f 0.8993 |
| css-text-decor/text-decoration-inset-011 | h1 runs host (fold bails → control) | P 0.9948 / f 0.6999 / f 0.6852 |
| css-text-decor/text-decoration-inset-014 | h1 folded | P 0.9975 / f 0.9238 / f 0.923 |

**14 carry only SIZED headings** (the extractor's `UA_H1_PROPS` or an author size). The rule stands down, so they are
byte-identical by construction:
- 12 of them carry FontSize + FontWeight: inset-001/002/003/004/007/008/009/012/013/015/016/024.
- 2 carry FontSize but NO FontWeight: `filter-effects/backdrop-filter-border-radius-change` (android f 0.5977) and
  `backdrop-filter-corner-shape-change` (android f 0.4904).
  - On Compose these two get the BOLD half only. They are failing movers.
  - The ring-fenced `backdrop-filter-basic-blur` carries no heading.

**2 carry only sup/sub:**
- text-decoration-subelements-002: members of folded hosts, so no change on either native.
- text-decoration-subelements-003: the sup is a member of a host whose fold BAILS on Android (the picture stacks "be" /
  "underlined" / "."), so the stacked sup gets `smaller`. Mover, f.

**Passing native cells inside the carrier set: exactly one,** `block-in-inline-015-print` iOS P 0.9894. Its leaf path is
unchanged by U2.

**Carrier sets (the stems control-check may see change):**
- **android:** block-in-inline-015-print, text-decoration-inset-005, -006, -014, text-decoration-color,
  text-decoration-subelements-003, counter-list-item, counter-reset-increment-overflow-underflow,
  backdrop-filter-border-radius-change, backdrop-filter-corner-shape-change.
  - Expected byte-identical: inset-011 (control) and subelements-002.
- **ios:** text-decoration-inset-005, -006, -014. Expected byte-identical: -011.
- **web:** none. The web harness renders a real `<h1>`, so Chromium's `html.css` applies.
- The 327-pair product corpus carries no `sourceTag` (Swift banner, `grep '"_tag"' fixtures/…`), so the fixture net is
  byte-identical.

## 7. Ownership (disjoint) and seams

**Owned:**
- New `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt`.
- New `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt`.
- Their tests under `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/`.
- New `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift`, with a new
  `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift`.
- `UAElementFontRule.swift` (docs only) and `UAElementFontRuleTests.swift` (additions).
- `tools/titan/results/wave54-<lane>/`.

**Read-only:** `InlineRunFold.kt`, `InlineRunFlow.swift`, `InlineSpanRing.{kt,swift}`, `MonospaceUAFontSize.{kt,swift}`,
`UaBlockMarginFontBasis.kt`. The gate CALLS the fold; it never edits it.

**Not touched:**
- `typography/wrapping/**` on either platform (the hyphenate-character lane's ground);
- `lists/**`, `table/**`, `layout/position/**`;
- every web file.

**Seam hunks (two, one per native renderer):**
- seam-1 Compose `ComponentRenderer.kt` :1141 (one call wrapped).
- seam-2 `ComponentRenderer.swift` :343-356 (one argument).
- No hunk in `extract-fixture.mjs` or `apps/web-harness/src/sdui/ComponentRenderer.tsx`.
- The compose-table-body-cell brief's seam is `ComponentRenderer.kt` :2638-2697, a disjoint hunk.

## 8. Geometry probe (`ua-heading-face.geometry.py`, executed on wave53-final → `ua-heading-face.geometry.out.txt`)

**Rules:**

| test | rule |
|---|---|
| block-in-inline-015-print | 4 bands, each height ±2 px and right edge ±3 px of the ref |
| inset-005 / -006 | below y110, exactly 2 bands; band 1 ≥ 30 px tall; band 2 ("fox") right edge ±4 px of the ref's x64 |
| inset-014 | 2 bands, each ≥ 20 px; band 2 right edge ±6 px of the ref's x155 |
| inset-011 (control) | native bands EXACTLY the recorded wave53-final bands |

The ref rows self-check (exit 1 if a ref row fails).

**Today it prints:**
- ref and web OK everywhere; iOS OK on block-in-inline-015-print;
- `android … GEOMETRY WRONG (band height 9 vs ref 18 (y23))`;
- `inset-005/-006 ios|android … GEOMETRY WRONG (1 heading line band(s) vs ref 2 …)`;
- `inset-014 ios|android … GEOMETRY WRONG (line-1 band 11|12 px tall (< 20: the 2em face is missing))`;
- `inset-011 … GEOMETRY OK` (control).

**After the lane:** every row "→ GEOMETRY OK".

**Pins (each with the mutation that turns it red):**
- JVM `UAElementFontRuleTest`:
  - verbatim block-in-inline h1 → FontSize 32 / FontWeight 700;
  - inset-001 h1 (own size and weight) → the SAME list instance. Mutation: drop the own-declaration guard;
  - inset-014 h1 → 26. Mutation: base 16 instead of `MonospaceUAFontSize`;
  - text-decoration-color `sup` leaf → 13.333;
  - untagged → identity.
- JVM `UAHeadingFoldGateTest`:
  - verbatim inset-005 / -006 / -014 hosts → applies (`Folded`);
  - inset-011 → stands down (`Bailed member-prop:TextDecorationColor-divergence`);
  - leaf → applies;
  - mutation: return the old `hasChildren` gate → 005 red.
- Catalyst `UAHeadingFoldGateTests`: the same three, with the same mutation.

**Suites (after the gate releases the host):**
- focused `--tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest'`;
- Catalyst `-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests` and `UAHeadingFoldGateTests`;
- then the orchestrator's single sweep.

**Probe sections:** css-text-decor, css-break, css-lists, filter-effects.

## 9. Predictions (wave53-final → closing gate)

**Target cells:**

| cell | from → to | confidence | floor | basis |
|---|---|---|---|---|
| block-in-inline-015-print android | f 0.9489 → **P ≈ 0.989** | HIGH | **0.97** | the identical rule on iOS gives P 0.9894 on the same IR |
| text-decoration-inset-005 android | f 0.9 → **P ≈ 0.98** | MED | — | anchors inset-001…004 android 0.984–0.9859 |
| text-decoration-inset-005 ios | f 0.8995 → **P ≈ 0.965** | MED | — | anchors ios 0.9634–0.9707 |
| text-decoration-inset-006 android | f 0.8993 → **P ≈ 0.98** | MED | — | as -005 |
| text-decoration-inset-006 ios | f 0.8987 → **P ≈ 0.965** | MED | — | as -005 |
| text-decoration-inset-014 ios | f 0.9238 → P | MED-LOW | — | no monospace anchor; the probe decides |
| text-decoration-inset-014 android | f 0.923 → P | MED-LOW | — | no monospace anchor; the probe decides |

The -005/-006 natives add sup/sub inside the fold (the 4(e) ~2 px baseline class on Compose), which the anchors lack.

**Movers (f stays f; look, never count):**

| cell | from | expected |
|---|---|---|
| text-decoration-color android | f 0.6164 | ≈ 0.67 (the iOS anchor with the rule is f 0.674) |
| counter-list-item android | f 0.7307 | moves (iOS with the rule: f 0.7864) |
| counter-reset-increment-overflow-underflow android | f 0.8699 | direction unknown (iOS with the rule: f 0.857, and its digits are a counter defect) |
| subelements-003 android | f 0.9084 | moves (stacked sup → `smaller`) |
| backdrop-filter-border-radius-change android | f 0.5977 | moves (bold half only) |
| backdrop-filter-corner-shape-change android | f 0.4904 | moves (bold half only) |

**Must not move (byte-identical):**
- block-in-inline-015-print ios P 0.9894 and web P 1;
- text-decoration-inset-011 ios f 0.6999 / android f 0.6852 (control; probe exact);
- inset-001/002/003/004/009/015/016/024 ios and android, all P 0.9585–0.9859;
- inset-007/008/012/013 natives;
- text-decoration-subelements-002 ios f 0.7975 / android f 0.9047;
- every web cell;
- the 327-pair fixture net.

**Expected movement:**
- android: +1 HIGH, +2 MED, +1 MED-LOW;
- ios: +2 MED, +1 MED-LOW;
- lost: 0. No native P cell is a carrier except block-in-inline iOS, on an unchanged path.

## 10. Risks

1. **Wrap width of an abspos heading (MED).** The ref wraps "fox" onto line 2 inside the 358-px containing block.
   - If a native gives the abspos `<h1>` an unbounded proposal, the 2em paragraph stays on one line and runs past x390.
   - Then -005/-006 stay f; the probe prints "1 heading line band".
   - inset-014 already wraps at 16ch on both natives today, so the width channel exists. Whether `auto` width does the
     same is unproven.
2. **`ch` / `em` against the new face (MED-LOW).**
   - inset-014's `16ch` must re-resolve against 26 px mono. On Compose the rule sits before `DynamicValueResolver`
     (:1141 < :1206). On iOS the StyleBuilder width reads the merged FontSize.
   - inset-011's `10em` is a control and must not move.
3. **Shared carriers.**
   - counter-list-item and counter-reset-increment-overflow-underflow android are lists-section stems the lists/markers
     family may claim.
   - inset-005/006/014 are abspos `<h1>`s inside relative divs, which the layout scout's abspos containing-block family
     may touch.
   - plan-build must assign each stem to ONE lane and list it as must-not-move for the other.
4. **The gate re-runs the fold (LOW).** One extra `fold` per heading host, computed on the pre-UA list.
   - It is sound only while admission ignores FontSize/FontWeight (§4 D).
   - `InlineAtomRing.resolve(child.properties, hostProperties)` could read the host font for an atom member.
   - 0 of the 4 folded heading hosts carries an atom (§3 wire: their members are only `u` / `sup` / `sub` / `br`).
   - A pin must fail if that invariance breaks.
5. **Docs.** `UAElementFontRule.swift`'s TWIN STATUS and its `headingAppliesTo` table become false. BACKLOG 0(g) and the
   4(b′) mechanism sentence are rewritten in the wave's docs commit (orchestrator).
6. **Ring-fence:** `filter-effects/backdrop-filter-basic-blur` carries no heading and is untouched (report-only).

## 11. Recommendation

**GO (one lane, effort S–M; two units).**
- U1 (Compose twin + gate + seam-1) and U2 (iOS gate + seam-2) are each pinnable on verbatim wire with a mutation.
- **GO-SMALL fallback:** U1 with the gate forced to "leaf only". That is +1 Android HIGH (block-in-inline-015-print) plus
  the text-decoration-color / lists movers, and zero iOS change.

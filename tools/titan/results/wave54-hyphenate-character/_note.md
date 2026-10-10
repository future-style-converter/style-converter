# wave 54 · L3 hyphenate-character — lane note

Contract: `tools/titan/results/wave54-plan/PLAN.md` "### L3 · hyphenate-character" (+ §0, §3, §4, §6, fix rounds 1–3),
brief `tools/titan/results/wave54-plan/hyphenate-character-compose.md`. Shared tree, branch `campaign/applier-campaign`,
HEAD `029139fd` (= `db6e8aa0` + the plan's fix passes 2 and 3; the three seam files are byte-identical at `7cce3b22`,
`db6e8aa0` and `029139fd`). Nothing committed by this lane; no seam file left modified (sha256 below).

**Resume record.** A first attempt of this lane (19:05–21:06) built every unit, the four patches, the census, the
differentials and most mutations, then died before writing this note. This attempt (21:40–22:05) read its artifacts,
re-verified everything on the final bytes (fresh-XML Gradle runs, Catalyst `Executed N`, seam re-verification under the
lock, the geometry self-test on the current `geometry-gate.py`), added ONE pin (iOS StyleBuilder → TextConfig mirror,
mutation U2I-d) and wrote this note. The record of value for every claim below is the timestamped line it cites in
`mutations.log` / `seam-verify.log` / the `*.out.txt` beside this note.

## What changed and why (file:symbol)

The brief's finding: the glyph alone flips nothing (best "glyph fixed" replay 0.9474; web -004 already has the right
glyph and is f 0.9249). The pictures show four defects (A converter, C natives, D extractor, E drift); the lane fixes A,
C, D and puts E behind the probe-decided U3b. LOOKED at, before and after reasoning (sheets kept here, ref | web | ios |
android, top 480 rows of wave53-final): `look-001-ref-web-ios-android.wave53-final.png` (every platform paints `-` at
each cut, two blank lines before groups 3 and 4 where the ref has one, `re-/al-` where the ref has `real`, iOS's lone
`-` line under `tial`), `look-004-…png` (web already `/-/`, natives `imple-/menta-/tion` because a 1ch hyphen lets more
letters fit than the 3ch `/-/`), `look-block-ellipsis-004-…png` (web: a stray 20 px gap above the `Line 3` box that the
ref and both natives do not have).

- **U1, converter (A).** `HyphenateCharacterPropertyParser.parse` now decodes a value that is exactly ONE css-syntax-3
  `<string-token>` through the NEW `primitiveParsers/CssStringParser.parse` (§4.3.5 consume a string token; §4.3.7
  consume an escaped code point: 1–6 hex digits + one optional whitespace, 0 / surrogate / > U+10FFFF → U+FFFD,
  `\` + other → that code point, line continuation dropped, raw newline → bad string → null). `""` is kept as
  `HyphenateCharacterValue.String("")` (css-text-4 §6.3). Unquoted / malformed input keeps the pre-wave-54 strip-quotes
  acceptance byte-for-byte. Wire effect: a value change inside the existing `string` variant (001/002 `Generic
  _unmapped` → `{"type":"string","value":""}`, 003 `"\\2022"` → `"•"`, 005 `"\\00a0\\0640"` → `" ـ"`); no new
  byte shape, no freeze event.
- **U2-android, Compose (C).** NEW triplet `typography/wrapping/HyphenateCharacter{Config,Extractor,Applier}.kt`:
  `HyphenateCharacterExtractor.extract` reads the object shape (`{type:auto}` → `Config(null)`, `{type:string,value}`
  verbatim incl. `""`, last declaration wins; an unreadable payload → one `PropertyTracker` breadcrumb + auto);
  `HyphenateCharacterApplier.preBreakString(config, dictionaryHyphenation)` returns the string (null → U+2010
  `WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER`) and logs once when a non-auto value meets a Minikin dictionary run
  (the -002 android wall). NEW `SoftHyphenCuts.took(text, lines, hyphenChar)` answers "did the fold take a U+00AD?" by
  WALKING the display lines against the source words (`WordBreakOpportunities.analyze` ops, `paintsHyphen`), so it
  sees a `""` cut; the wave-52 count survives as `SoftHyphenCuts.legacyCount` (fallback when the walk cannot align +
  equivalence oracle). `PreBreakPipeline.preBreak` gains the defaulted `hyphenChar` parameter only (threaded into
  `GreedyLineBreaker.lines` / `clampLines`), and its private `tookSoftHyphenBreak` is replaced by the delegate
  `SoftHyphenCuts.took`. seam-1 adds the one argument at the `:7072` call.
- **U2-ios, SwiftUI (C).** The existing triplet is made real: `HyphenateCharacterConfig.value: String?` (was a dead
  lower-cased `keyword`); `HyphenateCharacterExtractor.extract(from:)` reads the discriminator first and keeps the string
  verbatim (unreadable → `PropertyTracker.logOnce` + auto); `HyphenateCharacterApplier.contribute` writes
  `agg.hyphenateCharacter` and raises `touched` for a real string only. `TypographyAggregate.hyphenateCharacter` (+1
  field), `StyleBuilder.swift` `TextConfig.hyphenateCharacter` (+1 field) and the mirror line beside `hyphensMode`
  (`:533`). NEW `wrapping/SpentHyphen.opportunityText(_:spentHyphen:)` (css-text-3 §5.5: a taken opportunity is spent —
  strip ONE trailing occurrence of the author's string before the soft-wrap-opportunity test);
  `GreedyLineBreaker.hasUnbreakableOverflowingLine(…, spentHyphen: String? = nil, …)` (one defaulted parameter). seam-2:
  `:5011` `hyphenChar: textConfig.hyphenateCharacter ?? AutoHyphenation.defaultHyphenCharacter`, `:5026`
  `spentHyphen: textConfig.hyphenateCharacter`. nil (undeclared / auto) keeps U+2010 and no strip.
- **U3, extractor re-arm (D), seam-3.** `buildNode` children loop: before `buildNode(child, …, childLineCtx)`, set
  `childLineCtx.hasInline = true` when `node.runs` holds a `{text}` entry with a non-collapsible character
  (`/[^ \t\n\r\f]/`, css-text-3 §4.1.1) strictly between child i-1 and child i — the child-scope twin of the body scope's
  `bodyRuns.some(run => run.afterElemIndex === idx)`; CSS 2.1 §9.5. A br that ENDS a text line is 0 instead of a 20 px
  blank line. Patch-borne NEW `tools/titan/extract-fixture-br-line-context.test.mjs` (rule 2b: its pins hold only with
  the seam; the authoring copy is `extract-fixture-br-line-context.test.mjs.staged` here, byte-identical to the file the
  patch creates, sha256 `88221750…`).
- **U3b, probe-decided (E), seam-3b.** NEW exported `brHostLineBoxPx(hostProps)` + the br-height rule: a LINE-START br
  whose host declares `line-height` ON ITSELF (longhand, or the `font` shorthand which resets it, css-fonts-4 §4.3) gets
  that line box (`<n>px`, `normal` → 1.2 × the host's own px font size, bare number → n × it; anything else → null →
  20 px). Carried as `childLineCtx.hostLineBox`. An INHERITED line-height does not trigger it (fix round 1, S4).

**Where the tree and the plan differ (the tree wins; said here).**
1. seam-1's argument is `preBreakString(extract(properties), dictionaryHyphenation)` — a second argument the §3 row does
   not show. `dictionaryHyphenation` is already in scope at the call (the `preBreak(dictionaryHyphenation = …)` argument
   of the same call); it feeds the Applier's Minikin-wall breadcrumb that §2 L3 U2-android requires. Header of
   `seam-1.patch` says so.
2. The pre-break call's insertion anchor is `:7101` (`clampLines = …DrawnLineClamp.cap(properties),`), inside the
   `:7072` call the registry names.
3. `PreBreakPipeline.kt` also loses its private `tookSoftHyphenBreak` (moved, verbatim, to
   `SoftHyphenCuts.legacyCount`) — "defaulted parameter + delegate" in the own list; the file shrinks 239 → 228 lines.
4. The Compose `typography/TextStyleApplier.extractHyphenateCharacter` (DEFAULT U+00AD, off the render path) is left
   untouched, as the plan's read-only list says; the queued removal stays queued (§5).
5. `GreedyLineBreakerTests.swift` grew 221 → 314 lines (past the ~300 split mark); it is in the own list as the pin home
   and no new test file is in it. `PreBreakPipelineTest.kt` was already 371 at base (now 482). BACKLOG 0(d)'s split is
   not this wave's (§9 D8). Every new SOURCE file is ≤ 142 lines.

## Revert unit U1 (one commit)

Paths:
- `converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/CssStringParser.kt` (new)
- `converter/src/main/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParser.kt`
- `converter/src/test/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParserTest.kt` (new)
- `tools/titan/results/wave54-hyphenate-character/` (this lane dir; not built or executed by any gate — it rides with the
  first L3 commit, as L7 / L1 do)

No seam patch. revertOrder (expectations): U3b, U3, then U1 (U3 without U1 is the degenerate trap). Reverting U1 alone
compiles everywhere (converter-only).

## Revert unit U2-android (one commit)

Paths:
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterConfig.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractor.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterApplier.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/SoftHyphenCuts.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipelineTest.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractorTest.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` ← `seam-1.patch`

Independent of every other unit in code (the tree compiles with it reverted: nothing outside this list references the
new symbols; the registry already claimed `HyphenateCharacter` via `TypographyRegistryTest`).

## Revert unit U2-ios (one commit)

Paths:
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterConfig.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/HyphenateCharacterApplier.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/wrapping/SpentHyphen.swift` (new)
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/TypographyAggregate.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/GreedyLineBreaker.swift`
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/GreedyLineBreakerTests.swift`
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/TypographyTests.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` ← `seam-2.patch`

seam-2 consumes `TextConfig.hyphenateCharacter` and `hasUnbreakableOverflowingLine(spentHyphen:)`, both in this same
unit, so a revert of the commit removes producer and consumer together.

## Revert unit U3 (one commit)

Paths:
- `tools/titan/extract-fixture.mjs` ← `seam-3.patch`
- `tools/titan/extract-fixture-br-line-context.test.mjs` (new; created by `seam-3.patch` — patch-borne, rule 2b)

## Revert unit U3b (one commit; PROBE-DECIDED, revertOrder U3b before U3)

Paths:
- `tools/titan/extract-fixture.mjs` ← `seam-3b.patch`

Its pins live in U3's test file and are feature-detected on the exported `brHostLineBoxPx`: with U3b reverted they SKIP
(loudly, "U3b (seam-3b) not on the tree") and the 001 row expects U3's 20 px blank lines — the suite stays green.
**Revert independence, executed** (throwaway repo of HEAD's three seam files, 22:00): every lane's seam patch in §4 order
applies clean (L2 → L5 → L4 → L3 on `ComponentRenderer.kt`, L5 → L3 on `.swift`); with commits seam-3 then seam-3b,
`git revert` of U3 ALONE (U3b on top) is clean, of U3b alone is clean, and U3b-then-U3 brings `extract-fixture.mjs` back
to base bytes.

**Suggested L3 block for `land-units.sh`** (its `L3_BLOCK_PLACEHOLDER`; variables as that script defines them):

```bash
L3=$R/wave54-hyphenate-character
commit "wave54 L3 U1: hyphenate-character keeps \"\" and decodes css-syntax-3 escapes in the reader (CssStringParser; css-text-4 §6.3, css-syntax-3 §4.3.5/§4.3.7)" \
"Revert unit L3-U1 ($L3/_note.md). Wire: hyphenate-character-001/-002/-003/-005 change value inside the existing string variant (no new byte shape). revertOrder: U3b, U3, then U1." \
  converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/CssStringParser.kt \
  converter/src/main/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParser.kt \
  converter/src/test/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParserTest.kt "$L3"
apply "$L3/seam-1.patch"
commit "wave54 L3 U2-android: Compose paints the hyphenate-character string in the pre-break fold (HyphenateCharacter triplet, SoftHyphenCuts walk)" \
"Revert unit L3-U2-android ($L3/_note.md). Carries hyphenate-character-001/-003/-004 android. Undeclared/auto keeps U+2010: every other run identical." \
  $KT/typography/wrapping/HyphenateCharacterConfig.kt $KT/typography/wrapping/HyphenateCharacterExtractor.kt \
  $KT/typography/wrapping/HyphenateCharacterApplier.kt $KT/typography/wrapping/SoftHyphenCuts.kt \
  $KT/typography/wrapping/PreBreakPipeline.kt $KTT/typography/wrapping/PreBreakPipelineTest.kt \
  $KTT/typography/wrapping/HyphenateCharacterExtractorTest.kt $CR_KT
apply "$L3/seam-2.patch"
commit "wave54 L3 U2-ios: SwiftUI paints the hyphenate-character string and treats it as spent (TextConfig.hyphenateCharacter, SpentHyphen; css-text-3 §5.5)" \
"Revert unit L3-U2-ios ($L3/_note.md). Carries hyphenate-character-001/-002/-003/-004 and hyphenate-limit-chars-001 ios. nil keeps U+2010 and no strip." \
  $SW/StyleEngine/typography/wrapping/HyphenateCharacterConfig.swift $SW/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift \
  $SW/StyleEngine/typography/wrapping/HyphenateCharacterApplier.swift $SW/StyleEngine/typography/wrapping/SpentHyphen.swift \
  $SW/StyleEngine/typography/TypographyAggregate.swift $SW/Renderer/StyleBuilder.swift \
  $SW/StyleEngine/typography/GreedyLineBreaker.swift $SWT/GreedyLineBreakerTests.swift $SWT/TypographyTests.swift $CR_SW
apply "$L3/seam-3.patch"
commit "wave54 L3 U3: a <br> that ends an interleaved text line is 0px, not a blank line (extract-fixture child-scope re-arm; CSS 2.1 §9.5)" \
"Revert unit L3-U3 ($L3/_note.md). Wire: 54 brs in 18 documents. Never lands before U1 + U2-android + U2-ios (degenerate -003 ios trap)." \
  tools/titan/extract-fixture.mjs tools/titan/extract-fixture-br-line-context.test.mjs
apply "$L3/seam-3b.patch"
commit "wave54 L3 U3b: a line-start <br> takes its host's own declared line box (brHostLineBoxPx; CSS 2.1 §10.8) — probe-decided" \
"Revert unit L3-U3b ($L3/_note.md). Wire: 12 brs in hyphenate-character-001…004 (20px → 19.2px). Reverted FIRST if its probeDecided rule fails." \
  tools/titan/extract-fixture.mjs
```

## Seam patches (hand-off; PLAN §3) — all cut against `db6e8aa0`, all `git apply --check` clean on `029139fd` (22:00)

| patch | unit | seam file · sha256 at base | anchor | verified (lock, applied, restored) |
|---|---|---|---|---|
| `seam-1.patch` | U2-android | `ComponentRenderer.kt` · `da2df0b4cca689ad30943213a5e10253881073b7287c45eabf42cb743e1d04db` | `:7072` call, inserted after `:7101` | `seam-verify.log` 21:01:35 (applied `0a92203a…`, restored BYTE-EXACT-TO-HEAD) + isolated exports: `export-verify.alone.out.txt` 21:59 (HEAD + L3 + seam-1: compile, 73 tests green, bytecode references `HyphenateCharacterApplier`), `export-verify.union.retry1.out.txt` 22:01 (shared tree's union + seam-1: same) |
| `seam-2.patch` | U2-ios | `ComponentRenderer.swift` · `905d1669d3856364c57fa0c821449eabfb28380b781eab557279e0280c996e1e` | `:5011` + `:5026` | `seam-verify.log` 21:57:12: Executed 49 tests, 0 failures (GreedyLineBreakerTests 19 · SoftHyphenPolicyTests 20 · TypographyTests 10); restored BYTE-EXACT-TO-HEAD |
| `seam-3.patch` | U3 | `extract-fixture.mjs` · `845a3d263a14369babae5a67aa4ea54748e0e53e20ae5b99921d33897cbfe5c9` | `buildNode` children loop `:11711-11713` (seed `:11706`) | `seam-verify.log` 21:59:45: `node --test` br-line-context + extract-fixture.test.mjs: 490 tests, 488 pass, 0 fail (2 U3b skips); restored BYTE-EXACT |
| `seam-3b.patch` | U3b | same | `:10142` (new helper), `:11459` br rule, `:11706` seed | `seam-verify.log` 21:59:46 (seam-3 + 3b): 490 / 490 pass; restored BYTE-EXACT |

The 21:52 union export FAILED in `compileDebugUnitTestKotlin` on L5's `UAElementFontRuleTest.kt` (its symbols were mid-
move in L5's in-flight edit; `export-verify.union.out.txt`): a foreign file, retried after the wait per rule 3, green at
22:01. No hunk for another lane's file was needed. Lock dir `tools/titan/runs/wave54-lock/` left empty.

## Census (blast radius) — own method

1. **Wire census** `l3-census.py` over the 1435 wave53-final per-test IR documents (`l3-census.wave53-final.out.txt`):
   14 `hyphenate-character` declarations in 6 documents, each decoded with the U1 rules in Python. Value changes in 4
   (001, 002 Generic → typed `""`; 003 → U+2022; 005 → U+00A0 U+0640); 004 `/-/` and limit-chars-001 `-` ×9
   byte-identical. U2-android reach: 3 documents (001/003/004 hosts, 5 U+00AD pieces each; 002 and limit-chars are
   dictionary runs, 005 is `pre`). U2-ios reach: 5 (the same + the CF-dictionary fold of 002 and limit-chars-001).
2. **U3 / U3b static differential** `u3-differential.sh` (in-process, NO gate flags: HEAD's `tools/titan/*.mjs` vs
   + seam-3 vs + seam-3 + seam-3b, every test of wave53-final's 30 `tests.list`; `u3-differential.out.txt`): 1435
   extracted, 0 errors. **U3: 18 fixtures, 54 brs (all 20px → 0px)** — exactly the plan's 18 (revert-layer-006,
   revert-val-001/-002, clip-path-filter-order, balance-grid-container, column-height-009, block-ellipsis-002/-004/-005/-006,
   hyphenate-character-001…004, backdrop-filter-clip-rect/-edge-clipping/-paint-order/-plus-filter);
   `backdrop-filter-basic-blur` (ring-fenced) is NOT among them. **U3b: 4 fixtures, 12 brs (20px → 19.2px)**, all in
   hyphenate-character-001…004. The `refFixture` side also changes (22 / 4): those are the `__ref.json` files written
   to gitignored `fixtures/wpt/`, which `build-combined-fixture.mjs` never reads (`:30` "We do NOT include refs") and the
   frozen ref PNGs never use — no gate input.
3. **Carrier cells today** `carrier-cells.py` (`carrier-cells.wave53-final.out.txt`, the plan's own snapshot):
   captures web 15 (P 8 · f 7), iOS 16 (P 7 · f 9), Android 15 (P 5 · f 10); must-not-move 74 lines (P 65 · f 9).
4. **Cross-check vs `expectations.json` `lanes["L3-hyphenate-character"]`** (census output (4)): wire carriers mine 19
   / plan 19, captures 15 / 16 / 15 — all EQUAL; per revert unit captures web/ios/android U1 [3,3,2] wire 4 · U2-android
   [0,0,3] · U2-ios [0,5,0] · U3 [15,15,15] wire 18 · U3b [4,4,4] wire 4.
5. **Gate fixtures**: none of the 9 `tools/visual/gate-fixtures.txt` fixtures declares `hyphenate-character` and none
   is a WPT extraction; no `tools/visual/baseline` PNG carries the property. R8 is out of reach. The per-property
   `fixtures/properties/typography/hyphenate-character.json` (`"-"`, `"~"`, `"•"`) decodes to the same strings.

## Pins (verbatim inputs) and executed mutations

Inputs: Compose / Swift pins use the per-test IR of `tools/titan/runs/wave53-final/sections/css-text/per-test-ir/`
VERBATIM (the 001/004 host JSON in `HyphenateCharacterExtractorTest.kt` was re-checked byte-for-byte against those files
at 22:03; byte-identical to wave54-open per §10); the U3 pins use the verbatim WPT HTML (the extractor's input).
Mutation runner `mutate.sh` (one exact substring, sha256 before / mutated / restored, red then green); Compose through
`gradle-focused.sh` (`--rerun` + a fresh-JUnit-XML check — R2-N5: a run that executes no test exits 3).

| unit | pin (file › test) | mutation (executed) | red (record) | restored sha256 |
|---|---|---|---|---|
| U1 | `HyphenateCharacterPropertyParserTest` › pin 2 hex escape `"\2022"`→`•`, pin 3 `"\00a0\0640"`→`" ـ"`, pin 4 `"\2022 x"`→`•x`, `escape replacement rules follow css-syntax-3 4_3_7` | U1-a drop the hex decoding in `CssStringParser.consumeEscape` | 4 failed (19:12:03) | `0e5f09c7…` BYTE-EXACT |
| U1 | › pin 1 `""`→`String("")`, `end to end an empty string is no longer a Generic passthrough`, `the wire carries the decoded value inside the existing string variant` | U1-b restore `isEmpty() → null` on the decoded path | 3 failed (19:12:33, final file) | `82d5befa…` BYTE-EXACT |
| U2-android | `PreBreakPipelineTest` › (a) `anEmptyHyphenateCharacterStillFiresAtTheTakenCuts` (piece 1, 4.5ch, `""` → `im/ple/men/ta/tion`), (b) `theHyphenStringsWidthDecidesTheBreaks` (`real/iza/tion`) | U2A-a `took` answers by the wave-52 count | 2 failed, junit failures=2 (21:46:55, fresh XML) | `d5cd45df…` BYTE-EXACT |
| U2-android | › (a)–(d) incl. (c) `aBulletHyphenateCharacterIsPaintedAtEveryCut` (5.5ch `•`), (d) `aMultiCharacterHyphenateCharacterOverflowsLikeTheRef` (6.5ch `/-/`, `tial/-/` overflows) | U2A-b drop `hyphenChar` from the fold | 4 failed (21:46:31, fresh XML) | `18bafc7b…` BYTE-EXACT |
| U2-android | `HyphenateCharacterExtractorTest` › `theStringIsNeverCaseFoldedNorReadAsAKeyword` | U2A-c lower-case the string | 1 failed (21:46:43) | `968d957f…` BYTE-EXACT |
| U2-android | › `theEmptyStringIsAValueNotAnAbsence`, `theVerbatim004WireReachesThePreBreakAsSlashHyphenSlash`, `aDictionaryRunKeepsTheStringAndAnUnreadablePayloadFallsBackToAuto`, + the case pin | U2A-d keyword path (`value` read before `type`) | 4 failed (21:46:46) | `968d957f…` BYTE-EXACT |
| U2-android (R2-N6) | `PreBreakPipelineTest` › (e) `theWalkAgreesWithTheWave52CountOnEveryCorpusRun`: every U+00AD text on the wave53-final wire (22 texts) × every half-ch box 1–40ch = 1738 cases, walk == wave-52 count AND the walk aligns by itself | U2A-e the walk misses a taken op | 9 failed (20:59:23, fresh XML) | `d5cd45df…` BYTE-EXACT |
| U2-android (R2-N6) | › `aSpacelessRunWithoutSoftHyphensIgnoresTheHyphenString` (hyphenate-limit-chars-001's 9 verbatim `example` runs with `"-"`, hyphens:auto → identity, same instance) | — (identity pin; held green on every run) | — | — |
| U2-android | (e) the default argument: every pre-existing `assertSame` identity pin in `PreBreakPipelineTest` stays green | — | — | — |
| U2-ios | `GreedyLineBreakerTests` › `testALineOverflowingOnlyByItsSpentHyphenIsUnbreakable` (`["tial/-/"]`, 6.5ch, spent `/-/` → true; nil → false; `tial‐` UA → false), `testSpentHyphenStripsOneTrailingOccurrenceOnly` | U2I-a drop the strip in `SpentHyphen` | Executed 19, 3 failures (19:56:15, final file) | `6cfed34f…` BYTE-EXACT |
| U2-ios | › `testTheExtractorReadsTheObjectShapeVerbatim` (`/-/`, `""`, `AbC`, quoted `auto`, `{type:auto}`) | U2I-b lower-case | 1 failure (19:38:13) | `8a0ba398…` BYTE-EXACT |
| U2-ios | › same + `TypographyTests.testWrappingChecks` (the old `:311` pin, moved to the object shape) | U2I-c keyword path | 4 + 1 failures (19:40:35) | `8a0ba398…` BYTE-EXACT |
| U2-ios (new this attempt) | › `testTheApplierCarriesTheStringOntoTheAggregate` now also asserts `StyleBuilder.build(from: [004's verbatim wire]).text.hyphenateCharacter == "/-/"` and nil for no declaration — the field seam-2 reads | U2I-d drop the StyleBuilder mirror line | Executed 49, 1 failure (21:55:49) | `0a97eff2…` BYTE-EXACT |
| U2-ios | › `testASlashHyphenSlashStringIsMeasuredInTheFold` (`" ini\u{AD}tial\u{AD}iza\u{AD}tion"`, 6.5ch, `/-/` → 4 lines), `testAnEmptyStringPaintsNothingAndWidensTheFit` | — (fold already took `hyphenChar`; these pin the inputs seam-2 will pass) | — | — |
| U3 | `extract-fixture-br-line-context.test.mjs` › 001 `<div>` brs `[0,20,0,20,0,20]` (today `[0,20,20,20,20,20]`), revert-val-001 all 0 | U3-a drop the re-arm | 3 not ok (21:59:45) | `2461e45e…` (patched file) BYTE-EXACT |
| U3 | › white-space-only interleave is no content (the blank br keeps 20) | U3-b count white-space-only runs | 1 not ok (21:59:46) | `2461e45e…` BYTE-EXACT |
| U3 | › B-RC1's five body-level brs stay 20 · `empty-span-001` unchanged | — (held green) | — | — |
| U3b | › negative: the verbatim `css-multicol/baseline-007` (40px on an ANCESTOR) and `baseline-002` (2em on an ancestor) brs stay 20 | U3b-a the inherited reading | 1 not ok (21:59:46) | `6ab6f2b8…` BYTE-EXACT |
| U3b | › 001 → `[0,19.2,0,19.2,0,19.2]` | U3b-b drop the host line box from the rule | 1 not ok (21:59:47) | `6ab6f2b8…` BYTE-EXACT |

Void records, kept and labelled in `mutations.log`: the 20:56:45 and 20:58:01 U2A-e blocks (a concurrent lane's Gradle
raced the shared `:runtime` build dir; no test executed) — superseded by 20:59:23. The 19:21 U2A-a…e blocks are valid
as RED; their green halves are superseded by the fresh-XML runs of 21:46.

**Final focused suites on the final bytes (this attempt):**
- converter `./gradlew :converter:test --tests '*HyphenateCharacter*' --tests '*CssString*' --rerun`: BUILD SUCCESSFUL,
  fresh XML `HyphenateCharacterPropertyParserTest tests=11 failures=0`. (R2-N5: the `*CssString*` filter matches no
  class — `CssStringParser` is pinned through the parser test's escape pins, which U1-a turns red.)
- Compose shared tree, 21:46 (`gradle-focused.sh`, every filter matched a class): GreedyLineBreakerTest 23 ·
  PreBreakPipelineTest 26 · HyphenateCharacterExtractorTest 6 · SoftHyphenPolicyTest 9 · WordBreakOpportunitiesTest 9,
  0 failures. Isolated exports with seam-1: alone 21:59, union 22:01 — the same 73, 0 failures.
- Catalyst (no seam), the green half of U2I-d at 21:55 on the final test file (`mutations.log`): **Executed 49 tests, 0
  failures** (GreedyLineBreakerTests 19 + SoftHyphenPolicyTests 20 + TypographyTests 10); with seam-2 under the lock
  21:57 (`seam-verify.log`): the same.
- `node --test` with seam-3 (+3b): 490 tests, 0 fail (21:59).

## Geometry probe

`python3 tools/titan/results/wave54-plan/geometry-gate.py wave53-final --base wave53-open --lanes L3 --self-test` →
**exit 0**, re-run at 21:44 on the current `geometry-gate.py` (`geometry-gate.L3.self-test.wave53-final.out.txt`):
32 keys — gating 6 (PASS 0 · FAIL 6: hyphenate-character-001 / -003 ios `[GATING -> revert U2-ios|U3]`,
block-ellipsis-002 / -004 / -005 / -006 web `[GATING -> revert U3]`), control 19 (PASS 19: the soft-hyphen
default-population keys and the eight block-ellipsis native freeze keys), report 7 (FAIL 7), 0 self-check failures,
`self-test: HOLDS`. The same on `wave54-open --base wave53-final` (`…wave54-open.out.txt`, exit 0). Every gating key
fails today, every control passes, every report key fails: the rules can fail.

## Predictions (against wave54-open = wave53-final; the plan's 46 rows, read by this lane)

The lane re-states the plan's rows; it adds no row and moves no floor. Its own confidence, where it differs or needs a
reason, is in the last column. Nothing below is claimed "picture-correct" from a replay: only the device capture plus
`hyphenate-character.geometry.py` / `block-ellipsis-br.geometry.py` earns it.

| cell | wave53-final → predicted | plan conf. (floor) | lane's read |
|---|---|---|---|
| hyphenate-character-001 ios | f 0.9287 → P ≈0.979 | MED-HIGH (0.965, gating) | agree; `""` paints nothing, so no face risk; U3b's GBn drift risk is the probe's to decide |
| hyphenate-character-003 ios | f 0.9337 → P ≈0.977 | MED-HIGH (0.965, gating) | agree, with the brief's risk 3 named: if the iOS monospace face lacks U+2022 the fallback advance enters the fit (measure and paint share it) |
| hyphenate-character-004 ios | f 0.9134 → P ≈0.979 | MED | agree; needs SpentHyphen (pinned) to stop TextKit re-wrapping `tial/-/` / `phen/-/` |
| 001 / 003 web | f 0.935 / 0.8812 → P ≈0.960 / 0.956 (U3), ≈0.976–0.981 with U3b | MED-LOW | agree (web needs no runtime change: `HyphenateCharacterExtractor.ts:17-24` emits `""` / `"•"` verbatim — code-read, not executed) |
| 004 web | f 0.9249 → 0.947–0.966 | LOW | agree |
| 001 / 003 / 004 android | f 0.9301 / 0.9292 / 0.905 → ≈0.957 / 0.953 / 0.945 (U3), ≈0.976–0.981 with U3b | LOW-MED / LOW / LOW | agree |
| 002 web / ios / android | f 0.92 / 0.9261 / 0.9223 → ≈0.96 / mover (undirected) / stays f | LOW-MED / LOW / LOW | agree; android is the logged Minikin wall (`HyphenateCharacterApplier` breadcrumb) |
| hyphenate-limit-chars-001 ios | f 0.8919 → mover (undirected) | LOW | agree; mechanism: the dictionary fold paints `-` instead of U+2010, and a `-`-ending overflowing line is now judged spent |
| block-ellipsis-002 web | P 0.9868 → ≈1.0 | MED-HIGH (0.995, gating) | agree |
| block-ellipsis-004 / -005 / -006 web | P 0.9737 / 0.9735 / 0.9735 → ≈0.998 | MED-HIGH (0.99, gating) | agree (LOOKED: the only web difference from the ref is the stray gap U3 removes) |
| block-ellipsis-002 ios / android | P 0.9813 (DEGENERATE) → ≈0.993, stays DEGENERATE / P 0.9884 → identical or up | MED / LOW | agree |
| block-ellipsis-004/-005/-006 natives | byte-identical expected | MED | agree (LOOKED: no stray gap on either native today) |
| backdrop-filter-clip-rect / -edge-clipping / -paint-order ×3 | moves, expected up; undirected | MED (web/ios) / LOW (android) | agree |
| backdrop-filter-plus-filter ×3 | stays | MED | agree |
| clip-path-filter-order web / ios / android | f → P ≈1.0 / P ≈0.998 / ≈0.955 | MED / MED / LOW-MED | agree |
| balance-grid-container web | f 0.9207 → P ≈1.0 | MED | agree |
| balance-grid-container ios / android, column-height-009 ×3 | moves; undirected | LOW | agree |

DEGENERATE-by-construction (001/003/004): a cell on platform p is relabelled faithful ONLY when U1, U2-p and U3 are all
on the closing tree AND its `hyphenate-character.geometry.py` row prints `→ GEOMETRY OK` (`degenerateRetirementCheck`).
Web -004 is never degenerate.

## Must not move (74 lines, `expectations.json` mustNotMove; census basis above)

- `hyphenate-character-005` ×3 (wire changes under U1; `white-space: pre` takes no break; also L1's);
- `revert-layer-006`, `revert-val-001`, `revert-val-002` ×3 (wire changes under U3; green-on-green inside a 100 px
  `overflow: hidden` box — HIGH);
- `hyphenate-limit-chars-001` web / android;
- the U2 default-argument population on both natives: 17 css-text/hyphens tests (+ `hyphens-none-shy-on-2nd-line-001`)
  and `block-ellipsis-014` / `-028` — nil / undeclared keeps U+2010, and the walk == count equivalence pin covers every
  U+00AD text they carry;
- every css-text/hyphens WEB cell except L3's four carriers and L6's `hyphens-out-of-flow-002`.

## Hand-offs

- **Docs (for the closing docs pass, not edited here):** `node tools/visual/coverage-audit.mjs` (stdout mode, 22:00,
  shared tree) prints `real: android=16 ios=70 web=516`, `applier files: android=45` — Android `real` 15 → 16 and
  dedicated applier files 44 → 45 (`CLAUDE.md` "Real-applier floor", `docs/STATUS.md`). Test counts this lane adds:
  converter +11 (`HyphenateCharacterPropertyParserTest`), Compose runtime +12 (PreBreakPipelineTest 20 → 26,
  HyphenateCharacterExtractorTest 6), SwiftUI +6 (GreedyLineBreakerTests 13 → 19), tooling +7
  (`extract-fixture-br-line-context.test.mjs`, 5 when U3b is reverted + 2 skips). BACKLOG 0(b)'s hyphenate-character
  line: honoured end-to-end on the wire, web, Compose and SwiftUI, landing as U1 / U2-android / U2-ios / U3 / U3b;
  4(f)'s "DEGENERATE by construction" label retires per cell under `degenerateRetirementCheck`.
- **Queued, not done (brief §11.6):** the lost `<b>` (corpus convention); -002 android (Minikin dictionary hyphen, a
  logged wall); -005 natives (the bidi bake would have to bake ` ـ`); generalising SpentHyphen to U+2010;
  `TextStyleApplier.extractHyphenateCharacter`'s dead DEFAULT U+00AD.
- No hunk for another lane's file. No change to any lane's ownership.

## ORCHESTRATOR WINDOW REQUESTS

**[W-L3] the gate-flag wire differential** (PLAN §2 L3, §4 step 4 — starts Chromium for the bakes and runs the
converter, so it is a window; also the converter hop for U1's four documents). Script written and its compare step
self-tested on synthetic inputs (`wl3-wire-differential.selftest.out.txt`); never run against the corpus by this lane.
- Command (two worktrees with `node_modules`, `tools/wpt`, `tools/titan/wpt-buckets.json`,
  `apps/android-harness/local.properties` symlinked, JDK 21):
  `bash tools/titan/results/wave54-hyphenate-character/wl3-wire-differential.sh <pre-tree> <post-tree> tools/titan/results/wave54-gate/wl3-diff wave54-open > tools/titan/results/wave54-gate/wl3-diff.out.txt`
  - L3 alone: pre = the landed tree right before L3 U1, post = after L3 U3b.
  - Union (PLAN §4 step 4): pre = HEAD-before-landing, post = fully landed.
- Expected: L3 alone → `content-changed 19` = hyphenate-character-001 / -002 / -003 / -005 (U1) ∪ the 18 U3 documents
  listed in the census, `renumbered 0`, every other document identical. Union → content-changed 23 (L1's 4 + L3's 19),
  renumbered 15 (`css-counter-styles/cssom/*`), the three zero-padding selectors documents identical. U3b's 12 brs live
  inside 001…004 (already counted).
- Decision it feeds: R4b (wire control) and revert rule 5 for U1 / U3 / U3b — any other changed document is a leak:
  stop and bisect by unit (U3b, U3, U1 in revertOrder). It is also the only executed proof that the bake flags
  (`POST_LOAD_EXTRACT`, `BIDI_BAKE`, `VT_BAKE`) do not widen U3's radius beyond the static 18.

No other window is needed by this lane before the probes; the device reads are the plan's stage-2 probe (css-text,
css-overflow, filter-effects, css-cascade, css-masking, css-multicol) and the U3b `probeDecided` rule.

## What I could NOT verify

- **No device picture.** Every prediction is a replay or a model; no native cell is claimed picture-correct. The two
  gating iOS rows and the four gating web rows are decided by the stage-2 probe + geometry gate.
- **The seam hunks themselves have no failing unit test.** Dropping seam-1's argument or seam-2's two edits leaves the
  JVM / Catalyst suites green: seam-1 is proven COMPILED IN (bytecode reference in the export), seam-2's inputs
  (`TextConfig.hyphenateCharacter`, `spentHyphen`) are pinned (U2I-d), but the renderer paths (`PlaceholderContent` +
  Minikin with `softWrap = false`; `PlaceholderLabel` + `.fixedSize` under ImageRenderer) only run on device.
- **[W-L3] not run**: my differential ran WITHOUT the gate's bake flags and without the converter, so (a) a bake-path
  document's brs and (b) U1's converter output on the four real documents are unverified at the gate's own flags (the
  converter tests `the wire carries the decoded value inside the existing string variant` / `end to end an empty string
  is no longer a Generic passthrough` exercise the reader → IR model path, not `:converter:run` over a combined fixture).
- **Web runtime**: unchanged; its handling of `""` / `•` / `" ـ"` is code-read, not executed.
- **Full suites** (converter, web, compose, harness, Catalyst, tooling, conformance): the orchestrator's single sweep.
  Only the focused suites listed above ran.
- **iOS / Android glyph advance of U+2022 in the pinned monospace face** (brief risk 3): untestable off device.
- **U3b's iOS drift risk (GBn)**: probe-decided by design.

## Files beside this note

`l3-census.py` / `.wave53-final.out.txt`; `carrier-cells.py` / `.wave53-final.out.txt`; `u3-differential.sh` /
`.out.txt` / `.json`; `wl3-wire-differential.sh` / `.selftest.out.txt`; `geometry-gate.L3.self-test.{wave53-final,wave54-open}.out.txt`;
`mutate.sh`, `gradle-focused.sh`, `seam-verify.sh`, `u3-pins.sh`, `export-verify.sh` with `mutations.log`,
`seam-verify.log`, `export-verify.{alone,union,union.retry1}.out.txt`; `look.py` and the three `look-*.png` sheets;
`seam-1.patch`, `seam-2.patch`, `seam-3.patch`, `seam-3b.patch`; `extract-fixture-br-line-context.test.mjs.staged`
(authoring copy of the patch-borne test, byte-identical; NOT a `*.test.mjs`, so the tooling glob never picks it up);
`owned-paths.txt`; `sha256-final.txt` (every owned path and hand-off, full 64-hex).

Seam files at the end of this attempt: `ComponentRenderer.kt` `da2df0b4…`, `ComponentRenderer.swift` `905d1669…`,
`extract-fixture.mjs` `845a3d26…` — all equal to HEAD; `tools/titan/extract-fixture-br-line-context.test.mjs` absent
from the tree (it enters with seam-3). Owned source diffs grepped for `probe` / `Probe` leftovers: none (the one hit in
`GreedyLineBreaker.swift` is pre-existing base text; "probe-decided" in the test banner names the unit).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (Gradle in its
`apps/android-harness` and repo root; xcodebuild with `-derivedDataPath <scratchpad>/dd-l3`); throwaway Gradle export
trees `<scratchpad>/export-l3-alone` and `<scratchpad>/export-l3-union` (built by `export-verify.sh`; nothing kept
there); the static differential's variant roots `<scratchpad>/u3diff/{base,u3,u3b}` (built by `u3-differential.sh`).

STATUS: COMPLETE

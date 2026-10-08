# wave53 L2 · soft-hyphen — SKEPTIC report

Lane under review: `tools/titan/results/wave53-soft-hyphen/_note.md` (STATUS: COMPLETE), PLAN §2 "### L2 · soft-hyphen", brief
`tools/titan/results/wave53-plan/spaceless-soft-hyphen.md`. Shared tree HEAD e330e255 (runtime sources byte-identical to dev
cdb8a845). Every repro below was EXECUTED. Mutations and suites ran in a private EXPORT tree (`git archive HEAD` of
`apps/android-harness runtimes/compose runtimes/swiftui Package.swift gradle schema fixtures` + L2's 9 owned files copied
from the shared tree + both seam patches applied). The shared tree was never written except under this directory. No
device, Chromium, emulator or simulator was used.

## Verdict: MIXED

The code does what the lane says. Every pin's mutation replays red → restore (sha256 equal) → green. My independent census
reproduces the blast radius exactly. Both seam patches apply clean on HEAD and stack under L4's patches in landing order.
Both revert units are green on their own.

**But the pre-registered geometry gate for L2's three Android rows cannot print `GEOMETRY OK` on the picture the lane
predicts.** `soft-hyphen.geometry.py`'s ink predicate (channel sum < 300) does not see Android's anti-aliased hyphen. I ran
it on the plan's own "anchor for correct" (`hyphens-span-002` android, P 0.9943), which is exactly the picture L2 predicts
for span-001 android. It prints `GEOMETRY WRONG (box 1 line 1 ink x25-54 vs ref x25-61)`.

Under §6 revert rule 4 that would revert F1 (taking the iOS fix with it) and F2 on correct pictures. R6 would also
mislabel faithful pictures as DEGENERATE. **This has to be fixed before `wave53-probe`.**

## 1. Executed repros (outputs abridged; full JSON under `skeptic-evidence/`)

| # | repro | result |
|---|---|---|
| R1 | export tree + seam-2 / seam-1 applied | patched hashes `d251ace7…` (ComponentRenderer.kt), `524e3158…` (ComponentRenderer.swift) — equal to the lane's |
| R2 | Compose focused (PreBreakPipeline, InlineRunFold, RunFoldBreadcrumbSeam, GreedyLineBreaker, WordBreakOpportunities, SoftHyphenPolicy, AutoHyphenation, SeamReachability, InlineSpanRing, InlineRunPlan, LineBoxCensus) | without seam-2: 115 run, 1 fail = `RunFoldBreadcrumbSeamTest` (by design); with seam-2: **159/159** |
| R3 | Compose `com.styleconverter.runtime.typography.*` + `core.renderer.*` with L2 + seam-2 on HEAD | **750/750** |
| R4 | revert-unit isolation (`skeptic-evidence/revert-units.sh`) | F1-only (F2 files at HEAD, seam-2 off): **744/744**; F2-only (F1 files at HEAD): **746/746**; export tree restored byte-exact |
| R5 | Catalyst `SoftHyphenPolicyTests GreedyLineBreakerTests WordBreakOpportunitiesTests InlineRunFlowTests`, seam-1 applied | **89/89**, compiled from the export tree's patched ComponentRenderer.swift |
| R6 | seam patches on HEAD | `git apply --check` ✓ both; stacked in a scratch repo: L2 seam-1 → L4 seam-2 ✓, L2 seam-2 → L4 seam-1 ✓ |
| R7 | shared-tree hygiene | ComponentRenderer.kt `e6c2a650…` / .swift `ffa03357…` = HEAD; `tools/titan/runs/wave53-lock/` empty; no wave-53-L2-tagged edit outside the own: list |
| R8 | payload verbatim check (JSON-equal per component vs wave52-ship per-test IR) | all 7 test payloads equal (static-inside-inline-001 host: only `slot` dropped, as declared); the 8 source docs are sha1-identical in wave52-ship and wave53-open |
| R9 | `cells.mjs` over every carrier, mover and must-not-move cell, wave52-ship → wave53-open | every value the lane quotes matches; no L2 cell moved between the runs |
| R10 | `soft-hyphen.geometry.py wave52-ship` / `wave53-open` | five target rows WRONG, ref rows OK, exit 0 (as the lane states) |
| R11 | `skeptic-geometry-anchor.py wave52-ship` | see defect D1 |

### Mutations (my runner `skeptic-mutate.py`; sha256 before = restored on every row)

| id | file (sha256) | mutation | red (failing) | green |
|---|---|---|---|---|
| M-a | PreBreakPipeline.kt `0915c0a2…` | space-only guard restored | (a), (d) | 20/20 |
| M-b | same | early identity return deleted | (b), (c) + 6 legacy identity pins | 20/20 |
| M-c | same | `!dictionaryHyphenation &&` removed | (c), `theSoftHyphenTriggerStaysOutOfDictionaryRuns` | 20/20 |
| M-over | same | guard line deleted | `declinesForASpacelessRunTheWholeRunGateOwns` | 20/20 |
| M-d | WordBreakOpportunities.kt `5d0dc052…` (EXPORT tree only) | `:87` `if (false && ch == '­')` | (a), (d), `composesWithTheSoftHyphenPolicy`, `firesWhenASoftHyphenBreakWasTaken` + 8 GreedyLineBreaker/WordBreak pins | 52/52 |
| M-F2-off | InlineRunFold.kt `917d877d…` | arm → `else if (false)` | out-of-flow-001 / -002 pins | 42/42 |
| M-F2-wide | InertOutOfFlowMember.kt `3810bd8f…` | any abspos/fixed admitted | hypothetical `Line 2`, both-facts pin | 42/42 |
| M-seam2 | ComponentRenderer.kt (seam-2 applied) `d251ace7…` | clause deleted | `RunFoldBreadcrumbSeamTest` | 1/1 |
| S-shy | SoftHyphenPolicy.swift `28a2f7f5…` | `guard false else { return false }` | `testASpacelessSoftHyphenRunIsAdmitted` | 89/89 |
| S-d3 | same | `guard horizontal \|\| true` | `testAVerticalSpacelessSoftHyphenRunIsRefused` | 89/89 |
| **own** S-x1 | same | old clauses gated on `horizontal` | `testThePreWave53ClausesAreUnchanged` | 89/89 |
| **own** S-x2 | ComponentRenderer.swift (seam-1) `524e3158…` | leaf site `horizontalWritingMode: true` | **none (survives)** | 89/89 |
| **own** x5 | InertOutOfFlowMember.kt | `"Color" -> transparentInk = true` (ANY colour) | **none (survives)** | 42/42 |
| **own** x2 | same | `return a <= 0f` → `return true` | **none (survives)** | 42/42 |
| **own** x3 | same | missing alpha → `?: return true` | **none (survives)** | 42/42 |
| **own** x1 | same | children/runs/decorations check deleted | **none (survives)** | 42/42 |
| **own** x4 | InlineRunFold.kt | `tag in TEXT_MEMBER_TAGS &&` dropped from the arm | **none (survives)** | 42/42 |

### Census (my own, never the lane's script)

- **JSON walk** (`skeptic-census.py`, inherited context up the `slot.parent` chain):
  - 1435 docs; U+00AD appears only in `.text` (33) and `.meta.runs[].text` (18), never in a property.
  - 17 docs carry U+00AD.
  - 30 space-less U+00AD strings: 15 leaf + 9 run piece + 6 host mirror.
  - Every governing Hyphens / WhiteSpace / WritingMode / Width is declared on the component itself.
  - No non-WPT fixture under `fixtures/` contains U+00AD, so the iOS pre-break (not WPT-gated) cannot reach the fixture net.
- **JVM** (`skeptic-f2-census.kt.txt`, export tree, production decoder + fold):
  - 21 runs hosts with an abspos/fixed member. 12 are Folded with `droppedOutOfFlowMembers = 1`, all in
    `hyphens-out-of-flow-001/-002` boxes 2-7.
  - 9 are Bailed for reasons decided before the F2 arm can be reached: `member-tag:none` ×6, `member-tag:label`,
    `member-prop:Position` (`Line 2`, refused by the predicate) and `member-prop:BackgroundColor` (ch-unit-001, EMPTY arm).
- **Reach, re-derived:**
  - Android: span-001, out-of-flow-001, -002 (F2), hyphenate-character-001/-003/-004.
  - iOS: span-001, out-of-flow-001 (host mirrors through the leading label), hyphenate-character-001/-003/-004.
  - Declined on both platforms: auto-control (Android by the dictionary veto; iOS already admitted by its dictionary arm),
    -005 (`white-space: pre` → Android softWrap off; iOS `noWrap`), vertical-001 (Android: the vertical branch returns
    before the horizontal `Text` that latches the wrap width; iOS: refused by D3).
  - **This equals `expectations.json` `captureCarriers` exactly. The radius is not under-reported.**
- **Lane residual closed by capture evidence:** today's `hyphens-auto-control` ios boxes are two line boxes tall. With
  `hyphenLocale == nil` the old guard would have declined and `singleLineText` would have pinned one line (the 26-px
  span-001 symptom). So the simulator's CF en-us dictionary is live, and the new clause cannot change that cell.

### PNGs looked at (ref / android / ios, wave52-ship)

- span-001: android reads `highwa`/`y` in 46-px boxes, iOS reads `high-`/`way` in 26-px boxes. Matches the lane's
  description.
- out-of-flow-001: android boxes 3-5 read `h`/`ighway` and `high`/`way`; iOS boxes 2-7 read the mirror `high-`/`way` at
  26 px.
- out-of-flow-002: android boxes 1/2/7 are correct, 3-6 are wrong; iOS is correct.
- vertical-001: iOS box 1 is horizontal `hyph`/`ena-`/`tion`.
- hyphenate-character-001: android piece 1 reads `impl`/`emen`/`tati`/`on`.
- span-002 android: correct `high‐`/`way`, with the hyphen at channel sum 342 (rows y121-122, x57-61).

## 2. Defects, ranked

### D1 — MUST-FIX before `wave53-probe`: the geometry gate cannot pass on L2's predicted Android picture

- **What.** `tools/titan/results/wave53-plan/soft-hyphen.geometry.py` bands ink with `geometry_common.dark` (r+g+b < 300).
  Android's hyphen glyph never gets that dark: its darkest pixel is channel sum 342.
  - span-002 android is the correct picture the lane predicts for span-001 android ("P ≈ 0.994 (= span-002 android 0.9943's
    picture)"). On it, the probe prints `GEOMETRY WRONG (box 1 line 1 ink x25-54 vs ref x25-61)`.
  - out-of-flow-002 android's already-correct boxes 1/2/7 read x25-54 too. That is why the probe already flags -002
    android at "box 1", a box the brief calls correct.
  - The F1-baked U+2010 and Minikin's auto-inserted hyphen are the same glyph of the same face (Minikin inserts U+2010 when
    the font has it). After F1/F2, span-001, out-of-flow-001 and -002 android will therefore print WRONG.
  - All three rows are `geometryGating`. Rule 4 then reverts F1, which takes the iOS fix in the same unit, and F2. R6 then
    labels faithful pictures DEGENERATE.
  - The lane's note §4 and ORCHESTRATOR WINDOW REQUEST 1 ("→ GEOMETRY OK on … android") are not achievable. The brief §2
    already warned that Android's hyphen "only shows up at a luminance threshold of 200".
- **Evidence.** `skeptic-evidence/geometry-anchor.wave52-ship.txt`:
  - `sum<300 … span-002 android line1-ink [(25, 54)…] → GEOMETRY WRONG`.
  - `max<200 … span-002 android [(25, 61)…] → GEOMETRY OK`.
  - Under `max<200`, every currently broken row still prints WRONG: span-001 android x25-77, -001 android x25-77, -002
    android box 3 x25-31, iOS 26 vs 46, -002 web 26 vs 46. Every ref row prints OK.
- **Fix (orchestrator; the probe is a plan file, L2 does not own it).**
  - Before L2 lands, restate the probe's line-ink predicate as max(r,g,b) < 200 in a §10 addendum. That is the brief's
    luminance-200 rule; it excludes the orange border (r = 255), which a plain sum < 450 would admit.
  - Add span-002 android (and ios/web) as a positive self-check row that must print OK, or exit 1.
  - Re-prove on wave52-ship: ref and anchor rows OK, the five target rows WRONG.
  - Then restate L2's geometry expectation lines.

### D2 — SHOULD-FIX: the predicate's "transparent ink REQUIRED" half is unpinned

- **What.** The pin `the inert predicate needs BOTH out-of-flow and transparent ink` claims to test "an opaque abspos span".
  It only REMOVES the Color declaration.
  - Mutation x5 (`"Color" -> transparentInk = true`, accept any declared colour) leaves 42/42 green. So do x2 (any explicit
    alpha) and x3 (missing alpha = transparent).
  - With x5, an abspos `color: red` span with real glyphs would be dropped from the paragraph, and no test would fail.
- **Fix (L2-owned `InlineRunFoldTest.kt`).**
  - Add synthetic negatives on the verbatim member: Color `{"srgb":{"r":1,"g":0,"b":0},"original":"red"}` (no alpha) and
    `{"srgb":{"r":0,"g":0,"b":0,"a":0.5}}`. Both must be refused.
  - Re-run x5, x2 and x3 and record them red.

### D3 — NIT: other predicate branches are unpinned

- x1 (structure check deleted) survives 42/42.
- x4 (the arm's `TEXT_MEMBER_TAGS` test dropped) survives 42/42.
- `FIXED` is never exercised.
- There is no corpus reach, but the banner's "REQUIRED"/"refuse" claims are partly untested. Add one negative each (a
  member with a `runs` child; a `label`/`b` tag with an otherwise inert shape).

### D4 — NIT (latent): border colours are treated as inert, which ignores the repo's own `border: inherit` wire dialect

- `InlineAtomRing.kt:39-50` documents that the converter emits `border: inherit` as exactly four
  `Border*Color {"original":"inherit"}` longhands, and that such a member PAINTS the parent's border.
- `InertOutOfFlowMember.BORDER_COLOR_TYPES` admits those as "styleless … paint nothing". An abspos transparent span with
  `border: inherit` inside an orange-bordered host would be dropped, though it paints.
- Corpus reach is 0: none of the 21 members carries a Border*Color.
- Fix: refuse a Border*Color whose data is the `inherit` keyword (InlineAtomRing's detection), or drop the tolerance, which
  no carrier needs.

### D5 — NIT: F1 widens an unlogged fallthrough

- F1 bakes U+2010 into three more Compose pieces (hyphenate-character-001/-003/-004 piece 1) where the wire's
  `hyphenate-character` is `""` / `•` / `/-/`.
- Compose logs nothing for the ignored property (`TextStyleApplier.extractHyphenateCharacter` is never consulted on this
  path). iOS has only a TODO in `HyphenateCharacterApplier.swift`.
- The plan labels those flips DEGENERATE, so the read-out is honest, but the runtime path is silent. Suggest a
  `PropertyTracker` breadcrumb when a fired run's `HyphenateCharacter` is not `auto`. That is a seam or caller change: hand
  it off, or queue it.

### D6 — NIT: the D3 seam wiring is unpinned on the host

- S-x2 (leaf site `horizontalWritingMode: true`) survives 89/89.
- The lane says so ("verified by compile + the helper pins"). The only check is the device must-not-move
  `hyphens-vertical-001` ios (css-text is probed).
- The probe must treat that cell's decoded-pixel identity as THE D3 pin, not as a formality.

### D7 — NIT: `alphaIsZero` can throw instead of refusing

- `srgb["a"]?.jsonPrimitive` throws on a non-primitive `a`, so it crashes rather than refuses. This happens only on a
  malformed wire.
- Use `(srgb["a"] as? JsonPrimitive)?.floatOrNull`.

Not defects (checked):
- Files and comments:
  - `InertOutOfFlowMember.kt` is 126 lines; `SoftHyphenPolicy.swift` 172 (+68 / -1).
  - The oversized files got only a condition (`PreBreakPipeline.kt`), or a field, counter and arm (`InlineRunFold.kt`).
  - Every new statement is commented.
- No "probe" leftovers in owned sources.
- The scratchpad path appears only in the required `TREES:` line.
- No seam file was left modified and no lock dir was left behind.
- seam-1 is 7 hunks rather than the registered 1. That is justified (PlaceholderLabel has no writing-mode input), and it
  stacks under L4's seam-2.
- DEGENERATE labels on the hyphenate-character movers are present.

## 3. What I could NOT check

- No device, so:
  - no capture of the post-fix pictures;
  - not that F1's baked U+2010 rasterises identically to span-002's Minikin hyphen (argued from Minikin's U+2010 insertion
    plus the same face; D1 holds whenever the hyphen stays lighter than sum 300);
  - not the real-font fit decisions (`highway` > 61 px is taken from today's Minikin emergency break).
- The iOS label render path, which is private to the seam (S-x2 shows it is unpinned).
- Whether the iOS hyphenate-character-00x movers move up or down.
- L4's seam-2 compiled together with L2's seam-1. I checked only that they apply textually. L4's patches construct no
  `PlaceholderLabel`, so the new required property cannot break them.

## 4. Hand-offs

- **Orchestrator (D1, blocking):**
  - restate `soft-hyphen.geometry.py`'s ink predicate (max channel < 200);
  - add the span-002 positive self-check row;
  - re-prove on wave52-ship (`python3 tools/titan/results/wave53-soft-hyphen/skeptic-geometry-anchor.py wave52-ship` shows
    the expected per-row verdicts);
  - record it in the PLAN §10 addendum before L2 lands.
- **L2 fix lane (D2, D3, D7):** pins in `InlineRunFoldTest.kt`, plus the one-line `alphaIsZero` hardening in
  `InertOutOfFlowMember.kt` (both L2-owned). Re-run x1-x5 red.
- **Queue (D4, D5):** for a later wave, or a fix lane if the orchestrator prefers.

## Files

- Scripts:
  - `skeptic-census.py` (JSON census)
  - `skeptic-f2-census.kt.txt` (JVM fold census, export tree only)
  - `skeptic-mutate.py` (mutation replayer, export tree only)
  - `skeptic-geometry-anchor.py` (D1 repro)
- Results, in `skeptic-evidence/`:
  - `mut-*.json`
  - `json-census.wave52-ship.txt`
  - `jvm-f2-census.wave52-ship.txt`
  - `geometry-anchor.wave52-ship.txt`
  - `revert-units.sh`

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read-only except this
directory); export tree for Gradle (`--no-daemon`, in-process Kotlin; every single-use daemon confirmed dead) and xcodebuild
(private derived data `…/sk/dd-sk`): /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/sk/exp

STATUS: COMPLETE

# Wave 51 PR (A) — Android skeptic verdict (executed repros, 2026-09-22)

Tree @ b2ed4454 (WIP 0a86172a + design record). Audited: `git diff b8f3593e -- runtimes/compose apps/android-harness`
(BlockLabel.kt, ComponentRenderer.kt, BlockLabelTest.kt, ScreenshotCaptureScreen.kt, HarnessLabelChromeTest.kt NEW).
Every lane claim below was RE-EXECUTED; nothing is taken from `lane-reports.json` on trust. Working log with commands
and outputs: session scratchpad `wave51/prA/skeptic-android.md` + `android-skeptic-*.log` (not durable).

## Executed
- `:runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.core.renderer.*'` → 27 classes / 222 tests / 0 failures (fresh).
- Fresh compile `--rerun` of :runtime + :app + :app unit tests → 0 errors; 28 pre-existing `w:` warnings, none in owned files.
- `:app:testDebugUnitTest --rerun` → 127 tests / 0 failures (XML dated today; the first, non-`--rerun` call was UP-TO-DATE,
  i.e. the lane's 09-16 cache — not a repro until forced). `:app:packageDebug --rerun` → fresh APK, exit 0.
- `bash tools/visual/doc-staleness-check.sh` (CI job) → **exit 1**: `live: android-harness(:app)=127`, README.md / CLAUDE.md /
  docs/STATUS.md "does NOT mention live android-harness test count 127" (docs say 116). Also red for the other lanes'
  counts (web-harness 297, swiftui 2019, tooling 2176); compose=3232 still matches.
- Six mutations, each restored byte-exact (sha256 equal, `git diff --quiet -- <file>` clean):
  - S1 = lane M2, WPT gate dropped from `showsLabel` → RED predicate_isGatedByTheWptCaptureFlag (1/11).
  - S2 = lane M1, `.drawWithContent{}` moved after `.padding(CaptureCanvasPadding)` → RED drawNode_sitsBetween… (1/11).
  - S3 mine, runtime `truncatedCount` drops EDGE_MARGIN → RED 3 BlockLabelTest + 3 HarnessLabelChromeTest geometry pins.
  - S6 mine, `_`→space dropped in `harnessLabelRects` → RED geometry_underscoreBecomesSpaceBeforeNormalize.
  - S4 mine, `drawContent()` moved AFTER the label loop (label painted UNDER the component) → **GREEN, 11/11** (unpinned).
  - S5 mine, harness paints `Color(0xB2EDEDED)` (α 178) instead of `BlockLabel.COLOR` → **GREEN, 11/11** (unpinned).
  (A first pass collided with the iOS skeptic's same-named runner in the shared scratchpad and ran xcodebuild; those
  results were discarded, files were verified byte-exact, and all six were re-run under `android-mutate.sh`.)

## Verified by reading / grep
- Deleted `BlockLabelPlaceholder`: zero callers (only the P3 assertion strings). `truncatedCount` rename is positional-safe.
  No other `componentWidth` label channel exists in runtimes/compose.
- Label-free corpus: `LocalWptCaptureMode provides inboxMode` (ScreenshotCaptureScreen.kt:611) wraps the ONLY `CaptureView`
  → only `CaptureCanvas(` call; `inboxMode` ← `--ez titanInbox true`, which feed-android.mjs:256 always passes; composed
  path provides `true` and renders `ComposedCaptureCanvas` (no chrome; pinned by composedCanvas_drawsNoLabel on its own slice).
- Predicate parity: Android `children.isNullOrEmpty() && _text.isNullOrEmpty()` on the SlotComposer root ≡ iOS
  `children?.isEmpty ?? true && text?.isEmpty ?? true` ≡ web `node.children.length===0 && !(text non-empty)` — null/"" both
  "no text", whitespace-only counts as text, on all three.
- Colour: `COLOR = 0xB3EDEDED` (α 179; pinned by BlockLabelTest) SrcOver on ground `0xFF1A1A2E` → (174.11,174.11,180.07) →
  (174,174,180), the tripwire's byte. Origin (8,6), advance 6, truncation `budget<=0?0:min(len,floor(budget/6))`, normalize —
  all identical to the tripwire; all 41 Kotlin glyph rows == `block-font.json` (0 mismatches), checksum recipe matches.
- Frame size: `drawWithContent` is draw-only; the removed 0×0 node was never a container measurable — every childless
  flex/grid root is demoted to `Box{RenderContent}` (ComponentRenderer.kt:2173), the 10 childless flex/grid baseline roots
  included. Chain order width→background→testTag→onGloballyPositioned→drawWithContent→padding; the out-of-flow root branch
  and `ComponentHost.Render` are inside the content lambda; PixelCopy crops the same node, unscaled.
- r3 corrections: P2 sliced to `private fun CaptureCanvas(`…`internal fun isOutOfFlowRoot(` (ComposedCaptureCanvas is
  outside it); case-insensitivity pin present and live; text = plain name, pinned by the exact-call assertion.
- New test file: 195 lines; each test/helper carries a why-comment, but 109 code lines vs 65 comment lines — not literally
  every line.

## Defects
1. MUST-FIX (CI): doc-staleness gate is red on HEAD — android-harness live 127 vs "116" in README.md:213, CLAUDE.md:293,
   docs/STATUS.md:2425 (+ prose at 2283/2369/2399/2414) and apps/android-harness/README.md:31. Restamp in the same PR
   (the sweep must also restamp web-harness/swiftui/tooling). No Android code change needed.
2. SHOULD-FIX: draw ORDER unpinned (S4). A regression painting the label under the component passes every JVM pin; only
   the post-refresh tripwire's glyph-mask clause would catch it, and only on the 19 band-paint stems. Add to P2: index of
   `drawContent()` < index of `if (showsLabel)` inside the `.drawWithContent {` block of the CaptureCanvas slice.
3. SHOULD-FIX: colour USE unpinned (S5). BlockLabelTest pins the constant's value; nothing pins that the harness paints
   with it. Add to P2: the slice contains `drawRect(BlockLabel.COLOR,`.
4. NIT: `if (!hasRawText) return` (ComponentRenderer.kt:6305) still runs placeholderDisplayText / applyTabSize /
   SoftHyphenPolicy / AutoHyphenation for every nameless leaf and discards the result (lane's "deliberately minimal");
   hoist it to just after the WPT gate at 6207 when the file is next touched.
5. NIT: HarnessLabelChromeTest.kt is not every-line-commented (imports, assert lines).

## Lane openQuestions, answered
- Q1: `Modifier.width(canvasWidth)` fixes the node's px width to `canvasWidth.roundToPx()` == `widthPx`; PixelCopy bounds come
  from the same node, so the Log.w fires only if the parent's max constraint is narrower than the canvas (a device narrower
  than 390dp) — the one case it must name. YES: the §5 refresh should `adb logcat -d | grep "Harness label chrome:"` and
  stop on any hit; test-all.sh already greps `Capture run config:` (lines 1116-1130) — add this grep there as a follow-up.
- Q2: confirmed — `WptCaptureModeTest` lives in :app (apps/android-harness/app/src/test); the :runtime filter matches
  nothing and Gradle tolerates it. Run the whole :app suite instead (127 tests, ~2 s).
- Q3: agree it is a follow-up; nothing here blocks moving BlockLabel.kt + BlockFont.gen.kt + BlockLabelTest into the harness.

## Not verified (device-gated, belongs to §5)
PNG (8,6) landing, the (174,174,180) byte, S1 PNG-dimension invariance and the two Log.w lines staying silent.

Verdict: the Android half is correct as built and every claimed pin is live; ship after the doc restamp (1) and,
preferably, the two one-line P2 assertions (2, 3).

## Re-verify (2026-09-22 17:40) — must-fix 1: doc-staleness android-harness 116→127

FIXED, by executed check; no regression introduced. Working log: scratchpad `wave51/prA/skeptic-android.md` (§ Re-verify).
- `git diff -- README.md CLAUDE.md docs/STATUS.md apps/android-harness/README.md` → exactly the four claimed hunks:
  the `| android-harness app (JUnit) | … | 127 |` row at README.md:213 / CLAUDE.md:293 / docs/STATUS.md:2425, and
  `(127 tests —` + `Titan inbox, harness label chrome):` at apps/android-harness/README.md:31/33. Nothing else in them.
- `git status --short -- runtimes/compose apps/android-harness` → only the README: no Android code, test or pin moved.
- `grep -rE '@Test' apps/android-harness/app/src/test | wc -l` → 127. `:app:testDebugUnitTest --rerun` (JDK 21) →
  BUILD SUCCESSFUL, 11 XMLs dated 17:40 (newer than the newest test source), tests=127 skipped=0 failures=0 errors=0.
- `bash tools/visual/doc-staleness-check.sh` → `live: android-harness(:app)=127`; ✓ README.md / ✓ CLAUDE.md /
  ✓ docs/STATUS.md mention android-harness=127; ✓ apps/android-harness/README.md still mentions compose=3232 (the
  `(3232 tests)` phrase next to the edited lines is intact); no `executed != annotated` warn (XML path == annotation
  path). CI's node-only job has no gradle build dir, so it takes the annotation path → the same 127.
- Dated-record claim checked: `git grep -cE '@Test' b8f3593e -- 'apps/android-harness/app/src/test/**/*.kt'` sums to
  116, so the four STATUS.md prose lines (2283/2369/2399/2414: wave-49/50, PR-1, PR-2 trees) are accurate records;
  `doc_quotes_suite_count` (doc-staleness-lib.sh:22-33) reads only the table row / `(N tests` phrase, never prose.
- Still red, NOT Android: tooling 2176, web 1338, swiftui 2019, web-harness 301 (exit 1 overall). Two of those moved
  during the fix pass (web-harness 297→301, web 1337→1338), so they are the ship-time single-writer sweep's restamp
  from the final tree — a CI blocker for the PR, not a device-gate blocker and not an Android defect.
- New SHOULD-FIX (comment-only, owned path): BlockLabel.kt:61-65 says "the web harness moved to 179/255 in the same
  PR", but runtimes/web/src/renderer/BlockFontLabel.ts:72 now ships alpha 0.706 (byte 180) because Chromium composites
  byte 179 one LSB dark. The composited byte (174,174,180) still matches on both; only the prose is stale. Replace the
  last two sentences with: "the composited byte (174,174,180) is the contract, not the alpha: Compose and SwiftUI
  reach it at alpha byte 179, web at byte 180 (CSS 0.706) — see runtimes/web/src/renderer/BlockFontLabel.ts". No pin moves.
- Should-fix 2/3 (S4 draw-order, S5 colour-use P2 assertions) remain open — untouched by the fix pass, as reported.

Re-verify verdict: must-fix 1 closed; Android half ships once the sweep restamps the other lanes' counts.

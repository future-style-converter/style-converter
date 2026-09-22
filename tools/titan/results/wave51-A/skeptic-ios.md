# Skeptic verdict — iOS lane, wave 51 PR (A) "harness label chrome"

Tree `b2ed4454` (campaign/wave51-labels), audited 2026-09-22 read-only except
executed-and-restored mutations (sha256 before == after, `git diff` empty on
every touched file; two temporary probe test files copied in, run, deleted —
`git status` clean on runtimes/ and apps/). No device, no simulator boot.
Working notes + logs: session scratchpad `wave51/prA/skeptic-ios.md`,
`ios-run-baseline.log`, `ios-probe.log`, `ios-probe2.log`, `ios-mut-{Malpha,
Malpha-probe,M2,M5,Morigin}.log`, `ios-harness-build-skeptic.log`.

## VERDICT: PASS — no must-fix; 1 should-fix, 4 nits. `mustFixBeforeGate: false`.

## Executed repros (all mine, not the lane's records)
1. Focused Catalyst run of the lane's 5 classes (HarnessLabelChromeRasterTests,
   BlendGroupCompositingTests, BlockLabelTests, WPTCaptureModeTests,
   EmptyFlexContainerTests): **53 tests, 0 failures**; no warnings in changed files.
2. Harness build, provision-devices.sh:102-107 invocation (absolute IOS_DIR,
   `-sdk iphonesimulator -arch arm64 CODE_SIGNING_ALLOWED=NO`): **BUILD
   SUCCEEDED**, `SwiftCompile … CaptureCanvas.swift` in target StyleConverterTest.
3. Probe 1 (8/8 green, temp XCTest on CaptureCanvasMirror with chrome at the
   harness mount point):
   - frame size with/without chrome: flow 390×72 == 390×72, out-of-flow 390×32
     == 390×32, 70-glyph name unchanged (S1 of design §5 holds on the mirror);
   - positional identity: lit set == P derived from BlockFont at (8,6), 178 px
     for "LABEL CHROME"; **178/178 EXACT (174,174,180)** — no ±1 needed;
   - out-of-flow branch: label at (8,6) over the abspos box ((176,176,176) =
     ink over #222), unclipped; (9,6) = box;
   - truncation vs FRAME: 70×'H' → 62 glyphs (1054 px, last column 378);
     frameWidth 250 → 39 glyphs (663 px); 21 → 0 px, no crash, frame unchanged;
   - `text:""` → label; `text:"x"` → none (raster); v2 flat doc with a slot
     child: IRComposer attaches children → root unlabelled, the child leaf
     standalone == P("KID LEAF"); margin-top −16 → chrome composited OVER the
     box paint; flag published outside the chain beneath styleKeyframes → 0 ink.
4. Probe 2 (2/2 green): **WPT mode: raster with chrome == raster without,
   byte-for-byte** (flow plain, flow multiply, out-of-flow) → TITAN/inbox
   captures are bit-identical to pre-(A). **Baseline mode: the set of pixels
   that differ with vs without chrome == P exactly (178)** for plain, multiply,
   opacity 0.5, margin-top −16 — nothing but the glyph rects moves.
5. Mutations (snapshot → mutate → xcodebuild → restore → sha256 == → git diff empty):
   - **M2** (lane's) drop `guard !wptCaptureMode` → RED 2: 178 px under the flag,
     predicate "wpt". Restored (sha cecb6f1d…).
   - **M5** (lane's) restore `BlockLabel(…, componentWidth: nil)` in the
     nameless-leaf branch → RED 6: WPT re-target 271 px flag OFF; chrome (a) 178,
     (f) 178, (e) 91, (g) 178, children 43 (one more than the lane recorded —
     the child's own in-element label). ComponentRenderer.swift == HEAD after.
   - **M-origin** (novel) originY 6→7 → RED 11 ((8,6)/(12,12) read stage; 38 px
     outside the band, first (8,7); BlockLabelTests 3).
   - **M-α** (novel) labelColor opacity 179/255 → 0.7 → **GREEN 18/18**; re-run
     with the exact-ink probe: still 178/178 exactly (174,174,180) — on the
     Catalyst raster CoreGraphics composites 0.7 to the same byte, so the ±1
     tolerance hides no iOS byte difference (see should-fix).
6. Call-site sweep: `BlockLabel(` only in HarnessLabelChrome + 2 tests;
   `usedWidth` gone, `SizeApplierResolve` still has 23 other users; no
   `componentWidth` channel left in ComponentRenderer; ComposedCaptureCanvas
   untouched; FixedHoistTests references the label only in a comment.

## Design conformance (design §2–§4, r3 corrections)
- Chrome is a sibling `.overlay(.topLeading)` after `.background(canvasBackground)`
  on both root branches (out-of-flow after `.clipped()`); nothing above the
  insert is a paint node. Explicit `public init` on BlockLabel, BlockLabelLayout,
  HarnessLabelChrome. Chrome reads `\.wptCaptureMode` + `\.backdropPass` itself.
- Predicate == Android `!LocalWptCaptureMode && children.isNullOrEmpty() &&
  _text.isNullOrEmpty()` == web `!WPT_MODE && node.children.length===0 &&
  !(non-empty text)`: iOS `children?.isEmpty ?? true && text?.isEmpty ?? true`
  on the CaptureCanvas input (composed root; IRComposer leaves children nil).
- Gate wiring: bundled `captureNext` never sets the flag (label shows);
  `captureAllComponents` publishes `CaptureOverrides.titanInbox`, which is
  always true there (ContentView routes to InboxCaptureView only under it);
  composed path publishes literal true and mounts no chrome. `.sampling` guard
  is defence-in-depth as labelled. Text = plain name, `_`→space, uppercase in
  glyphString (case pin: BlockLabelTests:59). Truncation formula, origin (8,6),
  atlas checksum cb3c6e411c7b2859, colour 179/255 match the tripwire's
  expectations (origin, `normalize`, `floor((w−16)/6)`, INK_ALPHA 179) and
  Android 0xB3EDEDED / web 0.70196. All 130 baseline stems are within the atlas.
- New files 130/136/200 lines, commented per statement; BlockLabel.swift 209
  (existing file — accept).

## Defects
- **should-fix** `runtimes/swiftui/Tests/StyleConverterRuntimeTests/HarnessLabelChromeRasterTests.swift:125,131`
  — the ink-byte pin tolerates ±1/channel while docs/DYNAMIC_CAPTURE.md §5
  says "one LSB of drift is a defect, not a tolerance"; measured 178/178 EXACT
  in three runs, and M-α proves the pin cannot see the α-0.7 class. Tighten to
  exact equality (the raster supports it) so the iOS pin states the contract.
- **nit** `HarnessLabelChromeRasterTests.swift:19-33` header: M5 signature omits
  the children case (43 px, red on re-execution); "lane notes hold the logs"
  points at a purged scratchpad — repoint to tools/titan/results/wave51-A/.
- **nit** `BlockLabel.swift:147-153` labelColor comment: "0.7 would leave the
  final byte to rounding" is unmeasured — on Catalyst 0.7 yields the identical
  byte; keep 179/255 for cross-platform readability, reword the claim.
- **nit** `BlockLabel.swift:69-75` glyphString iterates Swift `Character`
  (grapheme) where web/tooling iterate code points: a combining sequence in a
  name would give one `-` on iOS vs two elsewhere. No fixture/baseline name has
  one; document or normalise to unicodeScalars for parity.
- **nit** (record) lane-reports.json iOS M5 says "chrome 4 red"; actual 5.

## Not verified
Device/simulator bytes (Catalyst only; §5 S2/S4 own it); full 2008-test suite
(orchestrator sweep; grep found no other label-dependent test); harness overlay
wiring is compile-proven, not captured.

## Open questions answered
- Q1 web α: `runtimes/web/src/renderer/BlockFontLabel.ts:61` is 0.70196 (=179/255)
  in this tree → the BlockLabel.swift comment is true for this PR.
- Q2 mutation record: the purged logs are superseded by this file's executed
  M2/M5 (+ M-α, M-origin); repoint the test header (nit above).
- Q3 209 lines: accept (existing file; CLAUDE.md splits at ~300).
- Q4 `withLabelChrome:` flag form: accept — it mounts at the harness's exact
  point; probe 2 shows the `false` arm is byte-neutral.
- Tooling (iii) ±1 as it concerns iOS: iOS ink is exact and deterministic over
  ground and over paint; any device ±1 is a compositor property, not iOS code —
  no objection to positional-only on the 19 glyph-mask stems.

## Incident
A generic `mutate.sh` I wrote in the SHARED scratchpad was invoked by the
Android lane (S1/S2) two seconds later; their Kotlin files were restored
byte-exact by its sha check, but `ios-mut-S1/S2.log` are xcodebuild output —
INVALID Android evidence. Runner renamed `ios-mutate.sh`; stub + COLLISION-NOTE.md left.

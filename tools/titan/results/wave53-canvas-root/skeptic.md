# wave-53 lane L3 · canvas-root — SKEPTIC report

Tree: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (HEAD e330e255, shared, other lanes live).
Inputs read: `_note.md`, PLAN.md §0/§1/§2 L3/§3/§4/§6/§10, `expectations.json` `lanes.L3-canvas-root`, the briefs
`contents-root-background.md` / `display-table-body.md`, the diff on all 17 owned paths, the unit patches.
Nothing here was taken from the lane's own scripts: every number below comes from a skeptic script in this directory
(`skeptic-*`), run on the shared tree, with every touched file restored byte-exact (sha256 logged).

## Verdict: MIXED — the lane's claims hold; the pinning has real gaps; no must-fix

What holds, by execution:
- **All 36 of the lane's mutations are RED** when replayed by an independent runner (`skeptic-mutate.py`). The runner reads red
  from STRUCTURED reports, not console greps: vitest `--reporter=json`, JUnit XML written after the run started (stale XML
  ignored), and xcodebuild `Test Case … failed` lines. A compile break would be reported as COMPILE and is never counted as red.
  Result: 36 RED, 0 NOT-RED, 0 COMPILE, 0 restore mismatches (`skeptic-mutations.log`). Every file was green again after the
  restore: web 78 + 89, Compose 175, Catalyst 101. All three totals match the note.
- **Blast radius re-derived three independent ways, and it matches the lane exactly.** The lane did not under-report it.
  - *Code-free wire census* (`skeptic-rule-census.py`, both runs): 1435 docs; 284 body-roots, giving 3 image bodies, 86
    colour-only and 195 background-less; 1151 docs without a body; 1 TABLE body (006). The near-miss `s-11-1-1b-005`
    TABLE_CELL body is correctly excluded. No `none`-only image bodies, no contained image bodies, no slotted body-roots.
  - *Web, per CANVAS from the COMBINED section documents.* These are what the capture page actually loads, so this exercises
    the gallery's own `groupByTest`; the lane's census used the per-test documents (`skeptic-web-census.sh`). HEAD gallery
    vs tree: **1435 canvases, 4 differ** (006, margin-root-001, -002, display-contents-root-background), 0 on one side only.
    Identical on wave52-ship and wave53-open.
  - *Natives through the real resolvers* (temporary census classes appended to L3-owned test files, then restored
    byte-exact: `ComposedCanvasTableBodyTest.kt` d42ce789…, `TableBodyForestTests.swift` 160a14c9…). Compose: 1435, 0
    decode errors, 3 plans, 4 forests changed. Swift: 1435, 0 errors, 3 plans, 4 changed. Both runs give the same result.
  - So the carrier set is 4 documents × 3 platforms = `expectations.json` captureCarriers, and `wireCarriers` = [].
- **Scores** (`cells.mjs … wave52-ship wave53-open`): every cited number checks out.
  - target: f 0.534 (0.5341 on open) / 0.5346 / 0.5355.
  - margin-root-001: f 0.4511 / 0.4509 / 0.451. margin-root-002: f 0.339 / 0.3391 / 0.3389.
  - 006: P 0.9941 / 0.9953 / 0.9944.
  - All 78 must-not-move cells are unchanged between the two runs (e.g. contain-*-bg ×24, a98rgb-003, clip-path-document-element,
    s-11-1-1b-001…009).
- **PNGs looked at.**
  - The refs and wave52-ship captures for the target, margin-root-001 and -002, and 006.
    `canvas-root.geometry.py wave52-ship` prints GEOMETRY WRONG on all nine A rows: painted (66,66)-(323,365), green 0.0.
    The refs print GEOMETRY OK (bands at 66… for 001 and 16… for 002, frame clean).
  - `display-table-body.geometry.py wave52-ship 006` gives ref 56-75, web 66-85, ios/android 51-70, all at x 24-43.
  - `baseline-empty-cell-001` android: the second (empty) cell is missing and the first box is wider than the ref's. This
    matches the lane's reason for predicting a B-android failure.
  - I dumped the rendered 006 web DOM. The `data-capture-flow` wrapper is `display:table` and the td `<div>` is a DIRECT
    child with `display:table-cell`; there is no per-root block wrapper that would re-wrap it. Chrome's §17.2.1 fixup
    therefore puts the td in the anonymous row at the wrapper's top (y 16+40 = 56), as predicted.
- **Revert units** (`skeptic-units-check.sh`, independent of make-units.py): PASS on every check.
  - In a throwaway repo seeded with HEAD bytes, A → B-web → B-ios → B-android apply in order and reproduce all 17 tree files
    byte-for-byte.
  - A applies alone on HEAD, and each B applies alone on the A state.
  - Each B reverse-applies alone on the final tree.
  - Unit A carries 0 item-B symbols.
- **The predicted B-android revert leaves a working tree.** I reverse-applied `unit-B-android.patch` on the shared tree (4
  L3 paths backed up first). That A + B-web + B-ios state compiles, and 157 Compose tests run green. The paths were then
  restored (owned-shas all equal) and the restored state re-ran green.
- **Hygiene.**
  - No seam file or read-only file is modified (git status). `tools/titan/runs/wave53-lock/` is empty.
  - All L3-marked edits are inside the 17 owned paths, with 0 probe leftovers.
  - Every embedded test payload is verbatim wave52-ship IR: 97 component objects compared JSON-equal. The 3 non-verbatim
    objects in WPTCaptureModeTests.swift pre-date L3.
  - `ios-source-pins.mjs` gives 7/7 GREEN on the edited canvas. The L5-owned XCTest substrings in
    `ComposedCanvasIcbClipTests.swift` all survive L3's text.
  - tsc is clean on runtimes/web and apps/web-harness.
- **Honesty.**
  - B-android is predicted to FAIL its probe line, with LOW confidence and the expectation that it is reverted.
  - The 22 wrong-picture `s-11-1-1b-00x` passes are booked as DEGENERATE.
  - None of the predicted flips is on a picture that would still be wrong: the post-fix pictures for the target, 001, 002
    and 006 web/ios match the refs in geometry.

## Skeptic probes — mutations of branches the lane's 36 do not reach (outcome recorded, not asserted)

| id | mutation | outcome |
|---|---|---|
| XW1 | web framed plan's `base` 16 → 0 | RED (target_framedSurface) |
| XW2/2b | web layer attachment passed verbatim | RED (runtime + gallery pins) |
| XW3 | web `TABLE_CELL` body treated as a table | RED (nonTableBodies, s005_…) |
| XW5 | web wrapper `display` stays `flow-root` | RED (s006_*) |
| XW6 | web no-wrapper branch renders the unstripped forest | RED (target_bodyRootLosesImage) |
| XW7 | web root-clip routing ignored | GREEN — unpinned (no carrier) |
| XW9 | web F2 repeat gate dropped (`repeatsBothAxes → true`) | GREEN — unpinned |
| XW10 | web gradient always uniform | RED |
| **XC1** | **Compose `canvasModifier`: frame-band overpaint dropped for non-uniform stacks** | **GREEN — unpinned** |
| **XC2** | **Compose `canvasModifier`: tile base `frame.value` → 0 (band phase 50 instead of 66)** | **GREEN — unpinned** |
| **XC3** | **Compose `canvasModifier`: layer order not reversed (001's opaque black layer would paint on top)** | **GREEN — unpinned** |
| XC4 | Android `.then(rootImageModifier)` removed | RED (callSite source pin) |
| XC5 / XS4 | TableBodyForest `paintsOwnEdges` bail dropped (Compose / Swift) | GREEN — unpinned (no carrier) |
| XC6 / XS5 | TableBodyForest proper-non-cell bail dropped (Compose / Swift) | GREEN — unpinned (no carrier) |
| XC7 / XS6 | F2 repeat gate dropped (Compose / Swift) | GREEN — unpinned |
| XC8 | Compose gradient always uniform | RED |
| XS1 | Swift non-uniform stack not padded in to the ICB | RED (001/002 pixel pins) |
| XS3 | Swift layer order not reversed | RED (001/002 pixel pins) |
| **XI1** | **iOS: ALL THREE L3 call sites removed from CaptureCanvas.swift (`.background(rootImageBackground)`, the item-A strip, `TableBodyForest.rewrite`)** | **GREEN — nothing in the lane (or L5's XCTest substrings, replayed by ios-source-pins.mjs) can see it** |

## Defects, ranked

1. **should-fix — the Android item-A paint composition is unpinned.** This covers `RootBackgroundPropagation.kt`
   `canvasModifier`, executed as XC1–XC3. The three decisions that decide the device picture of margin-root-001/-002 android
   can each be broken with every test green: the frame-band overpaint, passing `frame` as the tile base, and reversing the
   layer order.
   - The note's "android configs pinned (offsets 66/16)" is true only of `layerConfigs` called directly with base 16. It is
     not true of the call the canvas makes.
   - Fix: factor a pure `canvasPaintPlan(props, plan, frame)` that returns the bottom-up configs plus an `overpaintFrame`
     flag, have `canvasModifier` consume it, and pin it on verbatim 001. The pin should assert bottom-up = [black at (16,16),
     ramp at (66,66)] and overpaint = true; on the target it should assert overpaint = false. Mutate XC1–XC3 red.
   - No gate rule depends on this: the cells are f today, MED/MED-LOW, report-only. This is a pin gap, not a predicted
     regression.
2. **should-fix — the iOS call sites have no pin at all (XI1).**
   - The target ios cell is HIGH and gating (floor 0.99), and it lives in unit A together with the web and Android target
     cells. A broken iOS wiring line would therefore revert all three platforms' gains at the probe.
   - Fix: add a source pin beside `ios-source-pins.mjs` (a node script in this directory, no device). It should assert:
     - `.background(rootImageBackground)` sits after `.clipShape(WPTCanvas.IcbClipBand`, before
       `.background(canvasBackground)` and before `.rootCanvasClip(`;
     - `splitRoots` contains `withCanvasOwnedRootBackground(document.components, rootImagePlan)` inside
       `TableBodyForest.rewrite(`.
   - Prove it red on XI1.
3. **should-fix — the Android target's HIGH / gating confidence rests on a path the JVM never executes.**
   - On the JVM, `canvasModifier` for the target is `Modifier`: the url decode is stubbed.
   - The note's only device corroboration is `css-image-fallbacks-and-annotations002 android P 0.9982`. That is the
     `image()` notation (`{"type":"image","srcs":[…]}` → `ImageNotation`), not the target's plain `{"url":"data:…"}` layer
     (→ `BackgroundImageConfig.Url`).
   - Better evidence exists and should be cited: `css-break/background-image-000/-001/-002 android P 1` (`cells.mjs`). They
     are plain `{url:data:image/png…}` layers over a red background colour, through ColorApplier.
   - Recommendation to the orchestrator: because unit A spans three platforms, an Android-only miss on the 0.99 floor reverts
     the web and iOS target gains too. Read the Android target at the probe before treating unit A as one go/no-go.
4. **nit — untested branches the note states as behaviour.**
   - These hold by code reading only: the TableBodyForest identity bails ("non-zero padding/border", "a proper non-cell
     table child") on both natives (XC5, XC6, XS4, XS5), the F2 repeat-both-axes gate on all three runtimes (XW9, XC7, XS6),
     and the web root-clip routing (XW7).
   - All have 0 corpus carriers, so nothing moves this wave. Either pin them or mark them "unpinned, no carrier" in the note.
5. **nit — a web/native twin divergence.** Web `CanvasTableBody.ts` has neither natives' bail (padding/border body, a run
   with a proper table child). A padded TABLE body would get a `display:table` wrapper on web while the natives return
   identity. There are 0 carriers. Record it, or mirror the bail.
6. **nit — F1 takes the root box from the merged html+body bag's margin.** For a body-sourced root background on a body with a
   margin, §2.11.2/§3.4 position it against the ROOT (html) element, not the body margin. All three carriers declare on
   `html`/`:root`, so none diverges. Record it as a limitation next to the root-`contents` self-strip.
7. **nit — the PLAN §0 file-size rule.**
   - The three new RootBackgroundPropagation files are 211 / 223 / 218 lines; the rule for new files is ≤ 200.
   - The oversized files gained more than call sites: the gallery has +95 lines including two exported helpers;
     ScreenshotCaptureScreen.kt +72 including `resolveComposedCanvasRootBackground` and `composedCanvasRoots`;
     CaptureCanvas.swift +42 including two computed properties.
8. **nit — vocabulary.** "the engine's own serialisers / reading(s) / url() serialiser" appears in
   `RootBackgroundPropagation.ts:32`, its test `:45`, `RootBackgroundPropagation.kt:186` and
   `RootBackgroundPropagation.swift:200`. It should be "runtime".
9. **nit — a few helper bodies have no line comments.** `CanvasTableBody.ts` `displayOf`/`pxCss`/`borderSpacingCss` (l.35-44,
   55-61), and `RootBackgroundPropagation.ts:116,120`.
10. **nit — a scratchpad path in a kept file.** `mutations.json` embeds the absolute session-scratchpad `-derivedDataPath`.
    It is a build cache, not an evidence pointer, but it is a dead path after the purge. The skeptic spec uses a `{DD}`
    placeholder instead.

## Notes for the orchestrator (not defects)
- Spending a probe on B-android, which the lane itself predicts will fail, is optional. Its revert state is verified green
  above, so either holding it or landing-then-reverting is safe.
- `expectations.json` `revertUnits.A.commit` still names "ColorApplier / BackgroundImageApplier one-liners". The lane edited
  neither (see the note §1, "where the tree won"), and unit A is the 10 paths in `make-units.py`.
- Every iOS composed capture now carries a nil `.background(...)` slot. It is pinned paint-neutral only on a trivial
  Catalyst view (`testRootBgA5…`), so the R4 decoded-pixel control on the probe is the real check for the 1431 non-carrier
  iOS captures.

## What I could NOT check
- No device, Chromium, emulator or simulator (lane rules). Every capture-level prediction is still a prediction. That
  includes the Android target's url-layer draw on device, the Android margin-root tile draw and frame overpaint, the iOS
  canvas as mounted on a simulator, and Chrome's actual 006 layout. The last was reasoned from the dumped DOM and the
  brief's replay only.
- The ios-harness XCTest scheme (L5 + L3, 30/30) is an orchestrator window. I checked only that its CaptureCanvas.swift
  substring pins still match.
- The brief's PNG replays are simulations. I re-ran `contents-root-background.replay.mjs` (it reproduces 1.0000 / 0.9995 /
  0.9991) but did not re-derive the margin-root replays.
- Full suites were not run (single-writer rule).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf

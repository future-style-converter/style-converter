# Skeptic verdict — web lane, wave 51 PR (A) harness label chrome

Audited 2026-09-22 on campaign/wave51-labels @ b2ed4454 (diffs vs b8f3593e), owned paths
`runtimes/web/**` + `apps/web-harness/**`. Every lane claim below was re-executed; full working
(commands, outputs, probe scripts) lived in the session scratchpad `wave51/prA/skeptic-web.md`.

## Verdict

**NOT gate-ready — one MUST-FIX.** The structural half is correct and every pin is live, but the
colour half is vacuous: `BLOCK_LABEL_FILL` at `0.70196` composites to the SAME byte as the old
`0.7`, so web does not reach the natives' (174,174,180) and the PR's own tripwire clause (ii)
would be red on all 111 band-identical stems after the §5 refresh.

## Executed repros

1. `cd apps/web-harness && npx vitest run tests/ui/LabelChrome.test.tsx tests/ui/LabelChrome.width250.test.tsx tests/ui/LabelChrome.wpt.test.tsx tests/sdui/ComponentRenderer.test.tsx tests/sdui/ComponentRenderer.widgets.test.tsx tests/sdui/RendererParity.test.tsx` → 6 files / 63 green.
2. `cd runtimes/web && npx vitest run tests/renderer/BlockFontLabel.test.ts` → 12 green.
3. `npm -w apps/web-harness run typecheck` and `npm -w runtimes/web run typecheck` → exit 0 ×2.
4. HEAD-baseline census (pngjs): 009/107/108 — web ink (173,173,179) ×48/276/282, Android+iOS (174,174,180). The review's measurement holds.
5. **Chromium raster probe** (puppeteer 25.4.0, the capture script's exact flags: headless 'new', `--disable-gpu`, `--force-color-profile=srgb`, 390×dsf1; DOM = CaptureCanvas replica with the chrome svg absolute at 8/6, crispEdges 1×1 rects), pixel (8,6) over #1A1A2E:
   `0.7`→(173,173,179) = the committed web baselines (control); `0.70196`→(173,173,179); `0.7019607843`/`0.702`/`0.703`/`0.7035`/`0.7039`→(173,173,179); `0.704`/`0.705`/`0.706`→(174,174,180); `0.71`→175; `0.69`→172. Neighbours ground → crisp, origin exactly (8,6). The break at 0.7039→0.704 (179.49→179.52) means Chromium rounds the alpha to 8 bits and its CPU src-over then lands alpha byte 179 at 173: parity with Compose/SwiftUI needs **alpha byte 180** on web.
   Frame size: natural-height canvas 390×62 with and without the chrome (unchanged).
6. Parity golden vs `git show b8f3593e:…renderer-parity-golden.json`: 19 cases — 7 unchanged, 12 svg-run-removed only, 0 other (lane claim verified).
7. Mutations, each restored from a snapshot with sha256 + `git diff --quiet` (all OK):
   - re-exec lane #2 `frameWidth={Infinity}` → RED '599'≠'233' (width250) and '599'≠'371';
   - re-exec lane #3 drop `!WPT_MODE` → RED wpt.test "2 found, 0 expected", LabelChrome.test stays green;
   - drop children term → RED "root WITH composed children"; drop text term → RED "root WITH text";
   - origin (24,22) in LabelChrome.tsx → RED 'left:8px'; FixtureCanvas predicate forced true → RED "text root or container";
   - `BLOCK_LABEL_FILL` back to `0.7` → RED only in the harness string pin; `runtimes/web` BlockFontLabel.test 12/12 GREEN.
8. Callers of deleted/renamed symbols (`componentWidth|usedPxWidth|parsePx|blockLayout|isBlockLabel|PlaceholderContent name`): none live; stale comments only (ComponentRenderer.tsx:1287, gen-control-fixture.mjs:95, RendererOptions.ts:92-98).
9. WPT/inbox/composed trace: section-runner.sh:557 `WPT_MODE=1` → capture-screenshots.mjs:224 → capture-url.mjs:106 `&wpt=1` → CaptureGallery.tsx:69 `WPT_MODE` → :451 `showLabel=false`. capture-isolated.mjs:79 (wptMode + wptComposed) → ComposedCaptureGallery, which imports neither LabelChrome nor CaptureCanvas and is pinned at 0 chrome under both URLs. test-all.sh:1212 (no WPT_MODE) is the legacy path → labelled by design.

## Design / r3 checks (web)

Sibling + last child after `</RootErrorBoundary>` (boundary returns children bare, RootErrorBoundary.ts:67) ✓ · canvas supplies relative/translateZ(0)/overflow:hidden ✓ · origin (8,6) in the frame (probe) ✓ · truncation vs `CANVAS_WIDTH_PX` = `?width=` = puppeteer clip width (capture-url.mjs:115, capture-screenshots.mjs:681/699) ✓ · FixtureCanvas own predicate, no WPT term, `FIXTURE_FRAME_WIDTH_PX=390` emits `width:390px` byte-identically (named-constant deviation, pinned) ✓ · text = plain name `_`→space ✓ · case-insensitivity pinned (BlockFontLabel.test.ts:80) ✓ · one URL per vitest file, `getAttribute('width')` ✓ · predicate meaning identical ×3: slot-composed children (pseudos are never children, IRDecode.ts:203/260), `""` = no text; Android reads `_text`, which IRDocumentDecoder.kt:359 fills from v2 `text` ✓ · slot span geometry kept, pinned by regex ✓ · LabelChrome.tsx 149 lines, every code line commented ✓ · **colour ✗**.

## Defects

- **MUST-FIX** `runtimes/web/src/renderer/BlockFontLabel.ts:61` — `rgba(237, 237, 237, 0.70196)` renders (173,173,179), identical to 0.7 (repro 5); natives (174,174,180). The comment's "round(0.70196×255)=179 reaches the native byte" is wrong in effect: Chromium's CPU-raster blend renders alpha byte 179 one LSB darker than Skia-on-Android/CoreGraphics. Docs §5 ("web included … (174,174,180) on all three"), Android BlockLabel.kt:57-65 and iOS BlockLabel.swift:153 all assert a parity that does not exist, and tripwire clause (ii) goes red ×111 post-refresh. Fix: alpha byte 180 on web — measured window 0.704…0.706; `0.706` (180.03) is robust to floor and round. Follow through: LabelChrome.test.tsx:120 string pin, the BlockFontLabel.ts comment, docs §5, the two native comments.
- **SHOULD-FIX** `apps/web-harness/tests/ui/LabelChrome.test.tsx:120` — the only colour pin is string equality; it passed while the byte never moved. Land the raster probe as a test (puppeteer is already a dependency) or state that the post-refresh tripwire is the only colour gate.
- **SHOULD-FIX** `runtimes/web/src/renderer/RendererOptions.ts:92-98` — `renderEmptyContent` doc still says the harness draws a "name label"; stale (lane's own open question, confirmed).
- **NIT** `LabelChrome.tsx:100-103` console.warn fires per render, not once (natives log once). · `tools/visual/gen-control-fixture.mjs:95` names `isBlockLabel` (renamed `isLabelSlot`). · `runtimes/web/tests/renderer/BlockFontLabel.test.ts` pins no fill. · `LabelChrome.test.tsx` 219 lines (> 200 target).

## Open questions answered

- web Q1 (RendererOptions doc): stale at :92-98 — fix in this PR. web Q2 (console.warn): acceptable twin of Log.w, but per-render; a `data-label-chrome-dropped` canvas attribute would be tripwire-greppable — nit.
- iOS Q ("web moves to α 179/255"): false in effect today; after the fix web is alpha byte 180, so no platform comment may say "179/255 everywhere" — say "the composited byte (174,174,180)".
- tooling Q (clause iii ±1): compatible with the fix — byte 180 vs 179 differs ≤ 1/channel over any backdrop.
- Android Q1 (frame width == PNG width): web twin holds by construction (one `captureWidth` feeds CSS width and clip).
- Tree note: final `git status` showed `M apps/android-harness/…/ScreenshotCaptureScreen.kt` — not mine (my four files sha-verified byte-exact); a concurrent Android skeptic's in-flight mutation.

## Re-verify

Re-verified 2026-09-22 on the fix pass (working tree over b2ed4454: 5 M + 1 new under `runtimes/web` +
`apps/web-harness`; CaptureGallery / FixtureCanvas / ComponentRenderer untouched). Working in the session
scratchpad `wave51/prA/skeptic-web.md` (probe script, mutation script, outputs).

**Verdict: the MUST-FIX is CLOSED with executed evidence, no regression found — gate-ready on the web half;
five out-of-lane files still assert the old parity-by-spelling and must be reworded in the same PR (should-fix).**

Executed:
1. `LabelChrome.raster.test.tsx` as-is → 4/4 green (3.4 s, Chrome for Testing 151, puppeteer 25.4.0). Full harness
   suite → 35 files / 301 green; the 7 named files → 67/67; runtime `BlockFontLabel.test.ts` → 13/13; both typechecks exit 0.
2. CI path executed, not assumed: `PUPPETEER_CACHE_DIR=<empty>` → 4 skipped, exit 0 (`executablePath()` returns a
   non-existent path, never throws); `+LABEL_CHROME_RASTER_REQUIRED=1` → FAIL, exit 1.
3. Independent Chromium probe (replica canvas, capture flags): 0.7 / 0.70196 / 0.7039 → (173,173,179);
   0.704 … 0.7078 → (174,174,180); 0.7079 / 0.71 → (175,175,181). Chromium rounds alpha to 8 bits at the .5 boundaries,
   so the byte-180 window is [0.704, 0.7078] and `0.706` (180.03) sits mid-window, ±0.002 either side. The bytes match
   a premultiplied-source-truncated model exactly (237·179/255→166 + 26·76/255→7 = 173; at byte 180, 167+7 = 174).
4. Mutations on `BlockFontLabel.ts` (sha256 `4ecf6a30…45a32` before/after each, = the fix report's hash):
   M1 `0.70196` → raster 3 RED ((8,6) = [173,173,179]; 117 / 1116 band mismatches, every P pixel one LSB dark, none
   elsewhere) + both string pins RED; M2 `0.7` → identical failure set; extra `0.71` → [175,175,181] RED. The colour
   pin is no longer vacuous: it reads the real gallery markup's PNG bytes and is red on both old spellings.
5. Greps: no live `0.70196` / `179/255` left in web paths; `BLOCK_LABEL_FILL` consumers = LabelChrome.tsx:126 + two
   string pins; RendererOptions.ts:92-102 doc corrected (previous SHOULD-FIX closed); tripwire's synthetic model at
   alpha 179 → (174,174,180), and it compares PNG bytes ×3, so web's byte-180 raster satisfies clause (ii).

Residual defects:
- **SHOULD-FIX (out of the web lane's paths, comment/doc only, no byte effect)** — still say web moved to 179/255:
  `docs/DYNAMIC_CAPTURE.md:266-269` (normative §5: "alpha 0.7 rounded HALF-UP to 179 on every platform, web
  included"), `design-record.md:58-61` and `:140`, Android `BlockLabel.kt:57-65`, iOS `BlockLabel.swift:147-153`,
  `label-chrome-tripwire.test.mjs:14-15`. Reword to: contract = the composited byte (174,174,180); natives paint it
  at alpha 179/255, web at alpha byte 180 (`0.706`) because Chromium composites byte 179 to (173,173,179).
- **NIT** `LabelChrome.raster.test.tsx`: `headless: 'new'` is not in puppeteer 25's type (`boolean | 'shell'`) and
  `pngjs` has no typings — invisible to CI (harness tsconfig include = `src`), runtime identical (`'new'` →
  `--headless=new`, ChromeLauncher.js:203); 224 lines (> 200 target); the `shell()` comment calls the border-box reset
  load-bearing, but `canvasStyle` already carries `boxSizing: 'border-box'` inline.
- Unchanged by design: committed web baselines still carry (173,173,179) until the §5 `UPDATE_BASELINE` refresh
  (device run, out of scope here); tripwire clause (ii) goes green only then.

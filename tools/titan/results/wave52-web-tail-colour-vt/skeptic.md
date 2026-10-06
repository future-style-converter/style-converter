# L1 · web-tail-colour-vt — skeptic record (wave 52, 2026-10-05)

Tree: `campaign/wave52` @ HEAD `7d9c22a7`, shared with other lanes' uncommitted work. Scope: PLAN.md §2 "### L1", §3, §5, §9;
`_note.md` (ends `STATUS: COMPLETE`) and every file in this directory. No device, emulator, simulator, test-all.sh or feed was run.
The headless-Chromium bake drive (not a device) was run read-only: no fixture pair was written (`fixtures/wpt/css-view-transitions/*.json`
mtimes unchanged, 2026-09-25 16:56). Every mutation was restored byte-exact from a scratch copy, and `shasum -a 256 -c` passed on all six lane files at the end:
ColorConversion.kt `82856d07…f833a`, ColorParser.kt `45570db2…e9af`, view-transition-bake.mjs `5bde8f73…ca88c`, ColorConversionTest.kt `df1d96b7…9fcb`,
ColorParserLinearSpaceTest.kt `704fed0c…f8a0`, view-transition-bake.test.mjs `e8d5960e…c9e0`. Those match the lane's mutation-record hashes.

## Verdict

**The code is sound. Both fixes do what the note says, and every predicted HIGH-tier flip reproduced in my own replay.**
**One must-fix remains, and it is in the record only:** the F-B blast-radius census claims 9 stamps and 4 "movers down". When I drove all 18
solve-class bails through the real code, exactly **4** tests stamped (the four targets). The other 14 read a null ring, the four claimed
movers among them, on two separate runs. There are also two should-fix items: stale "bail leaves the fixture byte-identical" contracts, and an
unsupported hand-off to L12. No code change is required for the must-fix. `_note.md` and `watchlist-additions.txt` need correcting before the watchlist merge.

## Executed repros

1. **Pins re-run.** `./gradlew :converter:test --tests app.irmodels.ColorConversionTest --tests …ColorParserLinearSpaceTest --rerun-tasks` passed
   29 + 9 with 0 failures (XML timestamps 2026-10-05T15:21:59Z). `node --test tools/titan/view-transition-bake.test.mjs` passed 140 of 140.
   The neighbouring colour and gradient classes were also run and all passed with 0 failures: `*Color*`, `GradientPrefixGuardSoundnessTest` (9) and
   `BackgroundImagePropertyParserTest` (44). These two name `display-p3-linear` only as an interpolation space.
2. **Mutations, each executed and then restored with the sha verified:**
   - (new) `displayP3LinearToSrgb` given `REC2020_TO_XYZ` in place of `P3_TO_XYZ`: 3 fail
     (`display-p3-linear green is CSS green` g 0.51847, `-001 green` and `-006 moss green` r 0.42904).
     The matrix is therefore pinned, not just the white point.
   - (the lane's B, re-executed) deleting the `"display-p3-linear" ->` arm: 6 fail (-001…-006, `srgb` null). This matches the lane's record.
   - (new V1) deleting `if (!isSolveClassBail(bail)) return 0;`: 1 fail (`a NON-solve bail writes nothing`).
   - (the lane's M1, re-executed as V2) deleting the `walk?.active !== true` gate: 1 fail (`an inactive walk writes nothing`), 139 pass. This matches.
   - (new V3) dropping `&& dirtyProbes.length === 0` in the drive: 1 fail (the wiring pin, a source regex).
   - (new V4) `isSolveClassBail` also accepting `^no-active`: 2 fail.
3. **End-to-end convert.** The six `fixtures/wpt/css-color/display-p3-linear-00N.json` were run through `:converter:run --to ir`, and every
   `display-p3-linear` BackgroundColor now carries `srgb` equal to the ref PNG's own pixel: (0,128,0), (0,0,0), (255,255,255), (0,255,0),
   (255,255,0) and (114,137,73). Side observation, pre-existing and not L1's: the same run converts `rgb(100% 100% 100%)` to 254 and
   `rgb(44.8436% 53.537% 28.8112%)` to (114,136,73). That is one step below the ref, and harmless here.
4. **My own census** (scratch `sk-census.mjs`; it does not import the lane's census or the module under change). It covers 1435 per-test IR docs.
   - **F-A.** `color()` spaces found: `display-p3-linear` 6 declarations in 6 tests, 0 carrying `srgb`; `display-p3` 6/6; `a98-rgb` 4/4; `srgb` 43 (40 with `srgb`);
     `xyz-d65` 3/3; `--foo` 1 (null). `a98-rgb-linear`, `rec2020-linear` and `prophoto-*` occur in 0 docs.
   - **F-A raw text.** A raw-text scan finds one more doc, `css-images/gradient/display-p3-linear-gradient`. It holds only the `interp: "in display-p3-linear"`
     string, never the `color()` branch, and the only callers of the three new functions are the three new arms. **The F-A blast radius is 6 tests, 18 cells.
     This matches the lane.** The WPT sources agree: 7 files, the same 6 plus the gradient.
   - **F-B.** There are 48 sources and **29 backdrop authors**. The bake statuses are 16 baked, 29 bailed, 2 declined and 1 none. There are **18 solve-class bails**,
     9 with an authored backdrop and 9 without. Both counts match the lane's census.json.
5. **The real drive, my own `sk-probe.mjs`.** It calls `extractFixture` and `viewTransitionBakeFixture` and never calls `writeFixturePair`. I ran it on **all 18**
   solve-class bails, then a second time on the 5 contested tests.
   - **4 stamp:** the `fractional-box-with-{shadow,overflow-children}-{new,old}` tests. Each gets ring `rgb(255, 182, 193)` and the minted
     `<stem>__body {display:none, background-color: lightpink}`, and nothing else changes.
   - **14 do not stamp (ring null):** all 9 no-backdrop bails, plus `column-span…`, `capture-with-visibility-mixed-descendants`,
     `class-specificity`, `clip-path-larger-than-border-box-on-child-of-named-element` and `far-away-capture`. The second run gave the same result for those 5.
6. **PNG check.** I compared the wave51-fix web, iOS and Android captures with the frozen refs for every predicted-flip cell and the three at-risk -004/-005/-006 cells.
   I computed the diff region at threshold > 8 per channel, wrote half-scale montages and looked at them.
   - **F-A.** The diff is exactly the `.test` box: 192×192 at (16,88) for -001/-002, 192×96 at (16,184) for -003/-004/-005, and 192×64 at (16,88) for -006.
     The ref shows the colour the new arm emits; the capture shows white, or grey body for -003/-005. On the natives, only the paragraph-text antialiasing is left as a residual.
   - **F-B.** The diff is 100 % canvas: 219 115 / 222 874 px where the ref is (255,182,193) and the capture is white. The box ink already matches;
     the residual is iOS 144 px and Android 236+51 px of dark-green shadow edge.
7. **My own replay** (`sk-replay.mjs`). For F-A, the box is located on the REF and painted with the colour the real converter emitted. For F-B, white pixels become lightpink.
   Scoring uses the gate's own `diffWebVsRef` with the composed-path verdict composition (`inject-wpt-block.mjs:2016-2040`).
   Every "before" reproduces the manifest exactly, and every "after" reproduces the lane's numbers:
   - -001: P 1 / 0.999 / 0.9982
   - -002: P 1 / 0.9989 / 0.9981
   - -003: P 1 / 0.999 / 0.9984
   - -004/-005/-006: P → P ≥ 0.9973, with no colour veto on -004
   - shadow-{new,old}: P 1 / 0.998 / 0.9966
   - overflow-children-{new,old}: P 1 / 1 / 0.9996

   As a cross-check, the sibling `display-p3-001..006` tests already pass at these exact SSIMs (P 1 / 0.999 / 0.9982 …), which supports the native F-A prediction.
   A lightpink body-root minted by the wave-41 bake already passes on all three platforms (`css-tags-paint-order(-with-entry)` P 0.9791 ×3,
   `css-tags-shared-element` P 1 ×3), which supports the native F-B prediction.
8. **Ownership.** All files with L1 changes are in the "own:" list. ColorParserLinearSpaceTest.kt is new, under `primitiveParsers/` (see nit 5). No other lane's file carries an
   L1 hunk; the `L12-B` grep hits are L12's. There are no seam patches (PLAN "Seams: none", and none in this directory), so `git apply --check` has nothing to check.
9. **Watchlist.** `WATCH=<plan watchlist + watchlist-additions.txt> node …/watchlist-check.mjs` prints `unmatched 0`.
10. **Rules.** Code carve-out grep (fractional / column-span / far-away / `p3-linear-0` / lightpink outside comments) returns 0 hits. Ring-fence held:
    no linear space appears in `filter-effects/backdrop-filter-basic-blur`, and F-B touches only css-view-transitions solve-class bails. No device A/B is staged, so no hash sentence is owed.
    New files are within the line limit: 154, 152, 148 and 58 lines.

## Defects

1. **must-fix (record only): the F-B blast radius was claimed from an unmeasured predicate.**
   - **What the lane claimed.** `census.mjs` predicts a stamp as `!!backdrop && solveBail`, but the code stamps on the *measured* ring.
     On that basis `_note.md` claims "**9 stamps predicted**", "5 with a WHITE frozen-ref ring … the four already-failing movers-down",
     that "`far-away-capture` … will now carry a pink canvas", and "1 [body-root] in capture-with-visibility-mixed-descendants (override)".
   - **What `watchlist-additions.txt` claims.** It lists `capture-with-visibility-mixed-descendants`, `class-specificity`, `clip-path-larger-than-border-box-on-child-of-named-element`
     and `far-away-capture` as MOVERS DOWN that "move TOWARD the ref once L12-B re-freezes".
   - **What I measured.** On two real drives all four read ring null, so nothing was stamped and nothing will move. The lane drove only 6 of the 18 solve-class bails;
     the other 8 no-backdrop bails were assumed to be zero by authorship. They are zero, but only now is that measured.
   - **The measured F-B blast radius is 4 tests / 12 cells**, all toward the ref.
   - **Fix:** rewrite the note's Census / Predicted / Hand-offs lines with these numbers, and relabel the 4 lines as negative controls
     ("must NOT move under F-B"). Code is unchanged.
2. **should-fix: stale contracts.** These comments still say a bail leaves the fixture byte-identical, which is now false for a stamped solve-class bail:
   - `view-transition-bake.mjs:112-114`, the module banner: "Every one of these is a loud bail that leaves the fixture byte-identical".
   - `:2125-2126`, the `viewTransitionBakeFixture` JSDoc: "'bailed' … the fixture is left byte-identical".
   - `extract-fixture.mjs:11645`: "Skips/declines/bails leave the fixture byte-identical". This is a seam file, so L1 owes a one-line hand-off,
     and its note says "Hunks for other owners: none".
3. **should-fix: the column-span hand-off to L12 is unsupported.** "Expect P → f ×3 there from the re-freeze alone" assumes L12-B re-freezes
   `column-span-during-transition-doesnt-skip`. PLAN §2 L12 limits the re-freeze to "B10's 8 rows … the 3 all-green refs; Fix B's 10 pairs" and says to
   "sha1 every OTHER frozen ref". L12's `body-margin-census.wave51-fix.json` has 0 css-view-transitions rows, and the L12 note (STATUS: PARTIAL) does not mention it.
   The likely outcome is no change from either lane. The hand-off should say "0 unless L12-B adds it", so the orchestrator does not pre-register a −3 that will not happen.
4. **nit:** `ColorConversionTest.kt:140` says the gamma-decoding sibling "would give ~(0, 0.27, 0)". The mutation record above it measured g = 0.2129.
5. **nit:** the plan's own-line says "a new ColorParser linear-space test beside [ColorConversionTest.kt]", but the file sits at
   `converter/src/test/kotlin/app/parsing/css/properties/primitiveParsers/ColorParserLinearSpaceTest.kt`. That is the package-correct location and no other lane owns it;
   record the exact path in the ownership list.
6. **nit:** a body-root minted on the bail path is tagged `_lossyReasons: ['baked-view-transition-tree']` although nothing was baked.
   It is only component-level, since the bail path writes no `_wpt.lossyReasons`, so it does not reach inject's `lossyReasons`.
7. **nit:** when a solve-class bail samples the ring and gets null, the reason string records nothing about the sample. extract.log cannot tell
   "sampled, white or non-uniform" from "dirty, not sampled". A ` (frame-ring null)` suffix would close that.

## Predicted flips after this audit (against wave51-fix)

- +3 web +6 native for `css-color/display-p3-linear-001/-002/-003` (HIGH).
- +4 web +8 native for `css-view-transitions/fractional-box-with-{shadow,overflow-children}-{new,old}` (web HIGH, natives MED-HIGH).
- Movers up, P → P: -004/-005/-006 ×3.
- **No other cell moves because of L1.** That includes column-span ×3 and the 4 claimed movers down; all 14 non-stamping solve-class bails were measured.

## Appendix — the skeptic scripts (verbatim; run from the repo root, they lived in the session scratchpad)

### sk-census.mjs

```js
// Skeptic's OWN census for L1 (does not import the lane's census or the module under change).
import fs from 'node:fs';
import path from 'node:path';
const REPO = process.cwd();
const RUN = path.join(REPO, 'tools/titan/runs/wave51-fix/sections');
const WPT = path.join(REPO, 'tools/wpt');
// ---- F-A: raw-text + structural scan of every per-test IR doc ----
let docs = 0; const spaceCount = {}; const linearHitsText = new Map(); const srgbBy = {};
function visit(n, test) {
  if (Array.isArray(n)) { n.forEach((x) => visit(x, test)); return; }
  if (!n || typeof n !== 'object') return;
  // an `original` with a colorSpace is the ColorFunction representation; its holder is `n`
  if (n.original && typeof n.original === 'object' && typeof n.original.colorSpace === 'string') {
    const cs = n.original.colorSpace; const e = (spaceCount[cs] ??= { decls: 0, srgb: 0, tests: new Set() });
    e.decls++; e.tests.add(test); if (n.srgb) e.srgb++;
  }
  for (const v of Object.values(n)) visit(v, test);
}
for (const sec of fs.readdirSync(RUN)) {
  const d = path.join(RUN, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json'))) {
    docs++; const txt = fs.readFileSync(path.join(d, f), 'utf8');
    const m = txt.match(/(display-p3|a98-rgb|rec2020|prophoto-rgb)-linear/g);
    if (m) linearHitsText.set(f, m.length);
    visit(JSON.parse(txt), f);
  }
}
console.log('IR docs', docs);
for (const [k, v] of Object.entries(spaceCount).sort()) console.log(' colorSpace', k.padEnd(18), 'decls', v.decls, 'withSrgb', v.srgb, 'tests', v.tests.size);
console.log('raw-text linear-space hits (any context):', [...linearHitsText.entries()]);
// ---- F-A: WPT source scan over every test in every tests.list (test file only) ----
const srcHits = [];
for (const sec of fs.readdirSync(RUN)) {
  const tl = path.join(RUN, sec, 'tests.list'); if (!fs.existsSync(tl)) continue;
  for (const t of fs.readFileSync(tl, 'utf8').split('\n').map((s) => s.trim()).filter(Boolean)) {
    const rel = t.startsWith('css/') ? t : `css/${t}`; const p = path.join(WPT, rel);
    if (!fs.existsSync(p)) continue;
    const s = fs.readFileSync(p, 'utf8');
    const m = s.match(/(display-p3|a98-rgb|rec2020|prophoto-rgb)-linear/g);
    if (m) srcHits.push(`${rel} ×${m.length}`);
  }
}
console.log('WPT test sources naming a linear space:', srcHits.length, srcHits);
// ---- F-B: the 48 VT tests: backdrop author? bake note? my own solve-class rule ----
const vtDir = path.join(RUN, 'css-view-transitions');
const tests = fs.readFileSync(path.join(vtDir, 'tests.list'), 'utf8').split('\n').map((s) => s.trim()).filter(Boolean);
const log = fs.readFileSync(path.join(vtDir, 'extract.log'), 'utf8').split('\n');
const man = JSON.parse(fs.readFileSync(path.join(vtDir, 'manifest.json'), 'utf8')).wpt.results;
const rows = [];
for (const t of tests) {
  const rel = t.startsWith('css/') ? t : `css/${t}`;
  const src = fs.readFileSync(path.join(WPT, rel), 'utf8');
  // backdrop: any rule whose selector list contains a bare ::view-transition (not -group/-old/-new/-image-pair)
  let backdrop = null;
  for (const m of src.matchAll(/([^{}]*)\{([^{}]*)\}/g)) {
    const sel = m[1]; if (!/::view-transition(?![-\w])/.test(sel)) continue;
    const b = /background(?:-color)?\s*:\s*([^;]+)/i.exec(m[2]); if (b) backdrop = b[1].trim();
  }
  const line = log.find((l) => l.includes(`extracted ${rel} `)) ?? '';
  const note = /\[vt-bake: ([a-z]+)(?: — (.*?))?\](?: \[|$)/.exec(line);
  const status = note?.[1] ?? 'none'; const reason = note?.[2] ?? '';
  // my own rule, written from planViewTransitionBake's emit sites (:1496-1563)
  const solve = status === 'bailed' && /^(snapshot solve failed for |non-uniform-snapshot |missing snapshot solve for )/.test(reason);
  const cells = Object.fromEntries(Object.entries(man[rel]?.browserRef?.diffs ?? {}).map(([k, d]) => [k.replace('-ref', ''), `${d.scoreExcluded ? 'X' : d.wptPass ? 'P' : 'f'} ${d.ssim}`]));
  rows.push({ rel, backdrop, status, reason, solve, cells });
}
const authors = rows.filter((r) => r.backdrop);
const solves = rows.filter((r) => r.solve);
console.log(`VT sources ${rows.length} · backdrop authors ${authors.length} · bake statuses`,
  rows.reduce((a, r) => (a[r.status] = (a[r.status] ?? 0) + 1, a), {}), `· solve-class bails ${solves.length}`);
console.log('solve-class bails (authored backdrop?):');
for (const r of solves) console.log(`  ${r.backdrop ? 'BD ' + r.backdrop.padEnd(12) : 'no-backdrop    '} ${r.rel.replace('css/css-view-transitions/', '')} | ${r.reason.slice(0, 90)} | ${JSON.stringify(r.cells)}`);
fs.writeFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), 'sk-census.json'), JSON.stringify({ docs, rows }, null, 1));
```

### sk-probe.mjs

```js
// Skeptic's own drive probe: run the REAL bake drive (headless Chromium) on every
// solve-class bail of wave51-fix css-view-transitions and record whether the new
// bail-path stamp fires. Writes no fixture (writeFixturePair never called).
import fs from 'node:fs';
import path from 'node:path';
const REPO = process.cwd();
const { extractFixture } = await import(path.join(REPO, 'tools/titan/extract-fixture.mjs'));
const vt = await import(path.join(REPO, 'tools/titan/view-transition-bake.mjs'));
const rows = JSON.parse(fs.readFileSync(new URL('./sk-census.json', import.meta.url), 'utf8')).rows;
const only = process.argv.slice(2);
const targets = rows.filter((r) => r.solve || only.includes(r.rel)).map((r) => r.rel)
  .filter((r) => !only.length || only.includes(r));
const out = [];
try {
  for (const rel of targets) {
    const res = await extractFixture(rel);
    const before = JSON.parse(JSON.stringify(res.fixture.components ?? {}));
    const t0 = Date.now();
    let outcome;
    try { outcome = await vt.viewTransitionBakeFixture(res.fixture, rel); } catch (e) { outcome = { status: 'THREW', reason: String(e.message ?? e) }; }
    const after = res.fixture.components ?? {};
    const changed = Object.keys(after).filter((k) => JSON.stringify(after[k]) !== JSON.stringify(before[k]));
    const rec = { rel, ms: Date.now() - t0, status: outcome.status, reason: outcome.reason, frameRing: outcome.frameRing ?? null,
      changed: changed.map((k) => ({ id: k, props: after[k]?.properties })) };
    out.push(rec);
    console.log(`${rel.replace('css/css-view-transitions/', '')}: ${outcome.status} ring=${rec.frameRing} changed=${JSON.stringify(rec.changed)}\n    ${String(outcome.reason).slice(0, 160)}`);
  }
} finally { await vt.closeViewTransitionBakeBrowser(); }
fs.writeFileSync(new URL('./sk-probe.json', import.meta.url), JSON.stringify(out, null, 1));
```

### sk-replay.mjs

```js
// Skeptic's own replay. F-A: paint, in each platform's OWN capture, the region
// where the REF shows the test box (located on the ref, not on a capture) with
// the sRGB the REAL converter emitted (sk end-to-end convert, 8-bit). F-B: every
// capture pixel that is pure white AND sits where the ref is lightpink becomes
// lightpink (the stamp moves only the canvas). Scored with the gate's own
// diffWebVsRef + computeWptPass composition (composed path, inject-wpt-block :2016-2040).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const REPO = process.cwd();
const require = createRequire(path.join(REPO, 'package.json'));
const { PNG } = require('pngjs');
const ij = await import(path.join(REPO, 'tools/titan/inject-wpt-block.mjs'));
const { computeNovelInkFailed } = await import(path.join(REPO, 'tools/titan/novel-ink.mjs'));
const { degenerateVetoFailed } = await import(path.join(REPO, 'tools/titan/degenerate-veto-probe.mjs'));
const SP = path.dirname(new URL(import.meta.url).pathname);
const RUN = 'tools/titan/runs/wave51-fix/sections';
const REF = 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins';
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
// converter output (sk end-to-end convert of the six fixtures), test box colour
const FA = { '001': [0, 128, 0], '002': [0, 0, 0], '003': [255, 255, 255] };
async function verdict(cap, ref, fuzzy) {
  const d = await ij.diffWebVsRef(cap, ref);
  const fm = ij.checkFuzzyMatch(d, fuzzy);
  const pres = ij.computePresenceFailed(d.semanticPresence);
  const col = ij.computeColorFailed(d.colorDivergent, d.labDeltaE);
  const cov = ij.computeCoverageRatioFailed(d.semanticPresence);
  const ni = computeNovelInkFailed(d.novelInk); const dg = degenerateVetoFailed(d.degenerate);
  const pass = ij.computeWptPass(d.ssim, fm, pres, col, cov, ij.novelInkVetoActive(ni), ij.degenerateVetoActive(dg));
  return `${pass ? 'P' : 'f'} ${d.ssim}${col ? ' C' : ''}${pres ? ' Pr' : ''}${cov ? ' R' : ''}`;
}
const man = (sec) => JSON.parse(fs.readFileSync(path.join(RUN, sec, 'manifest.json'), 'utf8')).wpt.results;
async function run(sec, t, mutate) {
  const refP = path.join(REF, sec, `${t}.png`); const ref = PNG.sync.read(fs.readFileSync(refP));
  const m = man(sec)[`css/${sec}/${t}.html`]; const fuzzy = m?.fuzzy ?? null;
  const row = [];
  for (const [p, dir] of Object.entries(DIRS)) {
    const capP = path.join(RUN, sec, dir, `wpt__${sec}__${t}.png`);
    const before = await verdict(capP, refP, fuzzy);
    const img = PNG.sync.read(fs.readFileSync(capP));
    const n = mutate(img, ref);
    const outP = path.join(SP, `skr__${t}__${p}.png`); fs.writeFileSync(outP, PNG.sync.write(img));
    const after = await verdict(outP, refP, fuzzy);
    const man0 = m.browserRef.diffs[`${p}-ref`];
    row.push(`${p}: manifest ${man0.wptPass ? 'P' : 'f'} ${man0.ssim} | sk-before ${before} → sk-after ${after} (${n}px repainted)`);
  }
  console.log(`== ${sec}/${t}\n  ` + row.join('\n  '));
}
for (const [n, rgb] of Object.entries(FA)) {
  const t = `display-p3-linear-${n}`;
  await run('css-color', t, (img, ref) => {
    // the test box = the ref's LAST 192-wide box of that colour run below the text: rows 88..279 / 184..279
    const y0 = n === '003' ? 184 : 88, y1 = 279; let k = 0;
    for (let y = y0; y <= y1; y++) for (let x = 16; x <= 207; x++) {
      const i = (y * img.width + x) * 4; img.data[i] = rgb[0]; img.data[i + 1] = rgb[1]; img.data[i + 2] = rgb[2]; img.data[i + 3] = 255; k++;
    }
    return k;
  });
}
for (const t of ['fractional-box-with-shadow-new', 'fractional-box-with-shadow-old', 'fractional-box-with-overflow-children-new', 'fractional-box-with-overflow-children-old']) {
  await run('css-view-transitions', t, (img, ref) => {
    let k = 0;
    for (let i = 0; i < img.data.length; i += 4) {
      if (img.data[i] === 255 && img.data[i + 1] === 255 && img.data[i + 2] === 255) {
        img.data[i] = 255; img.data[i + 1] = 182; img.data[i + 2] = 193; k++;
      }
    }
    return k;
  });
}
```

## Re-verify (2026-10-05, after the lane's fix pass)

The tree was `campaign/wave52` @ `7d9c22a7`, shared with other lanes. I ran no device, emulator, simulator, test-all.sh or feed.
I did run the headless-Chromium bake drive (not a device), read-only. It wrote no fixture pair: the
`fixtures/wpt/css-view-transitions/*.json` mtimes are still 2026-09-25 16:56. Every mutation was restored byte-exact from a scratch copy taken
before the first edit. At the end all six lane files equal the lane's stated final shas:
ColorConversion.kt `82856d07…f833a`, ColorParser.kt `45570db2…e9af`, ColorConversionTest.kt `3b6a5b79…0103bc`,
ColorParserLinearSpaceTest.kt `704fed0c…f8a0`, view-transition-bake.mjs `acf8f897…0897c9` and view-transition-bake.test.mjs `d75c13c0…3f0d791`.
`extract-fixture.mjs` is `eb9742dc…25f60`, which equals HEAD. The seam lock dir was created, used and removed. My scripts are in the appendix below.

### Verdict

**Both must-fix defects are closed. I found no regression and no new must-fix.** The note's Census, Real drive, Predicted and Hand-offs sections now
state the measured F-B radius: 4 tests / 12 cells. `watchlist-additions.txt` labels the four former "movers down" as negative controls. My own census and
two fresh real-drive runs, made on the fix-pass code, reproduce every number the lane now states.

### Executed checks

1. **Must-fix 1 (blast radius from an unmeasured predicate): CLOSED.**
   - **My census.** `rv-census.mjs` is new. It does not import the lane's census or `view-transition-bake.mjs`, and it uses my own solve-class regexes.
     - It covers 1435 IR docs and 48 VT sources, of which 29 author a backdrop.
     - Statuses: baked 16, bailed 29, declined 2, none 1.
     - It finds **18 solve-class bails**: 9 with a backdrop and 9 without. These equal the note's numbers.
   - **The drive.** `rv-probe.mjs` drove all 18 through `extractFixture` and `viewTransitionBakeFixture` on the fix-pass code, **twice**.
     - Both runs: **18 driven, 17 sampled, 4 stamped** (`fractional-box-with-{shadow,overflow-children}-{new,old}`). Each stamp is ring `rgb(255, 182, 193)` and
       the only changed component is the minted `<stem>__body {display:none, background-color: rgb(255, 182, 193)}`.
     - Non-stamps: 0 mutated the fixture. Stamps: 0 touched a non-body-root.
     - Between my two runs, 0 of 18 verdicts differed.
     - `break-inside-avoid-child` was not sampled: a dirty crop probe fired on the drive and on its one re-drive, so there is no suffix and no mutation.
       The other 13 non-stamps end `(frame-ring null not stamped)`. That includes the four former "movers down" and `column-span…`
       (`isolation window 500x200 exceeds the 358x568 viewport`).
   - **Against the lane's probe files.** I compared both of my runs with the lane's `bake-probe.json` and `bake-probe.run1.json` per test, on sampled, stamped,
     ring and mutated: **0 disagreements** across all 4 run pairings.
   - **The note's text.** A grep of `_note.md` for "9 stamps", "movers down", "pink canvas", "override", "TOWARD the ref once" and "−3" finds them only as
     history (the fix-pass table) or as explicit withdrawals ("No movers down", "Do NOT pre-register a −3").
   - **The census script.** `census.mjs` now reads the stamp verdict from `bake-probe.json`, and any test the probe did not drive reads `unmeasured`.
     `census.log` prints `STAMPS MEASURED 4 (12 cells) · unmeasured 0`.
2. **Must-fix 2 (four lines labelled MOVERS DOWN): CLOSED.**
   - The four lines are now under a "NEGATIVE CONTROLS … must NOT move under F-B" header. `MOVERS DOWN` survives only inside the fix-pass history comment.
     A fifth negative control was added, `element-with-overflow`, which is a measured no-backdrop non-stamp.
   - I ran `WATCH=<plan watchlist.txt + watchlist-additions.txt> node tools/titan/results/wave52-plan/watchlist-check.mjs`. It printed `unmatched 0`, and each of the 5 L1 lines
     matched exactly 3 cells (1 test). Run on the additions file alone, it printed `unmatched 0` with 15 distinct cells.
3. **Pins re-run.**
   - `./gradlew :converter:test --tests app.irmodels.ColorConversionTest --tests …ColorParserLinearSpaceTest --rerun-tasks` gave 29 + 9 with 0 failures
     (XML 2026-10-05T16:13:38Z).
   - After all restores I ran it again together with the neighbouring `primitiveParsers.*Color*` classes:
     ColorConversion 29/0, LinearSpace 9/0, HslHue 14/0, LchPercent 6/0 and SyntaxClassifier 5/0.
   - `node --test tools/titan/view-transition-bake.test.mjs` passed 140 / 0, before and after the mutations.
4. **Mutations.** Each was executed on the fix-pass bytes, restored, and sha-verified.
   - (lane M3, re-executed) Deleting the `if (ringSampled) { … not stamped … }` return gave 139 / 1, the wiring pin. This matches the lane.
   - (lane V3, re-executed) Dropping `&& dirtyProbes.length === 0` from the `ringSampled` gate gave 139 / 1, the wiring pin. This matches the lane.
   - (new Vc) Deleting `if (!frameRing) return 0;` in `applyFrameRingStamp` gave 136 / 4: the white-ring pin, the step-2c byte-identity pin and two
     wave-40/41 apply pins.
   - (new Ve) Deleting the `^non-uniform-snapshot` arm of `isSolveClassBail` gave 139 / 1, the verbatim-classes pin.
   - (lane Kotlin, re-executed) Making `displayP3LinearToSrgb` gamma-decode its inputs gave ColorConversionTest 29 / 2, which matches the lane, and
     LinearSpaceTest 9 / 2. Restored to `82856d07…`.
5. **The seam hunk (`hunk-for-L5-1.patch`, comment-only, for L5).**
   - On HEAD: `git apply --check -v` is clean.
   - Under the lock (`tools/titan/runs/wave52-lock/extract-fixture.mjs`):
     1. The sha before was `eb9742dc…`. I ran `git apply`, which gave sha `f8aaa115…`.
     2. `node --check` passed and `view-transition-bake.test.mjs` passed 140 / 0.
     3. I restored with `git show HEAD:… >`. The sha is `eb9742dc…` again, `git diff --quiet` is clean, and the lock is released.
   - `extract-fixture.test.mjs` gives 463 / 20 both with and without the hunk. The 20 are all `wave52-L5 …` tests waiting for L5's own seam set, so they are not L1's.
   - Every +/- line of the hunk is a `//` comment: 0 non-comment lines.
   - **Stacked check.** In a scratch copy, L5's six `seam-{1..6}-*.patch` applied in order on HEAD, then L1's hunk applied clean at offset +413 (:12055).
     `node --check` passed, and the §8c print-line pin regex still matches the stacked file.
6. **PNGs.** For each predicted-flip test I built half-scale montages (ref | web | iOS | Android) from wave51-fix and the frozen refs and looked at them.
   - **F-A -001 / -002.** The ref has a 192×192 green or black square at (16,88). Every capture is white there. The web diff bbox is exactly (16,88)-(207,279).
     The natives' bbox also takes in the text line, from y=35.
   - **F-A -003.** The captures show only the top `.ref` half, 192×96. The `.test` half at (16,184) is grey body. The ref centre is (255,255,255).
     The new arm emits white, so painting the `.test` box closes the diff. The natives' `.ref` half reads (254,254,254), which is the pre-existing `rgb(100%)` → 254 rounding noted in §3.
   - **F-B ×4.** The ref is lightpink (255,182,193) edge to edge. Every capture is white in the corner and the centre. The diff is 219 115 px for shadow and
     222 874 px for overflow-children on web; the natives are within 0.13 % of those. The box ink matches across all four images.
   - The stamp the drive actually produced (body-root `background-color: rgb(255, 182, 193)`) is exactly the canvas colour the ref needs. The predicted picture is plausible
     on all three platforms.
   - L2's uncommitted composed-canvas edits change only the body-root MARGIN path. A minted body-root carries no margin, so the background channel F-B uses is untouched.
7. **Ownership.**
   - L1 changes appear only in its "own:" files: ColorParser.kt, ColorConversion.kt, ColorConversionTest.kt, the new `primitiveParsers/ColorParserLinearSpaceTest.kt`,
     view-transition-bake.mjs and view-transition-bake.test.mjs, plus this directory.
   - A marker grep (`wave-52 L1`, `F-A`/`F-B`, `applyBailFrameRingStamp`, `isSolveClassBail`, `display-p3-linear`, `frame-ring … stamped`) across every modified tracked file
     first hit 7 foreign files. Every one of those hits was an `L1x` string (L11 or L12), so there is **no L1 hunk in a foreign file**.
   - `extract-fixture.mjs` is at HEAD, and the `ColorParser.kt` diff is L1's 6 lines only.
8. **Rules.**
   - New files are within the limit: 154 (LinearSpace test), 184 (census.mjs), 106 (bake-probe.mjs) and 148 (png-replay.mjs) lines, all commented.
   - The code carve-out grep over added non-comment lines in the three product files returns 0 hits. It searched for fractional, column-span, far-away, `p3-linear-0`, lightpink,
     `255, 182, 193`, class-specificity and capture-with.
   - Ring-fence: `backdrop-filter-basic-blur` is not named, and no linear space appears in its IR.
   - No silent fallthrough: the prophoto arm stays a documented `else -> null`, and every bail return is named.
   - No device A/B is staged, so no hash sentence is owed.
   - CLI taxonomy: the key is `reason.split(/[\s(]/)[0]` and is unchanged by the suffix. `extract-fixture.mjs` is the only printer of `[vt-bake: …]`, and nothing under
     tools/titan, tools/visual or apps/web-harness/src parses the reason beyond that.

### Observations (none is a must-fix)

- **nit, census, non-target row.** My walk counts `srgb` as 43 declarations, 40 of them carrying `srgb`, in 14 tests, and also finds `lch` with 13 declarations, 0 carrying `srgb`, in 3 tests.
  The note's census row says "srgb 40 decls (12 tests)" and lists no `lch`. Neither row is in F-A's radius, and the `display-p3-linear` row (6/6/0) agrees exactly. This is a labelling difference in a
  context row, not a blast-radius error.
- **nit, hand-off.** The hunk is named `hunk-for-L5-1.patch`, not the §0 `seam-<n>.patch`. L5's note (STATUS: COMPLETE) does not acknowledge receiving it.
  The orchestrator must fold it in or apply it after L5's set, which I verified clean at +413. If it is dropped, a stale comment stays at the seam. That has no render effect.

### Predicted flips after re-verify (against wave51-fix): unchanged from the first audit

- +3 web, +6 native: `display-p3-linear-001/-002/-003` (HIGH).
- +4 web, +8 native: `fractional-box-with-{shadow,overflow-children}-{new,old}` (web HIGH, natives MED-HIGH).
- Movers up, P → P: -004/-005/-006 ×3.
- Negative controls with 0 change: the 4 former "movers down", `element-with-overflow`, and `column-span…` ×3.

### Appendix — re-verify scripts (verbatim; run from the repo root; they lived in the session scratchpad)

#### rv-census.mjs

```js
// Re-verify skeptic census for L1 (does NOT import the lane's census.mjs / census.json,
// nor view-transition-bake.mjs). Run from the repo root.
import fs from 'node:fs';
import path from 'node:path';
const REPO = process.cwd();
const RUN = path.join(REPO, 'tools/titan/runs/wave51-fix/sections');
const WPT = path.join(REPO, 'tools/wpt');
const SP = path.dirname(new URL(import.meta.url).pathname);
// ---- F-A: every per-test IR doc; count ColorFunction holders by colorSpace ----
let docs = 0; const spaces = {};
function walk(n, f) {
  if (Array.isArray(n)) { for (const x of n) walk(x, f); return; }
  if (!n || typeof n !== 'object') return;
  const o = n.original;
  if (o && typeof o === 'object' && typeof o.colorSpace === 'string') {
    const e = (spaces[o.colorSpace] ??= { decls: 0, srgb: 0, tests: new Set() });
    e.decls++; if (n.srgb) e.srgb++; e.tests.add(f);
  }
  for (const v of Object.values(n)) walk(v, f);
}
const rawLinear = [];
for (const sec of fs.readdirSync(RUN)) {
  const d = path.join(RUN, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d)) {
    if (!f.endsWith('.json')) continue; docs++;
    const txt = fs.readFileSync(path.join(d, f), 'utf8');
    if (/(display-p3|a98-rgb|rec2020|prophoto-rgb)-linear/.test(txt)) rawLinear.push(`${sec}/${f}`);
    walk(JSON.parse(txt), `${sec}/${f}`);
  }
}
console.log('IR docs', docs);
for (const k of Object.keys(spaces).sort()) console.log('  colorSpace', k.padEnd(18), 'decls', spaces[k].decls, 'withSrgb', spaces[k].srgb, 'tests', spaces[k].tests.size);
console.log('  raw linear-space docs:', rawLinear);
// ---- F-B: VT sources, backdrop authorship, bake status from extract.log ----
const vt = path.join(RUN, 'css-view-transitions');
const tests = fs.readFileSync(path.join(vt, 'tests.list'), 'utf8').split('\n').map((s) => s.trim()).filter(Boolean)
  .map((t) => (t.startsWith('css/') ? t : `css/${t}`));
const log = fs.readFileSync(path.join(vt, 'extract.log'), 'utf8').split('\n');
const man = JSON.parse(fs.readFileSync(path.join(vt, 'manifest.json'), 'utf8')).wpt.results;
// my own solve-class rule, written from the plan's emit sites (NOT imported)
const SOLVE = [/^snapshot solve failed for /, /^non-uniform-snapshot /, /^missing snapshot solve for /, /isolation window \d+x\d+ exceeds/];
const rows = [];
for (const rel of tests) {
  const src = fs.readFileSync(path.join(WPT, rel), 'utf8');
  let backdrop = null;
  for (const m of src.matchAll(/([^{}]*)\{([^{}]*)\}/g)) {
    if (!/::view-transition(?![-\w])/.test(m[1])) continue;
    const b = /background(?:-color)?\s*:\s*([^;]+)/i.exec(m[2]); if (b) backdrop = b[1].trim();
  }
  const line = log.find((l) => l.includes(`extracted ${rel} `)) ?? '';
  const note = /\[vt-bake: ([a-z]+)(?: — ([^\]]*))?\]/.exec(line);
  const status = note?.[1] ?? 'none'; const reason = note?.[2] ?? '';
  const solve = status === 'bailed' && SOLVE.some((r) => r.test(reason));
  const cells = Object.fromEntries(Object.entries(man[rel]?.browserRef?.diffs ?? {})
    .map(([k, d]) => [k.replace('-ref', ''), `${d.scoreExcluded ? 'X' : d.wptPass ? 'P' : 'f'} ${d.ssim}`]));
  rows.push({ rel, backdrop, status, reason, solve, cells });
}
const st = rows.reduce((a, r) => ((a[r.status] = (a[r.status] ?? 0) + 1), a), {});
const solves = rows.filter((r) => r.solve);
console.log(`VT sources ${rows.length} · backdrop authors ${rows.filter((r) => r.backdrop).length} · statuses ${JSON.stringify(st)}`
  + ` · solve-class bails ${solves.length} (backdrop ${solves.filter((r) => r.backdrop).length}, none ${solves.filter((r) => !r.backdrop).length})`);
for (const r of solves) console.log(`  ${(r.backdrop ?? '-').padEnd(10)} ${r.rel.replace('css/css-view-transitions/', '').padEnd(62)} ${JSON.stringify(r.cells)}`);
fs.writeFileSync(path.join(SP, 'rv-census.json'), JSON.stringify({ docs, rows }, null, 1));
```

#### rv-probe.mjs

```js
// Re-verify skeptic drive probe: run the REAL bake drive (headless Chromium) on every
// solve-class bail from MY census (rv-census.json) and record what the fix-pass bail
// path delivers. Never calls writeFixturePair. Run from the repo root.
import fs from 'node:fs';
import path from 'node:path';
const REPO = process.cwd();
const SP = path.dirname(new URL(import.meta.url).pathname);
const { extractFixture } = await import(path.join(REPO, 'tools/titan/extract-fixture.mjs'));
const vt = await import(path.join(REPO, 'tools/titan/view-transition-bake.mjs'));
const rows = JSON.parse(fs.readFileSync(path.join(SP, 'rv-census.json'), 'utf8')).rows;
const tag = process.argv[2] ?? 'run';
const targets = rows.filter((r) => r.solve).map((r) => r.rel);
const out = [];
try {
  for (const rel of targets) {
    const res = await extractFixture(rel);
    const before = JSON.stringify(res.fixture);
    const beforeC = JSON.parse(JSON.stringify(res.fixture.components ?? {}));
    let o;
    try { o = await vt.viewTransitionBakeFixture(res.fixture, rel); } catch (e) { o = { status: 'THREW', reason: String(e?.message ?? e) }; }
    const after = res.fixture.components ?? {};
    const changed = Object.keys(after).filter((k) => JSON.stringify(after[k]) !== JSON.stringify(beforeC[k]));
    const sfx = /\(frame-ring (.*?) (stamped|not stamped)\)$/.exec(String(o.reason ?? ''));
    const rec = { rel, status: o.status, reason: o.reason, frameRing: o.frameRing ?? null,
      sampled: !!sfx, stamped: sfx?.[2] === 'stamped', mutated: JSON.stringify(res.fixture) !== before,
      changed: changed.map((k) => ({ id: k, role: after[k]?._role, props: after[k]?.properties })) };
    out.push(rec);
    console.log(`${rel.replace('css/css-view-transitions/', '')}: ${o.status} sampled=${rec.sampled} stamped=${rec.stamped} ring=${rec.frameRing} mutated=${rec.mutated} changed=${JSON.stringify(rec.changed)}\n    ${String(o.reason).slice(0, 200)}`);
  }
} finally { await vt.closeViewTransitionBakeBrowser(); }
const st = out.filter((r) => r.stamped);
console.log(`driven ${out.length} · sampled ${out.filter((r) => r.sampled).length} · stamped ${st.length}: ${st.map((r) => r.rel.split('/').pop()).join(', ')}`
  + ` · non-stamp mutated ${out.filter((r) => !r.stamped && r.mutated).length}`
  + ` · stamp touched non-body-root ${st.filter((r) => r.changed.some((c) => c.role !== 'body-root')).length}`);
fs.writeFileSync(path.join(SP, `rv-probe.${tag}.json`), JSON.stringify(out, null, 1));
```

#### mut.py (the four vt-bake mutations)

```python
import sys,re
path,which=sys.argv[1],sys.argv[2]
s=open(path).read()
if which=='M3':
    # delete the whole `if (ringSampled) { ... }` not-stamped return block
    a=s.index('      if (ringSampled) {\n'); b=s.index("      return { status: 'bailed', reason: bail, dirtyProbe: dirtyProbes[0] ?? null };")
    s2=s[:a]+s[b:]
elif which=='V3':
    s2=s.replace('const ringSampled = isSolveClassBail(bail) && dirtyProbes.length === 0;','const ringSampled = isSolveClassBail(bail);',1)
elif which=='Vc':
    s2=s.replace('  if (!frameRing) return 0;\n','',1)
elif which=='Ve':
    s2=s.replace("    || /^non-uniform-snapshot /.test(r)             // the raster refusal\n",'',1)
assert s2!=s, which
open(path,'w').write(s2)
```

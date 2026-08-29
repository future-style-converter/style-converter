// threshold-derivation.mjs — derive the visual-gate thresholds from data
// instead of eyeballs. Rerunnable study; read-only over committed PNGs.
//
// ## Why
//
// The shipped gates (SSIM ≥ 0.95, Δpx ≤ 2%, ΔE95 ≤ 5.0) were picked by eye.
// The A/A noise floor is exactly ZERO (noise-floor.sh, STATUS 2026-08-28),
// so every point of slack is pure tolerance for real change — and the blur
// bug proved the slack was live: `filter: blur()` was wrong on BOTH natives
// at ΔE95 3.82/3.30/2.29 while every pairwise gate passed. This script
// derives thresholds from two measured populations instead:
//
//   HEALTHY  — every formable pair of the committed baselines in
//              tools/visual/baseline/ (399 pairs), minus the pairs excused
//              by cross-platform-expectations.json. All of these pass the
//              current gate; their envelope is the AA/rounding tolerance a
//              threshold must grant.
//   BUGGY    — the calibration set: metric readings for REAL bugs at the
//              moment they were alive, reconstructed from the pre-fix
//              baselines still in git history (git show <fix>^:<baseline>),
//              cross-checked against the numbers recorded in STATUS/commit
//              messages so a drifted pipeline cannot silently rescore them.
//
// A candidate threshold set is judged by (a) which calibration bugs it
// catches and (b) how many healthy pairs it newly fails — the catch/cost
// table. The derived proposal is recorded in docs/STATUS.md ("Deriving the
// thresholds", 2026-08-29); the shipped thresholds are NOT changed here —
// flipping them moves every recorded number and is the parent's call after
// device verification.
//
// ## Run
//
//   node tools/visual/threshold-derivation.mjs [--json out.json]
//
// ~2–4 min (399 pairs × 2 pixelmatch passes + SSIM + ΔE + edge scan).
// Exits non-zero if a known-value control fails (a broken pipeline must
// read as broken, not as a clean distribution).

import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { PNG } from 'pngjs';
import pixelmatchDefault from 'pixelmatch';
import { ssim as computeSsim } from 'ssim.js';
// Shipping helpers — the study MUST score with the same code the gate uses,
// or the derived numbers would not transfer to the gate.
import { computeLabDeltaE } from './compare-screenshots-metrics.mjs';
import { padToCanvas, PAD_SENTINEL } from './pad-canvas.mjs';

// pixelmatch ships CJS; interop mirrors compare-screenshots.mjs line 141.
const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

// Repo root = two levels above tools/visual/ — used for git-show + baseline paths.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
// The 399 committed per-platform captures the healthy population is built from.
const BASELINE_DIR = join(ROOT, 'tools', 'visual', 'baseline');
// The expectation ledger — excused pairs are marked, not counted as healthy.
const LEDGER_PATH = join(ROOT, 'tools', 'visual', 'cross-platform-expectations.json');

// ── Metric option sets ──────────────────────────────────────────────────────
// SHIPPING: exactly diffPair's options (compare-screenshots.mjs). Reminder
// from the corrected STATUS note: includeAA:true DISABLES AA detection, so
// threshold 0.25 is the only thing absorbing cross-rasterizer AA — and it
// makes the metric blind to uniform shifts below 66/255 (blue: 198/255).
export const SHIPPING_PIXELMATCH_OPTS = Object.freeze({ threshold: 0.25, includeAA: true });
// STRICT: the inverse trade — near-exact colour budget (fires on uniform
// shifts above ~6/255) with pixelmatch's own AA detector ON (includeAA:false
// = "do not skip AA detection"), so rasterizer edge pixels are excused by
// structure rather than by a colour budget wide enough to hide a recolour.
export const STRICT_PIXELMATCH_OPTS = Object.freeze({ threshold: 0.02, includeAA: false });
// Edge classifier: a diff pixel is "edge-class" if EITHER image has a hard
// colour boundary (>32/255 on any channel) at a 4-neighbour — i.e. the
// difference sits on rasterized structure. Flat-class diffs (interior fills,
// shadow/blur penumbras) are region recolours a person reads as "different".
export const EDGE_GRADIENT = 32;

// Max 4-neighbour single-channel gradient at (x,y) exceeds EDGE_GRADIENT?
export function isEdgePixel(img, x, y, W, H) {
  const i = (y * W + x) * 4;                                     // RGBA index of the centre pixel
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {   // 4-neighbourhood
    const nx = x + dx, ny = y + dy;
    if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;        // canvas border: no neighbour
    const j = (ny * W + nx) * 4;
    for (let c = 0; c < 3; c++) {                                // RGB only; captures are opaque
      if (Math.abs(img.data[i + c] - img.data[j + c]) > EDGE_GRADIENT) return true;
    }
  }
  return false;
}

// Score one pair with the full study metric block. Inputs are pngjs images;
// both are padded (ASYNC — await, see pad-canvas.mjs) to the shared canvas
// with the magenta sentinel so an under-sized capture reads as different.
export async function scorePair(aRaw, bRaw) {
  const W = Math.max(aRaw.width, bRaw.width), H = Math.max(aRaw.height, bRaw.height);
  const a = await padToCanvas(aRaw, W, H);                       // no-op when already W×H
  const b = await padToCanvas(bRaw, W, H);
  // Δpx, shipping options — the number the current gate compares to 2%.
  const dpxCur = pixelmatch(a.data, b.data, null, W, H, SHIPPING_PIXELMATCH_OPTS);
  // Δpx, strict variant — diffMask output so the edge/flat split can read
  // exactly the pixels this metric counted, not a re-derived approximation.
  const mask = new PNG({ width: W, height: H });
  const dpxStrict = pixelmatch(a.data, b.data, mask.data, W, H, { ...STRICT_PIXELMATCH_OPTS, diffMask: true });
  // Edge/flat split of the strict diff mask (diffMask leaves non-diff pixels
  // transparent, so alpha>0 identifies counted pixels).
  let edge = 0;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (mask.data[(y * W + x) * 4 + 3] === 0) continue;        // not a counted diff pixel
      if (isEdgePixel(a, x, y, W, H) || isEdgePixel(b, x, y, W, H)) edge++;
    }
  }
  // SSIM — same invocation as safeSsim (ssim:'fast', greyscale internals).
  const s = computeSsim(
    { data: new Uint8ClampedArray(a.data), width: W, height: H },
    { data: new Uint8ClampedArray(b.data), width: W, height: H },
    { ssim: 'fast' },
  );
  // ΔE — shipping stride 4, matching the manifest's default (the recorded
  // ledger/STATUS numbers this study cross-checks against used stride 4).
  const de = computeLabDeltaE(a, b, 4);
  return {
    ssim: +s.mssim.toFixed(4),                                   // 4dp, matching safeSsim
    dpxCur: +((dpxCur / (W * H)) * 100).toFixed(3),              // percent, matching diffPair
    dpxStrict: +((dpxStrict / (W * H)) * 100).toFixed(3),
    dE95: de?.p95 ?? null, dEmean: de?.mean ?? null,             // null = metric bailed (never fails a gate)
    edgeShare: dpxStrict ? +(edge / dpxStrict).toFixed(3) : null, // null when nothing differed
  };
}

// Percentile over a pre-sorted ascending array — same index formula as
// computeLabDeltaE's p95 (floor(p×(n−1))) so the two agree by construction.
export function percentile(sorted, p) {
  if (!sorted.length) return null;                               // empty population has no percentile
  return sorted[Math.floor(p * (sorted.length - 1))];
}

// ── Candidate threshold sets ────────────────────────────────────────────────
// null = metric not gated in that candidate. Values are the FAIL boundaries:
// ssim strictly below, the rest strictly above — mirroring pairRegressed.
export const CANDIDATES = Object.freeze([
  // C0 — the shipped gate, as the control row of the catch/cost table.
  { id: 'C0-current', ssim: 0.95, dpxCur: 2, dE95: 5, dpxStrict: null },
  // C1 — ΔE95 tightened to the derived value: healthy max is 1.093, the
  // weakest live-bug pair (blur iOS-Android) sat at 2.287 → 2.0 splits the
  // gap with margin both ways.
  { id: 'C1-dE2', ssim: 0.95, dpxCur: 2, dE95: 2, dpxStrict: null },
  // C2 — the "~3.0" probe from the mission brief: still catches blur (via
  // its two web pairs at 3.27/3.89) but with less margin on the catch side.
  { id: 'C2-dE3', ssim: 0.95, dpxCur: 2, dE95: 3, dpxStrict: null },
  // C3 — Δpx dead-slack removal: healthy max is 0.855%, so 1% costs nothing.
  { id: 'C3-dE2-px1', ssim: 0.95, dpxCur: 1, dE95: 2, dpxStrict: null },
  // C4 — add the strict pixelmatch variant as a fourth gate: healthy max
  // 2.927%, buggy-blur minimum 5.25% → 3.5 splits the gap.
  { id: 'C4-proposal', ssim: 0.95, dpxCur: 1, dE95: 2, dpxStrict: 3.5 },
  // C5 — SSIM-tightening demonstrator: what catching perspective-by-SSIM
  // would cost (it sits at 0.9884; even 0.96 already fails 15 healthy pairs).
  { id: 'C5-ssim96', ssim: 0.96, dpxCur: 1, dE95: 2, dpxStrict: 3.5 },
]);

// One pair fails one candidate? Null-handling mirrors pairRegressed: a
// missing metric can never manufacture a failure on its own.
export function pairFails(row, cand) {
  if (row.ssim !== null && row.ssim !== undefined && cand.ssim !== null && row.ssim < cand.ssim) return true;
  if (row.dpxCur !== null && row.dpxCur !== undefined && cand.dpxCur !== null && row.dpxCur > cand.dpxCur) return true;
  if (row.dE95 !== null && row.dE95 !== undefined && cand.dE95 !== null && row.dE95 > cand.dE95) return true;
  if (row.dpxStrict !== null && row.dpxStrict !== undefined && cand.dpxStrict !== null && row.dpxStrict > cand.dpxStrict) return true;
  return false;                                                  // nothing breached → pass
}

// ── The calibration set ─────────────────────────────────────────────────────
// Each entry names the fix commit whose PARENT still holds the buggy
// baseline, the component, the pairs that were live-buggy, and the readings
// recorded in STATUS / the fix commit at the moment the bug was alive.
// `recorded` values are VERIFIED against the recomputation (tolerances per
// entry, with provenance notes where the archival number was taken from the
// live run rather than the committed bytes). `rev: null` = not recomputable
// (captures never committed); those rows use pinned readings only.
export const CALIBRATION = Object.freeze([
  {
    bug: 'sepia-approximation (iOS)', rev: '6c427b02^', component: '035_Filter_Sepia',
    pairs: ['iOS-web', 'iOS-Android'],
    recorded: { 'iOS-web': { ssim: 0.9814, dpxCur: 0.164, dE95: 23.35 }, 'iOS-Android': { ssim: 0.9573, dpxCur: 0.57, dE95: 23.35 } },
    tol: { ssim: 0.0005, dpxCur: 0.005, dE95: 0.005 },           // recorded straight off this pipeline → exact
  },
  {
    bug: 'blur-sigma (both natives)', rev: '215b377b^', component: '032_Filter_Blur',
    pairs: ['iOS-web', 'Android-web', 'iOS-Android'],
    recorded: { 'iOS-web': { ssim: 0.9748, dE95: 3.82 }, 'Android-web': { dE95: 3.3 }, 'iOS-Android': { dE95: 2.29 } },
    // dE tolerance 0.1: the commit-message dEs were read from the live run's
    // manifest; SSIM reproduces exactly, dE within 0.07 (3.893 vs 3.82).
    tol: { ssim: 0.0005, dE95: 0.1 },
  },
  {
    bug: 'brightness-additive (iOS)', rev: 'da2c241d^', component: '033_Filter_Brightness',
    pairs: ['iOS-web'],
    recorded: { 'iOS-web': { dE95: 18.7 } },                     // STATUS: "ΔE95 18.7 pre-fix"
    tol: { dE95: 0.05 },                                         // reproduces to 18.697
  },
  {
    bug: 'transform-order (iOS)', rev: 'b4a26200^', component: '041_Transform_Combined',
    pairs: ['iOS-web'],
    recorded: { 'iOS-web': {} },                                 // no archived per-pair block; range only
    tol: {},
  },
  {
    bug: 'transform-order (iOS, multi)', rev: 'b4a26200^', component: '106_Edge_MultiTransform',
    pairs: ['iOS-web'],
    recorded: { 'iOS-web': {} },                                 // ditto — SSIM range 0.90–0.98 in STATUS
    tol: {},
  },
  {
    bug: 'perspective-drop (Android)', rev: 'da2c241d^', component: '046_Perspective_Rotate',
    pairs: ['Android-web'],
    recorded: { 'Android-web': {} },                             // STATUS records px counts, not pair metrics
    tol: {},
  },
  {
    bug: 'neumorphic-penumbra (iOS, live+ledgered)', rev: null, component: '098_Neumorphic_Light',
    live: true,                                                  // still unfixed: score CURRENT baselines
    pairs: ['iOS-Android', 'iOS-web'],
    recorded: { 'iOS-Android': { dE95: 5.34 }, 'iOS-web': { dE95: 5.59 } },
    // 0.3: ledger dEs were live-run readings; committed bytes give 5.116/5.309
    // — same side of every candidate boundary, so the slack is provenance,
    // not conclusion-bearing.
    tol: { dE95: 0.3 },
  },
  {
    bug: 'backdrop-saturate capture gap', rev: null, component: 'Backdrop_Saturate_OverStripes',
    pinnedOnly: true,                                            // composition-test captures are not committed
    pairs: ['iOS-web', 'Android-web'],
    pinned: { 'iOS-web': { ssim: 0.9778, dpxCur: 0, dE95: 24.92, dpxStrict: null }, 'Android-web': { ssim: 0.9783, dpxCur: 0, dE95: 24.92, dpxStrict: null } },
  },
]);

// Compare one computed row against its recorded control. Returns mismatch
// strings (empty = verified). Missing recorded metric ⇒ nothing to verify.
export function verifyAgainstRecorded(computed, recorded, tol) {
  const bad = [];
  for (const [metric, want] of Object.entries(recorded ?? {})) {
    const got = computed[metric];
    const t = tol?.[metric] ?? 0.01;                             // default tight: recorded off this pipeline
    if (got === null || got === undefined || Math.abs(got - want) > t) {
      bad.push(`${metric}: recorded ${want} vs recomputed ${got} (tol ${t})`);
    }
  }
  return bad;
}

// Read one baseline blob — from git history when rev is set (the pre-fix
// bytes), from the working tree when rev is null (live bugs).
function loadBaseline(rev, platform, component) {
  const rel = `tools/visual/baseline/${platform}__${component}.png`;
  if (rev === null) return PNG.sync.read(readFileSync(join(ROOT, rel)));
  // git show <rev>:<path> — read-only archaeology; maxBuffer for large PNGs.
  return PNG.sync.read(execFileSync('git', ['show', `${rev}:${rel}`], { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 }));
}

// ── Known-value controls ────────────────────────────────────────────────────
// If any of these fails the whole study aborts: a pipeline that cannot
// reproduce hand-computable answers must not be allowed to emit a
// plausible-looking distribution.
export async function runControls() {
  const failures = [];
  // Build a W×H flat field at grey value v (opaque — pixelmatch's alpha-0
  // branch scores ANY colour pair as identical, the exact trap that produced
  // the wrong 132 blind-spot figure in STATUS). 32×32 default because
  // ssim.js 'fast' needs its 11×11 window to fit; the blind-spot probes use
  // 8×8 (pixelmatch only, no window constraint).
  const flat = (v, W = 32, H = 32) => {
    const img = new PNG({ width: W, height: H });
    for (let i = 0; i < img.data.length; i += 4) { img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; }
    return img;
  };
  // Control 1 — identity: same bytes must score perfect on every metric.
  const idRow = await scorePair(flat(100), flat(100));
  if (idRow.ssim !== 1 || idRow.dpxCur !== 0 || idRow.dpxStrict !== 0 || idRow.dE95 !== 0) {
    failures.push(`identity control scored ${JSON.stringify(idRow)} — expected perfect`);
  }
  // Control 2 — the measured pixelmatch blind-spot boundary (STATUS
  // 2026-08-28 correction): a uniform grey shift of 65 is silent under the
  // shipping options, 66 fires; the strict variant fires on both.
  const at = (va, vb, opts) => pixelmatch(flat(va, 8, 8).data, flat(vb, 8, 8).data, null, 8, 8, opts);
  if (at(0, 65, SHIPPING_PIXELMATCH_OPTS) !== 0) failures.push('shipping opts fired on a 65/255 uniform shift — blind spot moved');
  if (at(0, 66, SHIPPING_PIXELMATCH_OPTS) !== 64) failures.push('shipping opts silent on a 66/255 uniform shift — blind spot moved');
  if (at(0, 65, STRICT_PIXELMATCH_OPTS) !== 64) failures.push('strict opts silent on a 65/255 uniform shift — variant miswired');
  // Control 3 — padding: a half-height image against a full one must read
  // massively different (magenta sentinel vs content), proving the ASYNC
  // padToCanvas was awaited and the sentinel is live. A forgotten await
  // throws here; a background-coloured pad would score near-zero.
  const padRow = await scorePair(flat(100, 32, 32), flat(100, 32, 64));
  if (padRow.dpxCur < 45) failures.push(`under-size pad scored dpxCur ${padRow.dpxCur}% — sentinel not applied (PAD_SENTINEL=${JSON.stringify(PAD_SENTINEL)})`);
  return failures;
}

// ── Study driver (guarded — importing this module runs nothing) ─────────────
async function main() {
  const controls = await runControls();
  if (controls.length) {                                         // broken pipeline reads as broken
    console.error('CONTROLS FAILED:\n  ' + controls.join('\n  '));
    process.exit(2);
  }
  // Healthy population: every formable pair of the committed baselines.
  const comps = [...new Set(readdirSync(BASELINE_DIR).map((f) => f.replace(/^[^_]*__/, '')))].sort();
  const ledger = JSON.parse(readFileSync(LEDGER_PATH, 'utf8')).expectations;
  // Ledger keys match the gate's componentKey (strip the unstable index).
  const excused = new Set(ledger.map((e) => `${e.component.replace(/^\d+_/, '')} ${e.pair}`));
  const rows = [];
  for (const c of comps) {
    const imgs = {};
    for (const p of ['iOS', 'Android', 'web']) {                 // load whichever platforms exist
      try { imgs[p] = PNG.sync.read(readFileSync(join(BASELINE_DIR, `${p}__${c}`))); } catch { /* absent */ }
    }
    for (const [pa, pb] of [['iOS', 'Android'], ['iOS', 'web'], ['Android', 'web']]) {
      if (!imgs[pa] || !imgs[pb]) continue;                      // pair not formable
      const pair = `${pa}-${pb}`;
      rows.push({ component: c, pair, ledgered: excused.has(`${c.replace(/^\d+_/, '')} ${pair}`), ...(await scorePair(imgs[pa], imgs[pb])) });
    }
  }
  const healthy = rows.filter((r) => !r.ledgered);
  // Envelope table: the tolerance a threshold must grant, per metric.
  console.log(`\npairs: ${rows.length} formable, ${rows.length - healthy.length} ledgered, ${healthy.length} healthy`);
  console.log('ledger entries not formable here (no committed baseline):',
    ledger.filter((e) => !comps.some((c) => c.replace(/^\d+_/, '') === e.component.replace(/^\d+_/, ''))).length);
  for (const m of ['ssim', 'dpxCur', 'dpxStrict', 'dE95']) {
    const vs = healthy.map((r) => r[m]).filter((v) => v !== null).sort((a, b) => a - b);
    const line = (p) => percentile(vs, p)?.toFixed(4);
    console.log(`${m.padEnd(9)} min=${vs[0].toFixed(4)} p50=${line(0.5)} p95=${line(0.95)} p99=${line(0.99)} max=${vs[vs.length - 1].toFixed(4)}`);
  }
  // Calibration: recompute each bug's live pairs and verify vs the record.
  const calRows = [];
  for (const cal of CALIBRATION) {
    for (const pairKey of cal.pairs) {
      let computed;
      if (cal.pinnedOnly) {
        computed = { ...cal.pinned[pairKey], edgeShare: null };  // not recomputable — pinned record only
      } else {
        const [pa, pb] = pairKey.split('-');                     // e.g. 'iOS-web' → platforms
        computed = await scorePair(loadBaseline(cal.rev, pa, cal.component), loadBaseline(cal.rev, pb, cal.component));
        const bad = verifyAgainstRecorded(computed, cal.recorded?.[pairKey], cal.tol);
        if (bad.length) {                                        // drifted pipeline or wrong blob → abort
          console.error(`CALIBRATION MISMATCH ${cal.bug} ${pairKey}:\n  ${bad.join('\n  ')}`);
          process.exit(3);
        }
      }
      calRows.push({ bug: cal.bug, component: cal.component, pair: pairKey, pinnedOnly: !!cal.pinnedOnly, ...computed });
      console.log(`CAL ${cal.bug} · ${pairKey}: ${JSON.stringify(computed)}`);
    }
  }
  // Catch/cost table: per candidate, which bugs fire (≥1 live pair fails)
  // and which healthy pairs newly fail (each named + edge/flat classified).
  const bugs = [...new Set(calRows.map((r) => r.bug))];
  const table = [];
  for (const cand of CANDIDATES) {
    const caught = bugs.filter((bug) => calRows.some((r) => r.bug === bug && pairFails(r, cand)));
    const newFails = healthy.filter((r) => pairFails(r, cand)).map((r) => ({
      component: r.component, pair: r.pair, ssim: r.ssim, dpxCur: r.dpxCur, dpxStrict: r.dpxStrict, dE95: r.dE95,
      // Edge-dominated (≥0.8) = rasterizer AA; flat-dominated (≤0.2) = region recolour; else mixed.
      class: r.edgeShare === null ? 'no-diff' : r.edgeShare >= 0.8 ? 'edge' : r.edgeShare <= 0.2 ? 'flat' : 'mixed',
    }));
    table.push({ id: cand.id, thresholds: cand, caught, missed: bugs.filter((b) => !caught.includes(b)), newFails });
    console.log(`\n${cand.id}: catches ${caught.length}/${bugs.length} bugs, ${newFails.length} new healthy failures`);
    console.log(`  caught: ${caught.join(' · ') || '—'}`);
    console.log(`  missed: ${table[table.length - 1].missed.join(' · ') || '—'}`);
    for (const f of newFails) console.log(`  NEW FAIL [${f.class}] ${f.component} ${f.pair} ssim=${f.ssim} dpxCur=${f.dpxCur} dpxStrict=${f.dpxStrict} dE95=${f.dE95}`);
  }
  // Optional machine-readable record of the whole study.
  const jsonIdx = process.argv.indexOf('--json');
  if (jsonIdx !== -1 && process.argv[jsonIdx + 1]) {
    writeFileSync(process.argv[jsonIdx + 1], JSON.stringify({ generated: new Date().toISOString(), rows, calRows, table }, null, 1));
    console.log(`\nwrote ${process.argv[jsonIdx + 1]}`);
  }
}

// Import-safe: node --test imports the pure helpers without running a 3-minute study.
// pathToFileURL handles spaces/symlinks that a hand-built file:// string would not.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}

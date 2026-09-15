#!/usr/bin/env node
// tools/titan/score-gate.mjs — the per-cell gate scorer (wave 50, tracked).
//
// Why this file exists: "lost" and "gained" are the PER-CELL DIFF of two run
// dirs, never a subtraction of totals (retro A1#1: four waves declared "zero
// regressions" over per-section drops their own snapshots show). Every
// previous wave re-derived that diff in a scratchpad script that was then
// wiped with the session; this is the scorer as code, with the idiom every
// snapshot is counted by (tools/titan/README.md "The scorer idiom"):
//
//   a CELL is one (test, platform) browser-ref diff; it is SCORED iff
//   typeof x.ssim === 'number' && !x.scoreExcluded; it PASSES iff
//   x.wptPass === true.
//
// Usage:
//   node tools/titan/score-gate.mjs <prev-run> <this-run> [--json out.json]
//        [--watch cells.txt] [--movers 0.01]
//
//   <prev-run>/<this-run>   run-id under tools/titan/runs/ or a run-dir path.
//   --json out.json         write the full diff (totals + sections in the
//                           corpus-v6 snapshot shape, plus the flip lists).
//   --watch cells.txt       one watch per line: "<sec>/<test> <platform>" or
//                           any substring of "<sec>/<test>" — prints prev→cur
//                           for every matching cell (skeptic watchlists).
//   --movers N              |Δssim| at which a same-verdict cell is listed as
//                           a mover (default 0.01). A passing cell that MOVES
//                           is not a regression by itself — look at the PNG.
//
// Exit codes: 0 diff computed (even with losses — the numbers are the
// output); 2 usage / a run dir without sections.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const TITAN_DIR = path.dirname(fileURLToPath(import.meta.url));
// The three browser-ref diff keys and the platform name each scores.
export const PLATFORM_KEYS = { 'web-ref': 'web', 'ios-ref': 'ios', 'android-ref': 'android' };
// Composed capture dirs per platform — one PNG per test (component PNGs
// carry a `__N` suffix and are not columns).
const CAPTURE_DIRS = { web: 'screenshots', android: 'android-screenshots', ios: 'ios-screenshots' };

// Resolve a run-id or a path to a run dir that has sections/.
export function resolveRunDir(idOrPath) {
  const candidates = [idOrPath, path.join(TITAN_DIR, 'runs', idOrPath)];
  for (const c of candidates) if (fs.existsSync(path.join(c, 'sections'))) return c;
  return null;
}

// The scorer idiom, verbatim — exported so a test can mutate-check it.
export function isScored(x) { return !!x && typeof x.ssim === 'number' && !x.scoreExcluded; }
export function isPass(x) { return !!x && x.wptPass === true; }

// Load one run: per section, every scored cell keyed "test|platform", plus
// the tests.list length and the composed-capture count per column.
export function loadRun(runDir) {
  const sectionsDir = path.join(runDir, 'sections');
  const out = { runDir, sections: {} };
  for (const sec of fs.readdirSync(sectionsDir).sort()) {
    const sdir = path.join(sectionsDir, sec);
    const manifestPath = path.join(sdir, 'manifest.json');
    const section = { cells: new Map(), hasManifest: fs.existsSync(manifestPath), testsListed: 0, columns: {} };
    const testsList = path.join(sdir, 'tests.list');
    if (fs.existsSync(testsList)) {
      section.testsListed = fs.readFileSync(testsList, 'utf8').split('\n').filter(l => l.trim()).length;
    }
    for (const [platform, dir] of Object.entries(CAPTURE_DIRS)) {
      const d = path.join(sdir, dir);
      section.columns[platform] = fs.existsSync(d)
        ? fs.readdirSync(d).filter(f => f.endsWith('.png') && !/__\d+\.png$/.test(f)).length
        : 0;
    }
    if (section.hasManifest) {
      const m = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
      const results = (m.wpt && m.wpt.results) || {};
      for (const [test, r] of Object.entries(results)) {
        const diffs = (r.browserRef && r.browserRef.diffs) || {};
        for (const [key, platform] of Object.entries(PLATFORM_KEYS)) {
          const x = diffs[key];
          if (!isScored(x)) continue;
          section.cells.set(`${test}|${platform}`, { ssim: x.ssim, pass: isPass(x) });
        }
      }
    }
    out.sections[sec] = section;
  }
  return out;
}

function emptyTotals() { return { web: { passing: 0, measured: 0 }, ios: { passing: 0, measured: 0 }, android: { passing: 0, measured: 0 } }; }
function addCell(tot, platform, cell) { tot[platform].measured++; if (cell.pass) tot[platform].passing++; }

// The diff. Every list below is a per-cell fact; totals are derived from the
// same cells, so "gained.length - lost.length" always equals the totals delta
// for cells measured on both sides (newly measured / unmeasured-now cells are
// listed separately and explain the remainder).
export function diffRuns(prev, cur, { moverThreshold = 0.01 } = {}) {
  const res = {
    totals: { prev: emptyTotals(), cur: emptyTotals() },
    sections: {},
    gained: [], lost: [], movers: [], newlyMeasured: [], unmeasuredNow: [],
    missingSections: [], columnShorts: [],
  };
  const allSections = new Set([...Object.keys(prev.sections), ...Object.keys(cur.sections)]);
  for (const sec of [...allSections].sort()) {
    const p = prev.sections[sec], c = cur.sections[sec];
    const secOut = { prev: emptyTotals(), cur: emptyTotals() };
    res.sections[sec] = secOut;
    if (!c || !c.hasManifest) { res.missingSections.push(sec); }
    if (c) {
      for (const [platform, have] of Object.entries(c.columns)) {
        if (c.testsListed && have !== c.testsListed) res.columnShorts.push({ sec, platform, have, want: c.testsListed });
      }
    }
    const keys = new Set([...(p ? p.cells.keys() : []), ...(c ? c.cells.keys() : [])]);
    for (const key of keys) {
      const [test, platform] = key.split('|');
      const a = p && p.cells.get(key), b = c && c.cells.get(key);
      if (a) { addCell(res.totals.prev, platform, a); addCell(secOut.prev, platform, a); }
      if (b) { addCell(res.totals.cur, platform, b); addCell(secOut.cur, platform, b); }
      const row = { sec, test, platform, prev: a ? a.ssim : null, cur: b ? b.ssim : null, prevPass: a ? a.pass : null, curPass: b ? b.pass : null };
      if (a && !b) { res.unmeasuredNow.push(row); continue; }
      if (!a && b) { res.newlyMeasured.push(row); continue; }
      if (!a.pass && b.pass) res.gained.push(row);
      else if (a.pass && !b.pass) res.lost.push(row);
      else if (Math.abs(b.ssim - a.ssim) >= moverThreshold) res.movers.push({ ...row, delta: +(b.ssim - a.ssim).toFixed(4) });
    }
  }
  const byScore = (x, y) => (x.sec + x.test).localeCompare(y.sec + y.test) || x.platform.localeCompare(y.platform);
  for (const k of ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow']) res[k].sort(byScore);
  return res;
}

// Watch cells: "<sec>/<test> <platform>" or any substring of "<sec>/<test>".
export function watchCells(prev, cur, patterns) {
  const rows = [];
  for (const raw of patterns) {
    const line = raw.trim(); if (!line || line.startsWith('#')) continue;
    const [pat, platform] = line.split(/\s+/);
    for (const sec of new Set([...Object.keys(prev.sections), ...Object.keys(cur.sections)])) {
      const keys = new Set([...(prev.sections[sec]?.cells.keys() || []), ...(cur.sections[sec]?.cells.keys() || [])]);
      for (const key of keys) {
        const [test, plat] = key.split('|');
        if (platform && plat !== platform) continue;
        if (!`${sec}/${test}`.includes(pat)) continue;
        const a = prev.sections[sec]?.cells.get(key), b = cur.sections[sec]?.cells.get(key);
        rows.push({ watch: line, sec, test, platform: plat, prev: a ? `${a.pass ? 'P' : 'f'} ${a.ssim}` : 'unscored', cur: b ? `${b.pass ? 'P' : 'f'} ${b.ssim}` : 'unscored' });
      }
    }
  }
  return rows;
}

function fmtTot(t) { return ['web', 'ios', 'android'].map(p => `${p} ${t[p].passing}/${t[p].measured}`).join('  '); }
function cell(r) { return `${r.sec}/${r.test} ${r.platform}`; }

function main(argv) {
  const args = argv.slice(2);
  const opts = { json: null, watch: null, movers: 0.01 };
  const pos = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--json') opts.json = args[++i];
    else if (args[i] === '--watch') opts.watch = args[++i];
    else if (args[i] === '--movers') opts.movers = Number(args[++i]);
    else pos.push(args[i]);
  }
  if (pos.length !== 2) { console.error('usage: score-gate.mjs <prev-run> <this-run> [--json out] [--watch cells.txt] [--movers N]'); return 2; }
  const prevDir = resolveRunDir(pos[0]), curDir = resolveRunDir(pos[1]);
  if (!prevDir || !curDir) { console.error(`score-gate: run dir not found (${!prevDir ? pos[0] : pos[1]}) — a run-id under tools/titan/runs/ or a path with sections/`); return 2; }
  const prev = loadRun(prevDir), cur = loadRun(curDir);
  const d = diffRuns(prev, cur, { moverThreshold: opts.movers });
  console.log(`score-gate  prev=${path.basename(prevDir)}  cur=${path.basename(curDir)}`);
  console.log(`  prev totals: ${fmtTot(d.totals.prev)}`);
  console.log(`  cur  totals: ${fmtTot(d.totals.cur)}`);
  console.log(`  per-cell: gained ${d.gained.length}  lost ${d.lost.length}  newly-measured ${d.newlyMeasured.length}  unmeasured-now ${d.unmeasuredNow.length}  movers(|Δ|>=${opts.movers}) ${d.movers.length}`);
  if (d.missingSections.length) console.log(`  MISSING SECTIONS in cur (no manifest): ${d.missingSections.join(' ')}`);
  if (d.columnShorts.length) { console.log('  COLUMN SHORTS (composed PNGs vs tests.list):'); for (const s of d.columnShorts) console.log(`    ${s.sec} ${s.platform} ${s.have}/${s.want}`); }
  console.log('  per section (prev → cur):');
  for (const [sec, s] of Object.entries(d.sections)) {
    const line = ['web', 'ios', 'android'].map(p => `${p} ${s.prev[p].passing}/${s.prev[p].measured}→${s.cur[p].passing}/${s.cur[p].measured}`).join('  ');
    console.log(`    ${sec.padEnd(24)} ${line}`);
  }
  const list = (title, rows, f) => { if (!rows.length) return; console.log(`  ${title}:`); for (const r of rows) console.log(`    ${f(r)}`); };
  list('LOST (P → f) — diagnose every one', d.lost, r => `${cell(r)}  ${r.prev} → ${r.cur}`);
  list('GAINED (f → P)', d.gained, r => `${cell(r)}  ${r.prev} → ${r.cur}`);
  list('UNMEASURED NOW (scored before, not now — excluded or missing)', d.unmeasuredNow, r => `${cell(r)}  was ${r.prevPass ? 'P' : 'f'} ${r.prev}`);
  list('NEWLY MEASURED', d.newlyMeasured, r => `${cell(r)}  now ${r.curPass ? 'P' : 'f'} ${r.cur}`);
  list(`MOVERS (same verdict, |Δssim| >= ${opts.movers})`, d.movers, r => `${cell(r)}  ${r.prevPass ? 'P' : 'f'} ${r.prev} → ${r.cur} (${r.delta > 0 ? '+' : ''}${r.delta})`);
  if (opts.watch) {
    const rows = watchCells(prev, cur, fs.readFileSync(opts.watch, 'utf8').split('\n'));
    console.log(`  WATCHLIST (${opts.watch}):`);
    for (const r of rows) console.log(`    ${cell(r)}  ${r.prev} → ${r.cur}   [${r.watch}]`);
  }
  if (opts.json) {
    fs.writeFileSync(opts.json, JSON.stringify({ prev: path.basename(prevDir), cur: path.basename(curDir), ...d }, null, 1));
    console.log(`  json → ${opts.json}`);
  }
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv));
}

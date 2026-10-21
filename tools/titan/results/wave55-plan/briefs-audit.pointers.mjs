#!/usr/bin/env node
// tools/titan/results/wave55-plan/briefs-audit.pointers.mjs
//
// The wave-55 cross-lane auditor's POINTER and CELL read-out over the seven lane briefs.
// READ-ONLY: it reads the briefs, `git ls-files` (passed in as a file), the run dirs and
// the frozen refs; it writes only to stdout. Written while the opening gate wave55-open
// captures on this host, so it is light (one pass over each brief, five manifest loads).
//
// Usage: node briefs-audit.pointers.mjs <tracked-list.txt>
//   (tracked-list.txt = `git ls-files` at HEAD 0d0d3a3d; the auditor passes it in so the
//   script never runs git while the gate is live).
//
// What it checks, per brief line:
//   P1  every `<path>.<ext>[:L[-M]]` token resolves to ONE file (repo-relative, `…/`-suffix,
//       brief-stem-relative `.x.y` tokens, or a unique basename / partial-path suffix
//       against the tracked list plus the untracked wave55-plan / wave55-gate dirs);
//   P2  a :L / :L-M range lies inside that file's line count;
//   P3  a path the same line labels NEW does not already exist;
//   C1  every fully written cell `<sec>/<test> <platform> <P|f> <ssim>` (optionally led by
//       a run id) equals the scorer's cell in the run it names (default: the brief's
//       evidence run, wave54-final), using score-gate.mjs's own loadRun (scored iff
//       typeof ssim === 'number' && !scoreExcluded; P iff wptPass === true).
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

// The repo root: this file sits at tools/titan/results/wave55-plan/.
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const PLAN = path.join(ROOT, 'tools/titan/results/wave55-plan');
const BRIEFS = ['hyphen-ios-linebox', 'cbb-android', 'positioned-parent-slot', 'ua-heading-margin',
  'counter-suffix-rows', 'android-paint-order', 'instruments'];
const tracked = fs.readFileSync(process.argv[2], 'utf8').split('\n').filter(Boolean);
// Untracked-but-present planning dirs count as resolvable (they are committed with the plan).
const extra = [];
for (const d of ['tools/titan/results/wave55-plan', 'tools/titan/results/wave55-gate']) {
  const walk = (rel) => { for (const e of fs.readdirSync(path.join(ROOT, rel), { withFileTypes: true })) {
    const r = path.join(rel, e.name); if (e.isDirectory()) walk(r); else extra.push(r); } };
  if (fs.existsSync(path.join(ROOT, d))) walk(d);
}
const trackedSet = new Set(tracked);
const all = [...tracked, ...extra];
// Suffix index: every "/"-boundary suffix of every path → the paths that end with it.
const suffix = new Map();
for (const p of all) { const parts = p.split('/');
  for (let i = 0; i < parts.length; i++) { const s = parts.slice(i).join('/');
    if (!suffix.has(s)) suffix.set(s, new Set()); suffix.get(s).add(p); } }
const lineCount = new Map();
const nLines = (p) => { if (!lineCount.has(p)) { try { lineCount.set(p, fs.readFileSync(path.join(ROOT, p), 'utf8').split('\n').length - 1); } catch { lineCount.set(p, -1); } } return lineCount.get(p); };

// Runs the briefs quote cells from (only those that exist on disk are loaded).
const RUNS = ['wave54-final', 'wave54-open', 'wave54-probe', 'wave54-ab-drop-U3b', 'wave53-final'];
const runs = {};
for (const r of RUNS) { const d = resolveRunDir(r); if (d) runs[r] = loadRun(d); }
// "css-text/hyphens/hyphenate-character-001|ios" → { pass, ssim } per run.
const cellOf = (run, test, platform) => { const R = runs[run]; if (!R) return undefined;
  const key = `css/${test}.html|${platform}`; for (const s of Object.values(R.sections)) { const c = s.cells.get(key); if (c) return c; } return null; };
const fmt = (c) => (c === undefined ? 'run-not-loaded' : c === null ? 'UNSCORED' : `${c.pass ? 'P' : 'f'} ${c.ssim}`);

const EXT = '(?:kts|kt|swift|tsx|ts|mjs|cjs|js|py|sh|md|json|txt|png|html|otf|ttf|patch|diff)';
// A path-ish token, optionally followed by :L or :L-M (also ':L, :M' lists are read as their first).
const PATH_RE = new RegExp(`([A-Za-z0-9_@…{},*./+-]*[A-Za-z0-9_}*-]\\.${EXT})(?::(\\d+)(?:[-–](\\d+))?)?(?![A-Za-z0-9])`, 'g');
const SECS = Object.keys((runs['wave54-final'] || { sections: {} }).sections);
// A full cell: optional run id, <sec>/<test...> <platform> <P|f> <num>.
const CELL_RE = new RegExp(`(?:\\b(wave5\\d-[A-Za-z0-9-]+)\\s+)?\\b((?:${SECS.map((s) => String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})/[A-Za-z0-9_./-]+?)\\s+(web|ios|android)\\s+\\**(P|f)\\s+(\\d+(?:\\.\\d+)?)`, 'g');

const out = [];
const counts = { paths: 0, ok: 0, fail: 0, cells: 0, cellsOk: 0, cellsFail: 0 };
for (const b of BRIEFS) {
  const lines = fs.readFileSync(path.join(PLAN, `${b}.md`), 'utf8').split('\n');
  out.push(`=== ${b}.md`);
  lines.forEach((line, i) => {
    const ln = i + 1;
    // ---- paths
    for (const m of line.matchAll(PATH_RE)) {
      let tok = m[1].replace(/^[(]+/, '');
      const from = m[2] ? +m[2] : null; const to = m[3] ? +m[3] : from;
      if (/[*{}]/.test(tok)) continue;                       // globs / brace sets: read by hand
      if (/^https?:/.test(tok) || /^[0-9.]+$/.test(tok)) continue;
      if (/^(?:css|filter-effects|selectors|CSS2|compositing)\b.*\.html$/.test(tok) && !tok.includes('tools/')) {
        // a WPT source path cited as "WPT 9b5435e5 css/…": the mirror is gitignored; check on disk.
        const wp = path.join(ROOT, 'tools/wpt', tok);
        counts.paths++; const ok = fs.existsSync(wp) || fs.existsSync(path.join(ROOT, 'tools/wpt', tok));
        if (!ok) { counts.fail++; out.push(`  L${ln} PATH-MISSING(wpt) ${tok}`); } else counts.ok++;
        continue;
      }
      let cands;
      if (tok.startsWith('.') && !tok.startsWith('./') && !tok.startsWith('..')) tok = `${b}${tok}`;
      if (tok.includes('…/')) tok = tok.slice(tok.lastIndexOf('…/') + 2);
      if (tok.startsWith('./')) tok = tok.slice(2);
      // run-dir / refs paths are gitignored: resolve on disk.
      if (/^tools\/(titan\/runs|wpt)\//.test(tok)) { counts.paths++;
        const ok = fs.existsSync(path.join(ROOT, tok)); if (!ok) { counts.fail++; out.push(`  L${ln} PATH-MISSING(disk) ${tok}`); } else counts.ok++; continue; }
      // per-test IR / capture file names: resolve inside wave54-final.
      if (/^wpt__/.test(tok)) { counts.paths++; const sec = SECS.find((s) => tok.startsWith(`wpt__${s}__`));
        const dirs = ['per-test-ir', 'screenshots', 'ios-screenshots', 'android-screenshots'];
        const ok = sec && dirs.some((d) => fs.existsSync(path.join(ROOT, 'tools/titan/runs/wave54-final/sections', sec, d, tok)));
        if (!ok) { counts.fail++; out.push(`  L${ln} PATH-MISSING(run wave54-final) ${tok}`); } else counts.ok++; continue; }
      cands = trackedSet.has(tok) || extra.includes(tok) ? new Set([tok]) : suffix.get(tok);
      const isNew = /\bNEW\b|\(new\)|new,|\bnew\b/.test(line.slice(Math.max(0, m.index - 60), m.index + tok.length + 20));
      counts.paths++;
      if (!cands || cands.size === 0) {
        if (isNew) { counts.ok++; out.push(`  L${ln} NEW-ABSENT(ok) ${tok}`); continue; }
        counts.fail++; out.push(`  L${ln} PATH-MISSING ${tok}${from ? `:${from}${to !== from ? `-${to}` : ''}` : ''}`); continue;
      }
      if (cands.size > 1) { counts.ok++; out.push(`  L${ln} AMBIGUOUS(${cands.size}) ${tok} → ${[...cands].slice(0, 4).join(', ')}`); continue; }
      const p = [...cands][0];
      if (isNew && !/held|RESTORED|re-land|5b333c8a|exists|already/i.test(line)) out.push(`  L${ln} NOTE NEW-but-exists ${p}`);
      if (from) { const n = nLines(p);
        if (n < to) { counts.fail++; out.push(`  L${ln} LINE-OUT-OF-RANGE ${p}:${from}${to !== from ? `-${to}` : ''} (file has ${n} lines)`); continue; } }
      counts.ok++;
    }
    // ---- cells
    for (const m of line.matchAll(CELL_RE)) {
      const run = m[1] && runs[m[1]] ? m[1] : (m[1] ? m[1] : 'wave54-final');
      const test = m[2].replace(/\.html$/, ''); const plat = m[3]; const want = `${m[4]} ${m[5]}`;
      counts.cells++;
      const got = cellOf(run, test, plat);
      if (got === undefined) { out.push(`  L${ln} CELL-RUN-NOT-LOADED ${run} ${test} ${plat} ${want}`); continue; }
      const gotS = fmt(got);
      // a written score may be rounded to fewer decimals than the manifest: compare at its precision.
      const dec = (m[5].split('.')[1] || '').length;
      const same = got && (got.pass ? 'P' : 'f') === m[4] && Math.abs(got.ssim - +m[5]) <= 0.5 * 10 ** -dec + 1e-12;
      if (same) counts.cellsOk++; else { counts.cellsFail++; out.push(`  L${ln} CELL-MISMATCH ${run} ${test} ${plat}: brief "${want}" vs run "${gotS}"`); }
    }
  });
}
out.push('', `paths checked ${counts.paths} · ok ${counts.ok} · fail ${counts.fail}`,
  `cells checked ${counts.cells} · ok ${counts.cellsOk} · mismatch ${counts.cellsFail}`, `runs loaded: ${Object.keys(runs).join(', ')}`);
console.log(out.join('\n'));

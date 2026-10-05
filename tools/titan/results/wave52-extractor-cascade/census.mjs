#!/usr/bin/env node
// wave-52 lane L5 (extractor-cascade) — the corpus census over the run of
// record: the 1435 per-test IR documents of tools/titan/runs/wave51-fix and
// their WPT sources, one shape per fix, plus the wave51-fix cells of every
// document the extract+convert DIFFERENTIAL (differential.sh) found changed.
//
// Read-only. Usage (from the repo root):
//   node tools/titan/results/wave52-extractor-cascade/census.mjs [diff-report.json ...] > census.json
// Each diff-report argument is a differential-report.mjs output; its changed
// per-test IR documents are resolved to their wave51-fix cells (web/ios/
// android, P/f, ssim) through score-gate.mjs's own loadRun, so a cell is
// named exactly as the gate scorer names it.
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..', '..', '..');
const SECTIONS = join(REPO, 'tools/titan/runs/wave51-fix/sections');
const WPT = join(REPO, 'tools/wpt');
const { loadRun, resolveRunDir } = await import(join(REPO, 'tools/titan/score-gate.mjs'));

// Every per-test IR doc: { section, file, test (manifest key), doc }.
const docs = [];
for (const sec of readdirSync(SECTIONS).sort()) {
  let files = [];
  try { files = readdirSync(join(SECTIONS, sec, 'per-test-ir')); } catch { continue; }
  for (const f of files.filter((x) => x.endsWith('.json')).sort()) {
    docs.push({ section: sec, file: f, doc: JSON.parse(readFileSync(join(SECTIONS, sec, 'per-test-ir', f), 'utf8')) });
  }
}
// The 1435 sources, from the union of the 30 sections' tests.list.
const tests = new Set();
for (const sec of readdirSync(SECTIONS)) {
  try { for (const l of readFileSync(join(SECTIONS, sec, 'tests.list'), 'utf8').split('\n')) if (l.trim()) tests.add(l.trim()); } catch { /* no list */ }
}
const source = (t) => { try { return readFileSync(join(WPT, t), 'utf8'); } catch { return ''; } };
// Comment-stripped <style> text + style attributes of one source.
const cssOf = (html) => {
  const h = html.replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, ' ');
  const style = [...h.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)].map((m) => m[1]).join('\n');
  const attrs = [...h.matchAll(/\sstyle\s*=\s*("([^"]*)"|'([^']*)')/gi)].map((m) => m[2] ?? m[3]);
  return { style, attrs };
};
const propOf = (c, type) => (c.properties ?? []).find((p) => p.type === type);

// ── F-E / T6: the 100x100 empty-node stamp (exactly Width 100 + Height 100) ──
const stampTags = {};
const liStampDocs = new Set();
for (const { file, doc } of docs) {
  for (const c of doc.components ?? []) {
    const ps = c.properties ?? [];
    if (ps.length !== 2) continue;
    const w = propOf(c, 'Width'); const h = propOf(c, 'Height');
    if (!(w?.data?.px === 100 && h?.data?.px === 100) || c.text) continue;
    const tag = c.meta?.sourceTag ?? '(untagged)';
    stampTags[tag] = (stampTags[tag] ?? 0) + 1;
    if (tag === 'li' || tag === 'summary') liStampDocs.add(file);
  }
}

// ── F-C: a color-mix() operand percentage outside [0,100] in the wire ───────
const colorMixOutOfRange = [];
for (const { file, doc } of docs) {
  for (const c of doc.components ?? []) {
    for (const p of c.properties ?? []) {
      const o = p.data?.original;
      if (o?.type !== 'color-mix') continue;
      const bad = [o.percent1, o.percent2].filter((x) => typeof x === 'number' && (x < 0 || x > 100));
      if (bad.length) colorMixOutOfRange.push({ file, component: c.name, property: p.type, percents: bad });
    }
  }
}
const colorMixSourceTests = [...tests].filter((t) =>
  [...cssOf(source(t)).style.matchAll(/color-mix\(([^;{}]*)\)/gi)]
    .some((m) => [...m[1].matchAll(/(^|[\s,])(-?\d*\.?\d+)%/g)].some((p) => Number(p[2]) < 0 || Number(p[2]) > 100)));

// ── F4: exponent-form angles, anywhere in a source or a wire document ──────
const EXP_ANGLE = /\d[eE][+-]?\d+(deg|grad|rad|turn)\b/i;
const expAngleSources = [...tests].filter((t) => EXP_ANGLE.test(source(t)));
const expAngleDocs = docs.filter(({ doc }) => EXP_ANGLE.test(JSON.stringify(doc))).map((d) => d.file);
// Upper-case angle units (the case half of F4) in sources.
const upperUnitSources = [...tests].filter((t) => /\d(DEG|GRAD|RAD|TURN|Deg|Turn|Rad|Grad)\b/.test(cssOf(source(t)).style));

// ── F1: importance carriers; F2: a `;` inside url( ; F3: layered sheets ────
const importanceSources = [...tests].filter((t) => { const c = cssOf(source(t)); return /!\s*important/i.test(c.style + c.attrs.join(';')); });
const urlSemicolonSources = [...tests].filter((t) => { const c = cssOf(source(t)); return /url\([^)]*;[^)]*\)/i.test(c.style + c.attrs.join('\n')); });
const layeredSources = [...tests].filter((t) => /@layer\b|revert-layer/i.test(cssOf(source(t)).style));

// ── the cells of every document a differential report found changed ───────
const run = loadRun(resolveRunDir('wave51-fix'));
const cellsFor = (section, irFile) => {
  // per-test IR name `wpt__<section>__<path with __>.json` → manifest key.
  const stem = irFile.replace(/\.json$/, '').replace(new RegExp(`^wpt__${section}__`), '');
  const out = {};
  for (const [key, cell] of run.sections[section]?.cells ?? new Map()) {
    const [test, platform] = key.split('|');
    if (test.replace(/^css\//, '').replace(/\.html?$|\.xht(ml)?$/, '').replace(`${section}/`, '').replaceAll('/', '__') === stem) {
      out[platform] = `${cell.pass ? 'P' : 'f'} ${cell.ssim}`;
      out.test = test;
    }
  }
  return out;
};
const differential = {};
for (const report of process.argv.slice(2)) {
  const r = JSON.parse(readFileSync(report, 'utf8'));
  differential[report.split('/').pop()] = r.perTestIr.changedDocs.map((d) => {
    const [section, file] = d.split('/');
    return { doc: d, cells: cellsFor(section, file) };
  });
}

console.log(JSON.stringify({
  run: 'wave51-fix', perTestIrDocs: docs.length, sources: tests.size,
  'F-E placeholder stamps by sourceTag': stampTags,
  'F-E docs with a li/summary stamp': [...liStampDocs].sort(),
  'F-C wire color-mix percent outside [0,100]': colorMixOutOfRange,
  'F-C sources with an out-of-range color-mix percentage': colorMixSourceTests,
  'F4 sources with an exponent angle': expAngleSources,
  'F4 wire docs with an exponent angle': expAngleDocs,
  'F4 sources with an upper-case angle unit in <style>': upperUnitSources,
  'F1 sources with !important': importanceSources,
  'F2 sources with a ; inside url(': urlSemicolonSources,
  'F3 sources with @layer / revert-layer': layeredSources,
  differential,
}, null, 1));

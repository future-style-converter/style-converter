// S6 INDEPENDENT census (written from scratch; does not use any builder lane's script):
// which wave49-final SCORED tests have a match-reference that declares a negative z-index?
import fs from 'node:fs';
import path from 'node:path';

const RUN = 'tools/titan/runs/wave49-final/sections';
const WPT = 'tools/wpt';

function readMatch(html, file) {
  // <link rel="match" href="...">  (attribute order agnostic)
  const re = /<link\b[^>]*>/gi;
  let m;
  while ((m = re.exec(html))) {
    const tag = m[0];
    if (!/rel\s*=\s*["']?match["']?/i.test(tag)) continue;
    const h = /href\s*=\s*["']([^"']+)["']/i.exec(tag);
    if (h) return path.resolve(path.dirname(file), h[1].split('#')[0].split('?')[0]);
  }
  return null;
}

function cssOf(file, seen = new Set()) {
  if (!file || seen.has(file) || !fs.existsSync(file)) return '';
  seen.add(file);
  const src = fs.readFileSync(file, 'utf8');
  let out = src;
  // pull in <link rel=stylesheet> and @import
  const lre = /<link\b[^>]*>/gi; let m;
  while ((m = lre.exec(src))) {
    const tag = m[0];
    if (!/rel\s*=\s*["']?stylesheet["']?/i.test(tag)) continue;
    const h = /href\s*=\s*["']([^"']+)["']/i.exec(tag);
    if (h) out += '\n' + cssOf(path.resolve(path.dirname(file), h[1]), seen);
  }
  const ire = /@import\s+(?:url\()?["']([^"')]+)["']\)?/gi;
  while ((m = ire.exec(src))) out += '\n' + cssOf(path.resolve(path.dirname(file), m[1]), seen);
  return out;
}

const NEGZ = /z-index\s*:\s*-\s*\d/i;

const sections = fs.readdirSync(RUN);
let scored = 0, unresolved = 0;
const refs = new Map();   // resolved ref path -> {tests:[], negz:bool}
const rows = [];
for (const sec of sections) {
  const mp = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const man = JSON.parse(fs.readFileSync(mp, 'utf8'));
  for (const [test, r] of Object.entries(man?.wpt?.results || {})) {
    const diffs = r?.browserRef?.diffs || {};
    const cells = {};
    let any = false;
    for (const [pair, d] of Object.entries(diffs)) {
      if (typeof d?.ssim !== 'number' || d.scoreExcluded) continue;
      cells[pair] = { v: d.wptPass === true ? 'P' : 'f', ssim: d.ssim };
      any = true;
    }
    if (!any) continue;
    scored++;
    const testFile = path.join(WPT, test);
    if (!fs.existsSync(testFile)) { unresolved++; continue; }
    const refFile = readMatch(fs.readFileSync(testFile, 'utf8'), testFile);
    if (!refFile || !fs.existsSync(refFile)) { unresolved++; continue; }
    const rel = path.relative(process.cwd(), refFile);
    if (!refs.has(rel)) refs.set(rel, { tests: [], negz: NEGZ.test(cssOf(refFile)) });
    const e = refs.get(rel); e.tests.push(test);
    if (e.negz) rows.push({ section: sec, test, ref: rel, cells });
  }
}
const negRefs = [...refs.entries()].filter(([, v]) => v.negz);
// Also: how many refs in the WHOLE corpus (any *-ref.html under tools/wpt/css) declare a negative z-index?
function walk(d, acc = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) { if (e.name !== 'refs') walk(p, acc); }
    else if (/-ref\.html?$/i.test(e.name)) acc.push(p);
  }
  return acc;
}
const allRefs = walk(path.join(WPT, 'css'));
const allNeg = allRefs.filter(f => NEGZ.test(cssOf(f)));
const cellCount = rows.reduce((a, r) => a + Object.keys(r.cells).length, 0);
const failCells = rows.reduce((a, r) => a + Object.values(r.cells).filter(c => c.v === 'f').length, 0);
console.log(JSON.stringify({
  scoredTestsExamined: scored,
  unresolvedMatchLink: unresolved,
  distinctRefsOfScoredTests: refs.size,
  scoredRefsWithNegativeZ: negRefs.length,
  scoredTestsWithNegativeZRef: rows.length,
  cellsOnThoseTests: cellCount,
  failingCellsOnThoseTests: failCells,
  corpusRefFilesScanned: allRefs.length,
  corpusRefFilesWithNegativeZ: allNeg.length,
  scoredRefs: negRefs.map(([k, v]) => ({ ref: k, tests: v.tests })),
  rows,
  corpusNegRefs: allNeg.map(f => path.relative(process.cwd(), f)),
}, null, 1));

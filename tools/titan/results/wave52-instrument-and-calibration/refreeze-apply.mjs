#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/refreeze-apply.mjs
//
// wave-52 L12-B — populate the '…-rootbg-uamargin' ref tree so that EXACTLY the
// refs the new contract moves are re-rendered, and every other ref keeps its
// frozen bytes. Why not just let the next gate re-render the tree lazily: the
// A/B (refreeze-ab.json) proved a byte-exact re-render is impossible on this host
// — 994 of 1435 refs come back with 1–45 glyph-antialias pixels changed under the
// OLD sheet itself — so a lazy re-render would move ~1000 cells for no contract
// reason and make the gate unattributable.
//
//   pre  : sha1 the old tree (whole + the per-test set), then CLONE the frozen
//          bytes of every NOT-moved test into the new tree's versioned slot
//          (<NEW_REV>/<browser-rev>/<sec>/<stem>.png). The moved tests (same-
//          session sheet A/B, refreeze-detail.json) are left absent.
//   (then run `node tools/titan/capture-browser-ref.mjs <tests…>` — the moved
//          ones render through the shipped path, the rest mirror as cache hits)
//   post : re-sha1; the OTHER set must be byte-identical old → new, the old tree
//          untouched, and each re-frozen PNG pixel-identical to the A/B's new arm.
// Usage: node …/refreeze-apply.mjs pre|post   (writes refreeze-apply.<mode>.json)
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

// Paths and the two contract revisions.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const REPO = path.resolve(TITAN, '..', '..');
const { fixtureStem } = await import(path.join(TITAN, 'safe-name.mjs'));
const { CANVAS_REV } = await import(path.join(TITAN, 'capture-browser-ref.mjs'));
const SHA = '9b5435e55e0b54a6cd09c1c563861eb3c999cef1';
const OLD_REV = 'white-black-ink-font-lh-imgpad-htmlpins';
const BROWSER = 'chrome-151.0.7922.47';
const ROOT = path.join(REPO, 'tools', 'wpt', 'refs', SHA);
const rel = (t) => path.join(t.split('/')[1], `${fixtureStem(t)}.png`);
const sha1 = (p) => crypto.createHash('sha1').update(fs.readFileSync(p)).digest('hex');
// One digest over a sorted (name, sha1) list — the "whole set" fingerprint.
const digest = (pairs) => crypto.createHash('sha1').update(pairs.map(([n, h]) => `${n} ${h}\n`).join('')).digest('hex');

// The gate's test list and the moved set from the same-session A/B.
const tests = [];
for (const sec of fs.readdirSync(path.join(TITAN, 'runs', 'wave52-open', 'sections')).sort()) {
  const tl = path.join(TITAN, 'runs', 'wave52-open', 'sections', sec, 'tests.list');
  if (fs.existsSync(tl)) tests.push(...fs.readFileSync(tl, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));
}
const moved = new Set(JSON.parse(fs.readFileSync(path.join(HERE, 'refreeze-detail.json'), 'utf8')).rows.map((r) => r.test));
const others = tests.filter((t) => !moved.has(t));

// Whole-tree digest of the OLD contract dir (every file, both views + claim).
function treeDigest(dir) {
  const pairs = [];
  const walk = (d) => { for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p); else pairs.push([path.relative(dir, p), sha1(p)]);
  } };
  walk(dir);
  pairs.sort((a, b) => a[0].localeCompare(b[0]));
  return { files: pairs.length, digest: digest(pairs) };
}

const mode = process.argv[2];
const oldView = (t) => path.join(ROOT, OLD_REV, rel(t));
const newView = (t) => path.join(ROOT, CANVAS_REV, rel(t));
const newVersioned = (t) => path.join(ROOT, CANVAS_REV, BROWSER, rel(t));
const othersPairsOld = () => others.map((t) => [rel(t), sha1(oldView(t))]);
const out = { mode, oldRev: OLD_REV, newRev: CANVAS_REV, tests: tests.length, moved: moved.size, others: others.length };

if (mode === 'pre') {
  // Refuse to clobber an existing tree: the bump creates it exactly once.
  if (fs.existsSync(path.join(ROOT, CANVAS_REV))) { console.error(`refreeze-apply: ${CANVAS_REV} already exists — refusing`); process.exit(2); }
  out.oldTree = treeDigest(path.join(ROOT, OLD_REV));
  out.othersDigestOld = digest(othersPairsOld());
  // Clone (APFS copy-on-write) the frozen bytes into the versioned slot.
  for (const t of others) {
    fs.mkdirSync(path.dirname(newVersioned(t)), { recursive: true });
    fs.copyFileSync(oldView(t), newVersioned(t), fs.constants.COPYFILE_FICLONE);
  }
  out.cloned = others.length;
  out.movedTests = [...moved];
} else if (mode === 'post') {
  const pre = JSON.parse(fs.readFileSync(path.join(HERE, 'refreeze-apply.pre.json'), 'utf8'));
  out.oldTree = treeDigest(path.join(ROOT, OLD_REV));
  out.oldTreeUntouched = out.oldTree.digest === pre.oldTree.digest && out.oldTree.files === pre.oldTree.files;
  // The OTHER set, read through the NEW scorer view (what inject will read).
  const newPairs = others.map((t) => [rel(t), fs.existsSync(newView(t)) ? sha1(newView(t)) : 'MISSING']);
  out.othersDigestOld = pre.othersDigestOld;
  out.othersDigestNew = digest(newPairs);
  out.othersIdentical = out.othersDigestNew === out.othersDigestOld;
  out.othersMismatch = newPairs.filter(([n, h], i) => h !== othersPairsOld()[i][1]).map(([n]) => n);
  // Each re-frozen ref: old vs new sha1, and pixel identity with the A/B's new arm.
  out.refrozen = [...moved].map((t) => {
    const a = PNG.sync.read(fs.readFileSync(newView(t)));
    const b = PNG.sync.read(fs.readFileSync(path.join(TITAN, 'runs', 'wave52-calib', 'refs-ab', 'new', rel(t))));
    return { test: t, oldSha1: sha1(oldView(t)), newSha1: sha1(newView(t)), size: `${a.width}x${a.height}`,
      matchesAbNewArm: a.width === b.width && a.height === b.height && Buffer.compare(a.data, b.data) === 0 };
  });
}
fs.writeFileSync(path.join(HERE, `refreeze-apply.${mode}.json`), JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify({ ...out, othersMismatch: out.othersMismatch?.length, refrozen: out.refrozen?.map((r) => `${r.test} ${r.size} ab=${r.matchesAbNewArm}`), movedTests: undefined }, null, 1));

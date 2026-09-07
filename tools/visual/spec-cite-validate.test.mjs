#!/usr/bin/env node
//
// Pins for tools/visual/spec-cite-validate.mjs — the spec-citation gate
// (retro 2026-09-05, skeptic S6 → lane F4).
//
// What is protected, and why each pin exists:
//   1. the citation GRAMMAR (spec + § + section, "section N", same-line
//      continuations, bare § refs ignored, step numbers not consumed) — a
//      regex regression would silently stop checking a citation shape;
//   2. the vendored ToC (spec-sections.json) — shape, dates, and the exact
//      numbering decisions retro P2d took (ED, not TR): if someone rebuilds
//      from TR pages the border-image/opacity/transforms pins flip;
//   3. the CLI contract (exit 1 with file:line on a miss, exit 0 clean);
//   4. THE LIVE TREE: zero unknown sections. This is the standing gate — a
//      comment-only lane that types a TR number again turns this red.
//
// Proven able to fail (lane F4, executed): renumbering ONE citation in the
// tree (runtimes/compose/…/color/BackgroundTileMath.kt "css-backgrounds-3
// §2.6" → "§3.6") makes the live-tree test AND the CLI report exactly that
// file:line; the test-string pins below carry their own negative cases.
// The strings under test are built by concatenation so this file's own
// examples can never be mistaken for real citations (the scanner also skips
// its own basenames).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import {
  parseCitations, checkText, loadSections, siblingsOf, compareSections, parseTocHtml,
  listFiles, scanTree, SECTIONS_PATH, REPO_ROOT, IGNORE_TOKEN, IGNORE_BEGIN, IGNORE_END, SPEC_ALIASES,
} from './spec-cite-validate.mjs';

const SCRIPT = fileURLToPath(new URL('./spec-cite-validate.mjs', import.meta.url));
const S = '§'; // § — concatenated so no line here reads as a real citation
const cite = (spec, sec) => `${spec} ${S}${sec}`;

// ── 1. grammar ──────────────────────────────────────────────────────────────

test('parseCitations: spec + § + dotted section, case-insensitive spec name', () => {
  const got = parseCitations(`// tile origin (${cite('css-backgrounds-3', '2.6')}: position anchors the pattern)`);
  assert.deepEqual(got.map((c) => [c.spec, c.section]), [['css-backgrounds-3', '2.6']]);
  assert.deepEqual(parseCitations(`CSS-Color-4 ${S}3.3`).map((c) => c.spec), ['css-color-4']);
});

test('parseCitations: the "section N" and "Sec. N" word forms count too', () => {
  assert.deepEqual(parseCitations('per css-transforms-2 section 8 (perspective)').map((c) => [c.spec, c.section]), [['css-transforms-2', '8']]);
  assert.deepEqual(parseCitations('css-values-4 Sec. 6.1.1').map((c) => c.section), ['6.1.1']);
});

test('parseCitations: same-line continuations belong to the spec that opened them', () => {
  const line = `${cite('css-backgrounds-3', '3.2')} / ${S}3.3 / ${S}6.1 box-shadow, then ${cite('css-color-4', '3.3')}, ${S}6.4`;
  assert.deepEqual(parseCitations(line).map((c) => `${c.spec}:${c.section}`),
    ['css-backgrounds-3:3.2', 'css-backgrounds-3:3.3', 'css-backgrounds-3:6.1', 'css-color-4:3.3', 'css-color-4:6.4']);
});

test('parseCitations: a bare § with no spec name on the line is not attributed', () => {
  assert.deepEqual(parseCitations(`// the ${S}6.4 outset grows the area`), []);
  assert.deepEqual(parseCitations('nothing cited here'), []);
});

test('parseCitations: step numbers and possessives are not part of the section', () => {
  assert.deepEqual(parseCitations(`${cite('css-flexbox-1', '9.2')} step 3.E`).map((c) => c.section), ['9.2']);
  assert.deepEqual(parseCitations(`${cite('css-position-3', '3.5.3')}'s rectangle`).map((c) => c.section), ['3.5.3']);
});

test('parseCitations: tree spellings without an upstream ToC resolve through SPEC_ALIASES', () => {
  assert.equal(SPEC_ALIASES['css-selectors-4'], 'selectors-4');
  assert.deepEqual(parseCitations(cite('css-selectors-4', '9.3')).map((c) => c.spec), ['selectors-4']);
  assert.deepEqual(parseCitations(cite('css-gap-decorations-1', '3.2')).map((c) => c.spec), ['css-gaps-1']);
});

// ── 2. the vendored ToC ─────────────────────────────────────────────────────

test('spec-sections.json: well-formed, dated, numerically sorted, no duplicates', () => {
  const { meta, raw } = loadSections();
  assert.match(meta.fetched, /^\d{4}-\d{2}-\d{2}$/, '_meta.fetched must be an ISO date');
  assert.match(meta.regenerate, /--rebuild/, '_meta.regenerate must name the rebuild command');
  const specs = Object.entries(raw.specs);
  assert.ok(specs.length >= 60, `expected the 68-spec vendored set, got ${specs.length}`);
  assert.equal(meta.specCount, specs.length);
  for (const [spec, entry] of specs) {
    assert.match(spec, /^[a-z0-9-]+-\d+$/, `${spec}: not a spec shortname`);
    assert.equal(entry.url, `https://drafts.csswg.org/${spec}/`);
    assert.ok(entry.sections.length >= 5, `${spec}: suspiciously few sections (${entry.sections.length})`);
    for (const s of entry.sections) assert.match(s, /^\d+(\.\d+)*$/, `${spec}: bad section ${s}`);
    assert.equal(new Set(entry.sections).size, entry.sections.length, `${spec}: duplicate sections`);
    assert.deepEqual(entry.sections, [...entry.sections].sort(compareSections), `${spec}: not numerically sorted`);
  }
});

test('spec-sections.json: the ED numbering decisions P2d took are pinned (TR numbers would flip these)', () => {
  const { specs } = loadSections();
  const has = (spec, sec) => specs.get(spec).has(sec);
  // border-image is §5.x in the ED (§6.x in the 2017 CR) — S6's ×10 residue
  assert.ok(has('css-backgrounds-3', '5.3') && has('css-backgrounds-3', '5.4') && !has('css-backgrounds-3', '6.3'));
  // background-position / -size are §2.6 / §2.9 (TR-era §3.6 / §3.9 do not exist)
  assert.ok(has('css-backgrounds-3', '2.6') && has('css-backgrounds-3', '2.9') && !has('css-backgrounds-3', '3.6'));
  // opacity is css-color-4 §3.3, currentcolor §6.4, no §2.1
  assert.ok(has('css-color-4', '3.3') && has('css-color-4', '6.4') && !has('css-color-4', '2.1'));
  // css-transforms-1 ED: §3 transform, §8 function lists (TR: §10)
  assert.ok(has('css-transforms-1', '8') && has('css-transforms-1', '3'));
  // static position is css-position-3 §3.5.3; outline lives in css-ui-4 §3.x (§4 = resize)
  assert.ok(has('css-position-3', '3.5.3') && !has('css-position-3', '3.1.4.1'));
  assert.ok(has('css-ui-4', '3.4') && has('css-ui-4', '4.1') && !has('css-ui-4', '4.3'));
  // flexbox algorithm steps are not sections
  assert.ok(has('css-flexbox-1', '9.7') && !has('css-flexbox-1', '9.7.4'));
  // object-view-box moved to css-images-5 §3.1; calc-size() is css-values-5 §11; var() is css-variables-1 §3
  assert.ok(has('css-images-5', '3.1') && has('css-values-5', '11') && has('css-variables-1', '3') && !has('css-variables-1', '2.3'));
});

test('parseTocHtml: reads bikeshed secno spans once each, numeric only, sorted', () => {
  const html = '<span class="secno">2.10. </span><span class="content">Ten</span>'
    + '<span class="secno">2.9. </span><span class="content">Nine</span>'
    + '<span class="secno">2.9</span> body heading repeat <span class="secno">A. </span>appendix';
  assert.deepEqual(parseTocHtml(html), ['2.9', '2.10']);
});

test('siblingsOf: hints list the existing sections under the cited parent', () => {
  const set = new Set(['1', '2', '3', '3.1', '3.2', '3.3', '3.2.1']);
  assert.deepEqual(siblingsOf(set, '3.31'), { parent: '3', siblings: ['3.1', '3.2', '3.3'] });
  assert.deepEqual(siblingsOf(set, '7'), { parent: '', siblings: ['1', '2', '3'] });
});

// ── 3. checkText + CLI contract ─────────────────────────────────────────────

test('checkText: a known section validates, an unknown one is a hit with file:line', () => {
  const { specs } = loadSections();
  const text = `ok ${cite('css-backgrounds-3', '2.6')}\nbad ${cite('css-backgrounds-3', '3.6')}\nunknown spec ${cite('css-nonexistent-9', '1')}`;
  const r = checkText(text, specs, 'x.kt');
  assert.equal(r.validated, 2);
  assert.deepEqual(r.hits.map((h) => [h.file, h.line, h.spec, h.section]), [['x.kt', 2, 'css-backgrounds-3', '3.6']]);
  assert.deepEqual([...r.unknownSpecs], [['css-nonexistent-9', 1]]);
});

test('checkText: the ignore token and the begin/end block skip deliberate quotes', () => {
  const { specs } = loadSections();
  const bad = cite('css-backgrounds-3', '3.6');
  assert.equal(checkText(`${bad} ${IGNORE_TOKEN}`, specs).hits.length, 0);
  assert.equal(checkText(`${IGNORE_BEGIN}\n${bad}\n${bad}\n${IGNORE_END}\n${bad}`, specs).hits.length, 1, 'only the line after -end counts');
});

test('CLI: exit 1 naming file:line on a miss, exit 0 on a clean tree (temp root)', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'spec-cite-'));
  try {
    fs.mkdirSync(path.join(root, 'runtimes'));
    fs.writeFileSync(path.join(root, 'runtimes', 'Ok.kt'), `// ${cite('css-color-4', '3.3')}\n`);
    let r = spawnSync(process.execPath, [SCRIPT, '--root', root], { encoding: 'utf8' });
    assert.equal(r.status, 0, r.stdout + r.stderr);
    fs.writeFileSync(path.join(root, 'runtimes', 'Bad.swift'), `//\n// per ${cite('css-color-4', '2.1')} opacity\n`);
    r = spawnSync(process.execPath, [SCRIPT, '--root', root], { encoding: 'utf8' });
    assert.equal(r.status, 1);
    assert.match(r.stdout, /NO-SUCH-SECTION runtimes\/Bad\.swift:2 {2}css-color-4 §2\.1/);
    // fixtures are opt-in: a bad cite there is invisible without --include-data
    fs.mkdirSync(path.join(root, 'fixtures'));
    fs.writeFileSync(path.join(root, 'fixtures', 'f.json'), `{"_comment": "${cite('css-color-4', '2.1')}"}`);
    fs.unlinkSync(path.join(root, 'runtimes', 'Bad.swift'));
    assert.equal(spawnSync(process.execPath, [SCRIPT, '--root', root], { encoding: 'utf8' }).status, 0);
    assert.equal(spawnSync(process.execPath, [SCRIPT, '--root', root, '--include-data'], { encoding: 'utf8' }).status, 1);
    // unknown arguments are a usage error, never a silent pass
    assert.equal(spawnSync(process.execPath, [SCRIPT, '--root', root, '--bogus'], { encoding: 'utf8' }).status, 2);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('listFiles: never descends into node_modules / build / the WPT mirror, skips its own files', () => {
  const files = listFiles(REPO_ROOT);
  assert.ok(files.length > 1000, `expected thousands of files, got ${files.length}`);
  assert.ok(files.every((f) => !/(^|\/)(node_modules|build|\.build|wpt|runs)\//.test(f)), 'excluded directory leaked into the scan');
  assert.ok(files.every((f) => !/spec-cite-validate|spec-sections\.json/.test(path.basename(f))), 'the gate must not scan itself');
  assert.ok(files.some((f) => f.endsWith('.kt')) && files.some((f) => f.endsWith('.swift')) && files.some((f) => f.endsWith('.ts')), 'all four trees are in scope');
});

// ── 4. the live tree — the standing gate ────────────────────────────────────

test('LIVE TREE: every same-line spec citation names a section that exists in its ED ToC', () => {
  const report = scanTree(REPO_ROOT);
  assert.ok(report.validated > 3000, `only ${report.validated} citations validated — did the scan roots move?`);
  const lines = report.hits.map((h) => `${h.file}:${h.line}  ${h.spec} §${h.section}  (${h.parent ? '§' + h.parent : 'top level'} has: ${h.siblings.join(', ') || 'nothing'})`);
  assert.deepEqual(lines, [], `unknown spec sections in the tree — renumber against ${SECTIONS_PATH} (fetched ${report.meta.fetched}) or, for a deliberate quote, mark the line ${IGNORE_TOKEN}:\n` + lines.join('\n'));
});

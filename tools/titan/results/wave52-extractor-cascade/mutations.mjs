#!/usr/bin/env node
// wave-52 lane L5 (extractor-cascade) — the EXECUTED mutation proofs for the
// wave52-L5 pins in tools/titan/extract-fixture.test.mjs ("A new check must be
// proven able to fail"). Each mutation is ONE source edit of the PATCHED
// extractor (the six seam patches applied); the runner writes it, runs the
// whole extract-fixture suite, records which tests went red, restores the
// original bytes and checks the sha-256 is back to the starting value.
//
// It NEVER touches the shared working tree: point it at a copy whose
// tools/titan/extract-fixture.mjs carries the seam patches (the lane used a
// `git archive HEAD` copy of tools/titan with seam-1…6 applied) and whose
// extract-fixture.test.mjs is the lane's WITH seam-6's test hunk applied —
// since the fix pass, seam-6-FE-li.patch carries the F-E pin (M24/M25) into
// that file, so copy the tree's test file in BEFORE applying seam-1…6.
//
// Usage: node mutations.mjs <copy-root> <out.json>
// Exit 1 when the unmutated suite is not green, when a mutation's anchor is
// not found exactly once, when a mutation leaves any of its expected pins
// green, or when a restore does not reproduce the starting sha-256.
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const [ROOT, OUT] = process.argv.slice(2);
if (!ROOT || !OUT) { console.error('usage: mutations.mjs <copy-root> <out.json>'); process.exit(1); }
const SRC = join(ROOT, 'tools/titan/extract-fixture.mjs');
const TEST = 'tools/titan/extract-fixture.test.mjs';
const sha = (buf) => createHash('sha256').update(buf).digest('hex');

// Run the suite once; return the names of the failing tests (TAP `not ok`).
function failingTests() {
  const r = spawnSync(process.execPath, ['--test', '--test-reporter=tap', TEST], { cwd: ROOT, encoding: 'utf8' });
  return [...r.stdout.matchAll(/^not ok \d+ - (.*)$/gm)].map((m) => m[1].trim());
}

// id · what the edit models · exact anchor · replacement · pins that must go red
// (each `expect` entry is a substring of a test name).
const MUTATIONS = [
  ['M1', 'F3: no candidate filter', 'const pool = valid.length > 0 ? valid : win;', 'const pool = win;',
    ['F3: a provably invalid declaration in a LATER layer', 'F3: revert-layer recursion']],
  ['M2', 'F3: always the filtered pool (no all-invalid clause)', 'const pool = valid.length > 0 ? valid : win;', 'const pool = valid;',
    ['F3: an ALL-invalid window']],
  ['M3', 'F3: filter only at depth 0', 'const pool = valid.length > 0 ? valid : win;',
    'const pool = valid.length > 0 && depth === 0 ? valid : win;', ['F3: revert-layer recursion']],
  ['M4', 'B1 anchor: assignDeclaration writes unconditionally',
    'if (reason !== null && provablyInvalidDeclaration(key, prev) === null) {',
    'if (false && reason !== null && provablyInvalidDeclaration(key, prev) === null) {',
    ['F3 anchor: css-values/angle-units-001']],
  ['M5', 'F1: no importance refusal in collapseDeclaration',
    '  if (important[k] && !bang && provablyInvalidDeclaration(k, props[k]) === null) return;\n', '',
    ['F1: an important declaration is not displaced', 'F1: the style attribute obeys the same rule']],
  ['M6', 'F1: the old flag probe (nothing clears a stale flag)',
    '  if (bang) important[k] = true;\n  else delete important[k];', '  if (bang && props[k] === v) important[k] = true;',
    ['F1: the importance flag follows the write that was taken']],
  ['M7', 'F1: gate not widened to importance carriers',
    '    || matchedRules.some((r) => r.important !== undefined)\n    || Object.keys(inlineImportant).length > 0;', ';',
    ['F1: an important declaration in an EARLIER rule', 'F1: css-gaps/flex/flex-gap-decorations-024',
      'F1: css-cascade/revert-val-002', 'F1: the style attribute obeys the same rule', 'F1: a non-revert-layer `all`']],
  ['M8', 'F1: layered resolver drops every `all`', "    if (name === 'all' && !keepAll) continue;",
    "    if (name === 'all') continue;", ['F1: a non-revert-layer `all`']],
  ['M9', 'F1: an INVALID important value still refuses a later normal one',
    '  if (important[k] && !bang && provablyInvalidDeclaration(k, props[k]) === null) return;',
    '  if (important[k] && !bang) return;', ['F1: the importance flag follows the write that was taken']],
  ['M10', 'F-D: a pseudo-element counted under (b)',
    '        sp[2]++;                                                   // (c) pseudo-element',
    '        sp[1]++;                                                   // (c) pseudo-element',
    ['F-D: selectorSpecificity follows Selectors-4']],
  ['M11', 'F-D: unlayered host bucket merged unsorted',
    "    for (const r of bySpecificity(buckets[''])) assignDeclarations(merged, r.props);",
    "    for (const r of buckets['']) assignDeclarations(merged, r.props);",
    ['F-D: a more specific EARLIER rule', 'F-D: css-cascade/import-conditional-002']],
  ['M12', 'F-D: pseudo buckets merged unsorted', '      for (const r of bySpecificity(rs)) assignDeclarations(mergedPe, r.props);',
    '      for (const r of rs) assignDeclarations(mergedPe, r.props);', ['F-D: pseudo-element buckets and the layered path']],
  ['M13', 'F-D: no specificity step in the layered `better`', '    if (bySpec !== 0) return bySpec > 0;\n', '',
    ['F-D: pseudo-element buckets and the layered path']],
  ['M14', 'F-D: @scope rule stamped with the rewritten chain', 'specificity: selectorSpecificity(s) };',
    'specificity: selectorSpecificity(scoped) };', ["F-D: an @scope rule's specificity"]],
  ['M15', 'F-D: bag keys in merge order, not document order',
    "    copyInDocumentOrder(props, merged, [...buckets[''].map((r) => r.props), inlineProps]);",
    '    Object.assign(props, merged);', ['F-D: css-cascade/import-conditional-002']],
  ['M16', 'F2: parseCss back on split', '      for (const decl of splitDeclarations(body)) {',
    "      for (const decl of body.split(';')) {", ['F2: a `;` inside an unquoted url()']],
  ['M17', 'F2: no string tracking in the splitter',
    "    if (ch === '\"' || ch === \"'\") { quote = ch; continue; }\n    // §4.3.6:", '    // §4.3.6:',
    ['F2: strings, escapes and the QUOTED url form']],
  ['M18', 'F2: no escape branch in the splitter',
    "    if (ch === '\\\\') { i++; continue; }\n    if (quote) {\n      // §4.3.5", '    if (quote) {\n      // §4.3.5',
    ['F2: strings, escapes and the QUOTED url form']],
  ['M19', 'F2: no url-token branch in the splitter', "    if ((ch === 'u' || ch === 'U') && /^url\\(/i.test(s.slice(i, i + 4))",
    "    if (false && (ch === 'u' || ch === 'U') && /^url\\(/i.test(s.slice(i, i + 4))",
    ['F2: strings, escapes and the QUOTED url form']],
  ['M20', 'F2: @keyframes frames back on split', '    for (const decl of splitDeclarations(decls)) {',
    "    for (const decl of decls.split(';')) {", ['F2: the style attribute and @keyframes frames']],
  ['M21', 'F2: style attribute back on split', '    for (const decl of splitDeclarations(attrs.style)) {',
    "    for (const decl of attrs.style.split(';')) {", ['F2: the style attribute and @keyframes frames']],
  ['M22', 'F-C: 100 % treated as out of range', '      if (n < 0 || n > 100) return `${m[2]}%`;',
    '      if (n < 0 || n >= 100) return `${m[2]}%`;', ['F-C: an out-of-range color-mix() percentage']],
  ['M23', 'F-C: no R2', '  const pct = colorMixPercentOutOfRange(value);', '  const pct = null;',
    ['F-C: an out-of-range color-mix() percentage', 'F-C: css-color/color-mix-percents-02']],
  ['M24', 'F-E: `li` not exempt', "export const LIST_ITEM_TAGS = new Set(['li', 'summary']);",
    "export const LIST_ITEM_TAGS = new Set(['summary']);", ['F-E: a rule-less empty <li>']],
  ['M25', 'F-E: exemption widened to `div`', "export const LIST_ITEM_TAGS = new Set(['li', 'summary']);",
    "export const LIST_ITEM_TAGS = new Set(['li', 'summary', 'div']);",
    ['A-RC1 part 2: a rule-less LEAF <div> still IS a placeholder', 'F-E: a rule-less empty <li>']],
];

const original = readFileSync(SRC);
const startSha = sha(original);
const baseline = failingTests();
const report = { source: SRC, startSha, baselineFailures: baseline, mutations: [] };
let ok = baseline.length === 0;
for (const [id, what, find, replace, expect] of MUTATIONS) {
  const text = original.toString('utf8');
  const hits = text.split(find).length - 1;
  const row = { id, what, find, replace, anchorHits: hits };
  if (hits !== 1) { row.error = 'anchor not found exactly once'; ok = false; report.mutations.push(row); continue; }
  writeFileSync(SRC, text.replace(find, replace));
  try {
    row.failed = failingTests();
  } finally {
    // Byte-exact restore, then prove it.
    writeFileSync(SRC, original);
  }
  row.restoredSha = sha(readFileSync(SRC));
  row.restoreOk = row.restoredSha === startSha;
  row.expected = expect;
  row.expectedAllRed = expect.every((e) => row.failed.some((f) => f.includes(e)));
  if (!row.restoreOk || !row.expectedAllRed) ok = false;
  report.mutations.push(row);
  console.log(`${id} ${row.expectedAllRed ? 'RED as expected' : 'NOT RED'} (${row.failed.length} failing) restore ${row.restoreOk ? 'ok' : 'MISMATCH'} — ${what}`);
}
report.allProven = ok;
writeFileSync(OUT, JSON.stringify(report, null, 1) + '\n');
console.log(`baseline failures ${baseline.length}; all proven: ${ok}`);
process.exit(ok ? 0 : 1);

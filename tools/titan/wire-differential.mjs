#!/usr/bin/env node
// tools/titan/wire-differential.mjs — the CLASSIFIER of tools/titan/wire-differential.sh (wave 55 L7 U2, BACKLOG 0(m)(2)).
//
// Why it exists: the wave-54 [W-L3] differential (results/wave54-hyphenate-character/wl3-wire-differential.sh) could not
// fail. It ended in a node block that only printed (exit 0 whatever it counted), walked the sections of the PRE side only,
// and called a document RENUMBERED once the `-<n>` id suffix was stripped — weaker than the wave-53 rule it claimed to
// apply. This classifier decides, and its exit code is the verdict:
//   IDENTICAL            the two per-test IR documents are byte-equal;
//   RENUMBERED           equal once the id counter is stripped from `id` / `slot.parent`, AND every id moved by ONE amount
//                        equal to the running component-count delta of the changed documents before it in tests.list
//                        order (the wave-53 rule, wave54-plan/expectations.json wireRenumbering.rule): the converter
//                        numbers the components of a whole section in one sequence, so a document that gains k
//                        components shifts every later document of the section by exactly k;
//   RENUMBERED-ANOMALOUS strip-equal but not that uniform shift — a leak, never a renumbering;
//   CONTENT-CHANGED      anything else;
//   ONE-SIDED            a document, a section, or a section's per-test-ir present on one side only.
// Exit (highest wins): 0 nothing below · 3 pipeline (a combine / convert / split rc != 0, extract rc or FAIL sets that
// differ between the sides, an unreadable document, nothing compared) · 4 ONE-SIDED · 5 the changed set differs from the
// expect file (UNEXPECTED / MISSING) or any RENUMBERED-ANOMALOUS · 2 usage (returned at once).
//
// Usage: wire-differential.mjs classify --pre <dir> --post <dir> --expect <file> --tests-root <run>/sections
//          (--status <status.tsv> | --no-status) [--sections a,b]
//   <dir>/<section>/per-test-ir/*.json on each side (the layout wire-differential.sh writes, and the parked wave-54 trees).
//   Expect file: lines `CONTENT-CHANGED <sec>/<file>` / `RENUMBERED <sec>/<file>`, `#` comments; empty = "no change".
import fs from 'node:fs';                                   // synchronous reads: a few thousand small JSON files
import path from 'node:path';                               // section / document paths
import { fileURLToPath } from 'node:url';                   // to know whether this module is the entry point
import { fixtureStem, safe } from './safe-name.mjs';        // THE stem rule split-combined-ir.mjs names documents by

// The exit codes, one place (the .sh and the tests read the same numbers).
export const EXIT = { OK: 0, USAGE: 2, PIPELINE: 3, ONE_SIDED: 4, UNEXPECTED: 5 };
// The wave-54 suffix strip, verbatim (wl3-wire-differential.sh:48): only `id` and `slot.parent` carry the counter —
// `{"child": <key>}` run entries name the authoring key, never the id (schema/spec/03-children.md §4.1).
export const strip = (s) => s.replace(/("(?:id|parent)":\s*"[^"]*?)-\d+"/g, '$1"');
// Every counter-carrying string of a document, in document order (each component's id, then its slot.parent).
const idsOf = (doc) => (doc.components ?? []).flatMap((c) => [c.id, c.slot?.parent].filter((v) => typeof v === 'string'));
// The numeric counter at the end of an id (`…-657` → 657); NaN when there is none, which can never be a uniform shift.
const counter = (s) => { const m = /-(\d+)$/.exec(s); return m ? Number(m[1]) : NaN; };
// The per-test-ir file of a tests.list entry: split-combined-ir writes `safe(wpt__<section>__<stem>).json`.
export const docName = (test) => `${safe(`wpt__${String(test).split('/')[1]}__${fixtureStem(test)}`)}.json`;
// A directory listing that is empty (not an exception) when the directory is absent: absence is classified, not thrown.
const ls = (d) => (fs.existsSync(d) ? fs.readdirSync(d) : []);
// Directories only (a pre/ side also holds status files in other layouts).
const dirs = (d) => ls(d).filter((e) => fs.statSync(path.join(d, e)).isDirectory());

// The expect file → Map('<sec>/<file>' → class). An unparseable line is a usage error: a typo must never read as "no change".
export function readExpect(file) {
  const want = new Map();                                                     // key → expected class
  for (const [i, raw] of fs.readFileSync(file, 'utf8').split('\n').entries()) {
    const line = raw.replace(/#.*$/, '').trim();                              // comments and blank lines carry nothing
    if (!line) continue;                                                      // (an empty file = no change expected)
    const m = /^(CONTENT-CHANGED|RENUMBERED) ([^\s/]+\/\S+\.json)$/.exec(line);   // one class, one <sec>/<file>
    if (!m) throw new Error(`expect file ${file}:${i + 1}: cannot read "${raw.trim()}"`);
    want.set(m[2], m[1]);                                                     // the last line for a key wins (none repeat)
  }
  return want;
}

// The pipeline status (status.tsv + fail-<tag>-<sec>.txt beside it) → problems (exit 3) and notes, per section in scope.
export function readStatus(tsv, sections) {
  const problems = [], notes = [];                                             // problems set exit 3; notes are printed only
  if (!fs.existsSync(tsv)) return { problems: [`no status file ${tsv} (the pipeline did not run, or wrote elsewhere)`], notes };
  const rows = new Map();                                                      // '<tag> <sec>' → its row
  for (const line of fs.readFileSync(tsv, 'utf8').split('\n')) {
    if (!line.trim() || line.startsWith('#')) continue;                        // the header comment
    const [tag, sec, rx, rb, rv, rs, ext, fail] = line.trim().split(/\s+/);    // the row wire-differential.sh writes
    rows.set(`${tag} ${sec}`, { rx, rb, rv, rs, ext: Number(String(ext).split('=')[1]), fail });
  }
  const fails = (tag, sec) => {                                                // the extract FAIL lines → the failed tests
    const f = path.join(path.dirname(tsv), `fail-${tag}-${sec}.txt`);
    return new Set(fs.existsSync(f)                                            // no fail file = no FAIL line on that side
      ? fs.readFileSync(f, 'utf8').split('\n').filter(Boolean).map((l) => l.replace(/^FAIL\s+/, '').replace(/:.*$/, '')) : []);
  };
  for (const sec of sections) {
    const a = rows.get(`pre ${sec}`), b = rows.get(`post ${sec}`);             // both sides must have run the section
    if (!a || !b) { problems.push(`${sec}: no status row for ${!a ? 'pre' : 'post'}`); continue; }
    for (const [tag, r] of [['pre', a], ['post', b]]) {
      for (const [step, rc] of [['combine', r.rb], ['convert', r.rv], ['split', r.rs]]) {   // the gate's steps 3-5
        if (rc !== '0') problems.push(`${sec}: ${tag} ${step} rc ${rc}`);                    // '-' = never ran = a failure
      }
      if (!(r.ext > 0)) problems.push(`${sec}: ${tag} extracted ${r.ext} documents (nothing to compare)`);
    }
    if (a.rx !== b.rx) problems.push(`${sec}: extract rc differs (pre ${a.rx}, post ${b.rx})`);   // a symmetric rc is a NOTE
    const fa = fails('pre', sec), fb = fails('post', sec);                     // FAIL sets: only a DIFFERENCE is a failure
    for (const t of fa) if (!fb.has(t)) problems.push(`${sec}: extract FAIL only on pre: ${t}`);
    for (const t of fb) if (!fa.has(t)) problems.push(`${sec}: extract FAIL only on post: ${t}`);
    for (const t of fa) if (fb.has(t)) notes.push(`NOTE ${sec}: symmetric extract FAIL ${t} (both sides; accepted)`);
  }
  return { problems, notes };
}

// One section, in tests.list order: the classes, and the running delta the RENUMBERED rule needs.
function classifySection(sec, pre, post, testsRoot, out) {
  const A = path.join(pre, sec, 'per-test-ir'), B = path.join(post, sec, 'per-test-ir');   // the two document dirs
  const onA = fs.existsSync(path.join(pre, sec)), onB = fs.existsSync(path.join(post, sec));
  if (!onA || !onB) return out.oneSided.push(`${sec} (section only on ${onA ? 'pre' : 'post'})`);
  if (!fs.existsSync(A) || !fs.existsSync(B)) {                                 // the wave-54 mis-invocation shape
    return out.oneSided.push(`${sec} (no per-test-ir on ${!fs.existsSync(A) && !fs.existsSync(B) ? 'either side' : !fs.existsSync(A) ? 'pre' : 'post'})`);
  }
  const list = path.join(testsRoot, sec, 'tests.list');                        // the order the converter numbered in
  if (!fs.existsSync(list)) throw Object.assign(new Error(`no ${list}: cannot order ${sec}`), { usage: true });
  const order = fs.readFileSync(list, 'utf8').split('\n').filter(Boolean).map(docName);
  const extra = [...new Set([...ls(A), ...ls(B)])].filter((f) => f.endsWith('.json') && !order.includes(f)).sort();
  for (const f of extra) out.notes.push(`NOTE ${sec}/${f} is not in tests.list (classified after it)`);
  let delta = 0;                                                                // Σ component-count change so far
  const count = (d) => (d ? (d.components ?? []).length : 0);                   // a missing side counts 0 components
  for (const f of [...order, ...extra]) {
    const key = `${sec}/${f}`, pa = path.join(A, f), pb = path.join(B, f);
    const x = fs.existsSync(pa) ? fs.readFileSync(pa, 'utf8') : null, y = fs.existsSync(pb) ? fs.readFileSync(pb, 'utf8') : null;
    if (x === null && y === null) { out.notes.push(`NOTE ${key} absent on both sides`); continue; }
    out.compared++;                                                             // the vacuity guard counts these
    let X = null, Y = null;                                                     // parsed documents (null = absent)
    try { X = x === null ? null : JSON.parse(x); Y = y === null ? null : JSON.parse(y); }
    catch (e) { out.pipeline.push(`${key}: unreadable per-test IR (${e.message.slice(0, 80)})`); continue; }
    if (x === null || y === null) { out.oneSided.push(`${key} (${x === null ? 'post' : 'pre'} only)`); delta += count(Y) - count(X); continue; }
    if (x === y) { out.identical++; continue; }                                 // byte-equal
    if (strip(x) === strip(y)) {                                                // same document but for the counter
      const ia = idsOf(X), ib = idsOf(Y);                                       // strip-equal ⇒ the same id positions
      const shifts = [...new Set(ia.map((s, i) => counter(ib[i]) - counter(s)))];
      if (shifts.length === 1 && shifts[0] === delta) out.renumbered.push(`${key} (shift ${delta >= 0 ? '+' : ''}${delta} = the running delta)`);
      else out.anomalous.push(`${key} (shifts {${shifts.join(', ')}} vs running delta ${delta})`);
      continue;                                                                 // a renumbering adds no components
    }
    out.changed.push(`${key} (components ${count(X)} → ${count(Y)})`);           // content: it moves the counter for the rest
    delta += count(Y) - count(X);
  }
}

// The whole read: { lines, exit, counts }.
export function classify({ pre, post, expect, testsRoot, status, sections }) {
  const out = { identical: 0, compared: 0, changed: [], renumbered: [], anomalous: [], oneSided: [], pipeline: [], notes: [] };
  const want = readExpect(expect);                                              // throws (usage) on a bad line
  const scope = sections?.length ? sections : [...new Set([...dirs(pre), ...dirs(post)])].sort();   // the UNION of both sides
  for (const sec of scope) {
    if (!fs.existsSync(path.join(pre, sec)) && !fs.existsSync(path.join(post, sec))) { out.pipeline.push(`${sec}: requested, produced on neither side`); continue; }
    classifySection(sec, pre, post, testsRoot, out);
  }
  if (out.compared === 0 && out.oneSided.length === 0) out.pipeline.push('nothing compared (no per-test IR document on either side)');
  const st = status === null ? { problems: [], notes: ['pipeline status NOT READ (--no-status: classify-only calibration)'] } : readStatus(status, scope);
  const key = (s) => s.split(' ')[0];                                           // '<sec>/<file> (detail)' → '<sec>/<file>'
  const seen = new Map([...out.changed.map((s) => [key(s), 'CONTENT-CHANGED']), ...out.renumbered.map((s) => [key(s), 'RENUMBERED'])]);
  const unexpected = [...seen].filter(([k, c]) => want.get(k) !== c).map(([k, c]) => `UNEXPECTED ${c} ${k}`);
  const unread = (k) => out.oneSided.some((s) => key(s) === k || key(s) === k.split('/')[0]);   // its doc / section is one-sided
  const missing = [...want].filter(([k, c]) => seen.get(k) !== c && !unread(k)).map(([k, c]) => `MISSING ${c} ${k} (expected, not observed)`);
  const notRead = [...want].filter(([k, c]) => seen.get(k) !== c && unread(k)).map(([k, c]) => `UNREAD ${c} ${k} (one-sided: exit 4 already)`);
  const lines = [...out.changed.map((s) => `CONTENT-CHANGED ${s}`), ...out.renumbered.map((s) => `RENUMBERED ${s}`),
    ...out.anomalous.map((s) => `RENUMBERED-ANOMALOUS ${s}`), ...out.oneSided.map((s) => `ONE-SIDED ${s}`),
    ...out.notes, ...st.notes, ...[...out.pipeline, ...st.problems].map((s) => `PIPELINE ${s}`), ...unexpected, ...missing, ...notRead];
  let exit = EXIT.OK;                                                           // highest wins
  if (out.pipeline.length || st.problems.length) exit = EXIT.PIPELINE;
  if (out.oneSided.length) exit = EXIT.ONE_SIDED;
  if (unexpected.length || missing.length || out.anomalous.length) exit = EXIT.UNEXPECTED;
  lines.push(`identical ${out.identical} · content-changed ${out.changed.length} · renumbered ${out.renumbered.length} · one-sided ${out.oneSided.length} · renumbered-anomalous ${out.anomalous.length}`);
  lines.push(`verdict: exit ${exit} — ${['nothing unexpected', '', '', 'PIPELINE failure', 'ONE-SIDED', 'changed set differs from the expect file'][exit]}`);
  return { lines, exit, counts: { identical: out.identical, changed: out.changed.length, renumbered: out.renumbered.length, oneSided: out.oneSided.length, anomalous: out.anomalous.length } };
}

// The CLI: parse, classify, print, exit with the verdict. Every usage problem is exit 2, never a silent default.
function main(argv) {
  const usage = (why) => { console.error(`usage: wire-differential.mjs classify --pre <dir> --post <dir> --expect <file> --tests-root <dir> (--status <tsv> | --no-status) [--sections a,b]\n  REFUSED — ${why}`); return EXIT.USAGE; };
  if (argv[0] !== 'classify') return usage('the first argument must be the subcommand `classify`');
  const o = { status: undefined };                                             // undefined = neither status flag given
  for (let i = 1; i < argv.length; i++) {
    const a = argv[i], v = argv[i + 1];                                        // every flag but --no-status takes a value
    if (a === '--no-status') { o.status = null; continue; }
    const k = { '--pre': 'pre', '--post': 'post', '--expect': 'expect', '--tests-root': 'testsRoot', '--status': 'status', '--sections': 'sections' }[a];
    if (!k) return usage(`unknown argument ${a}`);
    if (v === undefined || v.startsWith('--')) return usage(`${a} needs a value`);
    o[k] = k === 'sections' ? v.split(',').filter(Boolean) : path.resolve(v); i++;   // paths absolute before anything reads them
  }
  for (const k of ['pre', 'post', 'expect', 'testsRoot']) if (!o[k]) return usage(`--${k === 'testsRoot' ? 'tests-root' : k} is required`);
  if (o.status === undefined) return usage('give --status <status.tsv> (a pipeline run) or --no-status (classify-only calibration)');
  for (const k of ['pre', 'post', 'testsRoot']) if (!fs.existsSync(o[k])) return usage(`${o[k]} does not exist`);
  if (!fs.existsSync(o.expect)) return usage(`expect file ${o.expect} does not exist (an EMPTY file means "no change")`);
  try { const r = classify(o); console.log(r.lines.join('\n')); return r.exit; }
  catch (e) { return usage(e.message); }                                       // a bad expect line, a missing tests.list
}

// Run only as the entry point (the test imports the functions without executing the CLI).
if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) process.exitCode = main(process.argv.slice(2));

// tools/titan/wire-differential.test.mjs — pins P1–P6 of tools/titan/wire-differential.{sh,mjs} (wave 55 L7 U2).
// Synthetic per-test IR trees under os.tmpdir(): no Chromium, no gradle, no device. Each pin names the wave-54 defect it
// guards (instruments.md §2.2): (a) no verdict, (b) a relative out dir, (c) a swallowed pipeline failure, (d) sections
// read from PRE only, (e) a RENUMBERED class weaker than the wave-53 rule; (f) a symmetric extract FAIL is accepted.
import test, { after } from 'node:test';                      // the tooling suite's runner (node --test)
import assert from 'node:assert/strict';                      // strict equality: an exit code is a number, not "truthy"
import fs from 'node:fs';                                     // the synthetic trees
import os from 'node:os';                                     // their temp root
import path from 'node:path';                                 // path joins
import { spawnSync } from 'node:child_process';               // the CLI is the contract: run it, read its exit code
import { fileURLToPath } from 'node:url';                     // this file's directory

const HERE = path.dirname(fileURLToPath(import.meta.url));    // tools/titan
const MJS = path.join(HERE, 'wire-differential.mjs');         // the classifier under test
const SH = path.join(HERE, 'wire-differential.sh');           // the pipeline wrapper under test
const TMP = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'wire-diff-')));   // physical (macOS /var → /private/var)
after(() => fs.rmSync(TMP, { recursive: true, force: true }));   // leave nothing behind

const TESTS = ['css/s1/a.html', 'css/s1/b.html', 'css/s1/c.html'];   // one section, tests.list order a → b → c
const FILE = (t) => `wpt__s1__${t}.json`;                     // split-combined-ir's name for css/s1/<t>.html
// A document of k components numbered from n (the converter's section-wide counter): a root and k-1 children of it.
const doc = (stem, n, k, prop = 'x') => ({ irVersion: 2, minReaderVersion: 2, components: Array.from({ length: k }, (_, i) => ({
  id: `${stem}__${i}-${n + i}`, name: `${stem}__${i}`, properties: [{ type: 'P', data: prop }],
  ...(i ? { slot: { parent: `${stem}__0-${n}` } } : {}) })) });
const BASE = { a: doc('a', 0, 3), b: doc('b', 3, 2), c: doc('c', 5, 2) };   // a 0-2, b 3-4, c 5-6
const write = (f, s) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, s); };
const ROW = (tag, sec, rc = '0\t0\t0\t0') => `${tag}\t${sec}\t${rc}\textracted=3\tfail=0\n`;   // a status.tsv row

let n = 0;                                                    // one directory per scenario
// Build a scenario and run the classifier → { code, out }.
function classify({ pre = BASE, post = BASE, expect = '', rows, fails = {}, extraPostSection = false, status = true }) {
  const R = path.join(TMP, `case${++n}`);
  write(path.join(R, 'run/sections/s1/tests.list'), TESTS.join('\n') + '\n');
  for (const [tag, docs] of [['pre', pre], ['post', post]]) {
    fs.mkdirSync(path.join(R, 'out', tag, 's1', 'per-test-ir'), { recursive: true });   // an empty section is still a section
    for (const [t, d] of Object.entries(docs)) write(path.join(R, 'out', tag, 's1', 'per-test-ir', FILE(t)), JSON.stringify(d) + '\n');
  }
  if (extraPostSection) write(path.join(R, 'out/post/s2/per-test-ir', 'wpt__s2__z.json'), JSON.stringify(doc('z', 0, 1)) + '\n');
  write(path.join(R, 'out/status.tsv'), rows ?? (ROW('pre', 's1') + ROW('post', 's1')));
  for (const [tag, lines] of Object.entries(fails)) write(path.join(R, 'out', `fail-${tag}-s1.txt`), lines.join('\n') + '\n');
  write(path.join(R, 'expect.txt'), expect);
  const r = spawnSync(process.execPath, [MJS, 'classify', '--pre', path.join(R, 'out/pre'), '--post', path.join(R, 'out/post'),
    '--expect', path.join(R, 'expect.txt'), '--tests-root', path.join(R, 'run/sections'),
    ...(status ? ['--status', path.join(R, 'out/status.tsv')] : ['--no-status'])], { encoding: 'utf8' });
  return { code: r.status, out: r.stdout + r.stderr };
}
const changed = (d) => ({ ...d, components: d.components.map((c, i) => (i ? c : { ...c, properties: [{ type: 'P', data: 'y' }] })) });

test('P1 identical trees with an empty expect file → exit 0', () => {
  const r = classify({});
  assert.equal(r.code, 0, r.out);
  assert.match(r.out, /identical 3 · content-changed 0 · renumbered 0 · one-sided 0/);
});

test('P2 one content change: expected → 0; unexpected → 5 UNEXPECTED; expected but absent → 5 MISSING (defect a)', () => {
  const post = { ...BASE, b: changed(BASE.b) };              // same component count: no renumbering after it
  assert.equal(classify({ post, expect: `CONTENT-CHANGED s1/${FILE('b')}\n` }).code, 0);
  const u = classify({ post });
  assert.equal(u.code, 5, u.out);
  assert.match(u.out, /UNEXPECTED CONTENT-CHANGED s1\/wpt__s1__b\.json/);
  const m = classify({ expect: `# a comment\nCONTENT-CHANGED s1/${FILE('b')}\n` });
  assert.equal(m.code, 5, m.out);
  assert.match(m.out, /MISSING CONTENT-CHANGED s1\/wpt__s1__b\.json/);
});

test('P3 a uniform +2 shift after a +2 content change is RENUMBERED; a non-uniform or wrong shift is ANOMALOUS (defect e)', () => {
  const expect = `CONTENT-CHANGED s1/${FILE('a')}\nRENUMBERED s1/${FILE('b')}\nRENUMBERED s1/${FILE('c')}\n`;
  const grown = doc('a', 0, 5);                              // a gains 2 components: b and c move by +2
  const ok = classify({ post: { a: grown, b: doc('b', 5, 2), c: doc('c', 7, 2) }, expect });
  assert.equal(ok.code, 0, ok.out);
  assert.match(ok.out, /RENUMBERED s1\/wpt__s1__b\.json \(shift \+2 = the running delta\)/);
  const skew = doc('b', 5, 2); skew.components[1].id = 'b__1-7';   // +2 on the root, +3 on the child: strip-equal, not uniform
  const bad = classify({ post: { a: grown, b: skew, c: doc('c', 7, 2) }, expect });
  assert.equal(bad.code, 5, bad.out);
  assert.match(bad.out, /RENUMBERED-ANOMALOUS s1\/wpt__s1__b\.json \(shifts \{2, 3\} vs running delta 2\)/);
  const wrong = classify({ post: { a: grown, b: doc('b', 4, 2), c: doc('c', 7, 2) }, expect });   // uniform +1, delta +2
  assert.equal(wrong.code, 5, wrong.out);
  assert.match(wrong.out, /RENUMBERED-ANOMALOUS s1\/wpt__s1__b\.json \(shifts \{1\} vs running delta 2\)/);
});

test('P4 a one-sided document → 4; a section present only on POST → 4 (defect d)', () => {
  const d = classify({ post: { a: BASE.a, b: BASE.b } });
  assert.equal(d.code, 4, d.out);
  assert.match(d.out, /ONE-SIDED s1\/wpt__s1__c\.json \(pre only\)/);
  const s = classify({ extraPostSection: true, rows: ROW('pre', 's1') + ROW('post', 's1') + ROW('pre', 's2') + ROW('post', 's2') });
  assert.equal(s.code, 4, s.out);
  assert.match(s.out, /ONE-SIDED s2 \(section only on post\)/);
});

test('P5 rc_convert=1 → 3; an asymmetric extract FAIL → 3; a symmetric FAIL → 0 + NOTE (defects c, f)', () => {
  const rc = classify({ rows: ROW('pre', 's1') + ROW('post', 's1', '0\t0\t1\t-') });
  assert.equal(rc.code, 3, rc.out);
  assert.match(rc.out, /PIPELINE s1: post convert rc 1/);
  const asym = classify({ fails: { post: ['FAIL css/s1/b.html: boom'] } });
  assert.equal(asym.code, 3, asym.out);
  assert.match(asym.out, /PIPELINE s1: extract FAIL only on post: css\/s1\/b\.html/);
  const sym = classify({ fails: { pre: ['FAIL css/s1/b.html: boom'], post: ['FAIL css/s1/b.html: boom'] } });
  assert.equal(sym.code, 0, sym.out);
  assert.match(sym.out, /NOTE s1: symmetric extract FAIL css\/s1\/b\.html \(both sides; accepted\)/);
});

test('P5b the wave-54 mis-invocation shape (no per-test-ir on either side) → 4; nothing at all → 3; --no-status is printed', () => {
  const R = path.join(TMP, 'mis'); for (const t of ['pre', 'post']) write(path.join(R, t, 's1', 'extract.log'), '');
  write(path.join(R, 'run/sections/s1/tests.list'), TESTS.join('\n')); write(path.join(R, 'e.txt'), '');
  const args = (p, q) => [MJS, 'classify', '--pre', p, '--post', q, '--expect', path.join(R, 'e.txt'), '--tests-root', path.join(R, 'run/sections'), '--no-status'];
  const mis = spawnSync(process.execPath, args(path.join(R, 'pre'), path.join(R, 'post')), { encoding: 'utf8' });
  assert.equal(mis.status, 4, mis.stdout);
  assert.match(mis.stdout, /ONE-SIDED s1 \(no per-test-ir on either side\)/);
  assert.match(mis.stdout, /pipeline status NOT READ/);
  fs.mkdirSync(path.join(R, 'empty-a')); fs.mkdirSync(path.join(R, 'empty-b'));
  const none = spawnSync(process.execPath, args(path.join(R, 'empty-a'), path.join(R, 'empty-b')), { encoding: 'utf8' });
  assert.equal(none.status, 3, none.stdout);                  // a vacuous read is a pipeline failure, never "identical"
  assert.equal(classify({ status: false }).code, 0);          // calibration mode still decides
  assert.equal(classify({ expect: 'CHANGED s1/x.json\n' }).code, 2);   // a typo in the expect file is a usage error
});

// The .sh refusals and its path resolution (defect b), dry-run only: nothing is extracted or converted.
const tree = (name) => { const t = path.join(TMP, name); for (const p of ['node_modules', 'tools/wpt']) fs.mkdirSync(path.join(t, p), { recursive: true });
  write(path.join(t, 'tools/titan/extract-fixture.mjs'), ''); write(path.join(t, 'tools/titan/wpt-buckets.json'), '{}'); return t; };
const sh = (args, cwd) => spawnSync('bash', [SH, ...args], { encoding: 'utf8', cwd });
const RUN = path.join(TMP, 'shrun'); write(path.join(RUN, 'sections/s1/tests.list'), TESTS.join('\n')); write(path.join(TMP, 'sh-expect.txt'), '');

test('P6 --dry-run from another cwd with a relative OUT prints an absolute OUT equal to its realpath, and creates nothing', () => {
  const cwd = path.join(TMP, 'elsewhere'); fs.mkdirSync(cwd);
  const r = sh([tree('tA'), tree('tB'), 'rel/out', '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN, '--dry-run'], cwd);
  assert.equal(r.status, 0, r.stdout + r.stderr);
  assert.match(r.stdout, new RegExp(`^  OUT     ${path.join(fs.realpathSync(cwd), 'rel/out').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'm'));
  assert.equal(fs.existsSync(path.join(cwd, 'rel')), false);  // the dry run wrote nothing
});

test('P6 PRE == POST → 2; PRE = this script\'s own checkout → 2', () => {
  const same = sh([tree('tC'), tree('tC'), path.join(TMP, 'o1'), '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN, '--dry-run']);
  assert.equal(same.status, 2, same.stdout + same.stderr);
  assert.match(same.stderr, /PRE == POST/);
  const own = sh([path.resolve(HERE, '../..'), tree('tD'), path.join(TMP, 'o2'), '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN, '--dry-run']);
  assert.equal(own.status, 2, own.stdout + own.stderr);
  assert.match(own.stderr, /this script's own checkout/);
});

// The run path itself (the four steps, status.tsv, the classifier call), on trees whose four tools are STUBS: the real
// extraction needs Chromium and the converter, so the first real run is the orchestrator's [W-L7a] window.
const stubTree = (name, failConvert) => {
  const t = tree(name);                                       // node_modules + tools/wpt + an extract stub path
  write(path.join(t, 'tools/titan/extract-fixture.mjs'), "for (const t of process.argv.slice(2)) console.log('extracted ' + t);\n");
  write(path.join(t, 'tools/titan/build-combined-fixture.mjs'), "import fs from 'node:fs'; const a = process.argv; fs.mkdirSync(a[a.indexOf('--out') + 1].replace(/[^/]*$/, ''), { recursive: true }); fs.writeFileSync(a[a.indexOf('--out') + 1], '{}');\n");
  write(path.join(t, 'gradlew'), `#!/bin/sh\no=$(printf '%s' "$*" | sed -n 's/.* -o \\([^ ]*\\).*/\\1/p'); mkdir -p "$o"; echo '{}' > "$o/tmpOutput.json"; exit ${failConvert ? 1 : 0}\n`);
  fs.chmodSync(path.join(t, 'gradlew'), 0o755);
  write(path.join(t, 'tools/titan/split-combined-ir.mjs'), `import fs from 'node:fs'; const o = process.argv[process.argv.indexOf('--out') + 1]; fs.mkdirSync(o, { recursive: true });
for (const [k, d] of Object.entries(${JSON.stringify(BASE)})) fs.writeFileSync(o + '/wpt__s1__' + k + '.json', JSON.stringify(d) + '\\n');\n`);
  return t;
};

test('P7 the run path: stub steps on both trees → status.tsv rows and exit 0; a failing convert on POST is never a WARN', () => {
  const ok = sh([stubTree('sA'), stubTree('sB'), path.join(TMP, 'run-ok'), '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN]);
  assert.equal(ok.status, 0, ok.stdout + ok.stderr);
  assert.match(ok.stdout, /^post\ts1\t0\t0\t0\t0\textracted=3\tfail=0$/m);
  assert.match(ok.stdout, /identical 3 · content-changed 0 · renumbered 0 · one-sided 0/);
  const bad = sh([stubTree('sC'), stubTree('sD', true), path.join(TMP, 'run-bad'), '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN]);
  assert.equal(bad.status, 4, bad.stdout + bad.stderr);       // POST has no per-test-ir: ONE-SIDED (4) outranks PIPELINE (3)
  assert.match(bad.stdout, /PIPELINE s1: post convert rc 1/);  // … and the cause is printed (the wave-54 script printed WARN, exit 0)
  assert.match(bad.stdout, /ONE-SIDED s1 \(no per-test-ir on post\)/);
  const again = sh([stubTree('sE'), stubTree('sF'), path.join(TMP, 'run-ok'), '--expect', path.join(TMP, 'sh-expect.txt'), '--run', RUN]);
  assert.equal(again.status, 2, again.stderr);                // a previous differential is never overwritten
  // [W-L7a] step 3's shape: re-classify the same out dir with a change PRE-REGISTERED that did not happen → 5,
  // written beside (never over) the run's own classify.txt.
  write(path.join(TMP, 'w3-expect.txt'), `CONTENT-CHANGED s1/${FILE('b')}\n`);
  const before = fs.readFileSync(path.join(TMP, 'run-ok/classify.txt'), 'utf8');
  const re = sh([path.join(TMP, 'sA'), path.join(TMP, 'sB'), path.join(TMP, 'run-ok'), '--expect', path.join(TMP, 'w3-expect.txt'), '--run', RUN, '--classify-only']);
  assert.equal(re.status, 5, re.stdout + re.stderr);
  assert.match(re.stdout, /MISSING CONTENT-CHANGED s1\/wpt__s1__b\.json/);
  assert.equal(fs.readFileSync(path.join(TMP, 'run-ok/classify.txt'), 'utf8'), before);
  assert.ok(fs.existsSync(path.join(TMP, 'run-ok/classify-only.w3-expect.txt.txt')));
});

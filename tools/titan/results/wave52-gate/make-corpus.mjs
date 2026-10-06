#!/usr/bin/env node
// tools/titan/results/wave52-gate/make-corpus.mjs
//
// Writes tools/titan/results/corpus-v6-18.json from the gate of record's own
// score files, so no number in the snapshot is typed by hand:
//   record   wave51-fix   → <run>   totals, per-section table, the per-cell diff
//   calib    wave51-fix   → wave52-calib   which flips are INSTRUMENT-only
//   review   cell-review.json              which gains are DEGENERATE (pass, picture does not earn it)
// The prose `_note` comes from a text file (the wave's story is written, not
// generated). Shape = corpus-v6-17.json plus four lists the earlier snapshots
// carried only in prose: unmeasuredNowCells, instrumentOnly, degenerateGains,
// and the run's installed-build hashes.
//
// Usage: node make-corpus.mjs <record.json> <calib.json> <cell-review.json> <note.txt> <out.json>
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const [recordPath, calibPath, reviewPath, notePath, outPath] = process.argv.slice(2);
if (!outPath) { console.error('usage: make-corpus.mjs <record.json> <calib.json> <cell-review.json> <note.txt> <out.json>'); process.exit(2); }
const load = (p) => JSON.parse(readFileSync(p, 'utf8'));
const record = load(recordPath), calib = load(calibPath), review = load(reviewPath);
const prev = load(path.join(HERE, '..', 'corpus-v6-17.json'));   // the static fields carry over

const key = (c) => `${c.test}|${c.platform}`;
// The earlier snapshots' cell line: "<section>/<test> <platform> <prev> -> <cur>".
const line = (c) => `${c.sec}/${c.test} ${c.platform} ${c.prev ?? 'unscored'} -> ${c.cur ?? 'unscored'}`;
const instrumentGained = new Set(calib.gained.map(key)), instrumentLost = new Set(calib.lost.map(key)), instrumentOut = new Set(calib.unmeasuredNow.map(key));
const degenerate = review.cells.filter((c) => c.final === 'DEGENERATE');
const degenerateKeys = new Set(degenerate.map(key));

// One line per degenerate family: what the picture shows (the reviewers' full text is in cell-review.json).
const WHY_DEGENERATE = {
  'anchor-position-multicol-007': 'the red anchor box the test forbids is still drawn, beside a jumble of green pieces',
  'contain-inline-size-bfc-floats-001': 'the orange bar is not beside the third float (the flow-root is sized from its content, not from the contained inline size)',
  'counter-suffix': 'the eight LTR lines match, but the two dir=rtl lists draw no marker at all',
  'hyphens-out-of-flow-001': 'no hyphen glyph and the word breaks at a character (highwa / y); only the box width — the ch fix — matches',
  'hyphens-out-of-flow-002': 'only three of seven boxes read high- / way; the out-of-flow span splits the word in the others',
  'hyphens-span-001': 'all nine boxes read highwa / y with no hyphen; only the box width — the ch fix — matches',
  's-11-1-1b-006': 'the black square sits 5 px too high (ink band y 36-70 against the reference text 36-50 + square 56-75): a display:table body laid out as blocks',
  'backdrop-filter-basic-blur': 'RING-FENCED (an external session owns this test), reported plainly: passes through the generic frame clip; its filter boxes sit 24 px right of the reference',
};

// Per-section totals of THIS run, in the previous snapshot's shape.
const sections = {};
for (const [sec, s] of Object.entries(record.sections)) sections[sec] = s.cur;

const run = record.cur;
const out = {
  _snapshot: 'corpus-v6.18',
  _note: readFileSync(notePath, 'utf8').trim(),
  artifact: `tools/titan/runs/${run} (gitignored)`,
  wptRef: prev.wptRef,
  devSha: '5d9ed628+wave52',
  date: '2026-10-05',
  bucket: prev.bucket,
  passThreshold: prev.passThreshold,
  criterion: `${prev.criterion} + per-cell absence-only exclusion (v6.18, wave 52 L12 Decision A: a PASS against a reference with ink under the presence floor is undiscriminating and leaves the denominator — inject-wpt-block.mjs isAbsenceOnly / applyAbsenceOnlyGate; a FAIL there is still a measurement and stays)`,
  sampling: prev.sampling,
  canvasBoundary: 'white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin (v6.18, wave 52 L12: the canvas background sits on :where(html) only, and the UA body margin is applied when the test declares a body margin and the reference does not; 23 references re-rendered, 1412 cloned byte-identical)',
  totals: record.totals.cur,
  sections,
  perCellDiff: {
    against: `${record.prev} (corpus-v6.17)`,
    scorer: `node tools/titan/score-gate.mjs ${record.prev} ${run} --watch tools/titan/results/wave52-plan/watchlist.txt --movers 0.005`,
    gained: record.gained.length,
    lost: record.lost.length,
    newlyMeasured: record.newlyMeasured.length,
    unmeasuredNow: record.unmeasuredNow.length,
    movers: record.movers.length,
    gainedCells: record.gained.map(line),
    lostCells: record.lost.map(line),
    newlyMeasuredCells: record.newlyMeasured.map(line),
    unmeasuredNowCells: record.unmeasuredNow.map(line),
    // Flips that happened on IDENTICAL captures when only the instrument changed (wave52-calib).
    instrumentOnly: {
      calibration: `node tools/titan/score-gate.mjs ${calib.prev} ${calib.cur}  (the wave-51 captures re-scored against the re-frozen references; capture-hash check 4305/4305 identical)`,
      gainedCells: record.gained.filter((c) => instrumentGained.has(key(c))).map(line),
      lostCells: record.lost.filter((c) => instrumentLost.has(key(c))).map(line),
      unmeasuredNowCells: record.unmeasuredNow.filter((c) => instrumentOut.has(key(c))).map(line),
    },
    renderGained: record.gained.filter((c) => !instrumentGained.has(key(c))).length,
    renderLost: record.lost.filter((c) => !instrumentLost.has(key(c))).length,
    // Every gained cell was looked at against its reference (cell-review.json); these pass and do not earn it.
    degenerateGains: record.gained.filter((c) => degenerateKeys.has(key(c))).map((c) => {
      const why = Object.entries(WHY_DEGENERATE).find(([stem]) => c.test.includes(stem));
      // A degenerate cell with no one-line reason here would publish an unexplained label.
      if (!why) { console.error(`no WHY_DEGENERATE line for ${key(c)}`); process.exit(1); }
      return { cell: line(c), why: why[1] };
    }),
    faithfulGains: record.gained.filter((c) => !degenerateKeys.has(key(c))).length,
  },
  // The installed base.apk sha1 / .app digest of every launch of this run (build-hashes.txt; all must say MATCH).
  installedBuilds: readFileSync(path.join(HERE, 'build-hashes.txt'), 'utf8').split(/^== /m).filter((b) => b.startsWith(`${run} `)).map((b) => {
    const lines = b.split('\n');
    return { launch: lines[0].trim(), android: lines.find((l) => /^android\s+serial=/.test(l))?.replace(/\s+/g, ' ') ?? null, ios: lines.find((l) => /^ios\s+udid=/.test(l))?.replace(/\s+/g, ' ') ?? null, match: lines.filter((l) => /\bMATCH$/.test(l) && !/MISMATCH/.test(l)).length === 2 };
  }),
  reproduce: `tools/titan/gate-driver.sh ${run} --skip-fixture-net, then rm -f /tmp/titan-device-pool/provisioned-* and tools/titan/gate-driver.sh ${run} --skip-corpus  (quiet host: idle load < 6, free+inactive > 2 GB, ONE emulator + ONE simulator; the driver refuses otherwise; per section it runs POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 section-runner.sh <section> --all-platforms --max-tests 48 --run-id ${run} --foreground; the net is BASELINE=1 ./test-all.sh --gate-set). Score: node tools/titan/score-gate.mjs ${record.prev} ${run} --watch tools/titan/results/wave52-plan/watchlist.txt ; adjudicate: node tools/titan/results/wave52-gate/adjudicate.mjs <record.json> score-calib.json <render.json>`,
};
// A snapshot whose degenerate list names a cell that did not gain would be a lie about the review.
const gainedKeys = new Set(record.gained.map(key));
const stray = degenerate.filter((d) => !gainedKeys.has(key(d)));
if (stray.length) { console.error(`cell-review.json labels ${stray.length} DEGENERATE cell(s) that are not gained in ${recordPath}: ${stray.map(key).join(', ')}`); process.exit(1); }
writeFileSync(outPath, JSON.stringify(out, null, 2) + '\n');
const t = out.totals;
console.log(`${outPath}: web ${t.web.passing}/${t.web.measured} · ios ${t.ios.passing}/${t.ios.measured} · android ${t.android.passing}/${t.android.measured}; gained ${out.perCellDiff.gained} (${out.perCellDiff.instrumentOnly.gainedCells.length} instrument + ${out.perCellDiff.renderGained} render; ${out.perCellDiff.faithfulGains} faithful, ${out.perCellDiff.degenerateGains.length} degenerate), lost ${out.perCellDiff.lost} (${out.perCellDiff.renderLost} render), unmeasured-now ${out.perCellDiff.unmeasuredNow}`);

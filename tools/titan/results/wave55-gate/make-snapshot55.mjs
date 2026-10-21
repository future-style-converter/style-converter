#!/usr/bin/env node
// tools/titan/results/wave55-gate/make-snapshot55.mjs — the wave-55 corpus snapshot generator: wave54-gate/make-snapshot54.mjs
// (the record that wrote corpus-v6-20.json) with its wave-54 constants turned into options whose DEFAULTS are wave 55's, plus
// four refusals (exit 2) the wave-54 script did not have. Run from the repo root.
//   prev            tools/titan/results/corpus-v6-20.json   (the static fields — wptRef, bucket, criterion, … — carry over)
//   _snapshot       corpus-v6.21
//   run / base      wave55-final / wave55-open               (artifact, reproduce, the scorer string)
//   expectations    tools/titan/results/wave55-plan/expectations.json  (probeReverts from probeDecisions.reverted; the
//                   scorer's --watch is the watchlist beside it)
//   gate dir        tools/titan/results/wave55-gate          (installedBuilds from build-hashes.txt; degenerateGains from
//                   final/cell-review.json; the adjudicate.mjs the `reproduce` line names)
// Refusals (exit 2), each a way the wave-54 script could have published a wrong snapshot without a word:
//   ORDER        the record is not score-gate.mjs <base> → <run>;
//   NOT THE 0.005 RECORD  a mover with |Δ| < 0.005: the --movers 0 record (score-final.json, `scoreOfRecord`) lists every
//                same-verdict cell, and perCellDiff.movers would publish ≈4000 under a scorer string that says 0.005
//                (expectations.json `corpusSnapshotSource`, wave-54 R2-S3);
//   NO CELL REVIEW  <gate>/final/cell-review.json missing or without a `gained` map: degenerateGains would be silently empty;
//   NO INSTALLED BUILD  no build-hashes.txt block whose label starts with "<run> " (a run without that record is a score, not
//                evidence — installed-build-hash.sh). Blocks are matched on the LABEL, not anywhere in the block, so an A/B
//                arm's block ("wave55-ab-<arm> (… read against wave55-final)") is never published as a launch of the run.
// Usage: node tools/titan/results/wave55-gate/make-snapshot55.mjs <score.movers0005.json> <note.txt> <out.json>
//          [--prev <corpus.json>] [--snapshot corpus-v6.21] [--run wave55-final] [--base wave55-open] [--exp <expectations.json>]
//          [--gate <gate dir>] [--date YYYY-MM-DD (default: today, UTC)] [--against "<perCellDiff.against prose>"]
//        DEV_SHA=<sha+wave55 (gate tree …)> in the environment, as in wave 54.
// The wave-54 reproduction (toolkit-proofs.sh D1): --prev corpus-v6-19.json --snapshot corpus-v6.20 --run wave54-final
// --base wave54-open --exp wave54-plan/expectations.json --gate wave54-gate --date 2026-10-09 --against <v6.20's> on
// wave54-gate/score-final.movers0005.json reproduces corpus-v6-20.json.
import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const opt = (name, dflt) => { const i = args.indexOf(name); return i < 0 ? dflt : args.splice(i, 2)[1]; };
const R = 'tools/titan/results';
const prevPath = opt('--prev', `${R}/corpus-v6-20.json`);
const snapshotId = opt('--snapshot', 'corpus-v6.21');
const run = opt('--run', 'wave55-final');
const base = opt('--base', 'wave55-open');
const expPath = opt('--exp', `${R}/wave55-plan/expectations.json`);
const gate = opt('--gate', `${R}/wave55-gate`).replace(/\/$/, '');
const date = opt('--date', new Date().toISOString().slice(0, 10));
const against = opt('--against', `${base} (= wave54-final content on the same host: 1435 / 1435 captures byte-identical on web, iOS and Android, wire 1435 / 1435; corpus-v6.20 numbers exactly — wave55-gate/_note.md)`);
const [scorePath, notePath, outPath] = args;
const refuse = (what, why) => { console.error(`${what}: ${why}. Refusing (nothing written).`); process.exit(2); };
if (!outPath || args.length !== 3) {
  console.error('usage: make-snapshot55.mjs <score.movers0005.json> <note.txt> <out.json> [--prev …] [--snapshot …] [--run …] [--base …] [--exp …] [--gate …] [--date …] [--against …]');
  process.exit(2);
}
const prev = JSON.parse(fs.readFileSync(prevPath, 'utf8'));
const sc = JSON.parse(fs.readFileSync(scorePath, 'utf8'));
const exp = JSON.parse(fs.readFileSync(expPath, 'utf8'));
if (sc.prev !== base || sc.cur !== run) refuse('ORDER', `${scorePath} is score-gate.mjs ${sc.prev} → ${sc.cur}, the snapshot needs ${base} → ${run}`);
const small = sc.movers.filter((c) => Math.abs(c.delta) < 0.005);
if (small.length) refuse('NOT THE 0.005 RECORD', `${small.length} of its ${sc.movers.length} movers have |Δ| < 0.005 (e.g. ${small[0].sec}/${small[0].test} ${small[0].platform} Δ ${small[0].delta}); the snapshot's source is the --movers 0.005 record (expectations.json corpusSnapshotSource)`);
const reviewPath = `${gate}/final/cell-review.json`;
if (!fs.existsSync(reviewPath)) refuse('NO CELL REVIEW', `${reviewPath} does not exist (degenerateGains would be empty)`);
const review = JSON.parse(fs.readFileSync(reviewPath, 'utf8'));
if (!review.gained || typeof review.gained !== 'object') refuse('NO CELL REVIEW', `${reviewPath} has no \`gained\` map`);

const fmt = (c) => `${c.sec}/${c.test} ${c.platform} ${c.prev} -> ${c.cur}`;
// installedBuilds: the run's own launches (label "<run> …"), as in wave 54 (whose blocks all began with the run name).
const hashes = fs.readFileSync(`${gate}/build-hashes.txt`, 'utf8').split('\n== ').map((b, i) => (i === 0 ? b.replace(/^== /, '') : b))
  .filter((b) => b.startsWith(`${run} `)).map((b) => {
    const lines = ('== ' + b).split('\n'); return { launch: lines[0].replace(/^== /, ''), android: lines.find((l) => l.startsWith('android  serial')), androidMatch: lines.find((l) => /^android  (MATCH|MISMATCH)/.test(l)), ios: lines.find((l) => l.startsWith('ios      udid')), iosMatch: lines.find((l) => /^ios      (MATCH|MISMATCH)/.test(l)) };
  });
if (!hashes.length) refuse('NO INSTALLED BUILD', `${gate}/build-hashes.txt has no block labelled "${run} …"`);
const probeReverts = (exp.probeDecisions?.reverted ?? []).map((r) => `${r.lane} ${r.unit} (${r.commit} → ${r.revertCommit}, ${r.run}): rule ${r.rule} — ${r.withdrawnPredictions.length} predictions withdrawn, ${(r.mustNotMoveAfter ?? []).length} cells held to must-not-move`);
const watchlist = path.join(path.dirname(expPath), 'watchlist.txt');
const snap = {
  _snapshot: snapshotId,
  _note: fs.readFileSync(notePath, 'utf8').trim(),
  artifact: `tools/titan/runs/${run} (gitignored)`,
  wptRef: prev.wptRef,
  devSha: process.env.DEV_SHA || '0d0d3a3d+wave55',
  date,
  bucket: prev.bucket, passThreshold: prev.passThreshold, criterion: prev.criterion, sampling: prev.sampling, canvasBoundary: prev.canvasBoundary,
  totals: sc.totals.cur,
  sections: Object.fromEntries(Object.entries(sc.sections).map(([k, v]) => [k, v.cur])),
  perCellDiff: {
    against,
    scorer: `node tools/titan/score-gate.mjs ${base} ${run} --watch ${watchlist} --movers 0.005`,
    gained: sc.gained.length, lost: sc.lost.length, newlyMeasured: sc.newlyMeasured.length, unmeasuredNow: sc.unmeasuredNow.length, movers: sc.movers.length,
    gainedCells: sc.gained.map(fmt), lostCells: sc.lost.map(fmt), newlyMeasuredCells: sc.newlyMeasured.map(fmt), unmeasuredNowCells: sc.unmeasuredNow.map(fmt),
    moverCells: sc.movers.map((c) => `${fmt(c)} (${c.delta >= 0 ? '+' : ''}${c.delta})`),
    // DEGENERATE gains = the cell review's verdicts (<gate>/final/cell-review.json, `gained` map: "<sec>/<test> <platform>" →
    // {label, what}); a label other than FAITHFUL on a gained cell is published here, never counted as earned.
    degenerateGains: Object.entries(review.gained).filter(([, v]) => v.label !== 'FAITHFUL').map(([k, v]) => `${k}: ${v.label} — ${v.what}`),
    instrumentOnly: [],   // no calibration run: no scorer / instrument change between the base and the run (expectations.json expected.lost = [])
    probeReverts,
  },
  installedBuilds: hashes,
  reproduce: `tools/titan/gate-driver.sh ${run} --skip-fixture-net, then rm -f /tmp/titan-device-pool/provisioned-* and tools/titan/gate-driver.sh ${run} --skip-corpus; score with the perCellDiff.scorer line; adjudicate with node ${gate}/adjudicate.mjs <score.json>`,
};
fs.writeFileSync(outPath, JSON.stringify(snap, null, 2) + '\n');
console.log('wrote', outPath, 'totals', JSON.stringify(snap.totals), 'gained', snap.perCellDiff.gained, 'lost', snap.perCellDiff.lost, 'movers', snap.perCellDiff.movers, 'builds', hashes.length, 'probeReverts', probeReverts.length, 'degenerateGains', snap.perCellDiff.degenerateGains.length);

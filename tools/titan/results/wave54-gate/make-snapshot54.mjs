// RECORD of the wave-54 corpus snapshot generator (copied from the session scratchpad at ship time; run from the repo root).
// make-snapshot54.mjs <score-final.movers0005.json> <note.txt> <out corpus-v6-20.json> — same shape as corpus-v6-19.json
// (which the wave-53 twin of this script wrote): totals/sections from the scorer's `cur` side, perCellDiff from its lists,
// installedBuilds from wave54-gate/build-hashes.txt (the wave54-final blocks), probeReverts from expectations.probeDecisions.
import fs from 'node:fs';
const [scorePath, notePath, outPath] = process.argv.slice(2);
const prev = JSON.parse(fs.readFileSync('tools/titan/results/corpus-v6-19.json', 'utf8'));
const sc = JSON.parse(fs.readFileSync(scorePath, 'utf8'));
const exp = JSON.parse(fs.readFileSync('tools/titan/results/wave54-plan/expectations.json', 'utf8'));
const fmt = (c) => `${c.sec}/${c.test} ${c.platform} ${c.prev} -> ${c.cur}`;
const hashes = fs.readFileSync('tools/titan/results/wave54-gate/build-hashes.txt', 'utf8').split('\n== ').filter((b) => b.includes('wave54-final')).map((b) => {
  const lines = ('== ' + b).split('\n'); return { launch: lines[0].replace(/^== /, ''), android: lines.find((l) => l.startsWith('android  serial')), androidMatch: lines.find((l) => /^android  (MATCH|MISMATCH)/.test(l)), ios: lines.find((l) => l.startsWith('ios      udid')), iosMatch: lines.find((l) => /^ios      (MATCH|MISMATCH)/.test(l)) };
});
const probeReverts = (exp.probeDecisions?.reverted ?? []).map((r) => `${r.lane} ${r.unit} (${r.commit} → ${r.revertCommit}, ${r.run}): rule ${r.rule} — ${r.withdrawnPredictions.length} predictions withdrawn, ${(r.mustNotMoveAfter ?? []).length} cells held to must-not-move`);
const snap = {
  _snapshot: 'corpus-v6.20',
  _note: fs.readFileSync(notePath, 'utf8').trim(),
  artifact: 'tools/titan/runs/wave54-final (gitignored)',
  wptRef: prev.wptRef,
  devSha: process.env.DEV_SHA || '7cce3b22+wave54',
  date: '2026-10-09',
  bucket: prev.bucket, passThreshold: prev.passThreshold, criterion: prev.criterion, sampling: prev.sampling, canvasBoundary: prev.canvasBoundary,
  totals: sc.totals.cur,
  sections: Object.fromEntries(Object.entries(sc.sections).map(([k, v]) => [k, v.cur])),
  perCellDiff: {
    against: 'wave54-open (= wave53-final content on the same host: 1435 / 1435 captures byte-identical; corpus-v6.19 numbers exactly)',
    scorer: 'node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0.005',
    gained: sc.gained.length, lost: sc.lost.length, newlyMeasured: sc.newlyMeasured.length, unmeasuredNow: sc.unmeasuredNow.length, movers: sc.movers.length,
    gainedCells: sc.gained.map(fmt), lostCells: sc.lost.map(fmt), newlyMeasuredCells: sc.newlyMeasured.map(fmt), unmeasuredNowCells: sc.unmeasuredNow.map(fmt),
    moverCells: sc.movers.map((c) => `${fmt(c)} (${c.delta >= 0 ? '+' : ''}${c.delta})`),
    // DEGENERATE gains = the cell review's verdicts (tools/titan/results/wave54-gate/final/cell-review.json, `gained` map:
    // "<sec>/<test> <platform>" → {label, what}); a label other than FAITHFUL on a gained cell is published here, never counted as earned.
    degenerateGains: Object.entries(JSON.parse(fs.readFileSync('tools/titan/results/wave54-gate/final/cell-review.json', 'utf8')).gained)
      .filter(([, v]) => v.label !== 'FAITHFUL').map(([k, v]) => `${k}: ${v.label} — ${v.what}`),
    instrumentOnly: [],   // no calibration run this wave: no instrument change between wave54-open and wave54-final
    probeReverts,
  },
  installedBuilds: hashes,
  reproduce: 'tools/titan/gate-driver.sh wave54-final --skip-fixture-net, then rm -f /tmp/titan-device-pool/provisioned-* and tools/titan/gate-driver.sh wave54-final --skip-corpus; score with the perCellDiff.scorer line; adjudicate with node tools/titan/results/wave54-gate/adjudicate.mjs <score.json>',
};
fs.writeFileSync(outPath, JSON.stringify(snap, null, 2) + '\n');
console.log('wrote', outPath, 'totals', JSON.stringify(snap.totals), 'gained', snap.perCellDiff.gained, 'lost', snap.perCellDiff.lost, 'builds', hashes.length, 'probeReverts', probeReverts.length);

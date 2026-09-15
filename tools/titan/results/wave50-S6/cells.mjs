// S6 skeptic helper: independent cell reader over a titan run dir.
// Usage: node tools/titan/results/wave50-S6/cells.mjs <runId> <regex> [section]
import fs from 'node:fs';
import path from 'node:path';
const [runId, rx, sectionFilter] = process.argv.slice(2);
const root = path.join('tools/titan/runs', runId, 'sections');
const re = new RegExp(rx);
const out = [];
for (const sec of fs.readdirSync(root)) {
  if (sectionFilter && sec !== sectionFilter) continue;
  const mp = path.join(root, sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  const res = m?.wpt?.results || {};
  for (const [test, r] of Object.entries(res)) {
    if (!re.test(test)) continue;
    const diffs = r?.browserRef?.diffs || {};
    const row = { section: sec, test, scoreEligible: r.scoreEligible, bucket: r.bucket, lossy: r.lossy, cells: {} };
    for (const [pair, d] of Object.entries(diffs)) {
      if (typeof d?.ssim !== 'number' || d.scoreExcluded) { row.cells[pair] = { skipped: true, scoreExcluded: !!d?.scoreExcluded }; continue; }
      row.cells[pair] = { v: d.wptPass === true ? 'P' : 'f', ssim: d.ssim,
        capW: d.frame?.capW, capH: d.frame?.capH, refW: d.frame?.refW, refH: d.frame?.refH,
        novelInkFailed: d.novelInkFailed, colorFailed: d.colorFailed, presenceFailed: d.presenceFailed,
        coverageRatioFailed: d.coverageRatioFailed, mismPct: d.pixelMismatchedPct };
    }
    out.push(row);
  }
}
console.log(JSON.stringify(out, null, 1));

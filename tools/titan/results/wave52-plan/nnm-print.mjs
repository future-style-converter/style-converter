// Print helper (read-only): compact per-cell table of the near-miss census.
import fs from 'node:fs';
const J = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const mode = process.argv[3] || 'table';
const f = (v, n = 4) => v == null ? '-' : Number(v).toFixed(n);
if (mode === 'table') {
  const rows = J.cells.slice().sort((a, b) => a.section.localeCompare(b.section) || a.cell.localeCompare(b.cell) || a.platform.localeCompare(b.platform));
  for (const c of rows) {
    const fl = c.flags.map(s => s.replace('Failed', '')).join('+') || 'none';
    console.log([
      c.platform.padEnd(7), f(c.ssim), fl.padEnd(22),
      'web', c.web ? (c.web.wptPass ? 'P' : 'f') + f(c.web.ssim) : '-',
      c.other.platform.slice(0, 3), c.other ? (c.other.wptPass ? 'P' : 'f') + f(c.other.ssim) : '-',
      'unexp%', f(c.degenerate?.unexplainedInkPct, 3), 'drop%', f(c.degenerate?.refDroppedPct, 3),
      'novel%', f(c.novelInk?.novelPct, 3), 'dEp95', f(c.labDeltaE?.p95, 1), 'dEmax', f(c.labDeltaE?.max, 0),
      'cov', f(c.semanticPresence?.aCoveragePct, 2) + '/' + f(c.semanticPresence?.bCoveragePct, 2),
      c.cell,
    ].join(' '));
  }
} else if (mode === 'cause') {
  const agg = {};
  for (const c of J.cells) { const k = c.platform + ' ' + c.failCause; agg[k] = (agg[k] || 0) + 1; }
  console.log(agg);
  const byCause = {};
  for (const c of J.cells) (byCause[c.failCause] ||= []).push(c);
  for (const [cause, list] of Object.entries(byCause)) {
    console.log('\n=== ' + cause + ' (' + list.length + ')');
    list.sort((a, b) => a.cell.localeCompare(b.cell) || a.platform.localeCompare(b.platform));
    for (const c of list) {
      console.log([c.platform.padEnd(7), f(c.ssim), 'ratio', f(c.coverageRatio, 2), 'cov', f(c.semanticPresence?.aCoveragePct, 2) + '/' + f(c.semanticPresence?.bCoveragePct, 2),
        'dEmean', f(c.labDeltaEMean, 2), 'KL', c.histogramKL ? [c.histogramKL.r, c.histogramKL.g, c.histogramKL.b].map(v => f(v, 2)).join('/') : '-',
        'web', c.web ? (c.web.wptPass ? 'P' : 'f') + f(c.web.ssim) : '-', c.other.platform.slice(0, 3), (c.other.wptPass ? 'P' : 'f') + f(c.other.ssim),
        'novel', (c.novelInk?.novelClasses || []).map(n => JSON.stringify(n)).join(';').slice(0, 90), c.cell].join(' '));
    }
  }
} else if (mode === 'flags') {
  const agg = {};
  for (const c of J.cells) { const k = c.platform + ' ' + (c.flags.join('+') || 'none'); agg[k] = (agg[k] || 0) + 1; }
  console.log(agg);
  const web = {};
  for (const c of J.cells) { const k = c.platform + ' web:' + (c.web?.wptPass ? 'P' : 'f') + ' other:' + (c.other?.wptPass ? 'P' : 'f'); web[k] = (web[k] || 0) + 1; }
  console.log(web);
  const sec = {};
  for (const c of J.cells) { sec[c.section] = sec[c.section] || { ios: 0, android: 0 }; sec[c.section][c.platform]++; }
  console.log(sec);
}

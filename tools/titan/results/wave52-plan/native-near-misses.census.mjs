// Read-only census: every FAILING wave51-fix cell with ssim >= 0.93 on iOS / Android.
// Reads tools/titan/runs/wave51-fix/sections/<sec>/manifest.json only; writes the JSON beside the brief.
import fs from 'node:fs';
import path from 'node:path';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const OUT = process.argv[2] || path.join(ROOT, 'tools/titan/results/wave52-plan/native-near-misses.census.json');
const THRESH = 0.93;
const cells = [];
let total = 0, eligible = 0;
for (const sec of fs.readdirSync(RUN).sort()) {
  const mp = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  for (const [test, r] of Object.entries(m.wpt.results)) {
    total++;
    if (!r.scoreEligible) continue;
    eligible++;
    const d = r.browserRef?.diffs || {};
    const web = d['web-ref'];
    for (const plat of ['ios', 'android']) {
      const x = d[`${plat}-ref`];
      if (!x || x.scoreExcluded) continue;
      if (x.wptPass !== false) continue;
      if (!(x.ssim >= THRESH)) continue;
      const short = test.replace(/^css\//, '').replace(/\.html$/, '');
      const file = 'wpt__' + short.replace(/\//g, '__') + '.png';
      const other = plat === 'ios' ? d['android-ref'] : d['ios-ref'];
      // The ONLY inputs computeWptPass reads (inject-wpt-block.mjs:627-655): presence, colour,
      // coverage-ratio vetoes, then raw ssim >= 0.95 / fuzzy. novelInk + degenerate are triage-only
      // unless TITAN_NOVEL_INK_VETO / TITAN_DEGENERATE_VETO were set for the run.
      const failCause = x.presenceFailed ? 'presence' : x.colorFailed ? 'color' : x.coverageRatioFailed ? 'coverageRatio'
        : (x.ssim < 0.95 ? 'ssim' : 'UNEXPLAINED(veto armed?)');
      const cap = x.semanticPresence?.aCoveragePct, ref = x.semanticPresence?.bCoveragePct;
      cells.push({
        cell: short, section: sec, platform: plat, ssim: x.ssim, failCause,
        coverageRatio: (cap != null && ref != null) ? +(Math.max(cap, ref) / Math.max(1e-9, Math.min(cap, ref))).toFixed(2) : null,
        histogramKL: x.histogramKL, labDeltaEMean: x.labDeltaE?.mean, colorDivergent: x.colorDivergent,
        flags: ['colorFailed', 'novelInkFailed', 'degenerateFailed', 'presenceFailed', 'coverageRatioFailed'].filter(f => x[f]),
        edgeSsim: x.edgeSsim, colorComposite: x.colorComposite, divergence: x.divergence,
        pixelMismatchedPct: x.pixelMismatchedPct, fuzzyMaxChannelDelta: x.fuzzyMaxChannelDelta,
        labDeltaE: x.labDeltaE, semanticPresence: x.semanticPresence, lowContentDensity: x.lowContentDensity,
        degenerate: x.degenerate && {
          unexplainedInkPct: x.degenerate.unexplainedInkPct, unexplainedInkFraction: x.degenerate.unexplainedInkFraction,
          refDroppedPct: x.degenerate.refDroppedPct, refDroppedFraction: x.degenerate.refDroppedFraction,
          divergentInkPx: x.degenerate.divergentInkPx, frameMismatch: x.degenerate.frameMismatch,
        },
        novelInk: x.novelInk && {
          novelPx: x.novelInk.novelPx, novelPct: x.novelInk.novelPct, novelFractionOfDivergentInk: x.novelInk.novelFractionOfDivergentInk,
          novelClasses: x.novelInk.novelClasses, paletteCoveragePct: x.novelInk.paletteCoveragePct,
        },
        frame: x.frame,
        web: web && { ssim: web.ssim, wptPass: web.wptPass, flags: ['colorFailed', 'novelInkFailed', 'degenerateFailed', 'presenceFailed'].filter(f => web[f]) },
        other: other && { platform: plat === 'ios' ? 'android' : 'ios', ssim: other.ssim, wptPass: other.wptPass },
        nativeParity: r.nativeParity, lossyReasons: r.lossyReasons, notApplicableTags: r.notApplicableTags,
        postLoadExtracted: r.postLoadExtracted, bidiBaked: r.bidiBaked, counterStyleBaked: r.counterStyleBaked,
        components: r.components, refPath: r.browserRef.path,
        capture: `tools/titan/runs/wave51-fix/sections/${sec}/${plat}-screenshots/${file}`,
        ir: `tools/titan/runs/wave51-fix/sections/${sec}/per-test-ir/${file.replace(/\.png$/, '.json')}`,
      });
    }
  }
}
const byPlat = { ios: cells.filter(c => c.platform === 'ios').length, android: cells.filter(c => c.platform === 'android').length };
fs.writeFileSync(OUT, JSON.stringify({ generatedAt: new Date().toISOString(), run: 'wave51-fix', threshold: THRESH, totalTests: total, scoreEligible: eligible, counts: byPlat, cells }, null, 1));
console.log('tests', total, 'eligible', eligible, 'near-miss cells', byPlat);

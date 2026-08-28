// cross-platform-gate.mjs — turn the three-way comparison into a gate.
//
// ## Why this exists
//
// Until now the ONLY path to a non-zero exit in compare-screenshots.mjs was
// a platform regressing against its OWN committed baseline. The three
// cross-platform pairs — iOS↔Android, iOS↔web, Android↔web — were computed,
// rendered into the report, and gated nothing. That is the comparison the
// entire product thesis rests on ("the same IR renders the same way on all
// three runtimes"), and it was advisory.
//
// Turning it on costs 21 failures out of 327 on fixtures/visual-test.json.
// That is a ledger, not a wall — which is exactly why it is worth doing as
// an ENUMERATED list of known divergences with reasons, rather than by
// loosening a threshold until the wall disappears.
//
// ## Shape: Chromium's TestExpectations, not a tolerance knob
//
// A tolerance hides *how many* things are wrong and *which*. An expectation
// ledger names each one, with a reason, an owner and an expiry, and prints
// the open count in the report headline so slack cannot accumulate
// invisibly. Chromium's `TestExpectations` is the canonical shape: known
// failures keyed by test + configuration, each with a bug reference.
//
// ## Two-sided, deliberately incompletely
//
// WPT's `fuzzy` annotation is a two-sided range: a reftest that becomes TOO
// CLEAN also fails, forcing the tolerance to be re-derived rather than left
// stale. The same discipline applies here — an expectation whose pair now
// passes is stale and should be deleted.
//
// We report stale/expired entries but do NOT fail on them yet, and the
// reason is honest rather than squeamish: the A/A noise floor of this
// harness has never been measured. A pair sitting at 0.9499 may flap across
// the 0.95 line run-to-run, and a stale-expectation failure would then be
// flaky in a way nobody could distinguish from a real fix. Promote stale to
// failing once the noise-floor study lands and the thresholds sit outside
// P99.9. Until then: loud in the report, silent at the exit code.

/** Pair keys the comparator produces, in report order. */
export const PAIR_KEYS = ['iOS-Android', 'iOS-web', 'Android-web'];

/** Exit code for "a cross-platform pair diverged and nothing said it would". */
export const EXIT_UNEXPECTED_DIVERGENCE = 4;

/**
 * Does one pair's metric block breach the thresholds?
 * Mirrors the baseline gate's expression exactly so the two gates cannot
 * drift apart in meaning — same metrics, same comparisons, different subject.
 */
export function pairRegressed(pair, { ssimThreshold, pixelThreshold }) {
  if (!pair) return false;                                   // absent pair is not a failure
  if (pair.pixelMismatchedPct > pixelThreshold) return true;
  return pair.ssim !== null && pair.ssim !== undefined && pair.ssim < ssimThreshold;
}

/**
 * Index a ledger into a Map keyed `${component}\u0000${pair}` for O(1) lookup.
 * Entries carrying a `fixture` apply only to that input label; entries
 * without one apply to any fixture (used for cross-suite divergences).
 */
function indexLedger(entries, inputLabel) {
  const byKey = new Map();
  for (const e of entries) {
    // Scope check: an entry pinned to another fixture must not silently
    // excuse a failure in this one.
    if (e.fixture && inputLabel && !inputLabel.includes(e.fixture)) continue;
    const k = `${componentKey(e.component)}\u0000${e.pair}`;
    // Collision guard. Component NAMES are unique per parent, not per
    // document: two children called `layer` under different parents flatten
    // to the same capture name once the position index is stripped. A plain
    // Map.set would silently drop one entry, so the divergence it excused
    // would come back as an unexpected failure with no hint why.
    if (byKey.has(k)) {
      throw new Error(
        `cross-platform-expectations.json: two entries collide on ` +
        `"${componentKey(e.component)}" / ${e.pair}. Component names are only ` +
        `unique within a parent, so two same-named children flatten to one ` +
        `key. Rename one of the fixture components, or qualify the entry.`,
      );
    }
    byKey.set(k, e);
  }
  return byKey;
}

/**
 * Strip the capture index from a component filename.
 *
 * Captures are named `{NNN}_{Name}.png`, where NNN is the component's
 * POSITION in the flattened capture list. That index is not stable: adding,
 * removing or suppressing any component renumbers every component after it.
 *
 * Keying the ledger on the raw filename made every entry position-dependent,
 * and it failed in the worst direction. Measured when the backdrop-dependent
 * child suppression landed and the composition-test capture set went 38 -> 32:
 * every entry past the first suppression was reported "orphaned" (a warning)
 * while the divergence it was supposed to excuse came back "unexpected" (a
 * hard failure). A ledger that silently unbinds itself when the corpus shifts
 * is worse than no ledger.
 *
 * The NAME is stable — it is the component's key in the fixture JSON, and
 * unique within a document. Key on that.
 */
export function componentKey(name) {
  return String(name).replace(/^\d+_/, '');
}

/** True when `entry.expires` is a date in the past. Absent expiry never expires. */
function isExpired(entry, now) {
  if (!entry.expires) return false;
  const t = Date.parse(entry.expires);
  return Number.isFinite(t) && t < now.getTime();
}

/**
 * Evaluate the cross-platform gate over the comparator's rows.
 *
 * @param {object[]} rows        analyzeComponent() output
 * @param {object}   ledger      parsed cross-platform-expectations.json
 * @param {object}   opts        { ssimThreshold, pixelThreshold, inputLabel, now }
 * @returns {{skipped:boolean, reason?:string, checked:number, unexpected:object[],
 *            expected:object[], stale:object[], expired:object[]}}
 */
export function evaluateCrossPlatformGate(rows, ledger, opts) {
  const { ssimThreshold, pixelThreshold, inputLabel = '', now = new Date() } = opts;

  // A run that captured fewer than two platforms has no cross-platform pair
  // to judge. CI deliberately runs one platform per job (SKIP_IOS=1 etc.),
  // so this branch is what keeps those jobs untouched by the new gate —
  // rather than a flag someone has to remember to set.
  const platformsSeen = new Set();
  for (const r of rows) {
    for (const [p, info] of Object.entries(r.platforms ?? {})) {
      if (info && info.present !== false) platformsSeen.add(p);
    }
  }
  if (platformsSeen.size < 2) {
    return {
      skipped: true,
      reason: `only ${platformsSeen.size} platform(s) captured (${[...platformsSeen].join(', ') || 'none'}) — no cross-platform pair to gate`,
      checked: 0, unexpected: [], expected: [], stale: [], expired: [],
    };
  }

  const byKey = indexLedger(ledger?.expectations ?? [], inputLabel);
  const seen = new Set();                                    // expectation keys actually exercised
  const unexpected = [], expected = [], stale = [], expired = [];
  let checked = 0;

  for (const row of rows) {
    for (const pairKey of PAIR_KEYS) {
      const pair = row.pairs?.[pairKey];
      if (!pair) continue;                                   // platform missing on one side
      checked += 1;

      const key = `${componentKey(row.name)}\u0000${pairKey}`;
      const entry = byKey.get(key);
      const failed = pairRegressed(pair, { ssimThreshold, pixelThreshold });

      // Snapshot enough metrics that a reviewer can judge the row without
      // opening the report. ΔE is the discriminator that matters most: a
      // failure at ΔE95 ≈ 0 is geometry/AA, not colour.
      const observed = {
        ssim: pair.ssim ?? null,
        pixelPct: pair.pixelMismatchedPct ?? null,
        labDeltaEP95: pair.labDeltaE?.p95 ?? null,
        divergence: typeof pair.divergence === 'string'
          ? pair.divergence
          : pair.divergence?.label ?? null,
      };
      const record = { component: row.name, pair: pairKey, observed, entry };

      if (entry) seen.add(key);

      if (failed && !entry) unexpected.push(record);         // the thing this gate exists to catch
      else if (failed && entry) {
        expected.push(record);
        // An expired entry still excuses the failure this run — expiry is a
        // review prompt, not a booby trap that reddens CI on a date change.
        if (isExpired(entry, now)) expired.push(record);
      } else if (!failed && entry) {
        // Passing but listed: either it was fixed, or it is flapping.
        stale.push(record);
      }
    }
  }

  // Entries that never matched any row — a renamed or deleted component
  // leaves its expectation behind, which is how ledgers rot.
  //
  // But ONLY for pairs this run could actually have evaluated. A run with
  // SKIP_ANDROID=1 has no iOS-Android and no Android-web pair at all, so
  // every Android expectation would come back "orphaned - delete the line".
  // Measured: a SKIP_ANDROID run of visual-test emitted 15 such warnings,
  // every one pointing at a perfectly valid entry. That is noise dressed as
  // a finding, and it teaches people to skim past the warning that matters.
  // An expectation is orphaned only when BOTH its platforms were captured
  // and it still matched nothing.
  for (const [key, entry] of byKey) {
    if (seen.has(key)) continue;
    const [component, pair] = key.split('\u0000');
    const [a, b] = pair.split('-');
    if (!platformsSeen.has(a) || !platformsSeen.has(b)) continue;
    stale.push({ component, pair, observed: null, entry, orphaned: true });
  }

  return { skipped: false, checked, unexpected, expected, stale, expired };
}

/** Human-readable one-liner per record, shared by the console and the report. */
export function formatRecord(r) {
  if (!r.observed) return `${r.component} · ${r.pair} — no such pair in this run (orphaned expectation)`;
  const { ssim, pixelPct, labDeltaEP95, divergence } = r.observed;
  const de = labDeltaEP95 === null ? '—' : labDeltaEP95.toFixed(2);
  return `${r.component} · ${r.pair} — SSIM ${ssim?.toFixed(4) ?? '—'} · Δpx ${pixelPct?.toFixed(2) ?? '—'}% · ΔE95 ${de} · ${divergence ?? '—'}`;
}

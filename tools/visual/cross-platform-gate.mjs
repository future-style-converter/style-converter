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
// ## Two-sided, and now enforced
//
// WPT's `fuzzy` annotation is a two-sided range: a reftest that becomes TOO
// CLEAN also fails, forcing the tolerance to be re-derived rather than left
// stale. The same discipline applies here — an expectation whose pair now
// passes is stale and must be deleted.
//
// This used to be a warning. The stated reason was that the harness's A/A
// noise floor had never been measured, so a pair sitting at 0.9499 might
// flap across the 0.95 line run-to-run and a stale-expectation failure
// would be indistinguishable from a real fix.
//
// That study has now run (`tools/visual/noise-floor.sh`) and the premise was
// false. Across independent full runs — fresh convert, fresh iOS build
// (test-all.sh rm -rf's the xcodeproj and build dir), fresh emulator boot
// (-no-snapshot), fresh install, fresh capture — every capture is
// BIT-FOR-BIT identical:
//
//     fixtures/visual-test.json        327 captures  identical  (N=2, web N=3)
//     fixtures/composition-test.json    96 captures  identical  (N=2)
//
// A metric computed from identical bytes is identical, so run-to-run
// flapping is not merely rare here, it is impossible. SSIM = 1.0000,
// Δpx = 0.00%, ΔE = 0 between any two runs, by construction. There is no
// distance between the noise floor and the thresholds to worry about
// because the floor is zero.
//
// So stale entries now FAIL (EXIT_STALE_EXPECTATION). A fix that makes a
// pair pass is not allowed to leave its excuse behind.
//
// SCOPE, honestly: this measures SAME-MACHINE determinism. Cross-machine
// variance (a different runner, Xcode version, or emulator image) is
// unmeasured, and CLAUDE.md records that device-level visual jobs are
// local-only today — so same-machine is currently the whole population.
// Re-run noise-floor.sh before these jobs move to CI; if captures stop
// being byte-identical there, this promotion is the first thing to revisit.
//
// `expired` stays a WARNING, and that is a separate judgement, not an
// oversight: expiry fires on a calendar rollover with no code change at
// all, so failing on it would redden a build nobody touched. That is the
// booby trap the old comment was right to avoid — it just was not the
// property that applied to stale.

/** Pair keys the comparator produces, in report order. */
export const PAIR_KEYS = ['iOS-Android', 'iOS-web', 'Android-web'];

/** Exit code for "a cross-platform pair diverged and nothing said it would". */
export const EXIT_UNEXPECTED_DIVERGENCE = 4;

/**
 * Exit code for "the ledger is stale — an entry passes, or names a component
 * that no longer exists". Distinct from 4 so a caller can tell "the runtimes
 * disagree" (a product problem) from "the ledger needs a line deleted" (a
 * bookkeeping problem). Both are failures; only one means something broke.
 */
export const EXIT_STALE_EXPECTATION = 5;

/**
 * Default ΔE95 above which a pair counts as regressed.
 *
 * 5.0 is not a fresh guess — it is the boundary this repo already calls
 * "clearly different" in the divergence classifier. CIEDE2000 ΔE ≈ 1 is the
 * just-noticeable difference under ideal viewing, 2–3 is noticeable in
 * context, and >5 is a colour a person would describe as simply wrong.
 */
export const DEFAULT_DELTA_E_THRESHOLD = 5.0;

/**
 * Does one pair's metric block breach the thresholds?
 * Mirrors the baseline gate's expression exactly so the two gates cannot
 * drift apart in meaning — same metrics, same comparisons, different subject.
 *
 * ## Why ΔE is in here
 *
 * It was computed on every pair, printed in the report, recorded in every
 * ledger row — and gated nothing. Both gates tested only SSIM and pixel%,
 * and neither of those sees colour the way a person does:
 *
 *   · pixelmatch's YIQ budget at threshold 0.25 cannot fire on a uniform
 *     lightness shift below 66/255 UNDER THE PRE-2026-08-29 SETTINGS
 *     (threshold 0.25, AA detection off; since the flip to 0.02 + AA-on
 *     the blind spot is ~6/255 uniform, ~17/255 pure blue) — measured
 *     then: black vs mid-grey scored as
 *     IDENTICAL), so Δpx routinely reads 0.00% on a blatant recolour.
 *   · SSIM is structural. Repaint a shape in the wrong colour without
 *     moving an edge and SSIM barely notices.
 *
 * That is not hypothetical. Measured on the live corpus, passing the
 * SSIM+pixel gate at the moment ΔE was added:
 *
 *     035_Filter_Sepia            ΔE95 23.35  SSIM 0.9573  Δpx 0.57%
 *     009_Backdrop_Saturate…      ΔE95 24.92  SSIM 0.9778  Δpx 0.00%
 *
 * The sepia row is a real iOS bug (it renders (74,110,113) where the CSS
 * matrix gives (153,158,143), which Android and web both hit exactly). A
 * gate that passes a 23-ΔE recolour at 0.00% pixel difference is measuring
 * the wrong thing.
 */
export function pairRegressed(pair, { ssimThreshold, pixelThreshold, deltaEThreshold = DEFAULT_DELTA_E_THRESHOLD }) {
  if (!pair) return false;                                   // absent pair is not a failure
  if (pair.pixelMismatchedPct > pixelThreshold) return true;
  // Absent ΔE (older manifest, or a pair the metric bailed on) never fails
  // alone — same rule as ssim, so a missing metric cannot manufacture a
  // failure the way a zero would.
  const de = pair.labDeltaE?.p95;
  if (de !== null && de !== undefined && de > deltaEThreshold) return true;
  return pair.ssim !== null && pair.ssim !== undefined && pair.ssim < ssimThreshold;
}

/**
 * Owner strings that carry no information. The ledger contract (header above
 * + the `note` field in cross-platform-expectations.json) says every line
 * names WHO owns the divergence; the retrospective (A9#5 / A12#8) found the
 * literal placeholder "unassigned" on 29/29 lines and nothing rejecting it,
 * so the field had been decorative since the wave-1 seed. Lower-cased match.
 */
const PLACEHOLDER_OWNERS = new Set(['unassigned', 'tbd', 'todo', 'none', 'n/a', 'nobody', '?']);

/**
 * 1-based line number of each ledger entry in the raw file text, so a schema
 * error can name the line (an index into a 400-line JSON array is useless to
 * the person who has to fix it). Every entry carries exactly one
 * `"component":` key at its own level (`observed` holds metrics, never a
 * component), so the n-th occurrence marks the n-th entry. Returns null when
 * the occurrence count disagrees with the entry count — a guess would point
 * at the wrong line, which is worse than no line.
 */
export function ledgerEntryLines(raw, entryCount) {
  const lines = [];
  const re = /"component"\s*:/g;                       // one per entry, by construction
  let m;
  while ((m = re.exec(raw)) !== null) {
    lines.push(raw.slice(0, m.index).split('\n').length); // newlines before the key + 1
  }
  return lines.length === entryCount ? lines : null;
}

/**
 * Schema-validate a parsed ledger. Returns an array of problems, each
 * `{ index, line, message }` (line is null when it cannot be located); an
 * empty array means the ledger honours its own contract.
 *
 * Checked per entry: `component` (non-empty), `pair` (one of PAIR_KEYS),
 * `reason` (non-empty), `owner` (non-empty and NOT a placeholder), `expires`
 * (present, ISO-parseable, and not before the date the divergence was
 * observed). The observation date is `observedAt` / `observed.at` when an
 * entry carries one; otherwise the ledger-level `seededFrom.run` is the floor
 * — the current schema records metrics under `observed` but no per-entry
 * date, so an expiry that predates the seed run is the only "dead on
 * arrival" case the data can expose. Kept separate from
 * evaluateCrossPlatformGate on purpose: a malformed line is a setup error
 * (exit 2 in the comparator), never a verdict about the runtimes.
 *
 * @param {object} ledger   parsed cross-platform-expectations.json
 * @param {string|null} raw the file text, for line numbers (optional)
 */
export function validateLedger(ledger, raw = null) {
  const problems = [];
  const entries = ledger?.expectations;
  if (!Array.isArray(entries)) {
    return [{ index: -1, line: 1, message: '`expectations` must be an array' }];
  }
  const lines = raw ? ledgerEntryLines(raw, entries.length) : null;
  const seedFloor = Date.parse(ledger?.seededFrom?.run ?? '');   // NaN when absent
  entries.forEach((e, i) => {
    const at = (message) => problems.push({ index: i, line: lines?.[i] ?? null, message });
    if (!e || typeof e !== 'object' || Array.isArray(e)) { at('entry must be an object'); return; }
    if (typeof e.component !== 'string' || e.component.trim() === '') at('`component` must be a non-empty string');
    if (!PAIR_KEYS.includes(e.pair)) at(`\`pair\` must be one of ${PAIR_KEYS.join(' | ')}, got ${JSON.stringify(e.pair)}`);
    if (typeof e.reason !== 'string' || e.reason.trim() === '') at('`reason` must be a non-empty string');
    const owner = typeof e.owner === 'string' ? e.owner.trim() : '';
    if (owner === '') at('`owner` must be a non-empty string (a lane or a person)');
    else if (PLACEHOLDER_OWNERS.has(owner.toLowerCase())) {
      at(`\`owner\` is the placeholder ${JSON.stringify(e.owner)} — name a lane or a person`);
    }
    if (typeof e.expires !== 'string') {
      at('`expires` is required (ISO date): an excuse without a re-review date is never re-reviewed');
    } else {
      const t = Date.parse(e.expires);
      if (!Number.isFinite(t)) at(`\`expires\` is not a parseable date: ${JSON.stringify(e.expires)}`);
      else {
        // Per-entry observation date wins when present; the seed run otherwise.
        const observedAt = Date.parse(e.observedAt ?? e.observed?.at ?? '');
        const floor = Number.isFinite(observedAt) ? observedAt : seedFloor;
        const floorLabel = Number.isFinite(observedAt) ? 'its observation date' : 'the ledger seed run (seededFrom.run)';
        if (Number.isFinite(floor) && t < floor) {
          at(`\`expires\` ${e.expires} precedes ${floorLabel} — the line was dead on arrival`);
        }
      }
    }
  });
  return problems;
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
  // deltaEThreshold is destructured and FORWARDED explicitly. It was
  // omitted here at first, so the cross-platform gate silently fell back to
  // pairRegressed's default while --delta-e-threshold appeared to work —
  // the two gates drifted apart in meaning, which is precisely what
  // delegating to one pairRegressed was supposed to make impossible.
  const {
    ssimThreshold, pixelThreshold,
    deltaEThreshold = DEFAULT_DELTA_E_THRESHOLD,
    inputLabel = '', now = new Date(),
  } = opts;

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
      const failed = pairRegressed(pair, { ssimThreshold, pixelThreshold, deltaEThreshold });

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
  // Two different situations used to collapse into one fatal "orphaned —
  // delete the line" verdict, and only one of them deserves it:
  //   · NO ROW carries the component's name → the component was renamed or
  //     deleted; the entry is genuinely orphaned (fatal via the stale path).
  //   · a row EXISTS but this pair was absent from it → one platform's
  //     capture of this one component failed or was skipped this run. The
  //     entry may be perfectly valid; deleting it on that evidence would
  //     un-excuse a real divergence. Misdiagnosed live during a stale-
  //     Android flake: valid Backdrop entries were reported as "no such
  //     component" because Android's captures were from the wrong fixture.
  const rowNames = new Set(rows.map((r) => componentKey(r.name)));
  const unexercised = [];
  for (const [key, entry] of byKey) {
    if (seen.has(key)) continue;
    const [component, pair] = key.split('\u0000');
    const [a, b] = pair.split('-');
    if (!platformsSeen.has(a) || !platformsSeen.has(b)) continue;
    if (rowNames.has(component)) {
      // The component rendered; the PAIR did not form. A warning, not a
      // deletion order.
      unexercised.push({ component, pair, entry });
      continue;
    }
    stale.push({ component, pair, observed: null, entry, orphaned: true });
  }

  return { skipped: false, checked, unexpected, expected, stale, expired, unexercised };
}

/** Human-readable one-liner per record, shared by the console and the report. */
export function formatRecord(r) {
  if (!r.observed) return `${r.component} · ${r.pair} — no such pair in this run (orphaned expectation)`;
  const { ssim, pixelPct, labDeltaEP95, divergence } = r.observed;
  const de = labDeltaEP95 === null ? '—' : labDeltaEP95.toFixed(2);
  return `${r.component} · ${r.pair} — SSIM ${ssim?.toFixed(4) ?? '—'} · Δpx ${pixelPct?.toFixed(2) ?? '—'}% · ΔE95 ${de} · ${divergence ?? '—'}`;
}

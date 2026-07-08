#!/usr/bin/env node
//
// tools/titan/bucket-wpt.mjs — Phase 0 WPT pre-classifier (the bucketer).
//
// Implements TITAN_ARCHITECTURE.md Section 4.1: walk every CSS reftest in
// tools/wpt/css/ once and emit tools/titan/wpt-buckets.json with each test
// classified A / B / C per the per-file string-grep heuristics from the
// spec table. The output is the durable artifact (corpus is gitignored
// per Section 10 Q5); Phase 1 reads from it to drive extraction.
//
// Heuristics (Section 4.1, plus Q9):
//   C — testharness.js, reftest-wait, <canvas>, <iframe>, scripted SVG,
//       no rel="match" link, missing ref file, http(s):// in href/src.
//   B — static SVG, @font-face → /fonts/, @supports, @container,
//       var(--), calc() with non-px units, viewport units (vw/vh/...).
//   A — everything else (clean conversion possible).
//
// Performance contract: <60s on M-series Mac for the full ~24k-file
// /css/ tree. We MUST stream files (one open-read-classify cycle at a
// time) — a naive Promise.all over readFile would blow the FD limit
// and the heap. The script uses an explicit concurrency-bounded worker
// pool over a single fs.readdir walk.
//
// Hard rules:
//   - String-grep based, no DOM parse (Section 4.1 says so explicitly).
//   - Bucket-C reasons are recorded per-test in `reasons` (Section 4.1
//     example shape) so the quarterly re-pin diff (Section 3.4) can
//     surface tests that newly went off-rails.
//   - Heuristics are exactly the Section 4.1 table; any deviation must
//     be flagged in the Phase 0 final report (caller spec, hard rule).
//
// Usage:
//   node tools/titan/bucket-wpt.mjs
//   WPT_DIR=path/to/wpt node tools/titan/bucket-wpt.mjs   # override
//   OUT=path/to/out.json node tools/titan/bucket-wpt.mjs  # override
//
// Exit codes:
//   0  — wrote tools/titan/wpt-buckets.json successfully.
//   1  — tools/wpt/css/ missing (run fetch-wpt.sh first).
//   2  — IO error during walk or write.

import { promises as fs } from 'node:fs';
import { resolve, dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// Architectural-exclusion rules surfaced by swarm-001. We import the
// pre-classifier as a separate module so the bucketer stays focused on
// the A/B/C string-grep heuristics and the architectural-exclusion
// catalog can be tested + evolved in isolation.
import { tagsForTest as architecturalTagsForTest } from './wpt-not-applicable.mjs';

// ---------------------------------------------------------------------------
// Paths.
// ---------------------------------------------------------------------------
// Resolve repo root relative to this file so the script is cwd-independent
// (matches fetch-wpt.sh — TITAN scripts get invoked from subshells).
const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
// WPT_DIR is overridable for testing the bucketer against a fixture tree
// without re-cloning 1.5 GB of WPT.
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
// CSS_ROOT is the only subdir we walk — all reftests live there and
// the spec instructs us to ignore other WPT subdirs.
const CSS_ROOT   = join(WPT_DIR, 'css');
// Output lives at the repo-relative path the spec dictates (Section 4.1).
const OUT_PATH   = process.env.OUT ?? join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json');

// ---------------------------------------------------------------------------
// Heuristic definitions (Section 4.1 table, in evaluation order).
//
// Each entry: { name, bucket, test(html, file) -> bool, reason }.
// Earliest-match wins. The order matters for "reason" attribution: a
// file that carries BOTH testharness.js and var(--) should be C (the
// stricter signal), not B.
//
// Why string-grep over a DOM parse:
//   - 24k files × ~1ms parse each = >24s baseline; jsdom is closer to
//     5ms each in practice ⇒ would blow the 60s budget.
//   - We don't need DOM accuracy for v1. False-positive bucket-C is
//     fine: the quarterly re-pin diff catches surprises and we can
//     tighten the regex.
//   - A future v2 can swap individual heuristics for a parser without
//     changing the public shape of wpt-buckets.json.
// ---------------------------------------------------------------------------

// Compile heuristic regexes once at module load. Each comment cites
// the spec row it implements so `git blame` can answer "why this
// regex".
const RX = {
    // Section 4.1 row 1: <script src="/resources/testharness.js"> →
    // testharness-driven test, not a reftest. Quoted/unquoted attr both.
    testharness: /<script[^>]+src=["']?[^"'>]*\/resources\/testharness\.js/i,

    // Section 4.1 row 2: class="reftest-wait" — body class signalling
    // JS-driven settle. Match either bare or as part of a class list.
    reftestWait: /class=["'][^"']*\breftest-wait\b/i,

    // Section 4.1 row 3: <canvas — IR doesn't model canvas. We tolerate
    // the tag self-closed or with attrs/whitespace.
    canvasTag:   /<canvas[\s>/]/i,
    // Section 4.1 row 3: <iframe — same rationale as canvas.
    iframeTag:   /<iframe[\s>/]/i,

    // Section 4.1 row 4 vs row 5: <svg with embedded <script> is C
    // (scripted SVG); <svg without scripts is B (static SVG, partial
    // IR). Caller code below applies the two-step check.
    svgTag:      /<svg[\s>/]/i,
    // Embedded <script> *anywhere in the file* is the conservative test
    // for "this SVG might be scripted". A future tightening could
    // require the <script> to be inside the <svg>, but per CLAUDE.md
    // we'd rather over-bucket-C than miss a JS-driven test.
    scriptTag:   /<script[\s>]/i,

    // Section 4.1 row 6: @font-face referencing /fonts/ — needs the
    // font asset to render faithfully; lossy without it.
    fontFaceFonts: /@font-face[\s\S]{0,400}url\(["']?[^"')]*\/fonts\//i,

    // Section 4.1 row 7: @supports / @container — conditional rules.
    // IR is flat-property (CLAUDE.md), so a rule that depends on
    // feature-detection won't round-trip.
    supports:    /@supports\b/i,
    container:   /@container\b/i,

    // Section 4.1 row 8: var(--) custom-prop fallback chains. CLAUDE.md
    // "IR Value Normalization" pins var() to runtime-dependent (null).
    cssVar:      /\bvar\(\s*--/i,

    // Section 4.1 row 9: calc() with non-px units. CLAUDE.md says calc
    // resolves at runtime in general; we only treat it as bucket-B
    // when it embeds a non-px length (em/rem/%/vw/...) so that pure
    // calc(10px + 5px)-style declarations stay in bucket-A.
    // Two-step: detect calc(...) presence, then check for a non-px
    // unit inside the call. Done in code below to keep the regex
    // readable.
    calcCall:    /\bcalc\(/i,
    calcNonPx:   /\bcalc\([^)]*?(\d+\s*(em|rem|ex|ch|vw|vh|vmin|vmax|dvw|dvh|svw|svh|lvw|lvh|%|cm|mm|in|pt|pc|fr))/i,

    // Section 4.1 row 10: viewport units anywhere — IR encodes them
    // as null per CLAUDE.md, so the rendered output won't faithfully
    // mirror the spec at our chosen 800x600 canvas.
    // \b is unreliable around %/digits, so we anchor on a digit
    // followed by the unit. Order matters in the alternation
    // (longest-first) to avoid `vw` swallowing `dvw`.
    viewportUnit: /\d(?:dvw|dvh|svw|svh|lvw|lvh|vmin|vmax|vw|vh)\b/i,

    // Section 4.1 row 11: rel="match" link — a reftest must declare
    // the reference. Tolerate quoted/unquoted attrs and rel before/after
    // href. Also accept rel="mismatch" tests (still reftests, just
    // with inverted expectation).
    relMatch:    /<link[^>]+rel=["']?(?:match|mismatch)\b[^>]*>/i,
    // We also need the href to extract the ref path for "ref file
    // missing" detection (Section 4.1 row 12).
    relMatchHref: /<link[^>]+(?:rel=["']?(?:match|mismatch)\b[^>]+href=["']([^"']+)["']|href=["']([^"']+)["'][^>]+rel=["']?(?:match|mismatch)\b)/i,

    // Section 10 Q9: http(s):// in a *runtime-loaded* href/src → remote
    // resource → bucket-C. We deliberately scope this narrowly because
    // a bare `(?:src|href)=["']https?://` is far too broad: WPT tests
    // routinely carry metadata `<link rel="help" href="https://...">` /
    // `<link rel="author" href="mailto:...">` that point at the spec or
    // the test author and have NO runtime effect. The Q9 prediction
    // ("less than 1% of CSS reftests") only holds if we limit the rule
    // to elements that actually fetch over the network at render time.
    //
    // Runtime-loading attributes:
    //   - <script src="...">          — JS execution
    //   - <img src="...">             — replaced element
    //   - <iframe src="...">          — already bucket-C above (iframe tag),
    //     but include for defense-in-depth
    //   - <embed src="...">           — replaced element
    //   - <object data="...">         — replaced element
    //   - <video src="..."> / <audio src="..."> / <source src="...">
    //   - <link rel="stylesheet" href="...">       — CSS @import equivalent
    //   - <link rel="preload"|"prefetch" ...>      — affects load order
    //   - <link rel="icon"|"shortcut icon" ...>    — favicon (cosmetic but
    //     can affect viewport size on some engines)
    //
    // Excluded (deliberately):
    //   - <link rel="author"|"help"|"reviewer"|"match"|"mismatch"|"manifest">
    //     — pure metadata, no runtime effect on layout/rendering.
    //   - <a href="...">               — inert in a screenshot.
    //   - background-image: url(https://...) inside CSS — Q9 calls out
    //     `<link href>` specifically; if CSS-side remote URLs become a
    //     real source of false-A's we'll add a separate heuristic in a
    //     future re-pin (and flag it per the Phase 0 hard rule).
    //
    // The matcher uses a DOTALL-style [\s\S]*? so attribute order doesn't
    // matter (e.g. <script async src="https://..."> still matches).
    remoteScriptSrc:    /<script\b[^>]*\bsrc=["']https?:\/\//i,
    remoteImgSrc:       /<img\b[^>]*\bsrc=["']https?:\/\//i,
    remoteEmbedSrc:     /<embed\b[^>]*\bsrc=["']https?:\/\//i,
    remoteObjectData:   /<object\b[^>]*\bdata=["']https?:\/\//i,
    remoteMediaSrc:     /<(?:video|audio|source|track)\b[^>]*\bsrc=["']https?:\/\//i,
    // <link rel="..."> with a runtime-loading rel value AND an https href
    // anywhere on the same tag. We use [\s\S]*? to span attributes in
    // either order (rel-then-href OR href-then-rel).
    remoteLinkRuntime:  /<link\b[^>]*?\brel=["']?(?:stylesheet|preload|prefetch|icon|shortcut icon|preconnect|dns-prefetch|modulepreload)\b[\s\S]*?\bhref=["']https?:\/\//i,
    remoteLinkRuntimeAlt: /<link\b[^>]*?\bhref=["']https?:\/\/[\s\S]*?\brel=["']?(?:stylesheet|preload|prefetch|icon|shortcut icon|preconnect|dns-prefetch|modulepreload)\b/i,
};

// Helper: combined remote-resource probe over the per-element regexes.
// Kept as a function so the classify() body reads as one call rather
// than a five-clause `||` chain.
function hasRemoteResource(html) {
    return RX.remoteScriptSrc.test(html)
        || RX.remoteImgSrc.test(html)
        || RX.remoteEmbedSrc.test(html)
        || RX.remoteObjectData.test(html)
        || RX.remoteMediaSrc.test(html)
        || RX.remoteLinkRuntime.test(html)
        || RX.remoteLinkRuntimeAlt.test(html);
}

// Map a relative test path (e.g. "css/css-grid/foo.html") to a spec
// section ("css-grid"). The spec section feeds the per-section
// histogram in Phase 5; we bake it into the bucket index now so
// later phases don't have to re-derive it. Tests directly under /css/
// (rare) get spec section "css".
function specSectionOf(relTestPath) {
    // relTestPath uses forward slashes (we normalise on emit). Split
    // and grab the second segment after the leading "css/".
    const parts = relTestPath.split('/');
    if (parts.length >= 2 && parts[0] === 'css') {
        return parts[1] || 'css';
    }
    return 'css';
}

// Resolve the on-disk path of the ref file given the test file's path
// and the href value from rel="match". Refs are usually a sibling
// (`foo-ref.html`) but can be relative paths (`../bar/baz-ref.html`)
// or even absolute (`/css/.../baz-ref.html`).
function resolveRefPath(testAbsPath, href) {
    if (href.startsWith('/')) {
        // Absolute href — anchored at the WPT root, NOT the filesystem
        // root. Strip leading slash and join under WPT_DIR.
        return join(WPT_DIR, href.slice(1));
    }
    // Relative href — resolve against the test file's directory.
    return resolve(dirname(testAbsPath), href);
}

// ---------------------------------------------------------------------------
// Single-file classifier. Returns { bucket: 'A'|'B'|'C', reason?: string }.
//
// Earliest-match wins per the spec table. We keep this synchronous and
// allocation-light — it's hot (24k invocations).
// ---------------------------------------------------------------------------
function classify(html, testAbsPath) {
    // Row 11 first: a missing rel="match" means this isn't a reftest at
    // all. Doing this check up-front lets us short-circuit the rest of
    // the heuristics for the ~half of /css/ HTML files that are ref or
    // testharness pages.
    if (!RX.relMatch.test(html)) {
        return { bucket: 'C', reason: 'no rel="match" link' };
    }

    // Row 1: testharness.js — reftests don't use it; if a test imports
    // it, that's a strong signal (per Section 2.4) that it's a wrapper.
    if (RX.testharness.test(html)) {
        return { bucket: 'C', reason: 'imports /resources/testharness.js' };
    }

    // Row 2: class="reftest-wait" — needs JS to settle before snapshot.
    if (RX.reftestWait.test(html)) {
        return { bucket: 'C', reason: 'class="reftest-wait" — JS settle required' };
    }

    // Row 3: <canvas>/<iframe> — IR doesn't model either.
    if (RX.canvasTag.test(html))  return { bucket: 'C', reason: 'uses <canvas>' };
    if (RX.iframeTag.test(html))  return { bucket: 'C', reason: 'uses <iframe>' };

    // Q9 (added to Section 4.1): remote resource → non-deterministic.
    // Scoped to runtime-loading tags only (script/img/embed/object/
    // media/link-stylesheet) — see the helper for the full list and
    // the rationale for excluding metadata <link rel="help|author|...">.
    if (hasRemoteResource(html)) {
        return { bucket: 'C', reason: 'remote resource (https?://) in runtime-loading href/src' };
    }

    // Row 12: ref file missing on disk. We need to extract the href to
    // check; if rel="match" is malformed (no parseable href) we flag
    // it as bucket-C with reason "unparseable rel=match href" — same
    // bucket the missing-ref case lands in.
    const m = RX.relMatchHref.exec(html);
    const href = m && (m[1] || m[2]);
    if (!href) {
        return { bucket: 'C', reason: 'unparseable rel="match" href' };
    }
    // Resolve + existence check is async, so we punt it to the caller
    // (which already has fs in scope) — return a sentinel here.
    // Stash the resolved ref so the caller doesn't re-parse the link.
    const refPath = resolveRefPath(testAbsPath, href);

    // Row 4 / 5: SVG handling. <svg> alone is bucket-B (static SVG,
    // partial IR). <svg> AND <script> in the same file is bucket-C
    // (scripted SVG). Order: check the strict variant first.
    const hasSvg    = RX.svgTag.test(html);
    const hasScript = hasSvg && RX.scriptTag.test(html);
    if (hasSvg && hasScript) {
        return { bucket: 'C', reason: 'scripted SVG (<svg> + <script>)', refPath };
    }

    // Bucket-B family — return the first matching reason; the lossy
    // marker is implicit in the bucket label.
    if (hasSvg) {
        return { bucket: 'B', reason: 'static SVG (<svg>) — partial IR coverage', refPath };
    }
    if (RX.fontFaceFonts.test(html)) {
        return { bucket: 'B', reason: '@font-face → /fonts/ asset needed', refPath };
    }
    if (RX.supports.test(html)) {
        return { bucket: 'B', reason: '@supports — IR is flat-property', refPath };
    }
    if (RX.container.test(html)) {
        return { bucket: 'B', reason: '@container — IR is flat-property', refPath };
    }
    if (RX.cssVar.test(html)) {
        return { bucket: 'B', reason: 'var(--…) — runtime-dependent per CLAUDE.md', refPath };
    }
    // calc() handling: only a non-px unit inside calc() pushes to B.
    // Pure pixel arithmetic stays in A because CLAUDE.md says we can
    // pre-resolve absolute lengths.
    if (RX.calcCall.test(html) && RX.calcNonPx.test(html)) {
        return { bucket: 'B', reason: 'calc() with non-px units — runtime-dependent', refPath };
    }
    if (RX.viewportUnit.test(html)) {
        return { bucket: 'B', reason: 'viewport units (vw/vh/...) — IR null per CLAUDE.md', refPath };
    }

    // Default: cleanly convertible. Caller still needs to verify the
    // ref file exists on disk (row 12) before promoting to A.
    return { bucket: 'A', refPath };
}

// ---------------------------------------------------------------------------
// Streaming directory walk. Yields absolute paths of *.html files under
// CSS_ROOT, one at a time. We use an async generator so the consumer
// can apply backpressure (the worker pool below).
// ---------------------------------------------------------------------------
async function* walkHtml(dir) {
    let entries;
    try {
        entries = await fs.readdir(dir, { withFileTypes: true });
    } catch (err) {
        // ENOENT means a sparse-checkout pruned the dir under us; not
        // an error from the bucketer's perspective.
        if (err.code === 'ENOENT') return;
        throw err;
    }
    // Stable ordering — easier diffing of wpt-buckets.json across re-pins.
    entries.sort((a, b) => a.name.localeCompare(b.name));
    for (const ent of entries) {
        const full = join(dir, ent.name);
        if (ent.isDirectory()) {
            // Skip dot-dirs (.git in case the corpus was pulled non-sparse).
            if (ent.name.startsWith('.')) continue;
            yield* walkHtml(full);
        } else if (ent.isFile() && ent.name.endsWith('.html')) {
            yield full;
        }
    }
}

// ---------------------------------------------------------------------------
// Bounded-concurrency map. Reads ~12 files in parallel — empirically
// the sweet spot between IO concurrency and FD pressure on macOS
// (default ulimit -n is 256). Higher concurrency tips the cost balance
// against context-switch overhead and risks EMFILE.
// ---------------------------------------------------------------------------
async function processAll(files, worker, concurrency = 12) {
    // Track results in input order so the eventual JSON is stable.
    const out = new Array(files.length);
    let next = 0;
    // Spawn `concurrency` long-lived runners that pull indices off the
    // shared `next` counter — classic pull-based parallelism.
    async function runner() {
        for (;;) {
            const i = next++;
            if (i >= files.length) return;
            out[i] = await worker(files[i], i);
        }
    }
    const runners = [];
    for (let i = 0; i < concurrency; i++) runners.push(runner());
    await Promise.all(runners);
    return out;
}

// ---------------------------------------------------------------------------
// Resolve the WPT pin SHA. Same source-of-truth resolution as
// fetch-wpt.sh: env override > WPT_REF file. We re-read to avoid
// importing a shell script.
// ---------------------------------------------------------------------------
async function resolveWptRef() {
    if (process.env.WPT_REF) return process.env.WPT_REF;
    const refFile = join(__dirname, 'WPT_REF');
    const raw = await fs.readFile(refFile, 'utf8');
    // Strip comment + blank lines exactly like fetch-wpt.sh.
    for (const line of raw.split('\n')) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        return trimmed.split(/\s+/)[0];
    }
    throw new Error('WPT_REF file contains no SHA line');
}

// ---------------------------------------------------------------------------
// Main.
// ---------------------------------------------------------------------------
async function main() {
    // Verify the corpus is materialised. A friendly error here saves
    // contributors from a cryptic ENOENT at the first readFile.
    try {
        await fs.access(CSS_ROOT);
    } catch {
        console.error(`bucket-wpt: ${CSS_ROOT} missing — run tools/titan/fetch-wpt.sh first`);
        process.exit(1);
    }

    const wptRef = await resolveWptRef();
    const generatedAt = new Date().toISOString();

    // Phase 1: collect all HTML paths up front. We need the count for
    // progress output and there are only ~24k entries; keeping the
    // filename list in memory is fine (<1 MB).
    const t0 = Date.now();
    process.stderr.write('bucket-wpt: walking tools/wpt/css/ ...\n');
    const files = [];
    for await (const f of walkHtml(CSS_ROOT)) files.push(f);
    process.stderr.write(`bucket-wpt: found ${files.length} *.html files in ${(Date.now() - t0) / 1000}s\n`);

    // Phase 2: classify each file with bounded concurrency. Each worker
    // reads the file, runs the regex panel, and (only for non-C
    // candidates) async-checks the ref file's existence on disk.
    const tClassify = Date.now();
    const records = await processAll(files, async (absPath) => {
        // Path stored in the bucket index is repo-relative + posix-style
        // for cross-platform stability. Section 4.1 example uses
        // "css/css-backgrounds/...".
        const rel = relative(WPT_DIR, absPath).split(sep).join('/');
        let html;
        try {
            html = await fs.readFile(absPath, 'utf8');
        } catch (err) {
            // A read failure is a bucket-C signal in itself: we can't
            // classify what we can't read.
            return { rel, bucket: 'C', reason: `read error: ${err.code || err.message}` };
        }
        const cls = classify(html, absPath);

        // Bucket-A / B require the ref file to exist on disk (Section 4.1
        // row 12). If it's missing, demote to C.
        if (cls.bucket !== 'C' && cls.refPath) {
            try {
                await fs.access(cls.refPath);
            } catch {
                return { rel, bucket: 'C', reason: 'rel="match" ref file missing on disk' };
            }
        }

        // Architectural-exclusion gate (swarm-001 17 rules). Runs on EVERY
        // test regardless of bucket, but the practical effect is to flag
        // bucket-A and bucket-B tests that should be excluded from SSIM
        // gating because they exercise web-platform features the IR
        // doesn't model. Bucket-C tests can also carry tags (they'd be
        // excluded for two independent reasons), but the dashboard prefers
        // the bucket-C reason since it's already "won't run".
        //
        // Refs: only resolve refRel for crash-test-blank-ref (the only
        // rule that needs the ref filename). Reading the full ref HTML
        // would double the disk-IO cost; the ref filename is enough.
        const refRel = cls.refPath
            ? relative(WPT_DIR, cls.refPath).split(sep).join('/')
            : '';
        const tags = architecturalTagsForTest({
            html,
            testRel: rel,
            refPath: cls.refPath ?? '',
            refRel,
        });

        return { rel, bucket: cls.bucket, reason: cls.reason, tags };
    });

    // Phase 3: assemble the spec-shape JSON (Section 4.1 example).
    // We sort each bucket so the file diffs cleanly on re-pin — order
    // matters for git-blame attribution. Tallies derive from `records`
    // — single source of truth — so the demotion path (B→C when ref is
    // missing) can't drift from the on-disk counts.
    const buckets = { A: [], B: [], C: [] };
    const reasons = {};
    // notApplicable: { test-rel-path: [tag, tag, ...] } — set by the
    // architectural-exclusion pass (swarm-001's 17 rules). Tests with
    // a non-empty tag list should be excluded from SSIM gating EVEN IF
    // they're bucket-A; the test exercises a web-platform feature the IR
    // doesn't model. Backward-compat: this is an additive top-level
    // field, existing consumers (extract-fixture.mjs, inject-wpt-block.mjs,
    // section-runner.sh) ignore it and keep working as before.
    const notApplicable = {};
    // Per-tag count histogram for the final report and external dashboards.
    const notApplicableTagHistogram = {};
    // Per-spec-section histogram: 3 counters per section. This is bonus
    // data that downstream phases would otherwise have to compute.
    const bySpecSection = {};
    let cA = 0, cB = 0, cC = 0;
    let cNotApplicable = 0;
    for (const rec of records) {
        buckets[rec.bucket].push(rec.rel);
        if (rec.bucket === 'A') cA++;
        else if (rec.bucket === 'B') cB++;
        else cC++;
        // Reasons: per spec, a per-test entry. We record reasons for B
        // and C; A entries don't carry one (the absence is the signal).
        if (rec.reason) reasons[rec.rel] = rec.reason;
        // Architectural-exclusion tags: record on EVERY test that matches
        // (incl. bucket-C tests), so the dashboard can audit "of bucket-A
        // structural-divergence failures, how many would have been
        // pre-excluded by the auto-bucketer". Tag list is sorted for stable
        // diffs.
        if (rec.tags && rec.tags.length > 0) {
            const sortedTags = [...rec.tags].sort();
            notApplicable[rec.rel] = sortedTags;
            cNotApplicable++;
            for (const t of sortedTags) {
                notApplicableTagHistogram[t] = (notApplicableTagHistogram[t] ?? 0) + 1;
            }
        }
        const sec = specSectionOf(rec.rel);
        if (!bySpecSection[sec]) bySpecSection[sec] = { A: 0, B: 0, C: 0 };
        bySpecSection[sec][rec.bucket]++;
    }
    for (const k of Object.keys(buckets)) buckets[k].sort();
    process.stderr.write(
        `bucket-wpt: classified in ${(Date.now() - tClassify) / 1000}s ` +
        `(A=${cA} B=${cB} C=${cC} notApplicable=${cNotApplicable})\n`
    );

    // Final JSON shape mirrors Section 4.1's example, plus the
    // bySpecSection convenience block (additive — Phase 1 can ignore
    // it without breaking) and the new notApplicable architectural-
    // exclusion map.
    const out = {
        wptRef,
        generatedAt,
        wptDir: relative(REPO_ROOT, WPT_DIR).split(sep).join('/'),
        totals: {
            A: cA,
            B: cB,
            C: cC,
            all: cA + cB + cC,
            // notApplicable count is a separate axis (orthogonal to A/B/C);
            // a test in bucket-A can ALSO be notApplicable. Sum over the 3
            // buckets stays equal to `all` so legacy consumers' arithmetic
            // doesn't break.
            notApplicable: cNotApplicable,
        },
        bySpecSection,
        buckets,
        reasons,
        notApplicable,
        notApplicableTagHistogram,
    };

    // Stable JSON formatting: 2-space indent, sorted top-level keys via
    // explicit key order. Trailing newline so the file plays nice with
    // POSIX tooling.
    await fs.writeFile(OUT_PATH, JSON.stringify(out, null, 2) + '\n', 'utf8');
    process.stderr.write(`bucket-wpt: wrote ${OUT_PATH}\n`);

    // Top reason histogram for the final report — printed to stdout so
    // the caller can grep for it. Sorted by count desc, ties by
    // alphabetical reason.
    const reasonHist = {};
    for (const r of Object.values(reasons)) reasonHist[r] = (reasonHist[r] || 0) + 1;
    const top = Object.entries(reasonHist).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
    process.stdout.write(`\nTotals: A=${cA} B=${cB} C=${cC} (total ${cA + cB + cC})  notApplicable=${cNotApplicable}\n`);
    process.stdout.write(`\nTop bucket reasons (descending):\n`);
    for (const [reason, n] of top.slice(0, 12)) {
        process.stdout.write(`  ${String(n).padStart(6)}  ${reason}\n`);
    }

    // Per-tag histogram for the architectural-exclusion classifier.
    // Always print all 17 rule tags (even those with zero hits) so the
    // re-pin diff makes a regression visible.
    const tagEntries = Object.entries(notApplicableTagHistogram)
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
    process.stdout.write(`\nArchitectural-exclusion tag hit counts (notApplicable rules):\n`);
    if (tagEntries.length === 0) {
        process.stdout.write(`  (no architectural-exclusion tags fired)\n`);
    } else {
        for (const [tag, n] of tagEntries) {
            process.stdout.write(`  ${String(n).padStart(6)}  ${tag}\n`);
        }
    }
    process.stdout.write(`\nDone in ${(Date.now() - t0) / 1000}s.\n`);
}

main().catch((err) => {
    console.error('bucket-wpt: fatal:', err);
    process.exit(2);
});

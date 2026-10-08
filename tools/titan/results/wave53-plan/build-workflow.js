export const meta = {
  name: 'wave53-build',
  description: 'Wave 53 builders (five lanes on the shared tree, disjoint ownership, seam patches), one executed-repro skeptic per lane, fix lanes, re-verification; file-driven and resumable',
  phases: [
    { title: 'Build', detail: 'L1 lists-bakes · L2 soft-hyphen · L3 canvas-root · L4 float-avoid · L5 harness-hygiene', model: 'opus' },
    { title: 'Skeptics', detail: 'one per lane: own census, executed repros, mutation replay', model: 'opus' },
    { title: 'Fix', detail: 'must-fix defects, then re-verification', model: 'opus' },
  ],
}
// args: { tree, head, done?: ['L1', …], only?: ['L2'], model? }
const T = args.tree
const HEAD = args.head
const DONE = new Set(args.done || [])
const ONLY = args.only ? new Set(args.only) : null
const M = args.model || 'opus'
const PLAN = `${T}/tools/titan/results/wave53-plan/PLAN.md`
const EXP = `${T}/tools/titan/results/wave53-plan/expectations.json`
const G52 = `${T}/tools/titan/results/wave52-gate`
const L = { L1: 'lists-bakes', L2: 'soft-hyphen', L3: 'canvas-root', L4: 'float-avoid', L5: 'harness-hygiene' }
const SECTION = { L1: '### L1 · lists-bakes', L2: '### L2 · soft-hyphen', L3: '### L3 · canvas-root', L4: '### L4 · float-avoid', L5: '### L5 · harness-hygiene' }
const OUT = (id) => `${T}/tools/titan/results/wave53-${L[id]}`

const RULES = (id) => `HARD RULES — the shared tree ${T}, branch campaign/applier-campaign, HEAD ${HEAD} (= dev cdb8a845 + the committed wave-53 plan). Four other lanes edit this tree at the same time under DISJOINT ownership. Read ${PLAN} §0 (rules), §1, YOUR section ("${SECTION[id]}" in §2), §3 (seam registry), §4 (landing order), §6 (what the closing gate will check) and your briefs under ${T}/tools/titan/results/wave53-plan/ before touching anything.
(0) RESUME FIRST: if ${OUT(id)}/_note.md exists, read it and \`git -C ${T} status --short\` / \`git -C ${T} diff\` on your OWNED paths — a dead earlier attempt may have left partial edits or an un-restored mutation. Continue, do not restart.
(1) OWNERSHIP: touch only the paths in your lane's "own:" list (and ${OUT(id)}/). The plan says "private worktrees"; the ORCHESTRATOR DECIDED (recorded in PLAN §10) that lanes work on the SHARED tree instead, as wave 52 did: no worktree of your own, no git commit / checkout / stash / reset (the orchestrator commits each lane's revert units from its ownership list in the §4 landing order). A change needed in a file you do not own is a HUNK for its owner, written to ${OUT(id)}/hunk-for-<lane>-<n>.patch with a header naming the base.
(2) SEAM FILES are never edited (runtimes/compose/…/core/renderer/ComponentRenderer.kt, runtimes/swiftui/…/Renderer/ComponentRenderer.swift, apps/web-harness/src/sdui/ComponentRenderer.tsx, tools/titan/extract-fixture.mjs). A lane that needs one delivers ${OUT(id)}/seam-<n>.patch exactly as §3 registers it, applying clean on ${HEAD} (\`git apply --check\`), and VERIFIES its pins with the patch applied under the per-file lock: \`mkdir ${T}/tools/titan/runs/wave53-lock/<basename>\` (mkdir is the mutex — if it exists, wait 60 s and retry), apply, run the focused suite, then restore the seam file byte-exact (\`git -C ${T} show HEAD:<path> > <path>\`, sha256 before/after in your note), then \`rmdir\` the lock. Never leave a seam file modified.
(3) ANOTHER LANE'S IN-FLIGHT EDIT may break compilation of a module you share. If the compile error is in a file you do not own, wait 3 minutes and retry (up to 5 times); never touch it. Kotlin IC-cache errors: delete ${T}/runtimes/compose/build/kotlin and retry.
(4) HOST: NO device, NO Chromium, NO emulator/simulator, no test-all.sh, no section-runner, no feed-*.mjs, no puppeteer, no full suites. Focused tests only — Compose \`(cd ${T}/apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '<Class>')\` (harness code: :app:testDebugUnitTest); SwiftUI from ${T}: \`xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -only-testing:StyleConverterRuntimeTests/<Class>\`; web \`npx vitest run <path>\`; web-harness \`npm -w apps/web-harness run test -- <path>\`; tooling \`node --test <file>\`; converter \`./gradlew :converter:test --tests '<filter>'\`. Anything the plan marks as an ORCHESTRATOR WINDOW (a CDP probe, a gate-flag re-extraction, a simulator XCTest run, the hh-probe, an exclusive Gradle window) you do NOT run: write the exact command(s) and what to look for into ${OUT(id)}/_note.md under "## ORCHESTRATOR WINDOW REQUESTS" and continue with everything that does not depend on them.
(5) VERIFICATION OWED (${PLAN} §0 + your §2 entry): pins on the VERBATIM per-test IR from ${T}/tools/titan/runs/wave52-ship/sections/*/per-test-ir/ (byte-identical to wave53-open), every pin PROVEN able to fail by an EXECUTED mutation of the mechanism — red → restore byte-exact (sha256 logged) → green — recorded in your note; your own blast-radius census (which corpus documents / captures your change can reach — the lane's CARRIER SET for control-check.mjs — and how many pass today), cross-checked against ${EXP} for your lane; predictions per target cell with confidence; must-not-move cells. LOOK AT THE PNGS (reference under ${T}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/<section>/, capture under ${T}/tools/titan/runs/wave52-ship/sections/<section>/<dir>/) before and after reasoning about a fix.
(6) HOUSE RULES: every line commented (the WHY; CSS spec section or parser file for extractors, platform API for appliers); new logic in NEW files ≤ 200 lines (an oversized file gets only a call site); no silent fallthrough (PropertyTracker breadcrumb or TODO); "runtime", never "engine"; honest labels (DEGENERATE is never a fix).
(7) DURABLE NOTE ${OUT(id)}/_note.md (create the dir): what changed and why with file:symbol pointers; the census with its method; pins + executed mutations (red/green, sha256); predictions with confidence; must-not-move cells; hand-offs (seam patches, hunks, orchestrator window requests); what you could NOT verify; a \`TREES:\` line (${T}); and the last line \`STATUS: COMPLETE\` or \`STATUS: PARTIAL — <what is left>\`. Probe scripts you want kept go in ${OUT(id)}/ (never a scratchpad path in the note). Grep your own diff for "probe"/"Probe" leftovers in owned source files before finishing.
(8) Report DATA per the schema; be honest in notVerified.
`

const LANE = {
  type: 'object',
  properties: {
    lane: { type: 'string' }, status: { type: 'string', enum: ['COMPLETE', 'PARTIAL'] }, summary: { type: 'string' },
    filesChanged: { type: 'array', items: { type: 'string' } },
    seamPatches: { type: 'array', items: { type: 'string' } },
    windowRequests: { type: 'array', items: { type: 'string' }, description: 'commands the orchestrator must run in a device-idle window, verbatim' },
    pins: { type: 'array', items: { type: 'object', properties: { test: { type: 'string' }, mutation: { type: 'string' }, result: { type: 'string' } }, required: ['test', 'mutation', 'result'] } },
    predictedFlips: { type: 'array', items: { type: 'string' } },
    notVerified: { type: 'array', items: { type: 'string' } },
    remaining: { type: 'array', items: { type: 'string' } },
  },
  required: ['lane', 'status', 'summary', 'filesChanged', 'seamPatches', 'windowRequests', 'pins', 'predictedFlips', 'notVerified'],
}
const SKEPTIC = {
  type: 'object',
  properties: {
    skeptic: { type: 'string' }, verdict: { type: 'string', enum: ['CLAIM-HOLDS', 'MIXED', 'DEFECT-CONFIRMED'] },
    executedRepros: { type: 'array', items: { type: 'string' } },
    defects: { type: 'array', items: { type: 'object', properties: { severity: { type: 'string', enum: ['must-fix', 'should-fix', 'nit'] }, file: { type: 'string' }, what: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' } }, required: ['severity', 'file', 'what', 'evidence', 'fix'] } },
    mustFixBeforeGate: { type: 'boolean' },
  },
  required: ['skeptic', 'verdict', 'executedRepros', 'defects', 'mustFixBeforeGate'],
}

const buildPrompt = (id) => `You are builder lane ${id} (${L[id]}) of wave 53 of the Style-Converter applier campaign — a VERIFICATION-FIRST wave: a few lanes, each aimed at a picture that is wrong today, each with the mechanism traced in the brief before code.
${RULES(id)}
YOUR LANE: ${PLAN} "${SECTION[id]}" is your contract — targets, mechanism, the change, spec citation, ownership, carrier set, pins + mutations, predictions, must-not-move cells, what you may run. The briefs it cites sit beside it (with censuses, geometry probes and, for L1, a measured draft patch). Build exactly that; where the brief and the tree disagree, the tree wins and your note says so. Deliver seam patches as registered in §3. Finish with the note and the structured report.`

const skepticPrompt = (id) => `You are the adversarial SKEPTIC of builder lane ${id} (${L[id]}) of wave 53 of the Style-Converter applier campaign. Read ${OUT(id)}/_note.md, the lane's §2 contract in ${PLAN} ("${SECTION[id]}"), its briefs, and its diff on its owned paths (\`git -C ${T} diff -- <owned paths>\`, plus its new files and patches under ${OUT(id)}/). Your job is to find what is WRONG, by EXECUTED repros — never by reading for plausibility.
${RULES(id).replace('You are builder lane', 'You are the skeptic of')}
MUST DO: (a) replay every pin's mutation yourself (apply, run the focused test, confirm red, restore byte-exact, confirm green); (b) re-derive the lane's blast-radius census with YOUR OWN script over the per-test IR / sources (never the lane's script) and compare counts — an under-reported radius is the dangerous direction; (c) check every claimed verdict/score with \`node ${G52}/cells.mjs '<terms>' wave52-ship\`; (d) open the PNGs the lane reasoned from and check the described geometry; (e) look for silent fallthroughs, untested branches, files over 200 lines of new logic, uncommented lines, scratchpad pointers, probe leftovers, edits outside ownership (git status), seam files left modified, lock dirs left behind; (f) check the seam patch applies clean on HEAD and that the lane's pins were run with it applied; (g) honesty: are predicted flips labelled DEGENERATE where the picture would still be wrong? Write ${OUT(id)}/skeptic.md (verdict, repros with outputs, defects ranked, what you could not check) and return the schema.`

const fixPrompt = (id, defects) => `You are the FIX lane for builder lane ${id} (${L[id]}) of wave 53 of the Style-Converter applier campaign. The skeptic found these defects with executed evidence (${OUT(id)}/skeptic.md has the full text):
${JSON.stringify(defects, null, 1)}
${RULES(id).replace('You are builder lane', 'You are the fix lane of')}
Re-run each skeptic check first; fix every must-fix (and should-fix when safe) inside the lane's ownership; re-run the touched focused suites; re-execute the affected mutations; append a "## Fix pass" section to ${OUT(id)}/_note.md (defect → action or refusal with reason) and keep the STATUS line true. Return the lane schema.`

const reverifyPrompt = (id) => `You are the RE-VERIFIER of builder lane ${id} (${L[id]}) of wave 53. ${OUT(id)}/skeptic.md lists must-fix defects and ${OUT(id)}/_note.md "## Fix pass" claims each is fixed. For EACH must-fix: re-run the skeptic's own check and report fixed / not fixed with the command and output. Also confirm: no seam file modified (\`git -C ${T} status --short -- <the four seam files>\` empty), no lock dir left under ${T}/tools/titan/runs/wave53-lock/, no probe leftovers in owned sources. Append "## Re-verification" to ${OUT(id)}/_note.md. Return the skeptic schema (verdict CLAIM-HOLDS when everything is fixed).
${RULES(id).replace('You are builder lane', 'You are the re-verifier of')}`

const ids = Object.keys(L).filter((id) => !DONE.has(id) && (!ONLY || ONLY.has(id)))
log(`lanes this run: ${ids.join(' ')} (done: ${[...DONE].join(' ') || 'none'})`)

phase('Build')
const results = await parallel(ids.map((id) => async () => {
  const built = await agent(buildPrompt(id), { label: `build:${id}-${L[id]}`, phase: 'Build', model: M, schema: LANE })
  if (!built) return { id, built: null }
  const sk = await agent(skepticPrompt(id), { label: `skeptic:${id}`, phase: 'Skeptics', model: M, schema: SKEPTIC })
  if (!sk || !sk.mustFixBeforeGate) return { id, built, skeptic: sk }
  const must = sk.defects.filter((d) => d.severity !== 'nit')
  const fixed = await agent(fixPrompt(id, must), { label: `fix:${id}`, phase: 'Fix', model: M, schema: LANE })
  const re = await agent(reverifyPrompt(id), { label: `reverify:${id}`, phase: 'Fix', model: M, schema: SKEPTIC })
  return { id, built, skeptic: sk, fixed, reverified: re }
}))
return { lanes: results.filter(Boolean).map((r) => ({ id: r.id, status: r.built?.status, summary: r.built?.summary?.slice(0, 400), seamPatches: r.built?.seamPatches, windowRequests: r.built?.windowRequests, skeptic: r.skeptic?.verdict, mustFix: r.skeptic?.mustFixBeforeGate, defects: (r.skeptic?.defects || []).filter((d) => d.severity === 'must-fix').map((d) => d.what.slice(0, 160)), fixed: r.fixed?.status, reverified: r.reverified?.verdict, notVerified: r.built?.notVerified })) }

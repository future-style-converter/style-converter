#!/usr/bin/env python3
# skeptic-census.py — the L7 (instruments) SKEPTIC's own blast-radius census, written independently of the lane's
# census.mjs and of the brief's instruments.census.mjs (PLAN §0 "Skeptic census").
#
# Usage: /opt/homebrew/bin/python3.12 -B skeptic-census.py <shared-tree> <worktree-with-L7-patches-applied> <patch-dir>
# Reads: the eight delivered unit patches, the gate's three entry scripts (closure over every tools/ basename their
# text names — an OVER-approximation, the safe direction for a radius), wave54-final's manifest + extract logs + WPT
# sources, expectations.json lanes["L7-instruments"], landing-manifest.json. Writes nothing; prints the census.
import json, os, re, subprocess, sys, hashlib, glob, collections

SH, WT, PD = sys.argv[1:4]                                   # shared tree (runs, wpt), applied worktree, patch dir
PLAN = os.path.join(SH, 'tools/titan/results/wave55-plan')   # expectations.json + landing-manifest.json
RUN = os.path.join(SH, 'tools/titan/runs/wave54-final')      # the run of record (= wave55-open's IR)
out = []                                                     # printed at the end, one census line per entry
say = out.append

# ---- (1) the touched paths, per unit, read from the patch headers -------------------------------------------------
units = {}                                                   # unit → [paths]
for p in sorted(glob.glob(os.path.join(PD, 'unit-*.patch'))):
    u = os.path.basename(p)[5:-6]                            # unit-U6-web.patch → U6-web
    units[u] = re.findall(r'^diff --git a/(\S+) b/\1$', open(p).read(), re.M)
say('(1) touched paths per unit (from the patch headers):')
for u, ps in units.items(): say(f'    {u}: {len(ps)} — ' + ' '.join(ps))
SEAMS = {'runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt',
         'runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift',
         'apps/web-harness/src/sdui/ComponentRenderer.tsx', 'tools/titan/extract-fixture.mjs'}
allp = [x for ps in units.values() for x in ps]
say(f'    seam files touched: {sorted(SEAMS & set(allp)) or "none"} · paths in two units: '
    f'{[k for k, v in collections.Counter(allp).items() if v > 1] or "none"} · lane-records-dir writes: '
    f'{[x for x in allp if re.match(r"tools/titan/results/wave55-(?!gate/|plan/)", x)] or "none"}')

# ---- (2) gate reach: closure from the three entry scripts over every tools/ script basename their text names ---------
idx = collections.defaultdict(set)                           # basename → repo paths (tools/, results/runs excluded)
for root in ('tools/titan', 'tools/visual', 'apps/web-harness', 'apps/android-harness', 'apps/ios-harness', '.'):
    for f in glob.glob(os.path.join(WT, root, '*')):
        if re.search(r'\.(mjs|js|cjs|sh|py)$', f) and os.path.isfile(f): idx[os.path.basename(f)].add(os.path.relpath(f, WT))
seen, todo = set(), ['tools/titan/gate-driver.sh', 'tools/titan/section-runner.sh', 'test-all.sh']
while todo:
    f = todo.pop()
    if f in seen: continue
    seen.add(f)
    txt = open(os.path.join(WT, f), errors='replace').read()
    for b in set(re.findall(r'([\w.-]+\.(?:mjs|js|cjs|sh|py))\b', txt)):
        for q in idx.get(b, ()): todo.append(q)
say(f'(2) gate reach: {len(seen)} scripts in the closure of gate-driver.sh / section-runner.sh / test-all.sh')
BUILT = ('runtimes/web/src/', 'runtimes/compose/src/main/', 'runtimes/swiftui/Sources/', 'apps/web-harness/src/',
         'apps/android-harness/app/src/main/', 'apps/ios-harness/', 'converter/src/main/', 'Package.swift')
for u, ps in units.items():
    for x in ps:
        cls = 'EXECUTED by the gate' if x in seen else ('BUILT into captures' if x.startswith(BUILT) else 'off the gate route')
        if cls != 'off the gate route': say(f'    {u} {x}: {cls}')
say('    every other touched path: off the gate route (tests / prose / records / tools no gate script names)')

# ---- (3) what the executed / built bytes are: TS comment-stripped emit compared pre vs post ----------------------------
def head(path):                                              # the file at 611c9f93 (HEAD of the worktree)
    return subprocess.run(['git', '-C', WT, 'show', f'HEAD:{path}'], capture_output=True, text=True).stdout
# TypeScript 7 ships no JS API: strip with the toolchain vite already runs (rolldown/oxc) — TS → JS, then a
# compress-free, mangle-free minify, which drops every comment and normalises whitespace and nothing else.
STRIP = ("import {transformSync, minifySync} from 'rolldown/experimental'; import fs from 'node:fs';"
         "const n=process.argv[1], s=fs.readFileSync(0,'utf8'); const t=transformSync(n, s, {});"
         "if (t.errors?.length) { console.error(JSON.stringify(t.errors)); process.exit(1); }"
         "const m=minifySync(n.replace(/\\.tsx?$/, '.js'), t.code, {compress:false, mangle:false});"
         "if (m.errors?.length || !m.code) { console.error('minify failed'); process.exit(1); } process.stdout.write(m.code)")
def strip(name, code):                                       # → the comment-free emit; a failure is never "identical"
    r = subprocess.run(['node', '--input-type=module', '-e', STRIP, name], input=code, capture_output=True, text=True, cwd=SH)
    if r.returncode != 0 or not r.stdout: raise SystemExit(f'strip failed on {name}: {r.stderr[:300]}')
    return r.stdout
for u, ps in units.items():
    for x in ps:
        if not (x in seen or x.startswith(BUILT)): continue
        pre, post = head(x), open(os.path.join(WT, x)).read()
        if x.endswith(('.ts', '.tsx', '.mjs', '.js')):
            a, b = strip(os.path.basename(x), pre), strip(os.path.basename(x), post)
            if a == b: say(f'(3) {x}: comment-stripped emit IDENTICAL → COMMENTS ONLY')
            else:
                la, lb = a.replace(';', ';\n').splitlines(), b.replace(';', ';\n').splitlines()
                ch = [(i, la[i] if i < len(la) else None, lb[i] if i < len(lb) else None) for i in range(max(len(la), len(lb)))
                      if (la[i] if i < len(la) else None) != (lb[i] if i < len(lb) else None)]
                strs = all(l1 is not None and l2 is not None and re.sub(r"(['\"`]).*?\1", 'S', l1) == re.sub(r"(['\"`]).*?\1", 'S', l2) for _, l1, l2 in ch)
                say(f'(3) {x}: emit differs on {len(ch)} line(s) → {"STRING LITERALS ONLY" if strs else "CODE"}')
                for i, l1, l2 in ch[:4]: say(f'      - {str(l1).strip()[:150]}\n      + {str(l2).strip()[:150]}')

# ---- (4) U5's radius: the bidi-baked documents of wave54-final, their list items, mask / mix-blend mentions ----------
man = json.load(open(os.path.join(RUN, 'manifest.json')))
res = man['wpt']['results']                                  # test path → per-test record (bidiBaked, …)
baked = sorted(t for t, r in res.items() if isinstance(r, dict) and r.get('bidiBaked') is True)
src = lambda t: open(os.path.join(SH, 'tools/wpt', t), errors='replace').read()
LI = re.compile(r'<li\b|list-item', re.I)                    # a list item (element or display)
FX = re.compile(r'mask|mix-blend-mode', re.I)                # the two group effects U5 reads (incl. -webkit-mask)
say(f'(4) U5 radius: {len(baked)} bidi-baked documents in {len({t.split("/")[1] for t in baked})} sections '
    f'({", ".join(sorted({t.split("/")[1] for t in baked}))})')
logs = {}                                                    # test → its extract.log line(s)
for lg in glob.glob(os.path.join(RUN, 'sections/*/extract.log')):
    for line in open(lg, errors='replace'):
        m = re.match(r'^extracted (\S+)', line)
        if m: logs[m.group(1)] = line.strip()
for t in baked:
    s = src(t); line = logs.get(t, '')
    bb = re.search(r'\[bidi-bake: [^\]]*\]', line); cb = re.search(r'\[counter-bake: [^\]]*\]', line)
    say(f'    {t}: list-item {bool(LI.search(s))} · mask/mix-blend {bool(FX.search(s))} · '
        f'{bb.group(0) if bb else "(no bidi-bake stamp)"} {cb.group(0) if cb else ""}')
allsrc = [t for t in res if os.path.exists(os.path.join(SH, 'tools/wpt', t))]
both = [t for t in allsrc if LI.search(src(t)) and FX.search(src(t))]
say(f'    over all {len(allsrc)} corpus sources: list item AND mask/mix-blend: {len(both)} {both[:8]}')
nb = [t for t in both if t not in baked]
say(f'    of those, not bidi-baked at wave54-final (U5 cannot reach them: the paint read gates only a PLANNED marker of a '
    f'baked root, bidi-bake.mjs planBidiBake): {len(nb)}')

# ---- (5) carriers vs expectations.json lanes["L7-instruments"] and the landing manifest -------------------------------
L = json.load(open(os.path.join(PLAN, 'expectations.json')))['lanes']['L7-instruments']
say(f'(5) expectations captureCarriers {json.dumps(L["captureCarriers"])} · wireCarriers {L["wireCarriers"]} · '
    f'revertUnits {sorted(L["revertUnits"])} (each captures 0/0/0, wire [])')
lm = [u for u in json.load(open(os.path.join(PLAN, 'landing-manifest.json')))['units'] if u['lane'] == 'L7']
say(f'    landing-manifest L7 units: {[u["unit"] + ("(opt)" if u["kind"] == "optional" else "") for u in lm]}; '
    f'delivered patches: {sorted(units)}; manifest units with no patch: {[u["unit"] for u in lm if u["unit"] not in units]}')
mine_capture = {'web': [], 'ios': [], 'android': []}
say(f'    MY carriers: captures {mine_capture} · wire [] — PROVIDED [W-L7a] holds (U5 wire-identical); otherwise U5 does '
    f'not land. Delta vs expectations: {"IDENTICAL" if mine_capture == L["captureCarriers"] and not L["wireCarriers"] else "DIFFERS"}')
mnm = L['mustNotMove']
cov = collections.Counter('css/' + c.split(' ')[0] for c in mnm)   # the manifest keys carry the css/ prefix
say(f'    mustNotMove {len(mnm)} cells over {len(cov)} tests; bidi-baked tests with NO L7 must-not-move cell: '
    f'{[t for t in baked if t not in cov]}')
print('\n'.join(out))

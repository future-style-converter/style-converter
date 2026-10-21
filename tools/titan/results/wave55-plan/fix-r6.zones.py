#!/usr/bin/env python3
# tools/titan/results/wave55-plan/fix-r6.zones.py — derives and re-measures residual-read.py's recorded WINDOWS (round 6; D40).
#
# Why: residual-read.py's class zones (a colour its probe reads pixel for pixel, or the probe's own row bands) leave out the
# pixels a unit LEGITIMATELY changes in no such class — the anti-aliased corner arcs of the L2 clip boxes, the '0' glyphs of
# bidi-tab-001, clip-3's #middle band U2 grows (verify-r5 residual.py: 573 / 93 px at grow 1). Widening the class zone or a
# tolerance would re-admit the faint / off-colour paint round 5 found, so the zone is widened ONLY by the pixels the fixed
# pictures themselves change there: per cell, every pre-registered PASS picture of the standing set whose case id ends in
# `fixed` (probes-teeth.py + -r2: the briefs' replays, the arms, the wave54-probe device captures) is read against
# wave54-final with the cell's declaration MINUS its windows; the leftover px (outside ∪ vacated ∪ non-class) are clustered
# (8-connected after a 2-px dilation, so an arc's AA and its stray corner px join) and each cluster's bbox + 1 px is a window.
# Then every fixed picture of EVERY declared cell is re-read with the final declaration: 0 / 0 / 0 is required (exit 1).
# Usage: python3.12 -B fix-r6.zones.py            print the derivation and the re-measure (the record: fix-r6.zones.out.txt)
#        python3.12 -B fix-r6.zones.py --write    also rewrite residual-read.py's WINDOWS block (atomic: temp + os.replace)
#        python3.12 -B fix-r6.zones.py --check    exit 1 unless the installed WINDOWS literal is the derivation
#        … --also <run>:<cell>[,<cell>…]   (stage 1, PLAN §7 stage-1 step 8, D40) add that run's capture of the named cells as a
#        further fixed picture — ONLY for cells whose device line was decided right by a recorded look (§10): the windows then
#        cover the device's own legitimately-changed px; the class zones and every tolerance stay as they are
import hashlib, importlib.util, json, os, re, sys
sys.dont_write_bytecode = True
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def load(n, p):
    s = importlib.util.spec_from_file_location(n, os.path.join(HERE, p)); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m


RR = load('residual_read', 'residual-read.py')
pt = load('probes_teeth', 'probes-teeth.py'); r2 = load('probes_teeth_r2', 'probes-teeth-r2.py')
EXP = json.load(open(os.path.join(HERE, 'expectations.json')))
sha = lambda p: hashlib.sha256(open(os.path.join(HERE, p), 'rb').read()).hexdigest()[:16]
script = lambda pr: os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
CELL = {(script(pr), k): pr['keyCells'][k] for l in EXP['lanes'].values() for pr in l['geometryProbes'] for k in pr['expect']}
GATING = {(script(pr), k) for l in EXP['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
TALLY = {tuple(k.split(' ', 1)) for part in ('full', 'part', 'paintOrderOnly') for e in EXP['pictureCorrectTally'].get(part, []) for k in e['keys']}
cell_of = lambda sec, plat, test: f'{sec}/{test}.html {plat}'


def also_sources():
    """['<run>:<cell>', …]: residual-read.py's recorded WINDOWS_ALSO plus this invocation's --also <run>:<cell>[,<cell>…] (in order, deduplicated)."""
    out = list(getattr(RR, 'WINDOWS_ALSO', []))
    if '--also' in sys.argv:
        run, cells = sys.argv[sys.argv.index('--also') + 1].split(':', 1)
        out += [f'{run}:{c}' for c in cells.split(',')]
    return list(dict.fromkeys(out))


ALSO = also_sources()


def fixed_pictures():
    """{cell: [(case id, picture)]} — every pre-registered PASS picture whose case id ends in `fixed` (deduplicated), plus the
    --also run's captures of the cells it names (the stage-1 device pictures decided right by a look)."""
    out, seen = {}, set()
    # Stage 1: the --also sources are RECORDED in residual-read.py (WINDOWS_ALSO, inside the WINDOWS block --write rewrites), so a
    # later --check re-derives with the same device pictures; a new --also on the command line is appended to them.
    for src in ALSO:
        run, c = src.split(':', 1)
        if c not in RR.SPECS:
            raise SystemExit(f'--also: {c} is not a declared cell')
        F = RR.rgb(RR.capture_path(run, c))
        if F is None:
            raise SystemExit(f'--also: {run} has no capture of {c}')
        h = hashlib.sha1(F.tobytes()).hexdigest()
        if (c, h) not in seen:
            seen.add((c, h)); out.setdefault(c, []).append((f'{run} (decided by a look)', F))
    for L, b in (('L1', pt.l1_cases), ('L2', pt.l2_cases), ('L3', pt.l3_cases), ('L4', pt.l4_cases), ('L5', pt.l5_cases), ('L6', pt.l6_cases)):
        for lane, secs, fid, repl, want in b() + r2.cases(pt, L):
            if not fid.endswith('fixed') or not any(w == 'PASS' for w in want.values()):
                continue
            for (sec, plat, test), F in repl.items():
                c = cell_of(sec, plat, test)
                h = hashlib.sha1(F.tobytes()).hexdigest()
                if c in RR.SPECS and (c, h) not in seen:
                    seen.add((c, h)); out.setdefault(c, []).append((f'{lane} {fid}', F))
    return out


def clusters(m, join=2):
    """bboxes (x0, y0, x1, y1 inclusive) of m's pixels grouped by 8-connectivity after a `join`-px dilation."""
    g = RR.grow(m, join); lab = np.zeros(m.shape, np.int32); n = 0; H, W = m.shape
    for y0, x0 in zip(*np.nonzero(g)):
        if lab[y0, x0]:
            continue
        n += 1; st = [(y0, x0)]; lab[y0, x0] = n
        while st:
            y, x = st.pop()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < H and 0 <= xx < W and g[yy, xx] and not lab[yy, xx]:
                        lab[yy, xx] = n; st.append((yy, xx))
    out = []
    for k in range(1, n + 1):
        ys, xs = np.nonzero((lab == k) & m)
        if len(ys):
            out.append((int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()), int(len(ys))))
    return sorted(out)


say = print
FX = fixed_pictures()
say(f'# fix-r6.zones.py {sha("fix-r6.zones.py")} — residual-read.py {sha("residual-read.py")}, expectations.json {sha("expectations.json")}, '
    f'probes-teeth.py {sha("probes-teeth.py")}, probes-teeth-r2.py {sha("probes-teeth-r2.py")}; base wave54-final (= wave55-open byte-identical)')
keys = sorted(GATING | TALLY)
nospec = [k for k in keys if CELL[k] not in RR.SPECS]
say(f'\n## 1. coverage: {len(GATING)} gating + {len(TALLY - GATING)} tally-only keys = {len(keys)} keys on {len({CELL[k] for k in keys})} cells; '
    f'cells without a declaration: {len(nospec)}' + ''.join(f'\n   NO SPEC {k[0]} {k[1]} ({CELL[k]})' for k in nospec))
say('\n## 2. windows: each cell declaring `windows` read WITHOUT them; leftover clusters (bbox incl., px) → window = bbox + 1 px (half-open)')
derived = {}
for c in sorted(c for c, s in RR.SPECS.items() if 'windows' in s):
    spec = dict(RR.SPECS[c]); spec['windows'] = []
    m = None
    for fid, F in FX.get(c, []):
        B = RR.rgb(RR.capture_path('wave54-final', c))
        o, v, n, ms = RR.read_arrays(F, B, spec, cell=c)
        say(f'   {c:72} {fid:28} without windows: outside {o} · vacated {v} · non-class {n}')
        lm = ms[0] | ms[1] | ms[2]
        m = lm if m is None else (m | lm)
    cl = clusters(m) if m is not None and m.any() else []
    for x0, y0, x1, y1, npx in cl:
        say(f'      cluster x{x0}-{x1} y{y0}-{y1} ({npx} px)')
    if cl:
        derived[c] = [(max(0, x0 - 1), max(0, y0 - 1), x1 + 2, y1 + 2) for x0, y0, x1, y1, _ in cl]
lit = ('WINDOWS_ALSO = ' + repr(ALSO) + '   # stage 1 (D40): \'<run>:<cell>\' device captures decided right by a recorded look, window sources beside the fixed pictures (fix-r6.zones.py --also … --write records them here; --check re-derives with them)\n'
       + 'WINDOWS = {\n' + ''.join(f'    {c!r}: {w!r},\n' for c, w in sorted(derived.items())) + '}\n')
say('\n' + lit.rstrip())
src_path = os.path.join(HERE, 'residual-read.py'); src = open(src_path).read()
blk = re.search(r'(# WINDOWS_BEGIN\n)(.*?)(# WINDOWS_END)', src, re.S)
if '--write' in sys.argv:
    new = src[:blk.start(2)] + lit + src[blk.end(2):]
    if new != src:
        tmp = src_path + '.tmp-r6'; open(tmp, 'w').write(new); os.replace(tmp, src_path)
        say('   (--write: residual-read.py WINDOWS block rewritten atomically)')
    RR = load('residual_read_w', 'residual-read.py')
in_sync = blk.group(2) == lit or '--write' in sys.argv
say(f'   installed WINDOWS literal {"==" if in_sync else "!="} the derivation')
say('\n## 3. re-measure: every fixed picture of every declared cell with the FINAL declaration (outside / vacated / non-class; 0 / 0 / 0 required)')
bad = []
for c in sorted(FX):
    for fid, F in FX[c]:
        B = RR.rgb(RR.capture_path('wave54-final', c))
        o, v, n, _ = RR.read_arrays(F, B, RR.SPECS[c], cell=c)
        ks = [f'{k[1]}' for k in keys if CELL[k] == c]
        known = 'safe-002' in c
        if (o or v or n) and not known:
            bad.append((c, fid))
        say(f'   {"  " if not (o or v or n) else ("~~" if known else "!!")} {c:72} {fid:28} {o} / {v} / {n}   keys {len(ks)}'
            + ('   (round-3 N5: safe-002 has no passing fixed picture — its replay keeps 50 crossing px; not a window source)' if known else ''))
untested = sorted({CELL[k] for k in keys} - set(FX))
say('\n## 4. the anti-aliasing tolerances: the smallest value at which every fixed picture of the cells using it reads 0 (and with none)')
def _total(aa):
    t = 0
    for c in FX:
        if RR.SPECS[c].get('aa') == aa:
            B = RR.rgb(RR.capture_path('wave54-final', c))
            t += sum(sum(RR.read_arrays(F, B, RR.SPECS[c], cell=c)[:3]) for _, F in FX[c])
    return t
for name, aa, vals in (('NEUTRAL_TOL', 'neutral', (0, 1, 2)), ('RAMP_TOL', 'ramp', (0, 1, 3)), ('SEG_TOL', 'segment', (0, 1, 3)), ('BOX_TOL', 'box', (8, 10, 12, 13, 14))):
    old = getattr(RR, name); got = []
    for v in vals:
        setattr(RR, name, v)
        if name == 'NEUTRAL_TOL':
            RR.PRED['neutral'] = (lambda t: lambda a: (a.max(axis=2) - a.min(axis=2)) <= t)(v)
        got.append(f'{v} → {_total(aa)}')
    setattr(RR, name, old)
    if name == 'NEUTRAL_TOL':
        RR.PRED['neutral'] = lambda a: (a.max(axis=2) - a.min(axis=2)) <= RR.NEUTRAL_TOL
    keep = RR.SPECS; none = 0
    for c in FX:
        if keep[c].get('aa') == aa:
            sp = dict(keep[c]); sp['aa'] = None
            B = RR.rgb(RR.capture_path('wave54-final', c))
            none += sum(RR.read_arrays(F, B, sp, cell=c)[2] for _, F in FX[c])
    say(f'   {name:12} ({aa:8} cells) non-zero px over the fixed pictures at: {" · ".join(got)}; with no AA allowance: {none} non-class px; installed {old}')
say(f'\n   cells with no fixed picture: {untested or "none"}')
say(f'\nRESULT: {len(derived)} cells carry windows; {sum(len(v) for v in FX.values())} fixed pictures re-read, {len(bad)} not 0 / 0 / 0; '
    f'WINDOWS literal {"in sync" if in_sync else "STALE"}; cells without a declaration {len(nospec)}')
sys.exit(1 if bad or nospec or ('--check' in sys.argv and not in_sync) else 0)

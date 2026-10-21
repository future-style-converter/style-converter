#!/usr/bin/env python3
# Round-4 VERIFIER's own added-ink fakes (written independently of plan-skeptic-round-3.fakes/added_ink.py): four classes —
# vertical duplicate (VD), horizontal duplicate (HD), doubled glyph (DG), stray ink in the band (ST) — built from each key's
# OWN fixed picture (the standing set's `*fixed` cases = the briefs' replays / the wave54-probe device captures), written into
# a symlink-tree fake run of wave54-final under the scratch dir (probes-teeth.make_run) and read through the REAL
# geometry-gate.py on the generated expectations.json under the pinned interpreter (probes-teeth.gate).
#
# Every fake is STRICTLY ADDITIVE: per colour class c of the unit's own changed pixels (the top-2 SOLID exact colours of the
# pixels where the fixed picture differs from the wave54-final capture: at least 0.6 x the farthest changed colour's distance
# from the canvas, near-duplicates merged — an anti-aliasing grey is never a class, v3), the class's ink (pixels within 90
# of c) is copied / doubled / sprinkled, and a destination pixel that already is class-c ink is never written — no original
# ink is moved or removed (a 1-px "move" is not an added-ink fake; round 4's first draft produced such moves and was redone).
# Bands = the class's ink rows inside the diff's y-range ±2 (so unchanged ink the key also reads — dir-style row 7's text — is
# covered too). Offsets / placements differ from the fixer's on purpose: ±1 / ±2 / +4 / +9 y and ±1 / +2 / +3 / −4 / +10 x
# copies, per-band ±3 y / +2 / +5 x, a glyph doubled ABUTTING (+w+1), OVERLAPPING (+1 x smear) or BELOW itself, strays of
# 1 px / a 4×1 dash / a 1×4 bar / 2×2 / 3×3 at 3-7 px from the ink and a 1-px underline; plus whole-picture min() ghosts.
#
# NEW INK = pixels that become ink under either common probe mask (min channel < 200, or RGB sum < 300) and were not: a fake
# adding <= 2 new-ink px is listed apart (the 2-px floor every XOR / mass tolerance has); the rest are the must-fix candidates.
# Per faked capture: every row that READS it (key names the test and the platform) and PASSED on the fixed pictures is a
# reader; the readers that now FAIL caught it, by class. Reported per (fake, capture):
#   HOLE        no GATING or CONTROL reader moved (a report-row move alone changes no verdict);
#   CTRL-ONLY   only a control moved (loud, no revert) — adjudicated by zone in the verify report;
#   GATE-PASS   a gating key under test PASSes while another reader caught it — adjudicated by zone.
# Usage: python3.12 -B fakes4.py --scratch <abs dir> [--groups L1,L4] [--out file]
import hashlib, importlib.util, json, os, sys
sys.dont_write_bytecode = True
import numpy as np

P = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))     # round-4 fix: the plan dir this copy sits under (the verifier's hard-coded the shared tree)
def load(n, p):
    s = importlib.util.spec_from_file_location(n, p); m = importlib.util.module_from_spec(s); s.loader.exec_module(m); return m
pt = load('pt', f'{P}/probes-teeth.py'); r2 = load('r2', f'{P}/probes-teeth-r2.py')
EXP = f'{P}/expectations.json'; PY = ['/opt/homebrew/bin/python3.12', '-B']
a = sys.argv[1:]
SC = a[a.index('--scratch') + 1]; assert os.path.isabs(SC) and not os.path.realpath(SC).startswith(os.path.realpath(os.path.join(P, '..', '..', '..', '..')) + '/')
ONLY = a[a.index('--groups') + 1].split(',') if '--groups' in a else None
OUT = a[a.index('--out') + 1] if '--out' in a else None
MAXB = 12


def modal(x):
    v, c = np.unique(x.reshape(-1, 3), axis=0, return_counts=True); return v[c.argmax()]


def comps8(m):
    seen = np.zeros_like(m); out = []; H, W = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if seen[y0, x0]:
            continue
        st = [(y0, x0)]; seen[y0, x0] = True; ys, xs = [], []
        while st:
            y, x = st.pop(); ys.append(y); xs.append(x)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < H and 0 <= xx < W and m[yy, xx] and not seen[yy, xx]:
                        seen[yy, xx] = True; st.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out


def rowbands(m, gap=3):
    ys = np.nonzero(m.any(axis=1))[0]; out = []
    for y in ys:
        if out and y - out[-1][1] <= gap:
            out[-1][1] = y
        else:
            out.append([y, y])
    res = []
    for y0, y1 in out:
        xs = np.nonzero(m[y0:y1 + 1].any(axis=0))[0]; res.append((y0, y1, int(xs.min()), int(xs.max())))
    return res


class Cls:
    """One colour class c of one capture: its ink, the target ink (class components touching the diff + the class ink inside
    the diff's y-range), its bands and glyphs."""
    def __init__(s, F, d, c):
        s.F = F; s.H, s.W, _ = F.shape; s.c = c
        s.ink = np.abs(F - c).sum(axis=2) <= 90
        s.bg = modal(F)
        ys = np.nonzero(d.any(axis=1))[0]; y0, y1 = max(0, ys.min() - 2), min(s.H, ys.max() + 3)
        band = np.zeros_like(s.ink); band[y0:y1] = True
        cl = np.zeros_like(s.ink)
        lab = s.ink & d
        if lab.any():                                     # closure: whole class components that touch the diff
            for cy, cx in comps8(s.ink):
                if lab[cy, cx].any():
                    cl[cy, cx] = True
        s.T = cl | (s.ink & band)
        s.bands = rowbands(s.T)[:MAXB]
        s.glyphs = sorted([g for g in comps8(s.T) if len(g[0]) >= 3], key=lambda g: (s.band_of(g[0].min()), g[1].min()))

    def band_of(s, y):
        for i, b in enumerate(s.bands):
            if b[0] <= y <= b[1]:
                return i
        return 99

    def add(s, mask, dx, dy):
        """Class-c pixels of `mask` copied (dx, dy) away, written only where the destination is NOT class-c ink."""
        o = s.F.copy(); ys, xs = np.nonzero(mask); ty, tx = ys + dy, xs + dx
        ok = (ty >= 0) & (ty < s.H) & (tx >= 0) & (tx < s.W); ys, xs, ty, tx = ys[ok], xs[ok], ty[ok], tx[ok]
        free = ~s.ink[ty, tx]; o[ty[free], tx[free]] = s.F[ys[free], xs[free]]; return o

    def bmask(s, i):
        m = np.zeros_like(s.T); y0, y1, _, _ = s.bands[i]; m[y0:y1 + 1] = s.T[y0:y1 + 1]; return m

    def gmask(s, g):
        m = np.zeros_like(s.T); m[g[0], g[1]] = True; return m

    def blob(s, x, y, w, h):
        """A w x h blob of colour c at (x, y), moved right / left by up to 6 px until it lies wholly on background (the
        local modal colour around it): it adds exactly w*h px of new class-c ink (v3)."""
        for dx in (0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6):
            xx = x + dx
            if xx < 0 or y < 0 or xx + w > s.W or y + h > s.H:
                continue
            nb = s.F[max(0, y - 4):y + h + 4, max(0, xx - 4):xx + w + 4]; lb = modal(nb)
            if (np.abs(s.F[y:y + h, xx:xx + w] - lb).sum(axis=2) < 60).all() and not s.ink[y:y + h, xx:xx + w].any() and np.abs(lb - s.c).sum() > 90:
                o = s.F.copy(); o[y:y + h, xx:xx + w] = s.c; return o
        return None

    def widest_gap(s, i):
        y0, y1, x0, x1 = s.bands[i]; cols = s.T[y0:y1 + 1, x0:x1 + 1].any(axis=0); best = (None, 0); run = 0
        for k, col in enumerate(cols):
            run = 0 if col else run + 1
            if run > best[1]:
                best = (x0 + k - run // 2, run)
        return best


def whole(F, name):
    if name.startswith('vd-whole-min'):
        return np.minimum(F, pt.shift(F, 0, int(name[12:])))
    return np.minimum(F, pt.shift(F, int(name[12:]), 0))


def variant(k, name):
    """One added-ink variant of class k (None when it does not apply)."""
    if not k.bands:
        return None
    if name.startswith('vd-tgt'):
        return k.add(k.T, 0, int(name[6:]))
    if name.startswith('hd-tgt'):
        return k.add(k.T, int(name[6:]), 0)
    if name.startswith('vd-band') or name.startswith('hd-band'):
        i, n = name[7:].split('@'); i = int(i)
        if i >= len(k.bands):
            return None
        return k.add(k.bmask(i), 0, int(n)) if name[0] == 'v' else k.add(k.bmask(i), int(n), 0)
    G = k.glyphs
    w = lambda g: int(g[1].max() - g[1].min() + 1); h = lambda g: int(g[0].max() - g[0].min() + 1)
    if name.startswith('dg-'):
        if not G:
            return None
        if name == 'dg-first-adj':
            return k.add(k.gmask(G[0]), w(G[0]) + 1, 0)
        if name == 'dg-last-adj':
            return k.add(k.gmask(G[-1]), w(G[-1]) + 1, 0)
        if name == 'dg-largest-adj':
            g = max(G, key=lambda g: len(g[0])); dx = w(g) + 2 if g[1].max() + w(g) + 2 < k.W else -(w(g) + 2)
            return k.add(k.gmask(g), dx, 0)
        if name == 'dg-mid-smear':
            return k.add(k.gmask(G[len(G) // 2]), 1, 0)
        if name == 'dg-first-smear':
            return k.add(k.gmask(G[0]), 1, 0)
        if name == 'dg-last-below':
            return k.add(k.gmask(G[-1]), 0, h(G[-1]) + 1)
        if name.startswith('dg-band'):
            i = int(name[7:-5]); gi = [g for g in G if k.band_of(g[0].min()) == i]
            return k.add(k.gmask(gi[-1]), w(gi[-1]) + 1, 0) if gi else None
    B = k.bands
    if name == 'st1-far4':
        y0, y1, x0, x1 = B[-1]; return k.blob(x1 + 4, (y0 + y1) // 2, 1, 1)
    if name == 'st-dash':
        y0, y1, x0, x1 = B[0]; return k.blob(x1 + 3, (y0 + y1) // 2, 4, 1)
    if name == 'st-vbar':
        xg, wd = k.widest_gap(0); y0, y1, _, _ = B[0]
        return k.blob(xg, (y0 + y1) // 2 - 2, 1, 4) if xg is not None and wd >= 3 else None
    if name == 'st2-left5':
        y0, y1, x0, x1 = B[0]; return k.blob(x0 - 7, (y0 + y1) // 2, 2, 2)
    if name == 'st3-above':
        y0, y1, x0, x1 = B[0]; return k.blob((x0 + x1) // 2, y0 - 4, 3, 3)
    if name == 'st2-below':
        y0, y1, x0, x1 = B[-1]; return k.blob((x0 + x1) // 2, y1 + 3, 2, 2)
    if name == 'st-uline':
        y0, y1, x0, x1 = B[0]; return k.blob(x0, y1 + 2, x1 - x0 + 1, 1)
    if name.startswith('st2-band') and name.endswith('-end3'):
        i = int(name[8:-5])
        if i >= len(B):
            return None
        y0, y1, x0, x1 = B[i]; return k.blob(x1 + 3, (y0 + y1) // 2, 2, 2)
    if name.startswith('st2-band') and name.endswith('-gap'):
        i = int(name[8:-4])
        if i >= len(B):
            return None
        xg, wd = k.widest_gap(i); y0, y1, _, _ = B[i]
        return k.blob(xg, (y0 + y1) // 2, 2, 2) if xg is not None and wd >= 6 else None
    raise ValueError(name)


WHOLE = ['vd-whole-min+1', 'vd-whole-min-1', 'vd-whole-min+4', 'vd-whole-min-5', 'vd-whole-min+9',
         'hd-whole-min+3', 'hd-whole-min-1', 'hd-whole-min-4', 'hd-whole-min+7']
PERCLASS = (['vd-tgt+1', 'vd-tgt-1', 'vd-tgt+2', 'vd-tgt-2', 'vd-tgt+4', 'vd-tgt+9',
             'hd-tgt+1', 'hd-tgt-1', 'hd-tgt+2', 'hd-tgt+3', 'hd-tgt-4', 'hd-tgt+10',
             'dg-first-adj', 'dg-last-adj', 'dg-largest-adj', 'dg-mid-smear', 'dg-first-smear', 'dg-last-below',
             'st1-far4', 'st-dash', 'st-vbar', 'st2-left5', 'st3-above', 'st2-below', 'st-uline'])
PERBAND = lambda i: [f'vd-band{i}@+3', f'vd-band{i}@-3', f'hd-band{i}@+2', f'hd-band{i}@+5', f'dg-band{i}-last',
                     f'st2-band{i}-end3', f'st2-band{i}-gap']


def groups():
    out = {}
    for L, b in (('L1', pt.l1_cases), ('L2', pt.l2_cases), ('L3', pt.l3_cases), ('L4', pt.l4_cases), ('L5', pt.l5_cases), ('L6', pt.l6_cases)):
        for lane, secs, fid, repl, want in b() + r2.cases(pt, L):
            if not fid.endswith('fixed'):
                continue
            sig = (lane, secs, tuple(sorted((k, hashlib.sha1(v.tobytes()).hexdigest()) for k, v in repl.items())))
            g = out.setdefault(sig, [lane, secs, [], repl, set()])
            g[2].append(fid); g[4] |= {k for k, w in want.items() if w == 'PASS'}
    return [(lane, secs, '+'.join(f), repl, keys) for lane, secs, f, repl, keys in out.values()]


def reads(key, sec, plat, test):
    t = key.split(); base = test.split('/')[-1]
    if plat not in t:
        return False
    if t[0] == 'hyphen-ios-linebox':
        return len(t) > 1 and base.endswith('-' + t[1])
    return any(x == test or x == base or x.endswith('/' + base) for x in t)


lines = []
def say(s):
    lines.append(s); print(s, flush=True)


e = json.load(open(EXP))
script = lambda pr: os.path.basename(next(t for t in pr['cmd'].split() if t.endswith('.py')))
GATING = {(script(pr), k) for l in e['lanes'].values() for pr in l['geometryProbes'] for k in pr.get('gating', {})}
say(f'# round-4 verifier added-ink fakes (final: strictly additive, solid classes, free-background strays, darkest-colour fallback) on expectations.json {hashlib.sha256(open(EXP, "rb").read()).hexdigest()[:16]}…, '
    f'geometry-gate.py {hashlib.sha256(open(f"{P}/geometry-gate.py", "rb").read()).hexdigest()[:16]}…, fakes4.py {hashlib.sha256(open(__file__, "rb").read()).hexdigest()[:16]}…')
holes, ctrl_only, gpass, nreads, nfakes, untested, nkeys = [], [], [], 0, 0, [], set()
for lane, secs, gid, repl, keys in groups():
    if ONLY and lane not in ONLY:
        continue
    gid = gid.replace('/', '_')
    rid = pt.make_run(SC, f'r4-{lane}-{gid}-FIXED', repl)
    _, base_rows = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r4-{lane}-{gid}-FIXED.json')
    under = sorted(k for k in keys if k in GATING)
    ok = {k for k, r in base_rows.items() if r['verdict'] == 'PASS'}
    for k in sorted(k for k in GATING if any(reads(k[1], *x) for x in repl) and k not in ok):
        untested.append((lane, gid, k[1], base_rows.get(k, {}).get('verdict')))
    cls = {kk: base_rows[kk]['class'] for kk in base_rows}
    bad = [k[1] for k in under if k not in ok]
    say(f'\n## {lane} {gid}: gating keys under test {len(under)}' + (f' — NOT PASS ON FIXED: {bad}' if bad else ' (all PASS on the fixed pictures)'))
    caps = {}
    for x, F in repl.items():
        if x[1] == 'web':
            continue
        B = pt.cap('wave54-final', x[0], x[2], x[1]); d = (F != B).any(axis=2) if B.shape == F.shape else np.ones(F.shape[:2], bool)
        bg = modal(F); vals, ns = np.unique(F[d].reshape(-1, 3), axis=0, return_counts=True)
        dist = np.abs(vals - bg).sum(axis=1); far = dist.max() if len(dist) else 0
        top = []
        for i in np.argsort(-ns):                         # most frequent first; solid only; near-duplicates merged (v3)
            c = vals[i]
            if ns[i] < 15 or dist[i] < max(90, 0.6 * far) or any(np.abs(c - np.array(t)).sum() <= 90 for t in top):
                continue
            top.append(tuple(int(v) for v in c))
            if len(top) == 2:
                break
        if not top and d.any():                           # v4: a diff of anti-aliasing only (a 1-px move) — the darkest colour
            ys_, xs_ = np.nonzero(d)                      # of the diff's bbox (dilated 3 px) with >= 5 px is the unit's ink
            sub = F[max(0, ys_.min() - 3):ys_.max() + 4, max(0, xs_.min() - 3):xs_.max() + 4].reshape(-1, 3)
            v2, n2 = np.unique(sub, axis=0, return_counts=True); ok2 = (n2 >= 5) & (np.abs(v2 - bg).sum(axis=1) > 300)
            if ok2.any():
                top = [tuple(int(v) for v in v2[ok2][np.argmax(np.abs(v2[ok2] - bg).sum(axis=1))])]
        caps[x] = (F, [Cls(F, d, np.array(c)) for c in top] if d.any() else [])
        say(f'   {x[2].split("/")[-1]} {x[1]}: diff {int(d.sum())} px; classes ' + '; '.join(f'{tuple(int(v) for v in k.c)} {len(k.bands)} bands {len(k.glyphs)} glyphs' for k in caps[x][1]))
    names = WHOLE + [f'c{ci}:{n}' for ci in range(2) for n in PERCLASS + [p for i in range(MAXB) for p in PERBAND(i)]]
    for v in names:
        r = dict(repl); changed = {}
        for x, (F, ks) in caps.items():
            if v.startswith('c'):
                ci, n = v.split(':', 1); ci = int(ci[1:])
                o = variant(ks[ci], n) if ci < len(ks) else None
            else:
                o = whole(F, v)
            if o is not None and not (o == F).all():
                r[x] = o; dd = (o != F).any(axis=2); ys, xs = np.nonzero(dd)
                ni = max(int(((o.min(axis=2) < 200) & ~(F.min(axis=2) < 200)).sum()), int(((o.sum(axis=2) < 300) & ~(F.sum(axis=2) < 300)).sum()))
                changed[x] = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()), int(dd.sum()), ni)
        if not changed:
            continue
        nfakes += 1
        tag = v.replace(':', '_')
        rid = pt.make_run(SC, f'r4-{lane}-{gid}-{tag}', r)
        rc, rows = pt.gate(rid, lane, secs, EXP, PY, f'{SC}/gate-r4-{lane}-{gid}-{tag}.json')
        parts = []
        for x, bb in changed.items():
            rd = [k for k in ok if reads(k[1], *x)]
            moved = [k for k in rd if rows.get(k, {}).get('verdict') != 'PASS']
            mg = sorted(k[1] for k in moved if cls.get(k) == 'gating'); mc = sorted(k[1] for k in moved if cls.get(k) == 'control')
            mr = sorted(k[1] for k in moved if cls.get(k) not in ('gating', 'control'))
            acc = sorted(k for k in rd if k in under and rows.get(k, {}).get('verdict') == 'PASS')
            nreads += len([k for k in rd if k in under]); nkeys |= {k for k in rd if k in under}
            name = f'{x[2].split("/")[-1]} {x[1]}'
            if not [k for k in rd if cls.get(k) in ('gating', 'control')]:
                parts.append(f'{name}: no gating/control reader'); continue
            if not mg and not mc:
                gl = [rows[k]['line'][-120:] for k in rd if cls.get(k) == 'gating' and k in rows][:1]
                holes.append((lane, gid, v, name, bb, sorted(k[1] for k in rd), mr, gl)); parts.append(f'{name}: HOLE +{bb[4]}px (new ink {bb[5]}) bbox{bb[:4]}' + (f' (report rows moved: {mr})' if mr else ''))
            elif not mg:
                ctrl_only.append((lane, gid, v, name, bb, mc, [k[1] for k in acc])); parts.append(f'{name}: CTRL-ONLY {mc} +{bb[4]}px (new ink {bb[5]}) bbox{bb[:4]}')
            elif acc:
                for k in acc:
                    gpass.append((lane, gid, v, name, k[1], mg + mc, bb, rows[k]['line'][-150:]))
                parts.append(f'{name}: caught by {"; ".join(mg[:2])} — GATE-PASS {", ".join(k[1] for k in acc)} +{bb[4]}px (new ink {bb[5]}) bbox{bb[:4]}')
            else:
                parts.append(f'{name}: caught ({len(mg)} gating, {len(mc)} control)')
        say(f'  {v:22} rc {rc} | ' + ' | '.join(parts))
big = lambda b: b[5] > 2
say(f'\n{nfakes} fake runs; {nreads} reads of a faked capture by a gating key under test ({len(nkeys)} distinct keys); HOLES {len(holes)} '
    f'({sum(big(h[4]) for h in holes)} with > 2 new-ink px); CTRL-ONLY {len(ctrl_only)} ({sum(big(c[4]) for c in ctrl_only)} > 2); '
    f'GATE-PASS {len(gpass)} ({sum(big(g[6]) for g in gpass)} > 2)')
for h in holes:
    say(f'  HOLE{"" if big(h[4]) else "(<=2)"} {h[0]} {h[1]} {h[2]} — {h[3]} +{h[4][4]} px, new ink {h[4][5]}, bbox {h[4][:4]}; readers {h[5]}' + (f'; report rows moved {h[6]}' if h[6] else '') + (f'; probe: …{h[7][0]}' if h[7] else ''))
for c in ctrl_only:
    say(f'  CTRL-ONLY{"" if big(c[4]) else "(<=2)"} {c[0]} {c[1]} {c[2]} — {c[3]} +{c[4][4]} px, new ink {c[4][5]}, bbox {c[4][:4]}; controls moved {c[5]}; gating keys under test PASS {c[6]}')
for g in gpass:
    say(f'  GATE-PASS{"" if big(g[6]) else "(<=2)"} {g[0]} {g[1]} {g[2]} — {g[3]}: "{g[4]}" PASS; caught by {g[5]}; +{g[6][4]} px, new ink {g[6][5]}, bbox {g[6][:4]}')
for u in untested:
    say(f'  NO PASSING FIXED PICTURE in this group: {u[0]} {u[1]} {u[2]} ({u[3]})')
if OUT:
    open(OUT, 'w').write('\n'.join(lines) + '\n')
sys.exit(1 if any(big(h[4]) for h in holes) else 0)

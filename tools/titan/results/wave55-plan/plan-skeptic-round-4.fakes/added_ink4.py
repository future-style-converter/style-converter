#!/usr/bin/env python3
# tools/titan/results/wave55-plan/plan-skeptic-round-4.fakes/added_ink4.py — the round-4 VERIFIER's strictly-additive fake
# builders as an importable module (plan-skeptic-round-4.verify.md §2.1; fix-r4.done.md). Used by probes-teeth-r4.py (the pinned
# round-4 cases of the standing set); the sweep itself is fakes4.py beside this file (the verifier's generator, its plan-dir
# path made relative: `diff` against plan-skeptic-round-4.verify.d/fakes4.py shows the two path lines only).
#
# Why a module: fakes4.py is a script (its sweep runs at import). The functions below are COPIED VERBATIM from
# plan-skeptic-round-4.verify.d/fakes4.py (sha256 379b3a1e…: modal … PERBAND, lines 39-211) and its per-capture colour-class
# selection (lines 263-280, here `classes`); `pt` is the loaded probes-teeth module (its `shift`). fix-r4.teeth-r4-equiv.out.txt
# proves the pictures they build are byte-identical to fakes4.py's own fake runs for every pinned case.
# Every fake is STRICTLY ADDITIVE: class ink is written only where the destination is not class ink (nothing moved or erased).
import numpy as np

pt = None          # set by the importer (probes-teeth-r4.py): the probes-teeth module, for pt.shift
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


def classes(F, B):
    """The colour classes of one capture (fakes4.py lines 263-280): the top-2 SOLID exact colours of the pixels where the fixed
    picture F differs from the wave54-final capture B (≥ 15 px, ≥ max(90, 0.6 × the farthest changed colour's distance from the
    canvas), near-duplicates merged), else the darkest colour of the diff's bbox; one Cls each."""
    d = (F != B).any(axis=2) if B.shape == F.shape else np.ones(F.shape[:2], bool)
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
    return [Cls(F, d, np.array(c)) for c in top] if d.any() else []


def build(F, B, name):
    """fakes4.py's picture for variant `name` ('vd-whole-min+1' … or 'c<k>:<variant>') of the fixed capture F, or None."""
    if name.startswith('c'):
        ci, n = name.split(':', 1); ci = int(ci[1:]); ks = classes(F, B)
        return variant(ks[ci], n) if ci < len(ks) else None
    return whole(F, name)

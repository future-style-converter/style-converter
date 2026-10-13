#!/usr/bin/env python3
# tools/titan/results/wave55-plan/plan-skeptic-round-3.fakes/added_ink.py — the ADDED-INK fake class (plan-skeptic round 3,
# verify M2) as code: builders shared by sweep.py (the measurement over every lane) and probes-teeth-r3.py (the standing cases).
#
# Why: the round-1 / round-2 fake classes all REMOVE, MOVE, SHORTEN or REARRANGE the target ink. Round 3's verifier painted a
# row's text a second time a few px lower and three L3 gating keys accepted it (plan-skeptic-round-3.verify.md M2): a rule that
# reads where ink peaks / starts / ends is blind to EXTRA ink. Variants (each applied to a key's own fixed picture):
#   ghost±Ny   the whole picture painted again N px lower / higher (min(): dark-on-light ink over itself)
#   hdup±Nx    the whole picture painted again N px to the right / left (a second copy shifted horizontally)
#   tgt-*      the same, restricted to the TARGET region = where the fixed picture differs from the base capture (the unit's
#              own pixels), dilated 6 px — the row / box / line the key exists for, the rest of the picture untouched
#   glyph2-*   one glyph of the target's ink (a connected component of the target's ink colour — the most common non-white
#              colour of the unit's own changed pixels) painted again beside itself (a doubled glyph)
#   stray{2,3}-*  a 2×2 / 3×3 blob of the target's ink colour on a uniform patch of any other colour inside the target region,
#              within 2 px of the target's ink (stray ink inside the band; never a hole in the target)
# Pure numpy on the decoded pictures; writes nothing (the callers write fake runs under a scratch dir).
import numpy as np

DIL = 6


def shift(a, dx, dy, region=None, bg=(255, 255, 255)):
    """Translate `region` (x0, y0, x1, y1; default whole) by (dx, dy), refilling the vacated area with bg (= probes-teeth.shift)."""
    h, w, _ = a.shape
    x0, y0, x1, y1 = region or (0, 0, w, h)
    src = a[y0:y1, x0:x1].copy(); out = a.copy(); out[y0:y1, x0:x1] = bg
    ys0, xs0, ys1, xs1 = max(0, y0 + dy), max(0, x0 + dx), min(h, y1 + dy), min(w, x1 + dx)
    out[ys0:ys1, xs0:xs1] = src[ys0 - (y0 + dy):ys1 - (y0 + dy), xs0 - (x0 + dx):xs1 - (x0 + dx)]
    return out


def ghost(a, dx, dy, region=None):
    """The picture (or `region` of it) painted a second time (dx, dy) away: min() keeps both copies of dark-on-light ink."""
    if region is None:
        return np.minimum(a, shift(a, dx, dy))
    x0, y0, x1, y1 = region; o = a.copy(); sub = a[y0:y1, x0:x1].copy()
    o[y0:y1, x0:x1] = np.minimum(sub, shift(sub, dx, dy, bg=tuple(int(v) for v in modal(sub))))
    return o


def modal(a):
    """The most common colour of a picture (its background)."""
    flat = a.reshape(-1, 3); vals, cnt = np.unique(flat, axis=0, return_counts=True); return vals[cnt.argmax()]


def target(fixed, base):
    """(x0, y0, x1, y1) of the pixels where the fixed picture differs from the base capture, dilated DIL px; None if identical."""
    d = (fixed != base).any(axis=2)
    if not d.any():
        return None
    ys, xs = np.nonzero(d); h, w = d.shape
    return max(0, xs.min() - DIL), max(0, ys.min() - DIL), min(w, xs.max() + 1 + DIL), min(h, ys.max() + 1 + DIL)


def ink_colour(fixed, base, region):
    """The target's own ink colour: the most common INK colour (min channel < 200) among the pixels where the fixed picture
    differs from the base capture inside region (the unit's own pixels: green box, black glyphs, a teal child, …)."""
    x0, y0, x1, y1 = region; f, b = fixed[y0:y1, x0:x1], base[y0:y1, x0:x1]
    solid = f.min(axis=2) < 200                       # ink, never a light anti-aliasing grey (a 232-grey blob is not ink)
    d = (f != b).any(axis=2) & solid
    if not d.any():
        d = solid
    return modal(f[d][None]) if d.any() else np.array([0, 0, 0])


def inkmask(a, region, col):
    """Ink = pixels inside region within 60 (summed |RGB|) of the target ink colour."""
    x0, y0, x1, y1 = region; m = np.zeros(a.shape[:2], bool)
    m[y0:y1, x0:x1] = np.abs(a[y0:y1, x0:x1] - col).sum(axis=2) <= 60
    return m


def comps(mask):
    """4-connected components -> [(ys, xs)] (an explicit flood fill; the pictures are small)."""
    seen = np.zeros_like(mask); out = []; H, W = mask.shape
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        st = [(y0, x0)]; seen[y0, x0] = True; ys, xs = [], []
        while st:
            y, x = st.pop(); ys.append(y); xs.append(x)
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < H and 0 <= xx < W and mask[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True; st.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out


def glyph2(a, region, col, which):
    """One component of the target's ink ('small' = the smallest of ≥ 4 px, 'mid' = the median) painted again beside itself
    (right of it by its width + 2, else left, else below, else above) where the destination is clean background (the
    region's modal colour ±60 under the whole footprint: never half over another glyph's anti-aliasing); None if there is no
    room."""
    m = inkmask(a, region, col)
    x0, y0, x1, y1 = region; bg = modal(a[y0:y1, x0:x1])     # the copy lands on clean background only (a whole extra glyph)
    cs = sorted([c for c in comps(m) if len(c[0]) >= 4], key=lambda c: len(c[0]))
    if not cs:
        return None
    ys, xs = cs[0] if which == 'small' else cs[len(cs) // 2]
    w, h = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
    for dx, dy in ((w + 2, 0), (-(w + 2), 0), (0, h + 2), (0, -(h + 2))):
        ty, tx = ys + dy, xs + dx
        if ty.min() < 0 or tx.min() < 0 or ty.max() >= a.shape[0] or tx.max() >= a.shape[1]:
            continue
        if not m[ty.min():ty.max() + 1, tx.min():tx.max() + 1].any() and (np.abs(a[ty, tx] - bg).sum(axis=1) <= 60).all():
            o = a.copy(); o[ty, tx] = a[ys, xs]; return o
    return None


def stray(a, region, col, size, which):
    """A size×size blob of the target's ink colour on a uniform patch of any OTHER colour inside region, within 2 px of the
    target's ink: 'first' / 'mid' / 'last' of the candidate positions in reading order; None if there is none."""
    m = inkmask(a, region, col)
    if not m.any():
        return None
    x0, y0, x1, y1 = region
    near = np.zeros_like(m)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            near |= np.roll(np.roll(m, dy, axis=0), dx, axis=1)
    cand = []
    for y in range(y0, y1 - size):
        for x in range(x0, x1 - size):
            blk = (slice(y, y + size), slice(x, x + size))
            if near[blk].any() and not m[blk].any() and (a[blk] == a[y, x]).all():
                cand.append((y, x))
    if not cand:
        return None
    y, x = cand[{'first': 0, 'mid': len(cand) // 2, 'last': -1}[which]]
    o = a.copy(); o[y:y + size, x:x + size] = col; return o


VARIANTS = ['ghost+2y', 'ghost+3y', 'ghost-3y', 'hdup+1x', 'hdup+2x', 'hdup-2x', 'tgt-ghost+3y', 'tgt-ghost-3y', 'tgt-hdup+2x',
            'glyph2-small', 'glyph2-mid', 'stray2-first', 'stray2-mid', 'stray3-first', 'stray3-mid', 'stray3-last']


def apply(name, fixed, base):
    """The variant `name` of one fixed picture (base = the same capture on the base run); None when it does not apply."""
    if name.startswith('ghost'):
        return ghost(fixed, 0, int(name[5:-1]))
    if name.startswith('hdup'):
        return ghost(fixed, int(name[4:-1]), 0)
    reg = target(fixed, base)
    if reg is None:
        return None
    if name.startswith('tgt-ghost'):
        return ghost(fixed, 0, int(name[9:-1]), reg)
    if name.startswith('tgt-hdup'):
        return ghost(fixed, int(name[8:-1]), 0, reg)
    col = ink_colour(fixed, base, reg)
    if name.startswith('glyph2'):
        return glyph2(fixed, reg, col, name.split('-')[1])
    if name.startswith('stray'):
        return stray(fixed, reg, col, int(name[5]), name.split('-')[1])
    raise ValueError(name)


# ── L3 dir-style-02a: the verifier's row-band ghosts and the round-3 variants, per row k (row k spans y 16+20(k-1) … +20) ──
DIR_Y0 = lambda k: 16 + 20 * (k - 1)
_ink = lambda a: a.min(axis=2) < 200


def l3_dir(a, k, name):
    """The added-ink variant `name` of dir-style-02a row k (the other rows untouched)."""
    y0 = DIR_Y0(k)
    if name.startswith('band-ghost'):          # the verifier's: the row band (y0−2 … y0+18) painted again dy lower inside itself
        dy = int(name[10:-1]); o = a.copy(); b = a[y0 - 2:y0 + 18].copy(); o[y0 - 2:y0 + 18] = np.minimum(b, shift(b, 0, dy)); return o
    if name.startswith('band-hdup'):           # the row painted again dx to the right / left inside its band
        dx = int(name[9:-1]); o = a.copy(); b = a[y0:y0 + 20].copy(); o[y0:y0 + 20] = np.minimum(b, shift(b, dx, 0)); return o
    m = _ink(a[y0:y0 + 20]); xs = np.nonzero(m.any(axis=0))[0]
    if name == 'dot2-left':                    # the 2-px dot painted again 4 px left of itself ("..This")
        o = a.copy(); d = a[y0 + 12:y0 + 18, 16:21].copy(); o[y0 + 12:y0 + 18, 12:17] = np.minimum(o[y0 + 12:y0 + 18, 12:17], d); return o
    if name == 'dot2-end':                     # the dot painted again 2 px after the row's last glyph ("rtl.")
        o = a.copy(); d = a[y0 + 12:y0 + 18, 16:21].copy(); x1 = xs.max() + 2
        o[y0 + 12:y0 + 18, x1:x1 + 5] = np.minimum(o[y0 + 12:y0 + 18, x1:x1 + 5], d); return o
    sub = a[y0:y0 + 20]; col = sub.reshape(-1, 3)[np.argmin(sub.sum(axis=2))]          # the row's darkest ink colour
    if name.startswith('stray'):               # stray{2,3}-{gap,above,inside}: a blob in the first word gap after x60 / above the
        s = int(name[5]); where = name.split('-')[1]; o = a.copy()                     # x-height / over the glyphs at x80
        if where == 'gap':
            cols = m.any(axis=0); xg = next(x for x in range(60, xs.max()) if not cols[x:x + s].any()); o[y0 + 8:y0 + 8 + s, xg:xg + s] = col
        elif where == 'above':
            o[y0 + 1:y0 + 1 + s, 100:100 + s] = col
        else:
            o[y0 + 10:y0 + 10 + s, 80:80 + s] = col
        return o
    if name == 'glyph2':                       # the row's last glyph (its rightmost ink run, e.g. the final 'l') painted again 3 px right
        x1 = xs.max(); x0 = x1
        while x0 - 1 >= 0 and m[:, x0 - 1].any():
            x0 -= 1
        o = a.copy(); g = a[y0:y0 + 20, x0:x1 + 1].copy(); w = x1 - x0 + 1
        o[y0:y0 + 20, x1 + 3:x1 + 3 + w] = np.minimum(o[y0:y0 + 20, x1 + 3:x1 + 3 + w], g); return o
    raise ValueError(name)


L3_DIR_VARIANTS = ['band-ghost+3y', 'band-ghost+5y', 'band-ghost+7y', 'band-ghost-3y', 'band-hdup+1x', 'band-hdup+2x', 'band-hdup-2x',
                   'dot2-left', 'dot2-end', 'stray2-gap', 'stray3-gap', 'stray3-above', 'stray3-inside', 'glyph2']


# ── L3 bidi-tab-001 (RTL bands y86 / 106 / 186 / 226) and anchor-position-multicol-nested-001 (cells): in-target added ink ──
BIDI_BANDS = (86, 106, 186, 226)
_blue = lambda a: (a[..., 2] > 150) & (a[..., 0] < 120) & (a[..., 1] < 120)
BLUE = (0, 0, 255)


def _band_comps(a, y0):
    m = np.zeros(a.shape[:2], bool); m[y0:y0 + 20] = _blue(a)[y0:y0 + 20]
    return sorted(comps(m), key=lambda c: -len(c[0]))


def l3_bidi(a, name):
    """Added blue ink inside every RTL band of bidi-tab-001 (the gating key's own bands)."""
    o = a.copy()
    for y0 in BIDI_BANDS:
        cs = _band_comps(a, y0)
        (bys, bxs), (gys, gxs) = cs[0], cs[1]                      # the run's box, the '0' ring
        if name == 'stray3-band':                                  # a 3×3 blob on the yellow box, clear of the run
            x = int(max(bxs.max(), gxs.max()) + 20) if bxs.min() < 60 else int(min(bxs.min(), gxs.min()) - 20)
            o[y0 + 8:y0 + 11, x:x + 3] = BLUE
        elif name == 'stray2-touch-glyph':                         # a 2×2 blob touching the ring's right side
            o[y0 + 8:y0 + 10, gxs.max() + 1:gxs.max() + 3] = BLUE
        elif name == 'stray2-touch-box':                           # a 2×2 blob touching the box's top edge (inside the band)
            o[bys.min() - 2:bys.min(), bxs.min() + 4:bxs.min() + 6] = BLUE
        elif name == 'glyph2':                                     # the '0' (ring + inner dot) painted again 3 px right of itself
            x0, x1 = gxs.min(), gxs.max(); w = x1 - x0 + 1; sub = a[y0:y0 + 20, x0:x1 + 1]
            m = _blue(sub); dst = o[y0:y0 + 20, x1 + 3:x1 + 3 + w]; dst[m[:, :dst.shape[1]]] = BLUE
        else:
            raise ValueError(name)
    return o


L3_BIDI_VARIANTS = ['stray3-band', 'stray2-touch-glyph', 'stray2-touch-box', 'glyph2']
STACK_CELLS = ((190, 78), (360, 78), (190, 168), (360, 168))
GREEN = (0, 128, 0)


def l3_stack(a, name):
    """Added green ink beside the four out-of-flow squares (the [W] stack key's picture)."""
    o = a.copy(); x, y = STACK_CELLS[0]
    if name == 'stray2-2px-off':                                   # 2 px off cell 1's left edge (round 2 counted neither ring nor others)
        o[y + 4:y + 6, x - 4:x - 2] = GREEN
    elif name == 'stray3-free':                                    # a 3×3 green blob in the open between cells 1 and 2
        o[y + 4:y + 7, x + 80:x + 83] = GREEN
    elif name == 'stray2-touch':                                   # a 2×2 blob touching cell 3's bottom edge
        x3, y3 = STACK_CELLS[2]; o[y3 + 10:y3 + 12, x3 + 4:x3 + 6] = GREEN
    else:
        raise ValueError(name)
    return o


L3_STACK_VARIANTS = ['stray2-2px-off', 'stray3-free', 'stray2-touch']

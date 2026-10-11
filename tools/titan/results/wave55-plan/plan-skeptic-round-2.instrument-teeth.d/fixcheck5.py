import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
d5 = pt._d5; ROW = pt.L5_ROW
def strokes(a, xa, xb, y0, y1):
    """dark row-runs inside the blob's columns (一 = 1 horizontal stroke, 二 = 2)."""
    rows = d5(a[y0:y1, xa:xb + 1]).any(axis=1); n = 0; prev = False
    for r in rows:
        if r and not prev: n += 1
        prev = r
    ys = np.nonzero(rows)[0]
    return n, (int(ys.min()) + y0, int(ys.max()) + y0) if len(ys) else None
OK = 'counter-suffix-rows-replay/ok-all'
cs_rows = [('ref', ref('css-counter-styles', 'counter-suffix'))] + [(f'ok-{p}', pt.png(f'{OK}/counter-suffix.{p}.png')) for p in ('web', 'ios', 'android')]
cs_rows += [(f'{f}-{p}', fk(f'L5-{f}', p, 'counter-suffix')) for f in ('C-ideographs-swapped-5-6', 'C-ideograph+3y', 'H-letters-swapped-3-4', 'H-period-3y') for p in ('ios', 'android')]
say('## L5 [C] rows 5-6: ideograph stroke count and ink rows; [H] rows 3-4: letter column-profile corr vs the ref letter, period ink rows')
r0 = cs_rows[0][1]
def hletter(a, k):
    bl = pt.l5_blobs(a, 30, 64, *ROW[k]); L = [b for b in bl if b[1] - b[0] >= 4]; D = [b for b in bl if b[1] - b[0] <= 2]
    return L[0] if L else None, D[0] if D else None
for l, a in cs_rows:
    s = []
    for k in (5, 6):
        b = pt.l5_blobs(a, 20, 60, *ROW[k])[0]; s.append(f'r{k} {strokes(a, b[0], b[1], *ROW[k])}')
    for k in (3, 4):
        L, D = hletter(a, k); RL, RD = hletter(r0, k)
        p = d5(a[ROW[k][0]:ROW[k][1], L[0]:L[0] + 12]).sum(axis=0).astype(float); q = d5(r0[ROW[k][0]:ROW[k][1], RL[0]:RL[0] + 12]).sum(axis=0).astype(float)
        c = round(float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum())), 3)
        _, py = strokes(a, D[0], D[1], *ROW[k]); _, ry = strokes(r0, RD[0], RD[1], *ROW[k])
        s.append(f'r{k} letter-colcorr {c} period-rows {py} (ref {ry})')
    say(f'  {l:34} ' + ' | '.join(s))
say('\n## L5 [I] css3-counter-styles-005: first marker blob stroke count + ink rows per line')
c5_rows = [('ref', ref('css-counter-styles', 'cjk-decimal/css3-counter-styles-005'))] + [(f'ok-{p}', pt.png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')) for p in ('web', 'ios', 'android')]
c5_rows += [(f'{f}-{p}', fk(f'L5-{f}', p, 'cjk-decimal/css3-counter-styles-005')) for f in ('I-line1-ideograph-is-line2s', 'I-line1-marker+3y') for p in ('ios', 'android')]
for l, a in c5_rows:
    s = []
    for y0, y1 in ((95, 121), (121, 152)):
        b = pt.l5_blobs(a, 200, 330, y0, y1)[0]; s.append(f'y{y0} {strokes(a, b[0], b[1], y0, y1)}')
    say(f'  {l:36} ' + ' | '.join(s))
say('\n## L5 [K] ios: what the ok-all replay changes in row 9 (y208-231) vs wave54-final, by column range')
for p in ('ios', 'android'):
    a = pt.png(f'{OK}/counter-suffix.{p}.png'); b = pt.cap('wave54-final', 'css-counter-styles', 'counter-suffix', p)
    d = (np.abs(a[208:232] - b[208:232]).max(axis=2) > 0); xs = np.nonzero(d.any(axis=0))[0]
    say(f'  {p}: {int(d.sum())} px differ in row 9; columns {(int(xs.min()), int(xs.max())) if len(xs) else None}')
    for f in ('T-row9-text-erased', 'T-row9-text-3x'):
        c = fk(f'L5-{f}', p, 'counter-suffix'); m = np.abs(c[208:232] - a[208:232]).max(axis=2) > 0; m[:, 136:151] = False
        say(f'    {f}: px differing from ok-all in row 9 OUTSIDE the digit window x136-150: {int(m.sum())}')

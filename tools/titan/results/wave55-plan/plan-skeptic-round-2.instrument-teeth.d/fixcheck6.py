import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L2")[0])
inkp = lambda a: a.min(axis=2) < 200
ROW = pt.L5_ROW
def strokes(a, xa, xb, y0, y1):
    rows = inkp(a[y0:y1, xa:xb + 1]).any(axis=1); n = 0; prev = False
    for r in rows:
        if r and not prev: n += 1
        prev = r
    ys = np.nonzero(rows)[0]; return n, (int(ys.min()) + y0, int(ys.max()) + y0) if len(ys) else None
OK = 'counter-suffix-rows-replay/ok-all'
say('## L5 [C] / [I] with the ink predicate min channel < 200: ideograph stroke count + ink rows (vs ref; [I] android also vs wave54-final)')
rows = [('ref', ref('css-counter-styles', 'counter-suffix'))] + [(f'ok-{p}', pt.png(f'{OK}/counter-suffix.{p}.png')) for p in ('web', 'ios', 'android')]
rows += [(f'{f}-{p}', fk(f'L5-{f}', p, 'counter-suffix')) for f in ('C-ideographs-swapped-5-6', 'C-ideograph+3y') for p in ('ios', 'android')]
for l, a in rows:
    say(f'  [C] {l:34} ' + ' | '.join(f'r{k} {strokes(a, *pt.l5_blobs(a, 20, 60, *ROW[k])[0], *ROW[k])}' for k in (5, 6)))
rows = [('ref', ref('css-counter-styles', 'cjk-decimal/css3-counter-styles-005'))] + [(f'ok-{p}', pt.png(f'{OK}/cjk-decimal__css3-counter-styles-005.{p}.png')) for p in ('web', 'ios', 'android')]
rows += [(f'base-{p}', pt.cap('wave54-final', 'css-counter-styles', 'cjk-decimal/css3-counter-styles-005', p)) for p in ('ios', 'android')]
rows += [(f'{f}-{p}', fk(f'L5-{f}', p, 'cjk-decimal/css3-counter-styles-005')) for f in ('I-line1-ideograph-is-line2s', 'I-line1-marker+3y') for p in ('ios', 'android')]
for l, a in rows:
    say(f'  [I] {l:36} ' + ' | '.join(f'y{y0} {strokes(a, *pt.l5_blobs(a, 200, 330, y0, y1)[0], y0, y1)}' for y0, y1 in ((95, 121), (121, 152))))
say('\n## L6 [X]: base fill ⊄ capture fill (px of the base child fill the capture lost) and capture fill ⊄ base fill ∪ base band dilated 1 (fill where neither was)')
dark6 = lambda a: a.sum(axis=2) < 150
def dil(m):
    o = m.copy(); o[1:] |= m[:-1]; o[:-1] |= m[1:]; o[:, 1:] |= m[:, :-1]; o[:, :-1] |= m[:, 1:]; return o
for sec, t, fl in pt.L6_X:
    base = pt.cap('wave54-final', sec, t, 'android'); fx = pt.png(f'android-paint-order-replay/{t.replace("/", "__")}-android-U2.png')
    cases = [('fixed', fx), ('r2:one-child-moved-3px', fk('L6-X-one-child-moved-3px', 'android', t))]
    cases += [(f'r1:{f}', pt.A(Image.open(f'{SCR}/teeth-rerun/fakeruns/L6-X-{f}/sections/{sec}/android-screenshots/wpt__{sec}__{t.replace("/", "__")}.png'))) for f in ('child+3x', 'child-scrambled', 'child-shrunk8%')]
    say(f'  {t.split("/")[-1]}')
    for l, a in cases:
        lost = int((fl(base) & ~fl(a)).sum()); extra = int((fl(a) & ~dil(fl(base) | dark6(base))).sum())
        say(f'    {l:26} lost {lost:5d}  extra {extra:5d}')

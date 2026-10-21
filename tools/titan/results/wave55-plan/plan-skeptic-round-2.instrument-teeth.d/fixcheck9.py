import os, sys
sys.dont_write_bytecode = True
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixcheck.py')).read().split("say('## L3")[0].split("say('## L2")[0])
green = lambda a: (a[..., 0] < 8) & (np.abs(a[..., 1] - 128) < 8) & (a[..., 2] < 8)
bad = lambda a: ((a[..., 0] > 200) & (a[..., 1] < 80) & (a[..., 2] < 80)) | ((a[..., 0] > 240) & (np.abs(a[..., 1] - 127) < 12) & (a[..., 2] > 240))
t = 'backdrop-filter-nested-border-radius-clip-3'; r = ref('filter-effects', t)
rows = [('web', pt.cap('wave54-final', 'filter-effects', t, 'web')), ('ios', pt.cap('wave54-final', 'filter-effects', t, 'ios')), ('fixed-android', pt.png('cbb-android-replay/android-clip-3.png'))]
rows += [(f, fk(f'L2-{f}', 'android', t)) for f in ('c3-pink-sliver-off-scanlines', 'c3-notch-off-scanlines')]
say('## L2 clip-3: green-mask XOR vs the ref, fail ink')
for l, a in rows: say(f'  {l:32} xor {int((green(a) ^ green(r)).sum())} bad {int(bad(a).sum())}')

#!/usr/bin/env python3
# tools/titan/results/wave55-hyphen-ios-linebox/skeptic-census.py — wave-55 L1 SKEPTIC census (independent of the
# lane's census.py and of the brief's hyphen-ios-linebox.census.mjs; neither was imported or copied).
#
# Why: PLAN §0 "Skeptic census" — a builder's blast radius is re-derived by the skeptic with its OWN census before it is
# believed; an UNDER-reported radius is the dangerous direction, so this census is deliberately a SUPERSET model:
#   * every text-bearing component is assumed to reach PlaceholderLabel (no route filtering);
#   * the wrap width is assumed KNOWN for every run (so every run with a U+0020 space is pre-broken = exact);
#   * the unit's guard is read off the delivered patch (NormalLineBox.usedLineHeight): declared `normal` on the MERGED
#     list (GlobalExtractor.applyingAllReset first, then InheritedText.merge), WPT capture (every corpus doc), exact
#     line count, horizontal writing mode.
# Per run it prints face / size / raw line height / Blink box / N and a verdict; per document the union.
# Usage: /usr/bin/python3 -B skeptic-census.py [run]   (fontTools; reads the SHARED tree read-only)
import glob, json, math, os, re, sys
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave54-final'
SEC = os.path.join(ROOT, 'tools/titan/runs', RUN, 'sections')
INHERITED_SRC = os.path.join(ROOT, 'runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/InheritedText.swift')
# The inherited set, parsed from the Swift literal (between `inheritedTypes` and the closing `]`).
src = open(INHERITED_SRC).read()
block = src[src.index('static let inheritedTypes'):]
block = block[:block.index(']')]
INHERITED = set(re.findall(r'"([A-Za-z]+)"', block))
assert 'LineHeight' in INHERITED and 'WritingMode' in INHERITED and 'FontFamily' in INHERITED

# Face files: the iOS monospace pin (registered under `monospace` / `ui-monospace` for docs naming either), the bundled
# Inter (the default design), and document @font-face files resolved under tools/wpt/.
FACES = {'dejavu': os.path.join(ROOT, 'tools/titan/fonts/mono-pin/DejaVuSansMono.ttf'),
         'inter': os.path.join(ROOT, 'apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf')}
_cache = {}
def face(path):
    # hhea metrics + upm + cmap/hmtx for advances (CoreText reads hhea for these faces: none sets USE_TYPO with
    # different typo numbers — Inter's typo == hhea).
    if path not in _cache:
        t = TTFont(path)
        h = t['hhea']
        _cache[path] = dict(upm=t['head'].unitsPerEm, asc=h.ascent, desc=h.descent, gap=h.lineGap,
                            cmap=t.getBestCmap(), hmtx=t['hmtx'].metrics)
    return _cache[path]

def lround(x):  # half away from zero (lroundf)
    return math.floor(abs(x) + 0.5) * (1 if x >= 0 else -1)

def boxes(f, px):
    s = px / f['upm']
    a, d, g = f['asc'] * s, -f['desc'] * s, f['gap'] * s
    return a + d + g, lround(a) + lround(d) + lround(g)

def advance(f, px, text):
    s = px / f['upm']
    tot = 0
    for ch in text:
        gid = f['cmap'].get(ord(ch))
        tot += (f['hmtx'][gid][0] if gid in f['hmtx'] else f['upm'] * 0.55) * s
    return tot

def last(props, t):
    for p in reversed(props):
        if p['type'] == t:
            return p
    return None

def all_reset(own, inherited):
    # GlobalExtractor.applyingAllReset: last `All` keyword governs; before it only Direction/UnicodeBidi survive.
    idx = [i for i, p in enumerate(own) if p['type'] == 'All' and isinstance(p.get('data'), str)]
    if not idx:
        return own, inherited
    i = idx[-1]
    kw = own[i]['data'].upper().replace('-', '_')
    exempt = {'Direction', 'UnicodeBidi'}
    before = [p for p in own[:i] if p['type'] in exempt]
    after = [p for p in own[i + 1:] if p['type'] != 'All']
    keeps = kw in ('INHERIT', 'UNSET', 'REVERT', 'REVERT_LAYER')
    return before + after, (inherited if keeps else [p for p in inherited if p['type'] in exempt])

def merge(own, inherited):
    # InheritedText.merge (the Color unset/inherit filter is irrelevant to the line box).
    declared = {p['type'] for p in own}
    return [p for p in inherited if p['type'] not in declared] + own

def is_normal(props):
    p = last(props, 'LineHeight')
    return bool(p) and isinstance(p.get('data'), dict) and p['data'].get('original') == 'normal'

def families(props):
    p = last(props, 'FontFamily')
    d = p['data'] if p else []
    if isinstance(d, dict):
        d = d.get('families', [])
    if isinstance(d, str):
        d = [d]
    return [x.strip().strip('"\'').lower() for x in d if isinstance(x, str)]

def font_px(props, fams):
    p = last(props, 'FontSize')
    if p and isinstance(p.get('data'), dict) and isinstance(p['data'].get('px'), (int, float)):
        return float(p['data']['px']), 'declared'
    if p:
        return None, 'unresolved'
    # the 13px UA quirk for a bare monospace generic (MonospaceUAFontSize), else the 16px root
    return (13.0, 'ua-mono-13') if any(f in ('monospace', 'ui-monospace') for f in fams) else (16.0, 'root')

def width_px(props, f, px):
    p = last(props, 'Width')
    if not p or not isinstance(p.get('data'), dict):
        return None
    d = p['data']
    if isinstance(d.get('px'), (int, float)):
        return float(d['px'])
    o = d.get('original') or {}
    v, u = o.get('v'), (o.get('u') or '').upper()
    if v is None:
        return None
    if u == 'CH':
        return v * advance(f, px, '0')
    if u == 'EM':
        return v * px
    return None

def greedy_lines(f, px, text, avail):
    # space-split greedy fit (GreedyLineBreaker's shape; hyphenation not modelled → flagged by the caller)
    words = text.split(' ')
    lines, cur = 0, None
    sp = advance(f, px, ' ')
    w = 0
    for wd in words:
        a = advance(f, px, wd.replace('­', ''))
        if cur is None:
            cur, w = wd, a
            lines = 1
        elif w + sp + a <= avail + 0.01:
            w += sp + a
        else:
            lines += 1
            w = a
    return max(lines, 1)

def soft_wrap(text):
    for ch in text:
        c = ord(ch)
        if (ch.isspace() and c != 0xA0) or c in (0xAD, 0x200B, 0x2D, 0x2010, 0x2013, 0x2014) \
                or 0x2E80 <= c <= 0x9FFF or 0xAC00 <= c <= 0xD7AF:
            return True
    return False

rows, docs = [], {}
for path in sorted(glob.glob(os.path.join(SEC, '*', 'per-test-ir', '*.json'))):
    doc = json.load(open(path))
    stem = os.path.basename(path)[:-5]
    docfaces = {}
    for ff in doc.get('fontFaces') or []:
        fam = (ff.get('family') or '').strip().strip('"\'').lower()
        cand = os.path.join(ROOT, 'tools/wpt', ff.get('src', ''))
        if os.path.exists(cand):
            docfaces[fam] = cand
    comps = doc['components']
    names_mono = any(set(families(c.get('properties', []))) & {'monospace', 'ui-monospace'} for c in comps)
    if names_mono:
        docfaces.setdefault('monospace', FACES['dejavu'])
        docfaces.setdefault('ui-monospace', FACES['dejavu'])
    kids = {}
    for c in comps:
        par = (c.get('slot') or {}).get('parent')
        kids.setdefault(par, []).append(c)
    stack = [(c, []) for c in kids.get(None, [])]
    while stack:
        c, inh = stack.pop()
        own, rin = all_reset(c.get('properties', []), inh)
        merged = merge(own, rin)
        stack += [(k, [p for p in merged if p['type'] in INHERITED]) for k in kids.get(c['id'], [])]
        text = c.get('text')
        if not text or text.strip() == '' or not is_normal(merged):
            continue
        fams = families(merged)
        facename = next((fm for fm in fams if fm in docfaces), None)
        fpath = docfaces[facename] if facename else FACES['inter']
        design = 'mono' if any(x in ('monospace', 'ui-monospace') for x in fams) else (
            'serif' if any(x in ('serif', 'ui-serif') for x in fams) else 'default')
        approx = ''
        if not facename and design != 'default':
            approx = 'SYSTEM-FACE(approx by Inter)'  # faceMetrics → UIFont.systemFont: not measurable here
        f = face(fpath)
        px, pxsrc = font_px(merged, fams)
        if px is None:
            px, approx = 16.0, (approx + ' FONTSIZE-UNRESOLVED').strip()
        wm = last(merged, 'WritingMode')
        horizontal = not (wm and str(wm.get('data', '')).upper() in ('VERTICAL_RL', 'VERTICAL_LR', 'SIDEWAYS_RL', 'SIDEWAYS_LR'))
        ws = str((last(merged, 'WhiteSpace') or {}).get('data', '')).upper()
        nowrap = ws in ('NOWRAP', 'PRE')
        preserves = ws in ('PRE', 'PRE_WRAP', 'BREAK_SPACES', 'PRE_LINE')
        hy = str((last(merged, 'Hyphens') or {}).get('data', '')).upper()
        shown = text.replace('­', '') if hy == 'NONE' else text
        single = not any(ch.isspace() for ch in shown)
        dictionary = hy == 'AUTO'
        admits = (' ' in shown) or dictionary or ('­' in shown and horizontal)
        pre = (not nowrap) and (not preserves) and admits
        exact = single or nowrap or pre or (not soft_wrap(shown))
        raw, blink = boxes(f, px)
        verdict, n = None, None
        if not horizontal:
            verdict = 'DECLINED-vertical'
        elif not exact:
            verdict = 'DECLINED-not-exact'
        else:
            if pre:
                avail = width_px(merged, f, px) or 374.0
                n = greedy_lines(f, px, shown.replace('\n', ' '), avail)
                if (dictionary or '­' in shown) and advance(f, px, shown) > avail:
                    n = max(n, 2)
                    approx = (approx + ' HYPHENATION->N>=2').strip()
            else:
                n = shown.count('\n') + 1
            old = math.ceil(n * raw - 1e-9)
            new = n * blink
            pitch = any(round(k * raw) != k * blink for k in range(1, n))
            if old != new or pitch:
                verdict = 'MOVES' + ('(multi)' if n > 1 else '(1-line)')
            else:
                verdict = 'SAME'
        rows.append((stem, c['id'], facename or design, px, pxsrc, round(raw, 4), blink, n, verdict, approx,
                     shown[:40].replace('\n', '\\n')))
        docs.setdefault(stem, []).append(verdict)

print(f'run {RUN}: inherited set parsed ({len(INHERITED)} types); declared-normal text runs {len(rows)} in {len(docs)} documents')
for r in rows:
    print('  ' + ' | '.join(str(x) for x in r))
ios = sorted(s for s, v in docs.items() if any(x.startswith('MOVES') for x in v))
same = sorted(s for s, v in docs.items() if s not in ios and any(x == 'SAME' for x in v))
decl = sorted(s for s, v in docs.items() if s not in ios and s not in same)
print(f'\ncapture carriers (iOS, MOVES): {len(ios)}')
for s in ios: print('  ' + s)
print(f'in-radius SAME (box unchanged by the model): {len(same)}')
for s in same: print('  ' + s)
print(f'in-radius DECLINED only: {len(decl)}')
for s in decl: print('  ' + s + '  ' + ','.join(sorted(set(docs[s]))))
exp = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave55-plan/expectations.json')))['lanes']['L1-hyphen-ios-linebox']
plan = set(exp['captureCarriers']['ios'])
unit = set(exp['revertUnits']['NB-ios']['captures']['ios'])
print(f'\nvs expectations.json captureCarriers.ios ({len(plan)}): missing-from-plan {sorted(set(ios) - plan)}; '
      f'plan-not-in-census {sorted(plan - set(ios))}')
print(f'vs revertUnits.NB-ios.captures.ios ({len(unit)}): equal={set(ios) == unit}')
print(f"web {exp['captureCarriers']['web']} android {exp['captureCarriers']['android']} wire {exp['wireCarriers']}")

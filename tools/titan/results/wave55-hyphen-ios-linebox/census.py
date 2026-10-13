#!/usr/bin/env python3
# tools/titan/results/wave55-hyphen-ios-linebox/census.py — wave-55 L1 (NB-ios) builder census, read-only.
#
# Why: PLAN §0 "Skeptic census" / task rule (4): the lane re-derives its OWN blast radius instead of trusting the
# brief's (wave55-plan/hyphen-ios-linebox.census.mjs). This one is written independently and models the unit's actual
# predicate, guard by guard, as the delivered code states it (NormalLineBox.usedLineHeight + seam 1):
#   Blink box iff  effective == nil  (a declared `normal` under WPT capture: LineHeightNormal.lineBoxSource .natural)
#             and  lineCountIsExact  (PlaceholderLabel: singleLineText || noWrap || wptUnbreakableRun || preBroken)
#             and  horizontalWritingMode (D22).
# Inputs, all read-only: the VERBATIM per-test IR of a run (default wave54-final == wave55-open), the face FILES (hhea +
# hmtx via fontTools: the iOS monospace pin DejaVu Sans Mono, the bundled Inter, the document @font-face files the IR
# names), the Swift sources it mirrors (InheritedText.inheritedTypes is PARSED from the file, not retyped), the cell
# snapshot wave55-plan/cells-wave54-final.json, and expectations.json lanes["L1-hyphen-ios-linebox"] (cross-check).
# Per text run it decides: declined (which guard) / one line (box ceil(raw) → Blink) / multi-line (pitch raw → Blink),
# then per document: TARGET (multi-line moves) · MOVES (a one-line box changes) · SAME (nothing changes by the model).
# What it cannot decide statically is printed as such (wrap width: the measured advance against the box's own width,
# else the 374-px canvas content width — a conservative bound; route: every text run is assumed to reach
# PlaceholderLabel, the brief's R6 caveat).
#
# Usage: /usr/bin/python3 -B census.py [run=wave54-final] [--patches <patch> ...]   (python3 with fontTools)
import json, math, os, re, sys
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
argv = sys.argv[1:]
RUN = next((a for a in argv if not a.startswith('--') and not a.endswith('.patch')), 'wave54-final')
PATCHES = argv[argv.index('--patches') + 1:] if '--patches' in argv else []
SECTIONS = os.path.join(ROOT, 'tools/titan/runs', RUN, 'sections')
SWIFT = os.path.join(ROOT, 'runtimes/swiftui/Sources/StyleConverterRuntime')
CANVAS_CONTENT = 390 - 2 * 8  # the composed canvas (390 px) minus the UA body margin, CSS 2.1 §8.3 / html.css

# InheritedText.inheritedTypes, parsed from the Swift source so the walk inherits exactly what iOS inherits.
src = open(os.path.join(SWIFT, 'Renderer/InheritedText.swift'), encoding='utf-8').read()
block = src[src.index('static let inheritedTypes'):]
block = block[:block.index(']')]
INHERITED = set(re.findall(r'"([A-Za-z]+)"', block))
assert {'LineHeight', 'FontFamily', 'FontSize', 'WritingMode', 'WhiteSpace'} <= INHERITED, INHERITED

# Faces: hhea (ascender, |descender|, lineGap, upem) + an advance lookup — fontTools over the files iOS paints with.
FACE_FILES = {'dejavu': 'tools/titan/fonts/mono-pin/DejaVuSansMono.ttf',
              'inter': 'apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf'}
FACES = {}
def face(key, path):
    if key not in FACES:
        f = TTFont(os.path.join(ROOT, path), lazy=True)
        cmap, hmtx = f.getBestCmap(), f['hmtx']
        FACES[key] = dict(asc=f['hhea'].ascent, desc=-f['hhea'].descent, gap=f['hhea'].lineGap,
                          upem=f['head'].unitsPerEm, cmap=cmap, hmtx=hmtx, path=path)
    return FACES[key]
def lround(x):  # lroundf: half away from zero (all terms here are >= 0)
    return math.floor(x + 0.5)
def boxes(fc, px):
    a, d, g = fc['asc'] * px / fc['upem'], fc['desc'] * px / fc['upem'], fc['gap'] * px / fc['upem']
    return a + d + g, lround(a) + lround(d) + lround(g)
def advance(fc, text, px):
    w, miss = 0.0, 0
    for ch in text:
        if ch == '\u00ad': continue  # an unbroken soft hyphen paints nothing
        gid = fc['cmap'].get(ord(ch))
        if gid is None: miss += 1; w += 0.6 * px; continue  # fallback face: a 0.6 em guess, counted
        w += fc['hmtx'][gid][0] * px / fc['upem']
    return w, miss

def last(props, t):
    v = [p['data'] for p in props if p['type'] == t]
    return v[-1] if v else None
def has_soft_wrap(text):  # DecorationOps.hasSoftWrapOpportunity
    for ch in text:
        c = ord(ch)
        if (ch.isspace() and c != 0xA0) or c in (0xAD, 0x200B, 0x2D, 0x2010, 0x2013, 0x2014) \
           or 0x2E80 <= c <= 0x9FFF or 0xAC00 <= c <= 0xD7AF or 0xF900 <= c <= 0xFAFF or 0xFF00 <= c <= 0xFFEF:
            return True
    return False
def admits_prebreak(text, horizontal, dictionary):  # SoftHyphenPolicy.admitsPreBreak (verbatim clause order)
    if ' ' in text or dictionary: return True
    return '\u00ad' in text and horizontal
EXEMPT = {'Direction', 'UnicodeBidi'}  # css-cascade-4 §3.1: the two longhands `all` never resets
def all_reset(own, inherited):  # GlobalExtractor.applyingAllReset (wave 52 L11): the LAST keyword `All` governs
    idx = [k for k, x in enumerate(own) if x['type'] == 'All' and isinstance(x['data'], str)]
    if not idx: return own, inherited
    i, kw = idx[-1], own[idx[-1]]['data'].upper().replace('-', '_')
    before = [x for x in own[:i] if x['type'] in EXEMPT]          # own[0, i) drops except the exemptions
    after = [x for x in own[i + 1:] if x['type'] != 'All']         # own(i, end] kept verbatim
    keep = kw in ('INHERIT', 'UNSET', 'REVERT', 'REVERT_LAYER')    # INITIAL drops the inherited channel
    return before + after, (inherited if keep else [x for x in inherited if x['type'] in EXEMPT])

cells = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave55-plan/cells-wave54-final.json')))['cells']
exp = json.load(open(os.path.join(ROOT, 'tools/titan/results/wave55-plan/expectations.json')))['lanes']['L1-hyphen-ios-linebox']

runs, docs_n, comps_n, routes = [], 0, 0, {}
for sec in sorted(os.listdir(SECTIONS)):
    d = os.path.join(SECTIONS, sec, 'per-test-ir')
    if not os.path.isdir(d): continue
    for fn in sorted(os.listdir(d)):
        if not fn.endswith('.json'): continue
        doc = json.load(open(os.path.join(d, fn), encoding='utf-8')); docs_n += 1
        comps = doc['components']; comps_n += len(comps)
        by_id = {c['id']: c for c in comps}
        doc_faces = {ff['family'] for ff in (doc.get('fontFaces') or [])}
        merged = {}
        def merge(c):  # own over inherited (InheritedText.merge), parent first through slot.parent
            if c['id'] in merged: return merged[c['id']]
            p = by_id.get((c.get('slot') or {}).get('parent'))
            inh = [x for x in merge(p) if x['type'] in INHERITED] if p else []
            own, inh = all_reset(list(c.get('properties') or []), inh)  # BEFORE the merge, as the renderer does
            own_types = {x['type'] for x in own}
            m = [x for x in inh if x['type'] not in own_types] + own
            merged[c['id']] = m
            return m
        for c in comps:
            m = merge(c)
            lh = last(m, 'LineHeight')
            if not (isinstance(lh, dict) and lh.get('original') == 'normal'): continue
            if (c.get('meta') or {}).get('role') == 'line-break': continue
            # the label's text runs: a meta.runs host splits at its child entries (the <br> stacked boxes)
            segs, cur = [], ''
            for r in (c.get('meta') or {}).get('runs') or ([{'text': c.get('text')}] if c.get('text') else []):
                if isinstance(r.get('text'), str): cur += r['text']
                else: segs.append(cur); cur = ''
            segs.append(cur)
            segs = [s.strip(' \n\t') for s in segs if s and s.strip()]
            if not segs: continue
            fams = [x if isinstance(x, str) else (x.get('name') or x.get('keyword') or '') for x in (last(m, 'FontFamily') or [])]
            mono = any(re.fullmatch(r'(ui-)?monospace', x, re.I) for x in fams)
            # face: a document @font-face first (the mono pin delivers DejaVu as a face NAMED `monospace`), else Inter
            docface = next((x for x in fams if x in doc_faces), None)
            if docface:
                src_ = next(ff['src'] for ff in doc['fontFaces'] if ff['family'] == docface)
                path = 'tools/wpt/' + src_ + ('.otf' if src_.endswith('.woff') else '')
                if not os.path.exists(os.path.join(ROOT, path)):
                    path = 'tools/titan/fonts/mono-pin/' + os.path.basename(src_) + ('.otf' if src_.endswith('.woff') else '')
                fkey, fc = docface, face(docface, path)
            elif mono: fkey, fc = 'dejavu', face('dejavu', FACE_FILES['dejavu'])
            else: fkey, fc = 'inter', face('inter', FACE_FILES['inter'])
            fs = last(m, 'FontSize'); px = fs['px'] if isinstance(fs, dict) and isinstance(fs.get('px'), (int, float)) else 16
            wm = last(m, 'WritingMode'); horizontal = not (isinstance(wm, str) and re.match(r'(VERTICAL|SIDEWAYS)', wm))
            ws = (last(m, 'WhiteSpace') or ''); ws = ws.lower() if isinstance(ws, str) else ''
            tw = last(m, 'TextWrap'); tw = tw.lower() if isinstance(tw, str) else ''
            no_wrap = ws in ('nowrap', 'pre') or tw == 'nowrap'
            preserves = ws in ('pre', 'pre_wrap', 'pre-wrap', 'break_spaces', 'break-spaces')
            hy = last(m, 'Hyphens'); hy = hy.lower() if isinstance(hy, str) else None
            # `hyphens: auto` with a content language: the device ships the CF dictionary for `en` (AutoHyphenation)
            dictionary = hy == 'auto' and bool((c.get('meta') or {}).get('lang'))
            wd = last(m, 'Width'); avail, avail_src = CANVAS_CONTENT, 'canvas'
            if isinstance(wd, dict):
                o = wd.get('original') or {}
                if isinstance(wd.get('px'), (int, float)): avail, avail_src = wd['px'], 'width px'
                elif o.get('u') == 'CH': avail, avail_src = o['v'] * advance(fc, '0', px)[0], 'width ch'
                elif o.get('u') == 'EM': avail, avail_src = o['v'] * px, 'width em'
            raw, blink = boxes(fc, px)
            for s in segs:
                shown = s.replace('\u00ad', '') if hy == 'none' else s
                single = not any(ch.isspace() for ch in shown)
                pre = not no_wrap and not preserves and admits_prebreak(shown, horizontal, dictionary)
                unbreakable = not pre and not has_soft_wrap(shown)
                exact = single or no_wrap or unbreakable or pre
                w, miss = advance(fc, shown, px)
                lines1 = ((not has_soft_wrap(shown)) and not dictionary) or no_wrap or w <= avail + 0.01
                if not horizontal: verdict, why = 'DECLINED', 'vertical writing mode (D22 guard)'
                elif not exact: verdict, why = 'DECLINED', 'line count not exact (unpinned)'
                elif lines1:
                    one = math.ceil(raw - 1e-9)
                    verdict = 'MOVES' if one != blink else 'SAME'
                    why = f'1 line: box ceil({raw:.4f}) = {one} -> {blink}'
                else: verdict, why = 'TARGET', f'multi-line ({w:.1f} px > {avail:.1f} {avail_src}): pitch {raw:.4f} -> {blink}'
                runs.append(dict(sec=sec, doc=fn[:-5], comp=c['id'], text=shown, face=f'{fkey}@{px}', single=single,
                                 pre=pre, unbreakable=unbreakable, exact=exact, horizontal=horizontal, w=w, miss=miss,
                                 avail=avail, verdict=verdict, why=why))

print(f'census {RUN}: {docs_n} per-test IR documents, {comps_n} components; inherited types parsed: {len(INHERITED)}')
print(f'(1) declared-normal text runs: {len(runs)} in {len({r["doc"] for r in runs})} documents')
for r in runs:
    print(f'    {r["verdict"]:8} {r["doc"][5:]:<62} {r["comp"][-24:]:>24} {r["face"]:<14} '
          f'single={int(r["single"])} pre={int(r["pre"])} unbr={int(r["unbreakable"])} exact={int(r["exact"])} '
          f'horiz={int(r["horizontal"])} adv={r["w"]:.1f}{"(" + str(r["miss"]) + " fallback)" if r["miss"] else ""} | {r["why"]}  {r["text"][:28]!r}')
rank = {'TARGET': 3, 'MOVES': 2, 'SAME': 1, 'DECLINED': 0}
per_doc = {}
for r in runs:
    if r['doc'] not in per_doc or rank[r['verdict']] > rank[per_doc[r['doc']]['verdict']]: per_doc[r['doc']] = r
def cell(stem, plat):
    
    for k, v in cells.items():
        t, p = k.rsplit(' ', 1)
        if p == plat and t.replace('.html', '').replace('/', '__') == stem[5:]: return v
    return '—'
print('(2) per document (TARGET / MOVES = iOS capture carrier; SAME / DECLINED = in-radius must-not-move):')
for stem, r in sorted(per_doc.items()):
    print(f'    {r["verdict"]:8} {stem:<78} ios {cell(stem, "ios"):<9} {r["why"]}')
mine = sorted(s for s, r in per_doc.items() if r['verdict'] in ('TARGET', 'MOVES'))
theirs = sorted(exp['captureCarriers']['ios'])
print(f'(3) capture carriers: ios {len(mine)} · web 0 · android 0 (the unit is Swift-only); wire 0')
grew, shrank = sorted(set(mine) - set(theirs)), sorted(set(theirs) - set(mine))
print(f'    vs expectations.json lanes["L1-hyphen-ios-linebox"].captureCarriers.ios ({len(theirs)}): '
      + ('IDENTICAL' if not grew and not shrank else f'GREW by {len(grew)} {grew} · SHRANK by {len(shrank)} {shrank}'))
print(f'    wireCarriers expected {exp["wireCarriers"]} · revertUnits {list(exp["revertUnits"])} '
      f'(NB-ios captures ios {len(exp["revertUnits"]["NB-ios"]["captures"]["ios"])})')
print(f'    in-radius must-not-move ios: {sorted(s for s, r in per_doc.items() if r["verdict"] in ("SAME", "DECLINED"))}')
if PATCHES:  # the delivered patches touch Swift runtime files only → web / android / wire carriers are 0 by construction
    paths = sorted({p for f in PATCHES for p in re.findall(r'^diff --git a/(\S+) b/', open(f).read(), re.M)})
    foreign = [p for p in paths if not p.startswith('runtimes/swiftui/')]
    print(f'(4) patch paths {paths}: {"Swift-only — web / Compose / converter / extractor bytes unchanged" if not foreign else "FOREIGN " + str(foreign)}')
# (5) The Catalyst log (NormalLineBoxTests.testSameControlsLogTheirLineCountInputs, `catalyst-focused.log.txt` beside
# this script) is the RUNTIME's own reading of the in-radius controls' line-count inputs; the census ASSERTS its model
# against it (PLAN §2 L1: "the census asserts those values; it does not derive them").
LOG = os.path.join(HERE, 'catalyst-focused.log.txt')
if os.path.exists(LOG):
    logged = {}
    for line in open(LOG, encoding='utf-8'):
        mm = re.match(r'NB-ios SAME \S+ (\S+) horizontal=(\w+) singleLineText=(\w+) preBreak=(\w+) lineCountIsExact=(\w+) renderedLineCount=(\d+)', line)
        if mm: logged[mm.group(1)] = dict(h=mm.group(2) == 'true', single=mm.group(3) == 'true', pre=mm.group(4) == 'true',
                                          exact=mm.group(5) == 'true', n=int(mm.group(6)))
    counts = dict(re.findall(r'NB-ios SAME (\S+): (\d+) declared-normal', open(LOG, encoding='utf-8').read()))
    bad = []
    for cid, v in sorted(logged.items()):
        mine_ = [r for r in runs if r['comp'] == cid]
        if not mine_: bad.append(f'{cid}: logged by the runtime, absent from the census'); continue
        for r in mine_:  # a `<br>`-split host (hyphens-vertical-001 __3-321) logs its WHOLE text: compare the guards only
            if r['horizontal'] != v['h'] or r['exact'] != v['exact']: bad.append(f'{cid}: census horiz/exact {r["horizontal"]}/{r["exact"]} vs log {v["h"]}/{v["exact"]}')
            if len(mine_) == 1 and (r['single'] != v['single'] or r['pre'] != v['pre']): bad.append(f'{cid}: census single/pre {r["single"]}/{r["pre"]} vs log {v["single"]}/{v["pre"]}')
            if v['n'] != 1: bad.append(f'{cid}: renderedLineCount {v["n"]} != 1')
    ctl = ['all-prop-001', 'anonymous-table-ws-001', 'counter-style-at-rule__disclosure-styles',
           'boundary-shaping__boundary-shaping-010', 'hyphens__hyphens-vertical-001']
    for stem in ctl:
        n_census = len({r['comp'] for r in runs if r['doc'].endswith('__' + stem)})
        if int(counts.get(stem, -1)) != n_census: bad.append(f'{stem}: runtime logs {counts.get(stem)} declared-normal components, census {n_census}')
    print(f'(5) Catalyst log {os.path.basename(LOG)}: {len(logged)} logged runs, per-document counts {counts} → '
          + ('AGREES with the census (guards, line-count proxies, counts; all-prop-001 0 after the `all: initial` reset)' if not bad else 'DISAGREES: ' + '; '.join(bad)))

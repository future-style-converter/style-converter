#!/usr/bin/env python3
# tools/titan/results/wave53-soft-hyphen/skeptic-census.py — wave 53 L2 SKEPTIC's own blast-radius census (independent of
# the lane's census-jvm.kt.txt and of the plan's spaceless-soft-hyphen.census.py). Pure JSON walk over every per-test IR
# document of a run (default wave52-ship):
#   (1) every string ANYWHERE in a document (any key, properties included) that holds U+00AD;
#   (2) for each component-text / meta.runs[i].text / host-mirror string with no U+0020, the INHERITED context walked up
#       the slot.parent chain (Hyphens, WhiteSpace, WritingMode, OverflowWrap, WordBreak, TextWrap*, lang, nearest Width)
#       — the lane's census reads only the component's OWN list;
#   (3) every runs host with an abspos/fixed {child} member, classified by an independent re-implementation of the F2
#       predicate (no children/runs/decorations; tag span|time|data; Position ABSOLUTE|FIXED required; Color alpha 0
#       required; nothing else but Hyphens / Border*Color) — and whether the member is EMPTY (no text) which takes the
#       EMPTY arm instead.
import json, glob, os, sys, collections
RUN = sys.argv[1] if len(sys.argv) > 1 else 'wave52-ship'
ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf'
SHY = '­'
INH = ['Hyphens', 'WhiteSpace', 'WhiteSpaceCollapse', 'TextWrap', 'TextWrapMode', 'WritingMode', 'OverflowWrap', 'WordWrap', 'WordBreak', 'FontFamily']
docs = sorted(glob.glob(f'{ROOT}/tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json'))
anyshy_docs = set(); anyshy_by_key = collections.Counter(); spaceless = []; oof = []
def strings(o, path=''):
    if isinstance(o, str): yield path, o
    elif isinstance(o, dict):
        for k, v in o.items(): yield from strings(v, path + '.' + k)
    elif isinstance(o, list):
        for i, v in enumerate(o): yield from strings(v, path + f'[{i}]')
for f in docs:
    stem = os.path.basename(f)[:-5]
    d = json.load(open(f, encoding='utf-8'))
    comps = d['components']; byid = {c['id']: c for c in comps}
    byname = {c.get('name'): c for c in comps}
    for p, s in strings(d):
        if SHY in s:
            anyshy_docs.add(stem)
            # normalise the key path: drop indices
            import re
            anyshy_by_key[re.sub(r'\[\d+\]', '[]', p)] += 1
    def props(c): return {p['type']: p['data'] for p in c.get('properties', [])}
    def chain(c):
        out = []; seen = set()
        while c is not None and c['id'] not in seen:
            seen.add(c['id']); out.append(c)
            par = (c.get('slot') or {}).get('parent'); c = byid.get(par)
        return out
    def inherited(c):
        ctx = {}; width = None; lang = None
        for i, a in enumerate(chain(c)):
            pa = props(a)
            for k in INH:
                if k in pa and k not in ctx: ctx[k] = (json.dumps(pa[k]), i)
            if width is None and 'Width' in pa: width = (json.dumps(pa['Width']), i)
            l = (a.get('meta') or {}).get('lang') or a.get('lang')
            if lang is None and l: lang = (l, i)
        return ctx, width, lang
    for c in comps:
        meta = c.get('meta') or {}
        runs = meta.get('runs')
        cands = []
        if c.get('text') is not None: cands.append(('mirror' if runs else 'leaf', c['text']))
        for r in runs or []:
            if 'text' in r: cands.append(('run', r['text']))
        for where, s in cands:
            if SHY in s and ' ' not in s:
                ctx, width, lang = inherited(c)
                spaceless.append((stem, c['id'], where, s.replace(SHY, '<SHY>'), ctx, width, lang))
        # F2: runs hosts with an abspos/fixed child member
        for r in runs or []:
            if 'child' not in r: continue
            m = byname.get(r['child'])
            if m is None:
                # children are referenced by name; fall back to id prefix
                m = next((x for x in comps if x.get('name') == r['child'] or x['id'].startswith(r['child'] + '-')), None)
            if m is None: continue
            pm = props(m)
            pos = pm.get('Position')
            if not (isinstance(pos, str) and pos.upper() in ('ABSOLUTE', 'FIXED')): continue
            mm = m.get('meta') or {}
            tag = (mm.get('sourceTag') or '').lower() or None
            kids = [x for x in comps if (x.get('slot') or {}).get('parent') == m['id']]
            struct = bool(kids) or bool(mm.get('runs')) or bool(mm.get('decorations') or m.get('decorations'))
            col = pm.get('Color'); a = None
            if isinstance(col, dict) and isinstance(col.get('srgb'), dict): a = col['srgb'].get('a')
            others = [t for t in pm if t not in ('Position', 'Color', 'Hyphens', 'BorderTopColor', 'BorderRightColor', 'BorderBottomColor', 'BorderLeftColor')]
            text = m.get('text')
            empty = (not text) and not struct
            inert = (tag in ('span', 'time', 'data')) and not struct and a is not None and a <= 0 and not others and 'Color' in pm
            oof.append((stem, c['id'], r['child'], tag, pos, a, others, struct, empty, inert, text))
print(f'# skeptic census over {RUN}: {len(docs)} docs')
print(f'docs holding U+00AD anywhere: {len(anyshy_docs)}')
for k, n in sorted(anyshy_by_key.items()): print(f'  key {k}: {n}')
print(f'space-less U+00AD strings (leaf+run+mirror): {len(spaceless)}  by kind: {collections.Counter(x[2] for x in spaceless)}')
for x in spaceless:
    stem, cid, where, s, ctx, width, lang = x
    print('SPACELESS', stem.replace('wpt__', ''), cid.rsplit('__', 1)[-1], where, s, 'ctx=' + ','.join(f'{k}={v[0]}@{v[1]}' for k, v in ctx.items()), 'width=' + (f'{width[0]}@{width[1]}' if width else '-'), 'lang=' + (f'{lang[0]}@{lang[1]}' if lang else '-'), sep='\t')
print(f'abspos/fixed members in runs hosts: {len(oof)}  inert(F2-predicate): {sum(1 for x in oof if x[9])}  empty-arm: {sum(1 for x in oof if x[8])}')
for x in oof:
    stem, host, child, tag, pos, a, others, struct, empty, inert, text = x
    print('OOF', stem.replace('wpt__', ''), host.rsplit('__', 1)[-1], child, f'tag={tag}', f'pos={pos}', f'alpha={a}', f'others={others}', f'struct={struct}', f'empty={empty}', 'INERT' if inert else 'bail', repr(text)[:30], sep='\t')

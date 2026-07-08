#!/usr/bin/env python3
"""Careful incremental fixture expander v2.

For each Tier 1 partial row:
1. READ existing fixture (don't blow away).
2. Inspect existing components to identify the property under test AND any
   companion properties (e.g. border-style usually needs border-width).
3. Read parser to find keyword cases.
4. Filter out keywords already used and obviously-bad ones (identifier names
   matching the property itself, kotlin/swift reserved words, etc.).
5. APPEND new variants for missing keywords, reusing existing companion props.
6. Cap at 12 total components per fixture so test-all stays under 90s.
"""
import os, re, json
os.chdir('/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce')

CAP = 12

# Keywords to NEVER take from parser source (they're code, not CSS values)
BAD_KW = {
    'when','if','else','return','val','var','fun','it','this','let','const',
    'parseLength','parseColor','parseTime','parseAngle','parsePercent','parseInteger',
    'parseNumber','LengthParser','ColorParser','TimeParser','AngleParser',
    'PercentageParser','width','height','color','length','percent','time','angle',
    'integer','number','keyword','to','from','and','or','not','in','out','data',
    'src','main','app','kotlin','java','com','styleconverter','irmodels',
    'properties','longhands','parser','property','value','token','tokenize',
    'string','hash','dimension','number','ident','function','keyword','key',
    'true','false','null','none-yet','tbd','todo','fixme','xxx',
}

def kebab(p):
    return re.sub(r'(?<!^)(?=[A-Z])', '-', p).lower()

def parse_keywords(parser_path, css_prop):
    if not parser_path or parser_path == '(none)' or not os.path.exists(parser_path):
        return []
    src = open(parser_path).read()
    kws = []
    seen = set()
    for m in re.finditer(r'"([a-z][a-z0-9-]*)"', src):
        kw = m.group(1)
        if kw in BAD_KW or kw in seen: continue
        if kw == css_prop or kw == css_prop.replace('-', ''): continue  # don't use the property name as a value
        if len(kw) < 2 or len(kw) > 30: continue
        seen.add(kw)
        kws.append(kw)
    return kws

def expand_fixture(cat, prop_pascal, parser_path):
    fp = f'examples/properties/perfect/{cat}/{prop_pascal}.json'
    if not os.path.exists(fp):
        return ('SKIP_NOFIX', 0)
    css_prop = kebab(prop_pascal)
    try:
        d = json.load(open(fp))
    except Exception:
        return ('SKIP_BADJSON', 0)
    components = d.get('components', {})
    if not components:
        return ('SKIP_EMPTY', 0)

    # Get existing values for the css_prop
    existing_values = set()
    template_props = None
    for cname, cdata in components.items():
        props = cdata.get('properties', {})
        if css_prop in props:
            existing_values.add(str(props[css_prop]))
            if template_props is None:
                template_props = dict(props)  # copy as template

    if template_props is None:
        return ('SKIP_NOPROP', 0)

    # Pull keywords from parser
    candidates = parse_keywords(parser_path, css_prop)
    new_to_add = [kw for kw in candidates if kw not in existing_values]
    # Cap total components
    available_slots = CAP - len(components)
    if available_slots <= 0:
        return ('SKIP_FULL', len(components))
    new_to_add = new_to_add[:available_slots]
    if not new_to_add:
        return ('SKIP_NOMORE', len(components))

    # Append new variants reusing template's companion props
    base_idx = len(components)
    for i, val in enumerate(new_to_add):
        slug = re.sub(r'[^a-zA-Z0-9]', '_', val[:10])
        new_props = dict(template_props)
        new_props[css_prop] = val
        components[f'V{base_idx+i}_{slug}'] = {'properties': new_props}

    json.dump({'components': components}, open(fp, 'w'), indent=2)
    return ('OK', len(components))

# Walk partials
partials = []
for line in open('testing/TIER1_VARIANT_DEPTH.md'):
    parts = [p.strip() for p in line.split('|')]
    if len(parts) < 11 or parts[6] != 'partial': continue
    cat = parts[2]; prop_pascal = parts[3]; parser = parts[4]
    partials.append((cat, prop_pascal, parser))

print(f'{len(partials)} partial rows')

stats = {}
for cat, prop, parser in partials:
    code, count = expand_fixture(cat, prop, parser)
    stats.setdefault(code, []).append(f'{cat}/{prop}({count})')

for code, lst in stats.items():
    print(f'{code}: {len(lst)}')
    for s in lst[:5]: print(f'  {s}')

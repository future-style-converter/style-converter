#!/usr/bin/env python3
# skeptic-census.py — wave 53 L4 SKEPTIC's OWN blast-radius census (never the lane's script).
# Independent Python reading of the FloatAvoidPlan gate over every component of every composed tree of
# tools/titan/runs/<run>/sections/*/per-test-ir/*.json. Three tiers, widest first, so an under-reported
# radius shows up as a non-empty difference:
#   T0 structural  : >=1 leading Float children, then ONE last non-float child (any properties at all)
#   T1 child-proof : T0 + every CHILD-level clause the planner reads off the RAW children (G4-G8) + container
#                    plainBox + no Clear anywhere — i.e. everything a runtime property merge CANNOT change
#                    (both natives hand shape() the inheritance-merged + dynamically-resolved CONTAINER list,
#                    but the raw children)
#   T2 full gate   : T1 + the container-PROPERTY clauses (G2 attach, CONTAINER_BAILS, Display, Position)
# The runtime admits T2 at most when the merge only adds inherited types; it could admit up to T1 if a
# guaranteed-invalid var() / all-reset dropped a refusing container declaration.
# Both natives also rewrite the tree at the renderer entry BEFORE the block loop reads `component.children`
# (Compose ComponentRenderer.RenderComponent → ContentsUnboxing.resolve; Swift init → PseudoTextFold.resolve(
# ContentsUnboxing.resolve(...))). The census therefore runs twice: on the raw SlotComposer-shaped tree (what
# the lane's JVM / Catalyst census reads) and on the UNBOXED tree (css-display-3 §2.5 splice, emulated from
# ContentsUnboxing.kt: last Display wire CONTENTS, static, no pseudos → replaced by a text carrier + its
# recursively-unboxed children with the inheritable subset merged UNDER theirs; an unboxable ROOT keeps only its
# inheritable declarations). Usage: skeptic-census.py [run]
import glob, json, sys

RUN = sys.argv[1] if len(sys.argv) > 1 else "wave52-ship"
FLOAT_SIDES = {"LEFT": "L", "INLINE_START": "L", "RIGHT": "R", "INLINE_END": "R"}
BLOCK = {None, "BLOCK", "FLOW_ROOT"}; STATIC = {None, "STATIC"}
INTRINSIC = {"FIT_CONTENT", "MIN_CONTENT", "MAX_CONTENT"}
INLINE_LEVEL = {"INLINE", "INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID", "INLINE_TABLE"}
CONTAINER_BAILS = {"WritingMode", "Direction", "Gap", "RowGap", "ColumnGap", "ColumnCount", "ColumnWidth",
                   "BoxSizing", "Float", "MinWidth", "MaxWidth", "InlineSize", "MinInlineSize", "MaxInlineSize"}
BOX_BAILS = {"BoxSizing", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight", "InlineSize", "BlockSize",
             "MinInlineSize", "MaxInlineSize", "MinBlockSize", "MaxBlockSize", "AspectRatio", "WritingMode",
             "Direction", "Order", "Transform", "Translate", "Scale", "Rotate"}

INHERITED = {"FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch", "LetterSpacing", "LineHeight",
             "WordSpacing", "TextAlign", "TextTransform", "TextIndent", "WhiteSpace", "TabSize", "Direction",
             "WritingMode", "Color", "Visibility", "Cursor", "ListStyle", "ListStyleType", "ListStylePosition",
             "ListStyleImage", "Quotes", "TextShadow", "OverflowWrap", "WordWrap", "WordBreak", "Hyphens"}

def kw(props, t, last=False):
    # keyword of the first (or last) wire of type t: a bare string, or an object's keyword / value
    hits = [p for p in props if p["type"] == t]
    if not hits: return None
    d = hits[-1 if last else 0].get("data")
    s = d if isinstance(d, str) else (d.get("keyword") or d.get("value")) if isinstance(d, dict) else None
    return s.upper().replace("-", "_") if isinstance(s, str) else None

def px(c, t):
    hits = [p for p in c["properties"] if p["type"] == t]
    d = hits[0].get("data") if hits else None
    if not isinstance(d, dict) or d.get("type") == "percentage": return None
    v = d.get("px"); return float(v) if isinstance(v, (int, float)) else None

def contain(props):
    d = next((p.get("data") for p in reversed(props) if p["type"] == "Contain"), None)
    raw = d if isinstance(d, list) else (d.split() if isinstance(d, str) else [])
    out = set()
    for t in raw:
        t = str(t).upper().replace("-", "_")
        out |= {"STRICT": {"LAYOUT", "PAINT", "SIZE", "STYLE"}, "CONTENT": {"LAYOUT", "PAINT", "STYLE"}, "NONE": set()}.get(t, {t})
    return out

def clips(props):
    x = y = "VISIBLE"
    for p in props:
        k = kw([p], p["type"]); b = k if k in {"HIDDEN", "SCROLL", "AUTO", "CLIP"} else "VISIBLE"
        if p["type"] == "Overflow": x = y = b
        elif p["type"] in ("OverflowX", "OverflowInline"): x = b
        elif p["type"] in ("OverflowY", "OverflowBlock"): y = b
    return x != "VISIBLE" or y != "VISIBLE"

def bfc_root(props): return kw(props, "Display") == "FLOW_ROOT" or clips(props) or bool(contain(props) & {"LAYOUT", "PAINT"})
def side(c): return FLOAT_SIDES.get(kw(c["properties"], "Float", last=True))
def plain(c):
    m = c.get("meta") or {}
    return not c.get("text") and not m.get("runs") and c.get("pseudos") is None and (m.get("sourceTag") or "div").lower() == "div"
def boxbail(c): return any(p["type"].startswith(("Margin", "Padding", "Border")) or p["type"] in BOX_BAILS for p in c["properties"])
def paints(t): return t.startswith("Background") or t.startswith("Border") or t == "BoxShadow"
def has_text(c): return bool(c.get("text")) or bool((c.get("meta") or {}).get("runs")) or any(has_text(k) for k in c["kids"])
def has_wire(c, t): return any(p["type"] == t for p in c["properties"]) or any(has_wire(k, t) for k in c["kids"])
def painted_inline(c):
    return all((not any(paints(p["type"]) for p in d["properties"]) or
                (kw(d["properties"], "Display") in INLINE_LEVEL and px(d, "Width") is not None)) and painted_inline(d)
               for d in c["kids"])

def child_clauses(c):
    # Every clause read off the RAW children (+ container plainBox + no Clear in the subtree): None = pass, else why
    kids = c["kids"]
    if len(kids) < 2: return "fewer than 2 children"
    fl, b = kids[:-1], kids[-1]
    sides = [side(k) for k in fl]
    if None in sides: return "a leading child does not float"
    if not plain(c): return "container text/runs/pseudos/tag"
    if has_wire(c, "Clear"): return "Clear in subtree"
    if any(a == b2 for a, b2 in zip(sides, sides[1:])): return "same-side run"
    if any(not plain(k) or k["kids"] or boxbail(k) for k in fl): return "float not a plain childless box"
    if any(kw(k["properties"], "Display") not in (None, "BLOCK") for k in fl): return "float display"
    if any(px(k, "Width") is None or px(k, "Height") is None for k in fl): return "float non-px size"
    bp = b["properties"]
    if side(b) or not plain(b) or boxbail(b) or not bfc_root(bp): return "last child not a plain BFC root"
    if kw(bp, "Display") not in BLOCK or kw(bp, "Position") not in STATIC: return "BFC display/position"
    w = px(b, "Width")
    if w is None:
        cs = contain(bp)
        if kw(bp, "Width") not in INTRINSIC or "INLINE_SIZE" not in cs or cs & {"SIZE", "BLOCK_SIZE", "PAINT"} or clips(bp) or has_text(b):
            return "BFC inline size unprovable"
        if any(paints(p["type"]) for p in bp): return "contain-sized BFC paints"
    if not painted_inline(b): return "painted block descendant"
    return None

def container_clauses(c):
    cp = c["properties"]
    if c.get("slot") and not bfc_root(cp): return "G2 nested non-BFC"
    if any(p["type"] in CONTAINER_BAILS for p in cp): return "CONTAINER_BAILS"
    if kw(cp, "Display") not in BLOCK or kw(cp, "Position") not in STATIC: return "container display/position"
    return None

def unboxable(c):
    return kw(c["properties"], "Display", last=True) == "CONTENTS" and kw(c["properties"], "Position", last=True) in STATIC \
        and c.get("pseudos") is None

def unbox_kids(kids):
    out = []
    for k in kids:
        if not unboxable(k): out.append(k); continue
        inh = [p for p in k["properties"] if p["type"] in INHERITED]
        if k.get("text"): out.append({"id": k["id"] + "::contents-text", "properties": inh, "text": k["text"], "kids": []})
        for g in unbox_kids(k["kids"]):
            own = {p["type"] for p in g["properties"]}
            out.append(dict(g, properties=[p for p in inh if p["type"] not in own] + g["properties"]))
    return out

def unbox_tree(n):
    # renderer-entry rewrite, applied top-down exactly once per rendered component
    kids = [unbox_tree(k) for k in unbox_kids(n["kids"])]
    props = [p for p in n["properties"] if p["type"] in INHERITED] if unboxable(n) else n["properties"]
    return dict(n, kids=kids, properties=props)

def walk(n):
    yield n
    for k in n["kids"]: yield from walk(k)

files = sorted(glob.glob(f"tools/titan/runs/{RUN}/sections/*/per-test-ir/*.json"))
def census(unboxed):
    t0, t1, t2, ncomp = [], [], [], 0
    for f in files:
        comps = json.load(open(f))["components"]
        by = {c["id"]: dict(c, kids=[]) for c in comps}
        roots = []
        for c in comps:  # flat v2 list: relative array order of a slot.parent's entries IS the sibling order
            n = by[c["id"]]; par = (c.get("slot") or {}).get("parent")
            (by[par]["kids"] if par in by else roots).append(n)
        if unboxed: roots = [unbox_tree(r) for r in roots]
        for r in roots:
            for n in walk(r):
                ncomp += 1
                k = n["kids"]
                if len(k) >= 2 and all(side(x) for x in k[:-1]) and not side(k[-1]):
                    t0.append((f, n))
                    if child_clauses(n) is None:
                        t1.append((f, n))
                        if container_clauses(n) is None: t2.append((f, n))
    return t0, t1, t2, ncomp

name = lambda f: f.split("/")[-3] + "/" + f.split("/")[-1][:-5]
for unboxed in (False, True):
    t0, t1, t2, ncomp = census(unboxed)
    print(f"=== {RUN} {'UNBOXED (runtime entry rewrite)' if unboxed else 'RAW (SlotComposer tree)'}: "
          f"{len(files)} documents, {ncomp} components walked")
    print(f"T0 structural float…,last-non-float containers: {len(t0)} in {len({f for f,_ in t0})} documents")
    for f, n in t0: print("   T0", name(f), n["id"], "->", child_clauses(n) or ("container: " + (container_clauses(n) or "ADMIT")))
    print(f"T1 child-proof (runtime merge cannot change): {len(t1)}")
    for f, n in t1: print("   T1", name(f), n["id"], "container clause:", container_clauses(n) or "pass")
    print(f"T2 full gate: {len(t2)}: {sorted(n['id'] for _, n in t2)}")

#!/usr/bin/env python3
"""Skeptic (wave 54 L4) — an INDEPENDENT reach census for CBB-android over the
verbatim per-test IR (not the lane's JVM census). A box B moves the block its
children see iff, under WPT capture:
  (1) B's EFFECTIVE box-sizing is content-box (no BoxSizing BORDER_BOX),
  (2) B has a definite size on an axis — px, % of a known parent block, OR an
      unresolved expression / font-relative length the runtime resolves
      before childContainingBlock (the lane's census reads RAW properties and
      cannot see these; flagged 'expr'),
  (3) B has a non-zero padding/border band on that axis (px, em, expr),
and the moved block is READ by a descendant reached through % sizes:
  oof  an out-of-flow child (abspos % / inset stretch / auto margins),
  pct  any percentage / expression leaf on the child,
  vtx  text under a vertical writing mode (VerticalTextFlowLayout budget).
Prints one line per (document, box, reader). Usage: cbb_reach.py <run-dir>"""
import json, sys, glob, os, re

SPACE = re.compile(r'^(Margin|Padding|Top|Right|Bottom|Left|Inset|Width|Height|MinWidth|MinHeight|MaxWidth|MaxHeight|InlineSize|BlockSize|FlexBasis)')

def last(props, *types):
    v = None
    for p in props:
        if p["type"] in types: v = p["data"]
    return v

def length(d, base):
    """(value, tag): tag '' exact px, 'pct' resolved %, 'em'/'expr' approximate, None unknown."""
    if isinstance(d, dict):
        if isinstance(d.get("px"), (int, float)): return d["px"], ""
        if d.get("type") == "percentage" and isinstance(d.get("value"), (int, float)):
            return (d["value"] * base / 100.0, "pct") if base is not None else (None, None)
        if "expr" in d or isinstance(d.get("original"), dict): return 1.0, "expr"
        if d.get("u") in ("em", "rem", "ch", "ex", "lh") and isinstance(d.get("v"), (int, float)): return d["v"] * 16.0, "em"
    if isinstance(d, (int, float)) and d != 0: return float(d), ""
    return None, None

def band(props, *types):
    tot, tags = 0.0, set()
    for t in types:
        v, tag = length(last(props, t), None)
        if v: tot += v; tags.add(tag or "px")
    return tot, tags

def contentbox(props):
    bs = last(props, "BoxSizing")
    return not (isinstance(bs, str) and bs.upper() == "BORDER_BOX")

def tree(doc):
    comps = doc["components"]
    by = {c["id"]: dict(c, kids=[]) for c in comps}
    roots = []
    for c in comps:
        par = (c.get("slot") or {}).get("parent")
        (by[par]["kids"] if par in by else roots).append(by[c["id"]])
    return roots

def is_oof(props):
    pos = last(props, "Position")
    return isinstance(pos, str) and pos.upper() in ("ABSOLUTE", "FIXED")

def declared(props, *types):
    d = last(props, *types)
    return d is not None and d != "auto" and not (isinstance(d, str) and d.upper() == "AUTO")

def oof_reads(props):
    """An out-of-flow box reads its block (AbsposInsetStretch S1: both insets
    on an axis with no explicit size; a % size; auto margins between insets)."""
    sw = declared(props, "Left", "InsetInlineStart") and declared(props, "Right", "InsetInlineEnd") and not declared(props, "Width", "InlineSize")
    sh = declared(props, "Top", "InsetBlockStart") and declared(props, "Bottom", "InsetBlockEnd") and not declared(props, "Height", "BlockSize")
    pct = any(isinstance(p["data"], dict) and p["data"].get("type") == "percentage" for p in props
              if p["type"] in ("Width","Height","MinWidth","MinHeight","MaxWidth","MaxHeight","InlineSize","BlockSize"))
    am = any(p["type"].startswith("Margin") and p["data"] == "auto" for p in props)
    return sw or sh or pct or am or ALL_OOF

ALL_OOF = False

def vertical(props, inherited):
    wm = last(props, "WritingMode")
    return (isinstance(wm, str) and (wm.upper().startswith("VERTICAL") or wm.upper().startswith("SIDEWAYS"))) or (inherited and wm is None)

def readers(n, vert):
    """What on n reads the block it is laid out in (directly)."""
    props = n.get("properties") or []
    why = set()
    if is_oof(props) and oof_reads(props): why.add("oof")
    if any(SPACE.match(p["type"]) and re.search(r'"type": "percentage"|"expr"|"PERCENT"', json.dumps(p["data"])) for p in props): why.add("pct")
    if vertical(props, vert) and (n.get("text") or n.get("runs")): why.add("vtx")
    return why

def census(path):
    doc = json.load(open(path))
    out = []
    def walk(n, base_w, base_h, moved, vert):
        props = n.get("properties") or []
        v = vertical(props, vert)
        if moved:
            r = readers(n, v)
            if r: out.append((n["id"], moved, ",".join(sorted(r))))
        w, wt = length(last(props, "Width", "InlineSize"), base_w)
        h, ht = length(last(props, "Height", "BlockSize"), base_h)
        bw, bwt = band(props, "PaddingLeft", "PaddingRight", "BorderLeftWidth", "BorderRightWidth", "PaddingInlineStart", "PaddingInlineEnd")
        bh, bht = band(props, "PaddingTop", "PaddingBottom", "BorderTopWidth", "BorderBottomWidth", "PaddingBlockStart", "PaddingBlockEnd")
        mine = []
        if contentbox(props):
            if w is not None and bw: mine.append(f"W{w:g}{'/'+wt if wt else ''}+{bw:g}{'/'+'|'.join(sorted(bwt))}")
            if h is not None and bh: mine.append(f"H{h:g}{'/'+ht if ht else ''}+{bh:g}{'/'+'|'.join(sorted(bht))}")
        # A % child of a moved block moves its own block too (sizeOf(base)).
        inherited = None
        for k in n["kids"]:
            kp = k.get("properties") or []
            kw = last(kp, "Width", "InlineSize"); kh = last(kp, "Height", "BlockSize")
            pct_child = any(isinstance(x, dict) and (x.get("type") == "percentage" or "expr" in x) for x in (kw, kh))
            km = ";".join(mine) if mine else (moved if (moved and pct_child) else None)
            walk(k, w, h, km, v)
    for r in tree(doc): walk(r, 358.0, None, None, False)
    return out

run = sys.argv[1]
ALL_OOF = "--all-oof" in sys.argv
files = sorted(glob.glob(os.path.join(run, "sections/*/per-test-ir/*.json")), key=os.path.basename)
docs = {}
for f in files:
    for line in census(f): docs.setdefault(os.path.basename(f), []).append(line)
print(f"documents {len(files)}; reached {len(docs)}")
for d, lines in docs.items():
    tags = sorted({l[2] for l in lines}); approx = any("expr" in l[1] or "/em" in l[1] for l in lines)
    print(f"{d} readers={'|'.join(tags)} boxes={len(lines)}{' APPROX' if approx else ''}  e.g. {lines[0][0]} {lines[0][1]}")

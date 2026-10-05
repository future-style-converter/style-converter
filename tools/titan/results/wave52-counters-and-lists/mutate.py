#!/usr/bin/env python3
# tools/titan/results/wave52-counters-and-lists/mutate.py — lane L6's
# mutation harness (plan §0 rule i: "each proven able to fail by the named
# mutation"). `apply <name>` backs the target file up into the lane's
# scratch dir, records its sha256, and rewrites ONE exact substring (the
# replacement must match exactly once, or nothing is written); `restore
# <name>` copies the backup back and re-checks the sha256 so the restore is
# provably byte-exact. Every invocation appends to mutations.log beside
# this script — the durable record the test headers cite.
import hashlib, json, os, shutil, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
SCRATCH = os.environ.get('L6_SCRATCH', os.path.join(HERE, '.mutation-backups'))
LOG = os.path.join(HERE, 'mutations.log')

# name → (repo path, exact original substring, mutated substring, what it models)
MUTATIONS = {
    'korean-arm': ('runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListStyleExtractor.kt',
                   '"korean_hangul_formal" -> ListStyleType.KOREAN_HANGUL_FORMAL',
                   '"korean_hangul_formal_MUTATED" -> ListStyleType.KOREAN_HANGUL_FORMAL',
                   'T1: the typeFromKeyword arm no longer matches the wire keyword'),
    'band-clamp': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                   'kotlin.math.max(insetPx, extentPx - insetPx)',
                   'extentPx - insetPx',
                   'T2: the pre-fix far-edge centre (no clamp)'),
    # Fix pass (skeptic M1): the wiring itself, helper untouched — the
    # pre-fix-pass helper-only pins stayed green under side-bottom.
    'side-bottom': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                    '            Offset(0f, innerEdgeStrokeCentre(box.height, width / 2)),\n'
                    '            Offset(box.width, innerEdgeStrokeCentre(box.height, width / 2)),\n',
                    '            Offset(0f, box.height - width / 2),\n'
                    '            Offset(box.width, box.height - width / 2),\n',
                    'T2: sideGeometry BOTTOM back to the pre-fix centre (skeptic mutation 5)'),
    'side-end': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                 '            Offset(innerEdgeStrokeCentre(box.width, width / 2), 0f),\n'
                 '            Offset(innerEdgeStrokeCentre(box.width, width / 2), box.height),\n',
                 '            Offset(box.width - width / 2, 0f),\n'
                 '            Offset(box.width - width / 2, box.height),\n',
                 'T2: sideGeometry END back to the pre-fix centre'),
    'double-bottom': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                      '        Side.BOTTOM -> Offset(0f, innerEdgeStrokeCentre(box.height, inset)) to\n'
                      '            Offset(box.width, innerEdgeStrokeCentre(box.height, inset))\n',
                      '        Side.BOTTOM -> Offset(0f, box.height - inset) to\n'
                      '            Offset(box.width, box.height - inset)\n',
                      'T2: doubleGeom BOTTOM back to the pre-fix centre (double / groove / ridge)'),
    # R1 fix pass (skeptic re-verify R1). The entry ABOVE targets the pre-R1
    # file (sha256 9f806563…) and now refuses (0 matches); these target the
    # post-R1 doubleGeom / farEdgeBandCentre / doubleLines / grooveRidgeLines.
    'double-dev-bottom': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                          '        Side.BOTTOM -> Offset(0f, farEdgeBandCentre(box.height, sideWidth, inset)) to\n'
                          '            Offset(box.width, farEdgeBandCentre(box.height, sideWidth, inset))\n',
                          '        Side.BOTTOM -> Offset(0f, box.height - inset) to\n'
                          '            Offset(box.width, box.height - inset)\n',
                          'R1: doubleGeom BOTTOM = dev 5d9ed628 (`size.height - inset`) — only 0-tall/sub-band pins may go red'),
    'double-mirror': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                      '        Side.BOTTOM -> Offset(0f, farEdgeBandCentre(box.height, sideWidth, inset)) to\n'
                      '            Offset(box.width, farEdgeBandCentre(box.height, sideWidth, inset))\n'
                      '        // Start: measured in from the near edge — no clamp needed.\n'
                      '        Side.START -> Offset(inset, 0f) to Offset(inset, box.height)\n'
                      '        // End: measured in from the far edge of the (translated) band.\n'
                      '        Side.END -> Offset(farEdgeBandCentre(box.width, sideWidth, inset), 0f) to\n'
                      '            Offset(farEdgeBandCentre(box.width, sideWidth, inset), box.height)\n',
                      '        Side.BOTTOM -> Offset(0f, innerEdgeStrokeCentre(box.height, inset)) to\n'
                      '            Offset(box.width, innerEdgeStrokeCentre(box.height, inset))\n'
                      '        // Start: measured in from the near edge — no clamp needed.\n'
                      '        Side.START -> Offset(inset, 0f) to Offset(inset, box.height)\n'
                      '        // End: measured in from the far edge of the (translated) band.\n'
                      '        Side.END -> Offset(innerEdgeStrokeCentre(box.width, inset), 0f) to\n'
                      '            Offset(innerEdgeStrokeCentre(box.width, inset), box.height)\n',
                      'R1: doubleGeom BOTTOM+END back to the pre-R1 max(inset, extent - inset) (the regression itself)'),
    'double-mirror-bottom': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                             '        Side.BOTTOM -> Offset(0f, farEdgeBandCentre(box.height, sideWidth, inset)) to\n'
                             '            Offset(box.width, farEdgeBandCentre(box.height, sideWidth, inset))\n',
                             '        Side.BOTTOM -> Offset(0f, innerEdgeStrokeCentre(box.height, inset)) to\n'
                             '            Offset(box.width, innerEdgeStrokeCentre(box.height, inset))\n',
                             'R1: doubleGeom BOTTOM arm alone back to the pre-R1 mirror'),
    'double-mirror-end': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                          '        Side.END -> Offset(farEdgeBandCentre(box.width, sideWidth, inset), 0f) to\n'
                          '            Offset(farEdgeBandCentre(box.width, sideWidth, inset), box.height)\n',
                          '        Side.END -> Offset(innerEdgeStrokeCentre(box.width, inset), 0f) to\n'
                          '            Offset(innerEdgeStrokeCentre(box.width, inset), box.height)\n',
                          'R1: doubleGeom END arm alone back to the pre-R1 mirror'),
    'band-centre-mirror': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                           'kotlin.math.max(extentPx, bandPx) - insetPx',
                           'kotlin.math.max(insetPx, extentPx - insetPx)',
                           'R1: farEdgeBandCentre body = the per-line mirror clamp'),
    'double-lines-width': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                           'doubleGeom(side, inset = o, sideWidth = width, box = box)',
                           'doubleGeom(side, inset = o, sideWidth = line, box = box)',
                           'R1 wiring: drawDouble\'s plan passes one line width, not the side width, as the band'),
    'groove-lines-width': ('runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt',
                           'return doubleGeom(side, inset = half / 2f, sideWidth = width, box = box) to\n'
                           '            doubleGeom(side, inset = half / 2f + half, sideWidth = width, box = box)',
                           'return doubleGeom(side, inset = half / 2f, sideWidth = half, box = box) to\n'
                           '            doubleGeom(side, inset = half / 2f + half, sideWidth = half, box = box)',
                           'R1 wiring: groove/ridge plan passes the half width, not the side width, as the band'),
    'hang-width-kt': ('runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerOutsideHang.kt',
                      'return Placement(markerX, markerY, itemWidth, itemHeight)',
                      'return Placement(markerX, markerY, itemWidth + markerWidth + gapPx, itemHeight)',
                      'T5: the pair re-reserves the marker (the pre-T5 Row extent)'),
    'hang-width-swift': ('runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerOutsideHang.swift',
                         'width: itemSize.width, height: itemSize.height)',
                         'width: itemSize.width + markerSize.width + gap, height: itemSize.height)',
                         'T5: the pair re-reserves the marker (the pre-T5 HStack extent)'),
    'web-widen': ('runtimes/web/src/engine/lists/ListStyleTypeApplier.ts',
                  "  // Every predefined keyword: Chrome's own table is the oracle (wave 27).\n  return undefined;",
                  "  // Every predefined keyword: Chrome's own table is the oracle (wave 27).\n  return cssStringLiteral(`${markerText} `);",
                  'T3: the baked string applied to EVERY keyword (the broad variant)'),
    'web-narrow': ('runtimes/web/src/engine/lists/ListStyleTypeApplier.ts',
                   "if (!PREDEFINED_COUNTER_STYLE_KEYWORDS.has(kw)) return cssStringLiteral(`${markerText} `);",
                   "if (false && !PREDEFINED_COUNTER_STYLE_KEYWORDS.has(kw)) return cssStringLiteral(`${markerText} `);",
                   'T3: shape 2 (author names) dropped — shape 1 only'),
    'empty-pseudos': ('runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerEmptyItem.kt',
                      '            item.pseudos.isNullOrEmpty() &&\n',
                      '',
                      'T7-native: generated ::before content no longer counts as content'),
    'empty-pseudos-swift': ('runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerEmptyItem.swift',
                            '        default: return false\n',
                            '        default: break\n',
                            'T7-native: generated ::before content no longer counts as content (Swift)'),
    'web-inline': ('runtimes/web/src/engine/lists/ListStyleTypeApplier.ts',
                   "effectivePosition.trim().toLowerCase() === 'inside' && !hasChildren) {",
                   "effectivePosition.trim().toLowerCase() === 'inside' && !hasChildren && false) {",
                   'T3: the inline form dropped — every baked marker takes the <string> form'),
    'bake-inrange': ('tools/titan/counter-style-bake.mjs',
                     'return !ranges || ranges.some(([lo, hi]) => value >= lo && value <= hi);',
                     'return true;',
                     'T7/§4: range checking disabled'),
    'author-pad': ('tools/titan/counter-style-descriptors.mjs',
                   'if (a.t !== \'int\' || a.v < 0) return null;\n  const sym = parseSymbol(b);\n  return sym === null ? null : { length: a.v, symbol: sym };',
                   'if (a.t !== \'int\') return null;\n  const sym = parseSymbol(b);\n  return sym === null ? null : { length: a.v, symbol: sym };',
                   'T7: §3.1.6 pad accepts a negative length'),
}

def sha(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()

def log(entry):
    entry['at'] = time.strftime('%Y-%m-%dT%H:%M:%S%z')
    with open(LOG, 'a') as f:
        f.write(json.dumps(entry, ensure_ascii=False) + '\n')
    print(json.dumps(entry, ensure_ascii=False))

def main():
    verb, name = sys.argv[1], sys.argv[2]
    rel, orig, mutated, what = MUTATIONS[name]
    path = os.path.join(ROOT, rel)
    os.makedirs(SCRATCH, exist_ok=True)
    backup = os.path.join(SCRATCH, name + '.bak')
    if verb == 'apply':
        text = open(path, encoding='utf-8').read()
        # Exactly one match, or the mutation is not the one the header names.
        if text.count(orig) != 1:
            sys.exit(f'{name}: original substring found {text.count(orig)} times — refusing')
        shutil.copy2(path, backup)
        before = sha(path)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text.replace(orig, mutated))
        log({'verb': 'apply', 'name': name, 'file': rel, 'what': what, 'sha256_before': before, 'sha256_mutated': sha(path)})
    elif verb == 'restore':
        before = sha(backup)
        shutil.copy2(backup, path)
        after = sha(path)
        log({'verb': 'restore', 'name': name, 'file': rel, 'sha256_restored': after, 'byte_exact': after == before})
        if after != before:
            sys.exit(f'{name}: restore is NOT byte-exact')
        os.remove(backup)
    elif verb == 'result':
        log({'verb': 'result', 'name': name, 'file': rel, 'outcome': ' '.join(sys.argv[3:])})

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
# tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.cf-hyphenation.py — brief §4 measurement (read-only; no
# browser, no build). Asks CoreFoundation's `en` hyphenator — CFStringGetHyphenationLocationBeforeIndex, the macOS
# system hyphenation API — where it would hyphenate the -002 box text under the two models of what a line breaker hands
# it once an out-of-flow member sits inside the word:
#   (1) FRAGMENTS — each text piece between the member's item boundaries alone (`high`, `way`, …);
#   (2) U+FFFC   — the whole run with an OBJECT REPLACEMENT CHARACTER at the member's position (`high￼way`), asked
#                  at every before-index (a line breaker asks at the overflow index, not at the word end).
# Both models reproduce the web capture box by box (brief §4 table): boxes 2, 3, 6, 7 keep a high|way-equivalent point
# and render `high‐/way`; boxes 4 and 5 (`high|M|way`) get NO point at any index, so `highway` (63.68 px of Inter
# Regular at 16 px) overflows its 6ch (60.56 px) box on one line. Output: *.cf-hyphenation.out.txt.
import ctypes, ctypes.util
cf = ctypes.cdll.LoadLibrary(ctypes.util.find_library('CoreFoundation'))
class CFRange(ctypes.Structure):
    _fields_ = [('location', ctypes.c_long), ('length', ctypes.c_long)]
cf.CFStringCreateWithCharacters.restype = ctypes.c_void_p
cf.CFStringCreateWithCharacters.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_long]
cf.CFStringCreateWithCString.restype = ctypes.c_void_p
cf.CFStringCreateWithCString.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_uint32]
cf.CFLocaleCreate.restype = ctypes.c_void_p
cf.CFLocaleCreate.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
cf.CFStringIsHyphenationAvailableForLocale.restype = ctypes.c_bool
cf.CFStringIsHyphenationAvailableForLocale.argtypes = [ctypes.c_void_p]
cf.CFStringGetHyphenationLocationBeforeIndex.restype = ctypes.c_long
cf.CFStringGetHyphenationLocationBeforeIndex.argtypes = [ctypes.c_void_p, ctypes.c_long, CFRange, ctypes.c_ulong, ctypes.c_void_p, ctypes.c_void_p]
loc = cf.CFLocaleCreate(None, cf.CFStringCreateWithCString(None, b'en', 0x08000100))


def before(w, i):
    """CF's hyphenation location strictly before UTF-16 index i of w (-1 = none)."""
    u = w.encode('utf-16-le'); n = len(u) // 2
    s = cf.CFStringCreateWithCharacters(None, ctypes.create_string_buffer(u, len(u)), n)
    return cf.CFStringGetHyphenationLocationBeforeIndex(s, i, CFRange(0, n), 0, loc, None)


def points(w):
    """Every hyphenation point of w, found by walking before-index down from the end."""
    out, i = [], len(w)
    while i > 0:
        p = before(w, i)
        if p <= 0 or p >= i:
            break
        out.append(p); i = p
    return sorted(out)


print('hyphenation available for en:', cf.CFStringIsHyphenationAvailableForLocale(loc))
print('# (1) FRAGMENTS')
for w in ['highway', 'high', 'way', 'h', 'ighway', 'highwa', 'y']:
    pts = points(w)
    print(f'{w:8} points {pts} -> ' + '-'.join(w[a:b] for a, b in zip([0] + pts, pts + [len(w)])))
print('# (2) U+FFFC at the member position — CF answer at before-index 1..n (box: runs split)')
for box, (a, b) in enumerate([('', 'highway'), ('h', 'ighway'), ('high', 'way'), ('high', 'way'), ('highwa', 'y'), ('highway', '')], start=2):
    w = a + '￼' + b
    print(f'box {box} {a + "|M|" + b:13} {[before(w, i) for i in range(1, len(w) + 1)]}')

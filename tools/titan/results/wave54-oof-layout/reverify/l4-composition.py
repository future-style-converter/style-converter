# l4-composition.py — L4 RE-VERIFIER (wave 54): static read of a land-units.sh L4 block. For every L4 `commit` call it
# lists the lane-dir paths the commit adds, then asks, for each L4 unit's revert, which lane-dir file another unit's test
# reads would be deleted. The test reads come from a grep of the L4 test sources (only two literals exist; see the note).
import re, sys
src = open(sys.argv[1]).read()
blk = src[src.index('L4=$R/wave54-oof-layout'):src.index('# ── L3')]
# a commit call: commit "<title>" \ "<body>" \ <paths…> — joined over its backslash continuations
calls = re.findall(r'commit "([^"]+)" \\\n"[^"]*" \\\n\s*([^\n]+)', blk)
READS = {'OOF-android': ['census-compose.base.txt'], 'OOF-ios': ['census-swift.base.txt']}  # test → lane-dir literal
units = {}
for title, paths in calls:
    u = re.match(r'wave54 L4 (\S+?):', title).group(1)
    lane = [p for p in re.findall(r'"([^"]+)"', paths) if p.startswith('$L4')]
    units[u] = lane
    print(f'{u:12s} adds lane-dir paths: {lane or "-"}')
fails = 0
for u, lane in units.items():
    whole = '$L4' in lane
    for other, files in READS.items():
        if other == u: continue
        for f in files:
            if whole or f'$L4/{f}' in lane:
                # whole-dir add takes the file only if no earlier commit already committed it
                earlier = [v for v in list(units)[:list(units).index(u)] if f'$L4/{f}' in units[v] or '$L4' in units[v]]
                if earlier: continue
                print(f'FAIL: reverting {u} deletes {f}, which {other}\'s corpus pin reads'); fails += 1
print('composition:', 'HOLDS' if not fails else f'{fails} FAIL(s)')
sys.exit(1 if fails else 0)

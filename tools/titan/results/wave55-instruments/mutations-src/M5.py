import re
lines = s.split('\n'); hit = [i for i, l in enumerate(lines) if "is this script's own checkout" in l]
assert len(hit) == 1; lines[hit[0]] = "  :   # MUTATION M5: the own-checkout refusal dropped"; s = '\n'.join(lines)

a = "  if (member.children.length > 0 || (c.meta?.runs?.length ?? 0) > 0 || (c.meta?.decorations?.length ?? 0) > 0) return false;\n"
b = "  // MUTATION W1: the structural refusal deleted\n"
assert s.count(a) == 1, a
s = s.replace(a, b)

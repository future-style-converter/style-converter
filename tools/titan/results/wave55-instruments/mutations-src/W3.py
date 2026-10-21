a = "if (pending.length > 0 && last?.kind === 'text' && inWordEnd(last.text) && inWordStart(t)) {"
b = "if (false /* MUTATION W3 */) {"
assert s.count(a) == 1, a
s = s.replace(a, b)

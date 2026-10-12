a = "if (wouldBe.length && lit === wouldBe.length)"
assert s.count(a) == 1
s = s.replace(a, "if (wouldBe.length && lit > 0 /* MUTATION T2 (X8) */)")

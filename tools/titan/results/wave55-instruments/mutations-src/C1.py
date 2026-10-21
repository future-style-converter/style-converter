a = "val prop = properties.lastOrNull { it.type == IR_TYPE } ?: return null"
assert s.count(a) == 1; s = s.replace(a, "val prop = properties.firstOrNull { it.type == IR_TYPE } ?: return null // MUTATION C1")

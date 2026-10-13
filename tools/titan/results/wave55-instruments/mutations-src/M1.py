a = "process.exitCode = main(process.argv.slice(2));"
assert s.count(a) == 1; s = s.replace(a, "{ main(process.argv.slice(2)); process.exitCode = 0; }   // MUTATION M1: the exit code is always 0")

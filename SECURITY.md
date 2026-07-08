# Security policy

## Supported versions

There are no tagged releases yet. Until there are, security fixes land on
the two live branches:

| version | supported |
|---|---|
| `dev` (integration branch — all PRs target it) | yes |
| `main` (latest promoted release state) | yes |
| anything older | no — please update |

Once releases exist, this table will name the latest release explicitly;
only the latest release and `dev` will receive fixes.

## Reporting a vulnerability

Please **do not open a public issue** for anything you believe is a
security problem. Instead:

1. **GitHub private vulnerability reporting** — use the "Report a
   vulnerability" button under the repository's *Security* tab, once it is
   enabled for this repo. If you don't see the button, use option 2.
2. **Email** — [greaflorent@gmail.com](mailto:greaflorent@gmail.com) with
   a description, reproduction steps (a minimal fixture or IR document is
   ideal), and the commit you tested.

You'll get an acknowledgement within a few days. This is a
single-maintainer project, so please be patient with fix timelines — but
you won't be ignored.

## Disclosure

Coordinated disclosure with a **90-day** window: after you report
privately, we aim to fix and release within 90 days, after which you're
free to publish. If a fix lands sooner, feel free to publish as soon as
it's released. If we need longer, we'll say so and agree on a date rather
than go silent.

## Scope — what counts as a vulnerability here

This is a style converter and three runtime style engines. There is no
network stack, no authentication, no credential handling, and no server —
so most classic vulnerability classes simply don't apply.

What **is** in scope:

- **Parsing untrusted input.** The converter parses CSS/JSON envelopes,
  and the three runtimes (`runtimes/web`, `runtimes/compose`,
  `runtimes/swiftui`) decode IR JSON documents. On the server-driven-UI
  horizon those IR documents may come from a network — so any bug where a
  malicious envelope or IR document causes a crash the caller can't
  catch, unbounded memory/CPU consumption, or code execution in the
  converter or a runtime is a security bug, today. (The parser-fuzz tier
  in [docs/STATUS.md](docs/STATUS.md) exists for exactly this reason.)
- **Dependency vulnerabilities** that are actually reachable from the
  converter or runtime code paths.
- **Supply-chain issues** in the build or CI (`.github/workflows/`,
  Gradle/npm/SwiftPM manifests).

What is **not** in scope:

- Rendering divergences between platforms (file a rendering-bug issue —
  that's a correctness bug, not a security one).
- Issues requiring a compromised development machine.
- Vulnerabilities in the test harnesses (`apps/`) or visual tooling
  (`tools/`) that don't affect the shipped runtimes — still worth an
  issue, just a public one.

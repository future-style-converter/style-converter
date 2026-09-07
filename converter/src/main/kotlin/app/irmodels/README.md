# `irmodels/` — the typed IR

The Kotlin data model the CSS reader parses *into* and serializes *out of*.
Everything here is wire-facing: changing an emitted byte shape is a
major-version freeze event (`schema/spec/05-versioning.md`).

## What lives here

| file | role |
|---|---|
| `IRDocument.kt` | the document envelope (components, `irVersion` / `minReaderVersion`) |
| `IRProperty.kt` | the sealed base every property type extends |
| `IRPropertySerializer.kt` | polymorphic (de)serialization for that hierarchy |
| `IRWireV2.kt` | the current flat-list slot/placement wire (`--emit-ir v1` is the deprecated nested-children path) |
| `ValueTypes.kt` | shared value shapes (lengths, colors, angles, times) |
| `ColorConversion.kt` | color-space math → the sRGB 0-1 floats the wire carries |
| `PropertyScope.kt` | SELF / CONTAINER / ITEM style-ownership classification (`schema/spec/03-children.md`; not emitted on the wire) |
| `properties/` | the property catalogue, one file per CSS property |

There is no `platform/` directory: the IR is platform-neutral by
construction, and the three runtimes consume it as JSON.

## The catalogue

`properties/` holds **558** property models across **33** categories —
one folder per category, matching the runtime engine roots. Derive both
numbers rather than trusting this line:

```bash
find converter/src/main/kotlin/app/irmodels/properties -name '*Property.kt' | wc -l   # 558
ls -d converter/src/main/kotlin/app/irmodels/properties/*/ | wc -l                     # 33
```

**Naming is a contract.** Each file is `<Name>Property.kt` and declares the
IR type `<Name>`; `tools/visual/coverage-audit.mjs` derives the catalogue by
stripping that exact `Property.kt` suffix, and each platform's
`PropertyRegistry` claims properties by that `<Name>` string. Rename a file
and you silently drop a property from the coverage matrix.

## Pointers

- Value normalization (px / degrees / ms / sRGB floats, and what `null`
  means): `schema/spec/02-values.md`.
- Adding a property end-to-end (model → parser → registry → fixture →
  three runtime triplets → baseline): repo-root `CLAUDE.md`, *Adding a new
  property*.
- The parser that fills these models:
  `converter/src/main/kotlin/app/parsing/css/properties/longhands/<category>/<Name>PropertyParser.kt`.

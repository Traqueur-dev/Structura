# Changelog

All notable changes to Structura. Versions follow [semantic versioning](https://semver.org):
a minor version adds features without breaking existing code.

## 2.3.0 — Errors that say where

### Added

- **Located errors.** Every error raised while reading a document carries the path of the key
  (`limits.per-day`, `offers[2].price`, `currencies.gems.symbol`), its line and column, the source it
  was read from, and a stable kind: `StructuraException#path()`, `#line()`, `#column()`, `#source()`,
  `#kind()`, `#detail()`. Kinds: `UNKNOWN_KEY`, `MISSING`, `WRONG_TYPE`, `INVALID`, `READER`, `SYNTAX`,
  and `OTHER` for errors raised outside of a document.
- **`StructuraProcessor#parse(String, Class, String sourceName)`** and `#parseEnum(..., sourceName)` name
  the source in the position; `Structura.load(...)` passes the file path.
- **Collected errors.** `Structura.builder().collectErrors(true)` carries on past an error and throws a
  `StructuraErrors` (extends `StructuraException`) whose `errors()` lists every error of the document,
  located and in the order of the text.
- **Strict keys.** `Structura.builder().strictKeys(true)`, or `@Strict` on a record, makes a key that no
  component reads an `UNKNOWN_KEY` error suggesting the closest known key.

### Messages, before and after

Measured on the same documents with 2.2.1 and 2.3.0.

| Case | 2.2.1 | 2.3.0 |
|------|-------|-------|
| Wrong type in a nested record | `Failed to parse YAML for class Shop` (cause: `For input string: "many"`) | `3:12: 'many' is not a number (limits.per-day)` |
| Wrong type in a list element | `Failed to parse YAML for class Shop` (cause: `For input string: "cheap"`) | `8:12: 'cheap' is not a number (offers[1].price)` |
| Missing key in a nested record | `per-day is required but not provided` | `3:3: 'per-day' is required but not provided (limits.per-day)` |
| Missing key in a map value | `symbol is required but not provided` | `6:5: 'symbol' is required but not provided (currencies.gems.symbol)` |
| Validation rule | `Value must be at least 1` | `3:12: Value must be at least 1 (limits.per-day)` |
| Unknown polymorphic type | `No registered type found for gold in polymorphic type Reward. Available types: money` | `5:9: No registered type found for gold in polymorphic type Reward. Available types: money (reward.type)` |
| Invalid YAML | `Failed to parse YAML for class Shop` (cause: `while parsing a flow sequence…`) | `4:1: invalid YAML: expected ',' or ']', but got <stream end>` |
| Typo in a key (`max-item: 54`) | *no error, the default applies* | *unchanged by default*; with `strictKeys(true)`: `2:1: unknown key 'max-item' (did you mean 'max-items'?)` |
| Any of the above through `Structura.load(path)` | *no file name* | `config.yml:3:12: …` |
| A record refusing its values in its compact constructor | `Failed to create instance of Range. Arguments: [5, 1]` (the reason only in the cause) | `2:3: Range refused its values: min must not exceed max (range)` |

### Compatibility

- Nothing changes for code that does not enable the new modes: valid documents are read into the same
  values, and the existing test suite passes unchanged. One malformed shape is now refused: a section or
  a list where a boolean is expected (2.2.1 silently read it as `false`) is a `WRONG_TYPE` error.
- Messages gain a position and a path; their text stays the same apart from the cases listed above.
  Code matching on `getMessage()` should move to `kind()` and `path()`.
- An error raised outside of a document (registry misuse, a direct call to `Validator`) keeps its plain
  message.
- A map read into `Map<K, V>` now keeps the order of the document (`LinkedHashMap`) and accepts an empty
  value (`key:`), which used to fail with an internal error.

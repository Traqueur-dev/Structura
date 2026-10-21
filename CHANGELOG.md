# Changelog

All notable changes to Structura. Versions follow [semantic versioning](https://semver.org):
a minor version adds features without breaking existing code.

## 2.7.0 — Nested validation and strict enums

### Added

- **`Structura.builder().validateNested(true)`** validates every record reached through a list or a set
  (`offers[2].price`), a map value (`currencies.gems.symbol`), a polymorphic component, at any depth, and the
  lists and maps of enum constants; each error has its path and line and is collected with `collectErrors`.
  Off by default: configurations accepted before stay accepted.
- **Strict enums.** With `strictKeys(true)` or `@Strict` on the enum, `parseEnum` reports a key that is no
  constant and a key of a constant that none of its fields reads.
- `UnknownKeys.error(...)` / `UnknownKeys.closestKey(...)`: the unknown-key error and its suggestion, public
  for tools.

### Fixed

- An enum field of a generic type (`List<Offer> offers`) is converted with its type arguments; it used to
  hold raw maps.

### Messages, before and after

| Case | 2.6.0 | 2.7.0 |
|------|-------|-------|
| `price: 0` on the second offer, `@Min(1)`, with `validateNested(true)` | *accepted* | `5:12: Value must be at least 1 (offers[1].price)` |
| Same, without the option | *accepted* | *accepted* |
| `gold-pluss:` in an enum file, strict | *ignored* | `7:1: unknown key 'gold-pluss' (did you mean 'gold-plus'?)` |
| `treshold: 10` under a constant, strict | *ignored* | `3:3: unknown key 'treshold' under 'bronze' (did you mean 'threshold'?)` |

## 2.6.0 — A writer that keeps the file as written

What the writer did in 2.5.0, checked against the three needs of an in-game editor:

| Need | 2.5.0 | 2.6.0 |
|------|-------|-------|
| Rewrite a file keeping its comments and the order of its keys | No: `write` rebuilt the file, comments lost, keys in declaration order | `Structura.update(file, config)` |
| Write only what differs from the jar default, like the server file | No: the whole record was written | `Structura.writeOverrides(file, config, defaults)` |
| Use the same key names as the reader | Yes (same `FieldMapper`) | Yes, now tested against `Structura.keyOf` and a strict read |

### Added (`structura-writer`)

- **`Structura.update(Path, Loadable)`** rewrites a file with new values while keeping comments, key
  order and the quoting of unchanged values; a removed key goes away, a new key goes after the others, list
  indentation follows the file. Creates the file if needed.
- **`Structura.writeOverrides(Path, Loadable config, Loadable defaults)`** writes only what differs from the
  defaults — sections key by key, lists and values as a whole, as layered reading merges them — keeping the
  comments already in the file.
- `StructuraWriter#update` and `#writeOverrides`, as default methods of the SPI.

### Limits

- The comments attached to a removed key go with it.
- SnakeYAML reduces the spaces before an end-of-line comment to one.
- `writeOverrides` cannot express that a key of a map present in the default was removed: layered
  reading merges maps key by key.

## 2.5.0 — Keys, schema and registries per processor

### Added

- **`Structura.keyOf(RecordComponent)`** and **`Structura.keyOf(Enum)`** return the exact key the reader
  uses. The rule is documented: a hyphen goes between a lowercase letter and the uppercase letter that
  follows it, then the name is lowercased — `httpURL` → `http-url`, `HTTPServer` → `httpserver`,
  `level2Cap` → `level2cap`.
- **`Structura.schema(Class)`** / `StructuraProcessor#schema(Class)` describe a record as a `Schema` tree
  (keys, shapes, required keys, defaults, descriptions, polymorphic sub-types, list elements, map values),
  and `Schema#toJsonSchema()` renders it for editors.
- **`@Description("...")`** and **`@Options(description = "...")`** describe a component for the schema.
- **Registries per processor.** `Structura.builder().readers(CustomReaderRegistry.create())` and
  `.polymorphics(PolymorphicRegistries.create())`; the global registries stay the default and the fallback.

### Changed

- The global polymorphic registries are now thread-safe.
- Telling a list of records written as a map from a single record now honours `@Options(name)`.

## 2.4.0 — Layers and readers that see the value

### Added

- **Layered sources.** `StructuraProcessor#parse(List<Source>, Class)` (and `Structura.parse(List<Source>, Class)`,
  `parseEnum(List<Source>, Class)`) reads a default and its overrides in one call, without rewriting any
  text. Sections merge key by key at every depth; a list or a value of an upper layer replaces the one below.
  Each error names the layer and the line in that layer; strict mode checks every layer.
  `Source.of(name, text)`, `Source.of(Path)`, `Source.ofResource(path)`.
- **Contextual readers.** `ContextualReader<T>` receives a `ReadContext`: `raw()` as YAML typed it, `text()`,
  the full `target()` type, `path()`, `convert(Type)` and `typeArgument(int)`.
  `CustomReaderRegistry#registerContextual(Class | TypeToken, reader)`.
- **Generic readers.** `CustomReaderRegistry#registerGeneric(Resolvable.class, reader)` serves every
  parameterization of a type; a reader for an exact type wins over it.

### Changed

- During a read, a plain `Reader<T>` also receives the text of a number or a boolean when the value does
  not already have the requested type, so `amount: 100` and `amount: "100"` reach the same reader.
  The registry's own `convert(...)` methods keep handing only strings to a reader.

### Messages, before and after

| Case | 2.3.0 | 2.4.0 |
|------|-------|-------|
| `amount: 100` with a reader registered for `Amount` | `1:9: Unsupported conversion from 100 to com.example.Amount (amount)` | *read by the reader* |
| A failing reader | `1:9: Failed to convert value 'lots' to type Amount using custom reader: For input string: "lots" (amount)` | *unchanged*, kind `READER` |
| Wrong value in the server's file, read over the jar default | *needed a text merge; the line pointed into the rewritten text* | `config.yml:3:12: 'many' is not a number (limits.per-day)` |
| Wrong value kept from the jar default | *same* | `config.yml (jar):4:13: 'lots' is not a number (limits.per-week)` |

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

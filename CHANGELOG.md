## Changelog

### Added

- Plain POJO API: `@Configuration`-annotated classes can be loaded without extending `ConfigurablePojo` via `ConfigurationLoader.from(path).loadPlain(...)`, returning a `Config<T>` handle with `get()`, `save()`, `reload()`, `getLastLoadIssues()` and auto-reload control
- `@PostInject` methods may now take a single `List<LoadIssue>` parameter to receive the load issues directly (both API styles)
- Recursive `@Section` nesting: sections can contain sections at arbitrary depth (capped at 16) in all formats — YAML indented blocks, TOML `[parent.child]` tables, JSON nested objects, Properties dotted keys
- Load-issue reporting: values that cannot be converted (e.g. `port = "abc"` for an `int` field) keep the field default and are recorded on the POJO; inspect via `getLastLoadIssues()`, available inside `@PostInject` for custom validation (TOML and Properties)
- YAML: `java.time` support (`LocalDate`, `LocalDateTime`) as ISO-8601 scalars
- Properties format module (`de.bsommerfeld.jshepherd:properties`): pure-JDK `.properties` support with dotted keys for sections and maps, comma-separated lists, comments and UTF-8
- Auto-reload: `ConfigurationLoader.from(path).withAutoReload(Duration)` watches the file on a daemon thread and reloads the POJO on external changes; `setOnAutoReload(Runnable)` and `stopAutoReload()` on `ConfigurablePojo`
- Type coercion on load: quoted numbers and booleans are converted when the target field is numeric/boolean (`String` fields are never touched)
- JSON: `java.time` support (`LocalDate`, `LocalDateTime`, ...) serialized as ISO-8601 strings via the Jackson JSR-310 module
- JSON: generated Markdown documentation now includes `@Section` fields and their nested keys
- Unparseable config files are backed up to `<filename>.bak` before being replaced with defaults, so user edits are never silently lost

### Changed

- Clearer error messages: loading a file without an extension now explains the problem, and "unsupported extension" errors list the supported extensions (or hint at missing format modules)

### Fixed

- TOML: integer lists (`List<Integer>`) now load correctly (elements arrived as `Long`); typed map values are converted as well
- `@PostInject` methods declared in superclasses are now invoked (previously only the concrete class was scanned)
- YAML: comment-mode output was corrupted on Windows due to platform-dependent line-separator handling
- TOML: map keys that are not valid bare keys (spaces, special characters) are now quoted instead of producing invalid TOML
- A bare `@Comment` annotation without a value no longer emits an empty `# ` line

### Removed

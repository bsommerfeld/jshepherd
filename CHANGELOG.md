## Changelog

### Added

- Hidden fields: `hide(...)`/`show(...)` on `ConfigurablePojo` and the `Config<T>` handle keep fields or whole sections out of the configuration file until a condition of your choice unlocks them (e.g. debug options shown once the user sets `debug: true`). Set the initial state in the constructor and react to loaded values in `@PostInject`; when that changes the visibility after a load, `reload()` or auto-reload, the file is rewritten right away. Supported in all formats, including the JSON documentation file (bsommerfeld/jshepherd#13)
- `bindVisibility(key, condition)` on `ConfigurablePojo`, `FieldVisibility` and the `Config<T>` handle ties the visibility of a field or section to a condition (e.g. `bindVisibility("debug-options", () -> debug)` in the constructor), replacing the constructor/`@PostInject` pair for the common case. The condition is evaluated right away, before every `save()`, and after every load, `reload()` or auto-reload
- `@PostInject` methods may declare a `FieldVisibility` parameter (alone or next to `List<LoadIssue>`), so plain `@Configuration` POJOs can show and hide fields too

### Changed

- `save()` leaves the file untouched when its content would not change, so the modification time is no longer bumped by no-op saves

### Fixed

### Removed


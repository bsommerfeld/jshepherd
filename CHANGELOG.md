## Changelog

### Added

### Changed

### Fixed

- TOML: maps with enum keys (e.g. `Map<FlagType, Boolean>`) inside a `@Section` produced a broken file that ended at `default_flags = {`. Enum map keys are now always written by constant name (`MOBS = true`, not their `toString()`), and on load the keys are converted back to the declared key type, so `map.get(FlagType.MOBS)` works after a reload (bsommerfeld/jshepherd#14)
- Properties: enum map keys were written via `toString()` and loaded back as plain strings. They are now written by constant name (`default_flags.MOBS=true`) and converted to the declared key type on load

### Removed


## Changelog

### Added

### Changed

### Fixed

- YAML: empty lists and maps are now written as `[]` / `{}` instead of being split across two lines with a stray indent, which produced misaligned (and confusing) output for keys inside nested sections
- YAML: empty collections are no longer emitted as anchors/aliases (`&id001 []` / `*id001`) when several fields share the `List.of()` / `Map.of()` singleton
- JSON: the internal `lastLoadIssues` and `autoReloadActive` properties inherited from `ConfigurablePojo` are no longer written into the configuration file

### Removed


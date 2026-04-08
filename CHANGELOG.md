## Changelog

## [4.0.4]

### Added
- Module-path integration tests with JPMS ServiceLoader discovery validation

### Changed
- Upgraded Jackson from 2.15.2 to 2.18.5 (LTS) for better JPMS compatibility

### Fixed
- `module-info.class` is now correctly injected into shaded json/yaml/toml JARs via moditect,
  fixing JPMS ServiceLoader discovery on the module path (#11)
- Unrelocated multi-release class entries from bundled dependencies (Jackson, SnakeYAML) are now
  stripped to prevent split-package conflicts on the module path
- `org.checkerframework` (transitive dependency of tomlj) is now relocated to prevent
  split-package conflicts

### Removed

## [4.0.3]

### Added

### Changed

### Fixed

### Removed

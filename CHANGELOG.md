# Changelog

## Current

### Changes

- Added normal equipment drop rolls for non-player kills according to `loot_drop_mode`
- Added Brazilian Portuguese gamerule translations
- Added the namespaced snake-case gamerule translation keys used by the gamerule screen
- Updated the mod author and project contact links
- Added support for Minecraft 1.20 through 1.20.6
- Added the NeoForge port, built against NeoForge 20.6.139 for Minecraft 1.20.2 through 1.20.6
- Added the 1.20-compatible XP drop mixin
- Kept the mod bytecode compatible with Java 17
- Added resource-pack metadata compatible with the full declared Minecraft series
- Changed generated artifact names to the `ADL-<version>-NeoForge-<Minecraft series>.jar` pattern
- Added mapping-safe entity, timer, coordinate, and XP access across the 1.20.x series
- Added both legacy `mods.toml` and modern `neoforge.mods.toml` metadata so the same JAR is discovered across NeoForge 1.20.2 through 1.20.6
- Added a Forge-compatible NeoForge 1.20.1 entry point and mapping-safe legacy implementations so the same JAR supports NeoForge 1.20.1 through 1.20.6
- Added the legacy `MixinConfigs` manifest entry so NeoForge 1.20.1 actually applies the loot and experience hooks

## 5.1.0

### Changes

- Added `passiveXpModifier` gamerule to modify the passive XP drop rate when `alwaysDropXp` is enabled.

## 5.0.0

### Changes

- Updated to MC 1.17.1
- Replaced config loading with gamerules
- Internal: Switched to Parchment mappings
- Internal: Configured gametest framework and added tests for all modes

## 4.0.0

### Changes

- Updated to MC 1.15.2

## 3.6.0

### Changes

- Updated to MC 1.14.4

## 3.5.0

### Changes

- Unbundled Fabric API

## 3.4.0

### Changes

- Rebuilt on MC 1.14.1
- Switched to modular Fabric API

## 3.3.1

### Changes

- Rebuilt on MC 1.14

## 3.3.0

### Changes

- Updated to latest prerelease and changed version scheme.

## 3.2.0

### Changes

- Updated to latest loader
- Included fabric api as jar-in-jar
- Updated icon again because it was ugly

## 3.1.0

### Changes

- Updated to latest snapshot
- Added datapack config
- Updated ModMenu icon

## 3.0.0

### Changes

- Rewrote for MC 1.14 with a much more robust way of making the tweaks thanks to mixins!

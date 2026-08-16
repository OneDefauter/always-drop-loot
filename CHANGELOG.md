# Changelog

## Current

### Changes

- Added normal equipment drop rolls for non-player kills according to `loot_drop_mode`
- Added Brazilian Portuguese gamerule translations
- Added the namespaced snake-case gamerule translation keys used by the gamerule screen
- Updated the mod author and project contact links
- Added support for Minecraft 1.20 through 1.20.6
- Added the Forge port, built against Forge 50.2.10 for the 1.20.x series
- Added the 1.20-compatible XP drop mixin
- Kept the mod bytecode compatible with Java 17
- Fixed loading on Minecraft 1.20 with Forge 46
- Fixed reading enum gamerules on legacy Forge versions
- Added compatibility across the 1.20.4/1.20.5 mapping transition to keep one JAR for the full 1.20.x series
- Added resource-pack metadata compatible with the full declared Minecraft series
- Changed generated artifact names to the `ADL-<version>-Forge-<Minecraft series>.jar` pattern
- Fixed zombie-death crashes on Forge 46 by resolving legacy entity, timer, coordinate, and XP method names
- Disabled the ForgeGradle build cache to prevent incomplete merged-source-set JARs after clean builds

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

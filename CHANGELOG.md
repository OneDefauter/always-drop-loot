# Changelog

## Current

### Changes

- Fixed a mob-death crash on Minecraft 1.21.9 and newer caused by an incompatible world-accessor descriptor
- Restored the vanilla 8.5% equipment roll when affected 1.21.x versions initialize an equipped slot with a zero drop chance
- Added normal equipment drop rolls for non-player kills according to `loot_drop_mode`
- Added Brazilian Portuguese gamerule translations
- Added the namespaced snake-case gamerule translation keys used by the gamerule screen
- Updated the mod author and project contact links

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

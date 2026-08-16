# Always Drop Loot

Unofficial continuation of **Always Drop Loot** for Fabric, Forge, and NeoForge.

In vanilla Minecraft, mobs killed by falls, lava, suffocation, campfires, drowning, entity cramming, and other non-player causes may lose experience or loot that depends on a player kill. This mod makes that behavior configurable so passive farms and automated systems can receive the expected drops.

## Branches

This repository is organized by loader and Minecraft series. Use the branch matching the platform and version you want:

| Loader | Minecraft 1.20 | Minecraft 1.21 | Minecraft 26.1 | Minecraft 26.2 |
| --- | --- | --- | --- | --- |
| Fabric | [Fabric-1.20](https://github.com/OneDefauter/always-drop-loot/tree/Fabric-1.20) | [Fabric-1.21](https://github.com/OneDefauter/always-drop-loot/tree/Fabric-1.21) | [Fabric-26.1](https://github.com/OneDefauter/always-drop-loot/tree/Fabric-26.1) | [Fabric-26.2](https://github.com/OneDefauter/always-drop-loot/tree/Fabric-26.2) |
| Forge | [Forge-1.20](https://github.com/OneDefauter/always-drop-loot/tree/Forge-1.20) | [Forge-1.21](https://github.com/OneDefauter/always-drop-loot/tree/Forge-1.21) | [Forge-26.1](https://github.com/OneDefauter/always-drop-loot/tree/Forge-26.1) | [Forge-26.2](https://github.com/OneDefauter/always-drop-loot/tree/Forge-26.2) |
| NeoForge | [NeoForge-1.20](https://github.com/OneDefauter/always-drop-loot/tree/NeoForge-1.20) | [NeoForge-1.21](https://github.com/OneDefauter/always-drop-loot/tree/NeoForge-1.21) | [NeoForge-26.1](https://github.com/OneDefauter/always-drop-loot/tree/NeoForge-26.1) | [NeoForge-26.2](https://github.com/OneDefauter/always-drop-loot/tree/NeoForge-26.2) |

Each branch contains one self-contained Gradle project and its release workflow.

The Fabric, Forge, and NeoForge branches for the same Minecraft series publish to one shared GitHub release. The release uses the tag `v<mod version>-mc<Minecraft series>`, and its Assets section contains one runtime JAR for each available loader.

## Version compatibility

Each supported loader/series combination is distributed as **one runtime JAR**. For example, the Forge 1.21 JAR declares compatibility with the whole `1.21.x` series; a separate JAR is not required for every patch release.

| Minecraft series | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.16.x | Not available | Not available | Not available |
| 1.17.x | Not available | Not available | Not available |
| 1.18.x | Not available | Not available | Not available |
| 1.19.x | Not available | Not available | Not available |
| 1.20.x | `1.20`–`1.20.6` | `1.20`–`1.20.6` | `1.20.1`–`1.20.6` |
| 1.21.x | `1.21`–`1.21.11` | `1.21`–`1.21.11` | `1.21`–`1.21.11` |
| 26.1.x | `>=26.1 <26.2` | `>=26.1 <26.2` | `>=26.1 <26.2` |
| 26.2.x | `>=26.2 <26.3` | `>=26.2 <26.3` | `>=26.2 <26.3` |

`Not available` means that this repository does not provide a mod build for that series. NeoForge support in the 1.20 line starts at 1.20.1.

The ranges above are the compatibility ranges declared by each artifact. Each project uses one representative Minecraft version per series, listed below.

## Project matrix

| Loader | Series | Build target | Loader/API target | Java bytecode / target runtime |
| --- | --- | --- | --- | --- |
| Fabric | 1.20 | 1.20.6 | Loader 0.16.10 / API 0.100.8 | 17 / version-dependent |
| Fabric | 1.21 | 1.21 | Loader 0.15.11 / API 0.100.4 | 21 / 21 |
| Fabric | 26.1 | 26.1 | Loader 0.18.4 / API 0.144.0 | 22 / 25 |
| Fabric | 26.2 | 26.2 | Loader 0.19.3 / API 0.154.2 | 25 / 25 |
| Forge | 1.20 | 1.20.6 | Forge 50.2.10 | 17 / version-dependent |
| Forge | 1.21 | 1.21 | Forge 51.0.33 | 21 |
| Forge | 26.1 | 26.1 | Forge 62.0.2 | 25 |
| Forge | 26.2 | 26.2 | Forge 65.1.1 | 25 |
| NeoForge | 1.20 | 1.20.6 | NeoForge 20.6.139 | 17 / version-dependent |
| NeoForge | 1.21 | 1.21 | NeoForge 21.0.167 | 21 |
| NeoForge | 26.1 | 26.1.2 | NeoForge 26.1.2.95 | 25 |
| NeoForge | 26.2 | 26.2 | NeoForge 26.2.0.59 | 25 |

Source projects are under [`Fabric`](Fabric), [`Forge`](Forge), and [`NeoForge`](NeoForge). The current mod version is `5.6.0`.

## Installation

1. Install the loader matching the Minecraft version.
2. For Fabric, also install Fabric API. Forge and NeoForge builds have no additional mod dependency.
3. Copy the single non-`sources` JAR for the desired series to the `mods` directory.
4. Start the game or dedicated server.

## Configuration

Configuration is stored per world through gamerules:

```mcfunction
/gamerule always-drop-loot:always_drop_xp true
/gamerule always-drop-loot:passive_xp_modifier 1.0
/gamerule always-drop-loot:loot_drop_mode ALWAYS_AS_PLAYER
```

`always_drop_xp` enables experience from non-player kills. `passive_xp_modifier` controls its multiplier. `loot_drop_mode` accepts `ALWAYS_AS_PLAYER`, `NEVER_AS_PLAYER`, `VANILLA`, or `VANILLA_INVERSE`.

## Building

Open the directory for the desired loader and series, then run:

```powershell
.\gradlew.bat build
```

The distributable file is generated in `build/libs`. Use the JAR without the `-sources` suffix. Forge and NeoForge artifacts follow `ADL-<mod version>-<loader>-<Minecraft series>.jar`; Fabric keeps its already-published artifact names.

## Credits and license

Full credit for the original concept and implementation goes to [sargunv](https://modrinth.com/user/sargunv), author of the [original mod](https://modrinth.com/mod/always-drop-loot). This continuation remains licensed under Apache-2.0.

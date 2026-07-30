# Always Drop Loot

An unofficial Fabric update of **Always Drop Loot** for Minecraft `26.1`.

In vanilla Minecraft, many mobs only drop experience and some loot-table results when they are considered to have been killed by a player. That means falls, lava, suffocation, campfires, drowning, entity cramming, and other environmental deaths often reduce or remove rewards.

This mod changes that behavior so passive farms and automation setups can still receive the drops you would normally expect from a player kill.

## What the mod does

- Makes mobs able to drop XP even when they die without a direct player kill.
- Lets you control how much XP non-player kills should give.
- Lets you control how the `killed_by_player` loot condition behaves.
- Lets naturally equipped armor, weapons, and tools roll their normal drop chance on non-player kills.
- Works in singleplayer and on Fabric servers.
- Stores configuration per world through gamerules instead of a config screen.

In practice, this means farms based on fall damage, lava blades, environmental traps, or other automatic kill methods can still drop XP and loot depending on the rules you choose.

## Requirements

- Minecraft Java Edition `26.1`
- Fabric Loader
- Fabric API
- Java 25

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Install Fabric API for the same version.
3. Put the mod jar in your `mods` folder.
4. Start the game or server.

This mod is server-side in practice, so it is useful both in singleplayer and on dedicated servers.

## Configuration

The mod is configured with gamerules. Because they are world-specific, each save or server can have different behavior.

If cheats are disabled in a singleplayer world, you can temporarily enable access to gamerules by opening the world to LAN with cheats enabled.

### `always-drop-loot:always_drop_xp`

Controls whether mobs may drop XP when they die without being killed by a player.

Default:

```mcfunction
/gamerule always-drop-loot:always_drop_xp true
```

Behavior:

- `true`: mobs can still drop XP when death happens by environment or automation.
- `false`: vanilla-style XP behavior.

### `always-drop-loot:passive_xp_modifier`

Controls the XP multiplier for mobs that die **without** a player kill.

Default:

```mcfunction
/gamerule always-drop-loot:passive_xp_modifier 1.0
```

Behavior:

- `1.0`: non-player kills drop the mob's normal XP amount.
- `0.5`: non-player kills drop half XP.
- `0.0`: non-player kills drop no XP.
- `2.0`: non-player kills drop double XP.

This value only affects deaths that were not credited to a player. It does not change normal player-kill XP.

### `always-drop-loot:loot_drop_mode`

Controls how loot tables and equipped-item drops interpret whether the mob was killed by a player.

Default:

```mcfunction
/gamerule always-drop-loot:loot_drop_mode ALWAYS_AS_PLAYER
```

Available values:

- `ALWAYS_AS_PLAYER`: always treat the mob as if a player killed it.
- `NEVER_AS_PLAYER`: never treat the mob as a player kill.
- `VANILLA`: use normal vanilla behavior.
- `VANILLA_INVERSE`: invert vanilla behavior.

This rule affects loot-table drops that depend on `killed_by_player` and the vanilla drop chance for armor, weapons, and tools equipped by mobs.

## Example setups

Always drop normal XP and loot from automated farms:

```mcfunction
/gamerule always-drop-loot:always_drop_xp true
/gamerule always-drop-loot:passive_xp_modifier 1.0
/gamerule always-drop-loot:loot_drop_mode ALWAYS_AS_PLAYER
```

Allow loot from farms, but reduce XP from automated kills:

```mcfunction
/gamerule always-drop-loot:always_drop_xp true
/gamerule always-drop-loot:passive_xp_modifier 0.5
/gamerule always-drop-loot:loot_drop_mode ALWAYS_AS_PLAYER
```

Keep vanilla loot logic, but still allow some XP from passive farms:

```mcfunction
/gamerule always-drop-loot:always_drop_xp true
/gamerule always-drop-loot:passive_xp_modifier 0.5
/gamerule always-drop-loot:loot_drop_mode VANILLA
```

## Compatibility

This fork was updated specifically for Minecraft `26.1`.

This port includes the required `26.1` build migration:

- unobfuscated Fabric Loom (`net.fabricmc.fabric-loom`),
- Java 25,
- and the current Fabric Loader / Fabric API line for `26.1`.

## Credits

Full credit for the original mod concept, implementation, and open-source release goes to **sargunv**.

- Original author profile: <https://modrinth.com/user/sargunv>
- Original mod page: <https://modrinth.com/mod/always-drop-loot>

This updated fork exists to keep the mod usable on newer Fabric `26.1` versions while preserving the original idea and behavior as closely as possible.

## License

This fork continues to respect the original project's `Apache-2.0` license.

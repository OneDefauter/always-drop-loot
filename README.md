# Always Drop Loot — NeoForge 1.20

NeoForge port for Minecraft `1.20.1` through `1.20.6`, built against Minecraft `1.20.6` and NeoForge `20.6.139`. One runtime JAR covers the whole declared series.

The artifact includes both NeoForge metadata formats and loader entry points required across this range: the Forge-compatible NeoForge 1.20.1 bootstrap, legacy `META-INF/mods.toml` for early releases, and `META-INF/neoforge.mods.toml` for later releases.

Build with Java 21 using `.\gradlew.bat build`; the artifact is written to `build/libs`. See the [main README](../../README.md) for installation, configuration, and the full compatibility matrix.

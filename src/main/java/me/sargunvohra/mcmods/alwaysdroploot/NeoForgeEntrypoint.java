package me.sargunvohra.mcmods.alwaysdroploot;

import net.neoforged.fml.common.Mod;

@Mod(AlwaysDropLoot.MOD_ID)
public final class NeoForgeEntrypoint {
	public NeoForgeEntrypoint() {
		AlwaysDropLoot.initialize();
	}
}

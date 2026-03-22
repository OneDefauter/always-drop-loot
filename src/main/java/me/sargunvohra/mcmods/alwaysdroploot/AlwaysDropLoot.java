package me.sargunvohra.mcmods.alwaysdroploot;

import net.fabricmc.api.ModInitializer;

public class AlwaysDropLoot implements ModInitializer {
	public static final Object ALWAYS_DROP_XP = GameRuleCompat.registerBooleanRule("always_drop_xp", "always_drop_xp", true);

	public static final Object PASSIVE_XP_MODIFIER = GameRuleCompat.registerDoubleRule("passive_xp_modifier", "passive_xp_modifier", 1.0);

	public static final Object LOOT_DROP_MODE = GameRuleCompat.registerEnumRule(
		"loot_drop_mode",
		"loot_drop_mode",
		LootDropMode.ALWAYS_AS_PLAYER
	);

	@Override
	public void onInitialize() {
	}
}

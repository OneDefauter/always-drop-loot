package me.sargunvohra.mcmods.alwaysdroploot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AlwaysDropLoot.MOD_ID)
public final class AlwaysDropLoot {
	public static final String MOD_ID = "always_drop_loot";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Object ALWAYS_DROP_XP = GameRuleCompat.registerBooleanRule("always_drop_xp", "always_drop_xp", true);

	public static final Object PASSIVE_XP_MODIFIER = GameRuleCompat.registerDoubleRule("passive_xp_modifier", "passive_xp_modifier", 1.0);

	public static final Object LOOT_DROP_MODE = GameRuleCompat.registerEnumRule(
		"loot_drop_mode",
		"loot_drop_mode",
		LootDropMode.ALWAYS_AS_PLAYER
	);

	public AlwaysDropLoot(IEventBus modEventBus) {
		GameRuleCompat.bindModernRules(modEventBus);
		LOGGER.info("Always Drop Loot initialized");
	}
}

package me.sargunvohra.mcmods.alwaysdroploot;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AlwaysDropLoot.MOD_ID)
public final class AlwaysDropLoot {
	public static final String MOD_ID = "always_drop_loot";
	private static final String GAME_RULE_NAMESPACE = "always-drop-loot";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final DeferredRegister<GameRule<?>> GAME_RULES =
		DeferredRegister.create(Registries.GAME_RULE, GAME_RULE_NAMESPACE);

	public static final RegistryObject<GameRule<?>> ALWAYS_DROP_XP = GAME_RULES.register(
		"always_drop_xp",
		() -> (GameRule<?>) GameRuleCompat.createModernBooleanRule(true)
	);

	public static final RegistryObject<GameRule<?>> PASSIVE_XP_MODIFIER = GAME_RULES.register(
		"passive_xp_modifier",
		() -> (GameRule<?>) GameRuleCompat.createModernDoubleRule(1.0)
	);

	public static final RegistryObject<GameRule<?>> LOOT_DROP_MODE = GAME_RULES.register(
		"loot_drop_mode",
		AlwaysDropLoot::createLootDropModeRule
	);

	public AlwaysDropLoot(FMLJavaModLoadingContext context) {
		GAME_RULES.register(context.getModBusGroup());
		LOGGER.info("Always Drop Loot registration scheduled");
	}

	private static GameRule<?> createLootDropModeRule() {
		GameRule<?> rule = (GameRule<?>) GameRuleCompat.createModernEnumRule(LootDropMode.ALWAYS_AS_PLAYER);
		LOGGER.info("Always Drop Loot initialized");
		return rule;
	}
}

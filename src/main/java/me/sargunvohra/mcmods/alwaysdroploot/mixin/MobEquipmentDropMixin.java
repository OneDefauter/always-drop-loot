package me.sargunvohra.mcmods.alwaysdroploot.mixin;

import me.sargunvohra.mcmods.alwaysdroploot.AlwaysDropLoot;
import me.sargunvohra.mcmods.alwaysdroploot.GameRuleCompat;
import me.sargunvohra.mcmods.alwaysdroploot.LootDropMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mob.class)
public class MobEquipmentDropMixin {
	private static final float NATURAL_EQUIPMENT_DROP_CHANCE = 0.085F;

	@ModifyVariable(method = "dropCustomDeathLoot", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private boolean lootDropMode(
		boolean vanillaKilledByPlayer,
		ServerLevel serverLevel,
		DamageSource damageSource,
		boolean originalKilledByPlayer
	) {
		return treatsAsPlayerKill(serverLevel, vanillaKilledByPlayer);
	}

	@ModifyVariable(
		method = "dropCustomDeathLoot",
		at = @At(value = "STORE", ordinal = 0),
		ordinal = 0
	)
	private float restoreNaturalEquipmentDropChance(
		float vanillaDropChance,
		ServerLevel serverLevel,
		DamageSource damageSource,
		boolean effectiveKilledByPlayer
	) {
		if (vanillaDropChance != 0.0F || !effectiveKilledByPlayer) {
			return vanillaDropChance;
		}

		return NATURAL_EQUIPMENT_DROP_CHANCE;
	}

	private static boolean treatsAsPlayerKill(ServerLevel serverLevel, boolean vanillaKilledByPlayer) {
		return switch (GameRuleCompat.getEnumRule(serverLevel, AlwaysDropLoot.LOOT_DROP_MODE, LootDropMode.class)) {
			case VANILLA -> vanillaKilledByPlayer;
			case VANILLA_INVERSE -> !vanillaKilledByPlayer;
			case ALWAYS_AS_PLAYER -> true;
			case NEVER_AS_PLAYER -> false;
		};
	}
}

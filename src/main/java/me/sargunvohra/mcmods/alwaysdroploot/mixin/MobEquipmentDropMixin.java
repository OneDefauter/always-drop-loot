package me.sargunvohra.mcmods.alwaysdroploot.mixin;

import me.sargunvohra.mcmods.alwaysdroploot.AlwaysDropLoot;
import me.sargunvohra.mcmods.alwaysdroploot.GameRuleCompat;
import me.sargunvohra.mcmods.alwaysdroploot.LootDropMode;
import me.sargunvohra.mcmods.alwaysdroploot.ServerLevelResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mob.class)
public class MobEquipmentDropMixin {
	@ModifyVariable(method = { "dropCustomDeathLoot", "m_7472_" }, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
	private boolean lootDropMode(boolean vanillaKilledByPlayer) {
		ServerLevel serverLevel = ServerLevelResolver.getServerLevel((Mob) (Object) this);
		if (serverLevel == null) {
			return vanillaKilledByPlayer;
		}

		return switch (GameRuleCompat.getEnumRule(serverLevel, AlwaysDropLoot.LOOT_DROP_MODE, LootDropMode.class)) {
			case VANILLA -> vanillaKilledByPlayer;
			case VANILLA_INVERSE -> !vanillaKilledByPlayer;
			case ALWAYS_AS_PLAYER -> true;
			case NEVER_AS_PLAYER -> false;
		};
	}
}

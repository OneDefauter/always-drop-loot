package me.sargunvohra.mcmods.alwaysdroploot.mixin;

import me.sargunvohra.mcmods.alwaysdroploot.AlwaysDropLoot;
import me.sargunvohra.mcmods.alwaysdroploot.GameRuleCompat;
import me.sargunvohra.mcmods.alwaysdroploot.LootDropMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootItemKilledByPlayerCondition.class)
public class KilledByPlayerLootConditionMixin {
	@Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At("HEAD"), cancellable = true)
	private void lootDropMode(LootContext lootContext, CallbackInfoReturnable<Boolean> cir) {
		ServerLevel serverLevel = lootContext.getLevel();
		if (serverLevel == null) {
			return;
		}

		boolean vanillaKilledByPlayer = lootContext.hasParameter(LootContextParams.LAST_DAMAGE_PLAYER);
		boolean result = switch (GameRuleCompat.getEnumRule(serverLevel, AlwaysDropLoot.LOOT_DROP_MODE, LootDropMode.class)) {
			case VANILLA -> vanillaKilledByPlayer;
			case VANILLA_INVERSE -> !vanillaKilledByPlayer;
			case ALWAYS_AS_PLAYER -> true;
			case NEVER_AS_PLAYER -> false;
		};

		cir.setReturnValue(result);
	}
}

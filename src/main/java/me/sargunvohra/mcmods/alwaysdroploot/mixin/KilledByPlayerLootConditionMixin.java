package me.sargunvohra.mcmods.alwaysdroploot.mixin;

import me.sargunvohra.mcmods.alwaysdroploot.AlwaysDropLoot;
import me.sargunvohra.mcmods.alwaysdroploot.GameRuleCompat;
import me.sargunvohra.mcmods.alwaysdroploot.LootDropMode;
import me.sargunvohra.mcmods.alwaysdroploot.ServerLevelResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootItemKilledByPlayerCondition.class)
public class KilledByPlayerLootConditionMixin {
	@Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At("RETURN"), cancellable = true, remap = false)
	private void lootDropMode(LootContext lootContext, CallbackInfoReturnable<Boolean> cir) {
		ServerLevel serverLevel = ServerLevelResolver.getServerLevel(lootContext);
		if (serverLevel == null) {
			return;
		}

		switch (GameRuleCompat.getEnumRule(serverLevel, AlwaysDropLoot.LOOT_DROP_MODE, LootDropMode.class)) {
			case VANILLA:
				break;
			case VANILLA_INVERSE:
				cir.setReturnValue(!cir.getReturnValue());
				break;
			case ALWAYS_AS_PLAYER:
				cir.setReturnValue(true);
				break;
			case NEVER_AS_PLAYER:
				cir.setReturnValue(false);
				break;
		}
	}
}

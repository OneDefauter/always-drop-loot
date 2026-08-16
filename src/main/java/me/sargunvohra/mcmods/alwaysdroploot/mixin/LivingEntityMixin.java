package me.sargunvohra.mcmods.alwaysdroploot.mixin;

import java.lang.reflect.Field;

import me.sargunvohra.mcmods.alwaysdroploot.AlwaysDropLoot;
import me.sargunvohra.mcmods.alwaysdroploot.EntityCompat;
import me.sargunvohra.mcmods.alwaysdroploot.GameRuleCompat;
import me.sargunvohra.mcmods.alwaysdroploot.ServerLevelResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	private static final String[] PLAYER_HIT_TIMER_FIELD_NAMES = {
		"lastHurtByPlayerTime",
		"lastHurtByPlayerMemoryTime",
		"playerHitTimer",
		"f_20889_",
		"field_6238"
	};

	private static volatile Field playerHitTimerField;

	@Shadow(aliases = { "m_6124_" }, remap = false)
	protected abstract boolean isAlwaysExperienceDropper();

	@Shadow(aliases = { "m_213860_" }, remap = false)
	protected abstract int getExperienceReward();

	@Inject(method = { "dropAllDeathLoot", "m_6668_" }, at = @At("TAIL"), remap = false)
	private void alwaysDropXpFallback(DamageSource damageSource, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		ServerLevel serverLevel = ServerLevelResolver.getServerLevel(entity);
		if (serverLevel == null) {
			return;
		}

		if (this.isAlwaysExperienceDropper() || getPlayerHitTimer(entity) > 0) {
			return;
		}

		if (!GameRuleCompat.getBooleanRule(serverLevel, AlwaysDropLoot.ALWAYS_DROP_XP)) {
			return;
		}

		int originalXpAmount = this.getExperienceReward();
		double modifier = GameRuleCompat.getDoubleRule(serverLevel, AlwaysDropLoot.PASSIVE_XP_MODIFIER);
		int adjustedXpAmount = (int) Math.round(originalXpAmount * modifier);

		if (adjustedXpAmount > 0) {
			EntityCompat.awardExperience(serverLevel, entity, adjustedXpAmount);
		}
	}

	private static int getPlayerHitTimer(LivingEntity entity) {
		Field field = playerHitTimerField;
		if (field == null) {
			field = resolvePlayerHitTimerField(entity.getClass());
			playerHitTimerField = field;
		}

		try {
			return field.getInt(entity);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to read player hit timer", exception);
		}
	}

	private static Field resolvePlayerHitTimerField(Class<?> type) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (String candidate : PLAYER_HIT_TIMER_FIELD_NAMES) {
				try {
					Field field = current.getDeclaredField(candidate);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
		}

		throw new IllegalStateException("Could not locate LivingEntity player hit timer field");
	}
}

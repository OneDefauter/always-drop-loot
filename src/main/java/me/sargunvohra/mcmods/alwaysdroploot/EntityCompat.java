package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class EntityCompat {
	private static volatile Method getXMethod;
	private static volatile Method getYMethod;
	private static volatile Method getZMethod;
	private static volatile Method experienceOrbAwardMethod;

	private EntityCompat() {
	}

	public static void awardExperience(ServerLevel serverLevel, LivingEntity entity, int amount) {
		Method awardMethod = experienceOrbAwardMethod;
		if (awardMethod == null) {
			awardMethod = resolveMethod(ExperienceOrb.class, new Class<?>[] { ServerLevel.class, Vec3.class, int.class }, "award", "m_147082_");
			experienceOrbAwardMethod = awardMethod;
		}

		try {
			awardMethod.invoke(null, serverLevel, new Vec3(
				getCoordinate(entity, Axis.X),
				getCoordinate(entity, Axis.Y),
				getCoordinate(entity, Axis.Z)
			), amount);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to award passive experience", exception);
		}
	}

	private static double getCoordinate(LivingEntity entity, Axis axis) {
		Method method = switch (axis) {
			case X -> getXMethod;
			case Y -> getYMethod;
			case Z -> getZMethod;
		};

		if (method == null) {
			method = switch (axis) {
				case X -> resolveMethod(LivingEntity.class, new Class<?>[0], "getX", "m_20185_");
				case Y -> resolveMethod(LivingEntity.class, new Class<?>[0], "getY", "m_20186_");
				case Z -> resolveMethod(LivingEntity.class, new Class<?>[0], "getZ", "m_20189_");
			};

			switch (axis) {
				case X -> getXMethod = method;
				case Y -> getYMethod = method;
				case Z -> getZMethod = method;
			}
		}

		try {
			return ((Number) method.invoke(entity)).doubleValue();
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to read entity " + axis.name().toLowerCase() + " coordinate", exception);
		}
	}

	private static Method resolveMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (String name : names) {
				try {
					Method method = current.getDeclaredMethod(name, parameterTypes);
					method.setAccessible(true);
					return method;
				} catch (NoSuchMethodException ignored) {
				}
			}
		}

		throw new IllegalStateException("Could not locate compatible method on " + type.getName());
	}

	private enum Axis {
		X,
		Y,
		Z
	}
}

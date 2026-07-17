package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.server.level.ServerLevel;

public final class GameRuleCompat {
	private static final String MOD_ID = "always-drop-loot";
	private static final boolean MODERN_GAME_RULE_API = classExists("net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder");
	private static final Codec<Double> COMPAT_DOUBLE_CODEC = Codec.either(Codec.DOUBLE, Codec.STRING).comapFlatMap(value -> value.map(
		DataResult::success,
		stringValue -> {
			try {
				return DataResult.success(Double.parseDouble(stringValue));
			} catch (NumberFormatException exception) {
				return DataResult.error(() -> "Not a number");
			}
		}
	), doubleValue -> Either.right(String.valueOf(doubleValue)));

	private GameRuleCompat() {
	}

	public static Object registerBooleanRule(String modernPath, String legacyName, boolean defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule("forBoolean", modernPath, defaultValue)
			: registerLegacyRule("createBooleanRule", legacyName, defaultValue);
	}

	public static Object registerDoubleRule(String modernPath, String legacyName, double defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule("forDouble", modernPath, defaultValue)
			: registerLegacyRule("createDoubleRule", legacyName, defaultValue);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String modernPath, String legacyName, E defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule("forEnum", modernPath, defaultValue)
			: registerLegacyRule("createEnumRule", legacyName, defaultValue);
	}

	public static boolean getBooleanRule(ServerLevel level, Object rule) {
		Object value = getRuleValue(level, rule, boolean.class);
		return (Boolean) value;
	}

	public static double getDoubleRule(ServerLevel level, Object rule) {
		return ((Number) getRuleValue(level, rule, double.class)).doubleValue();
	}

	public static <E extends Enum<E>> E getEnumRule(ServerLevel level, Object rule, Class<E> enumClass) {
		return enumClass.cast(getRuleValue(level, rule, enumClass));
	}

	private static Object getRuleValue(ServerLevel level, Object rule, Class<?> expectedType) {
		Object gameRules = getGameRules(level);
		Object rawValue = invoke(findRuleAccessor(gameRules.getClass(), rule.getClass(), expectedType), gameRules, rule);

		if (matchesExpectedType(rawValue.getClass(), expectedType)) {
			return rawValue;
		}

		Object unwrappedValue = unwrapRuleValue(rawValue, expectedType);
		if (unwrappedValue != null) {
			return unwrappedValue;
		}

		throw new IllegalStateException("Could not unwrap gamerule value " + rawValue.getClass().getName());
	}

	private static Object getGameRules(ServerLevel level) {
		Method accessor = findGameRulesAccessor(level.getClass());
		return invoke(accessor, level);
	}

	private static Object registerModernRule(String builderMethodName, String path, Object defaultValue) {
		try {
			Class<?> builderClass = Class.forName("net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder");
			Method builderFactory = findCompatibleMethod(builderClass, builderMethodName, defaultValue);
			Object builder = invoke(builderFactory, null, defaultValue);
			if (defaultValue instanceof Double) {
				builder = applyCompatibleDoubleCodec(builder);
			}
			Method categoryMethod = findModernCategoryMethod(builder.getClass());
			Object dropsCategory = resolveDropsCategory(categoryMethod.getParameterTypes()[0]);

			invoke(categoryMethod, builder, dropsCategory);

			Method buildAndRegisterMethod = findMethod(builder.getClass(), "buildAndRegister", 1);
			Class<?> identifierClass = buildAndRegisterMethod.getParameterTypes()[0];
			Method identifierFactory = findStaticFactory(identifierClass);
			Object identifier = invoke(identifierFactory, null, MOD_ID, path);
			return invoke(buildAndRegisterMethod, builder, identifier);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to register modern gamerule " + path, exception);
		}
	}

	private static Object applyCompatibleDoubleCodec(Object builder) {
		Method codecMethod = findCompatibleMethod(builder.getClass(), "codec", COMPAT_DOUBLE_CODEC);
		if (codecMethod == null) {
			throw new IllegalStateException("Could not find codec method on " + builder.getClass().getName());
		}

		return invoke(codecMethod, builder, COMPAT_DOUBLE_CODEC);
	}

	private static Object registerLegacyRule(String factoryMethodName, String ruleName, Object defaultValue) {
		try {
			Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry");
			Class<?> factoryClass = Class.forName("net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory");

			Method factoryMethod = findCompatibleMethod(factoryClass, factoryMethodName, defaultValue);
			Object ruleType = invoke(factoryMethod, null, defaultValue);

			Method registerMethod = findLegacyRegisterMethod(registryClass, ruleType.getClass());
			Object dropsCategory = resolveDropsCategory(registerMethod.getParameterTypes()[1]);
			return invoke(registerMethod, null, MOD_ID + ":" + ruleName, dropsCategory, ruleType);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to register legacy gamerule " + ruleName, exception);
		}
	}

	private static Method findRuleAccessor(Class<?> type, Class<?> argumentType, Class<?> expectedType) {
		Method bestMethod = null;
		int bestScore = Integer.MIN_VALUE;

		for (Method method : type.getMethods()) {
			int score = scoreRuleAccessor(method, argumentType, expectedType);
			if (score > bestScore) {
				bestMethod = method;
				bestScore = score;
			}
		}

		if (bestMethod != null) {
			return bestMethod;
		}

		throw new IllegalStateException("Could not find gamerule accessor on " + type.getName());
	}

	private static int scoreRuleAccessor(Method method, Class<?> argumentType, Class<?> expectedType) {
		if (method.getParameterCount() != 1) {
			return Integer.MIN_VALUE;
		}

		Class<?> parameterType = method.getParameterTypes()[0];
		Class<?> returnType = method.getReturnType();
		if (!parameterType.isAssignableFrom(argumentType) || returnType == String.class || returnType == Void.TYPE) {
			return Integer.MIN_VALUE;
		}

		int score = 0;
		String methodName = method.getName();

		if (methodName.equals("getRuleValue") || methodName.equals("method_76185")) {
			score += 400;
		} else if (methodName.equals("getRule") || methodName.equals("method_20746")) {
			score += 300;
		} else if ((methodName.equals("getBoolean") || methodName.equals("method_8355")) && expectedType == boolean.class) {
			score += 250;
		} else if ((methodName.equals("getInt") || methodName.equals("method_8356")) && expectedType == int.class) {
			score += 250;
		}

		if (matchesExpectedType(returnType, expectedType)) {
			score += 120;
		} else if (!returnType.isPrimitive()) {
			score += 60;
		}

		if (returnType.getName().startsWith("net.fabricmc.fabric.")) {
			score -= 200;
		}

		if (parameterType == argumentType) {
			score += 20;
		} else {
			score += 10;
		}

		return score;
	}

	private static Method findGameRulesAccessor(Class<?> type) {
		Method bestMethod = null;
		int bestScore = -1;

		for (Method method : type.getMethods()) {
			int score = scoreGameRulesAccessor(method);
			if (score > bestScore) {
				bestMethod = method;
				bestScore = score;
			}
		}

		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Method method : current.getDeclaredMethods()) {
				int score = scoreGameRulesAccessor(method);
				if (score > bestScore) {
					method.setAccessible(true);
					bestMethod = method;
					bestScore = score;
				}
			}
		}

		if (bestMethod == null) {
			throw new IllegalStateException("Could not find GameRules accessor on " + type.getName());
		}

		return bestMethod;
	}

	private static int scoreGameRulesAccessor(Method method) {
		if (method.getParameterCount() != 0 || (method.getModifiers() & Modifier.STATIC) != 0) {
			return -1;
		}

		String returnTypeName = method.getReturnType().getName();
		if (!returnTypeName.equals("net.minecraft.world.level.gamerules.GameRules")
			&& !returnTypeName.equals("net.minecraft.world.level.GameRules")
			&& !returnTypeName.equals("net.minecraft.world.GameRules")
			&& !returnTypeName.equals("net.minecraft.class_1928")) {
			return -1;
		}

		if (method.getName().equals("getGameRules")) {
			return 100;
		}

		return 50;
	}

	private static Object unwrapRuleValue(Object rawValue, Class<?> expectedType) {
		Method namedGetter = null;

		for (Method method : rawValue.getClass().getMethods()) {
			if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
				continue;
			}

			if (matchesExpectedType(method.getReturnType(), expectedType)) {
				return invoke(method, rawValue);
			}

			if (method.getName().equals("get")) {
				namedGetter = method;
			}
		}

		if (namedGetter != null) {
			Object value = invoke(namedGetter, rawValue);
			if (value != null && matchesExpectedType(value.getClass(), expectedType)) {
				return value;
			}
		}

		for (Method method : rawValue.getClass().getMethods()) {
			if (method.getParameterCount() != 0
				|| method.getReturnType() == Void.TYPE
				|| method.getName().equals("getClass")) {
				continue;
			}

			Object value = invoke(method, rawValue);
			if (value != null && matchesExpectedType(value.getClass(), expectedType)) {
				return value;
			}
		}

		return null;
	}

	private static Method findMethod(Class<?> type, String name, int parameterCount) {
		for (Method method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
				return method;
			}
		}

		for (Method method : type.getDeclaredMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
				method.setAccessible(true);
				return method;
			}
		}

		Class<?> superClass = type.getSuperclass();
		return superClass == null ? null : findMethod(superClass, name, parameterCount);
	}

	private static Method findCompatibleMethod(Class<?> type, String name, Object argument) {
		for (Method method : type.getMethods()) {
			if (!method.getName().equals(name) || method.getParameterCount() != 1) {
				continue;
			}

			if (isCompatible(method.getParameterTypes()[0], argument)) {
				return method;
			}
		}

		return null;
	}

	private static Method findModernCategoryMethod(Class<?> builderType) {
		for (Method method : builderType.getMethods()) {
			if (!method.getName().equals("category") || method.getParameterCount() != 1) {
				continue;
			}

			if (canResolveDropsCategory(method.getParameterTypes()[0])) {
				return method;
			}
		}

		throw new IllegalStateException("Could not find modern category overload");
	}

	private static Method findLegacyRegisterMethod(Class<?> registryClass, Class<?> ruleTypeClass) {
		for (Method method : registryClass.getMethods()) {
			if (!method.getName().equals("register") || method.getParameterCount() != 3) {
				continue;
			}

			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes[0] != String.class || !parameterTypes[2].isAssignableFrom(ruleTypeClass)) {
				continue;
			}

			if (canResolveDropsCategory(parameterTypes[1])) {
				return method;
			}
		}

		throw new IllegalStateException("Could not find legacy gamerule register overload");
	}

	private static boolean canResolveDropsCategory(Class<?> categoryType) {
		return findStaticField(categoryType, "DROPS", "field_24097") != null;
	}

	private static Object resolveDropsCategory(Class<?> categoryType) {
		Field field = findStaticField(categoryType, "DROPS", "field_24097");
		if (field == null) {
			throw new IllegalStateException("Could not resolve DROPS category on " + categoryType.getName());
		}

		try {
			return field.get(null);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to read " + field, exception);
		}
	}

	private static Field findStaticField(Class<?> type, String... candidateNames) {
		for (String candidateName : candidateNames) {
			Class<?> current = type;

			while (current != null) {
				try {
					Field field = current.getDeclaredField(candidateName);
					if ((field.getModifiers() & Modifier.STATIC) != 0) {
						field.setAccessible(true);
						return field;
					}
				} catch (NoSuchFieldException ignored) {
				}

				current = current.getSuperclass();
			}
		}

		return null;
	}

	private static Method findStaticFactory(Class<?> type) {
		for (Method method : type.getMethods()) {
			if ((method.getModifiers() & Modifier.STATIC) == 0) {
				continue;
			}

			if (method.getParameterCount() == 2
				&& method.getParameterTypes()[0] == String.class
				&& method.getParameterTypes()[1] == String.class
				&& type.isAssignableFrom(method.getReturnType())) {
				return method;
			}
		}

		throw new IllegalStateException("Could not find identifier factory on " + type.getName());
	}

	private static boolean isCompatible(Class<?> parameterType, Object argument) {
		if (argument == null) {
			return !parameterType.isPrimitive();
		}

		Class<?> argumentType = argument.getClass();
		if (parameterType.isAssignableFrom(argumentType)) {
			return true;
		}

		if (!parameterType.isPrimitive()) {
			return false;
		}

		return (parameterType == boolean.class && argumentType == Boolean.class)
			|| (parameterType == int.class && argumentType == Integer.class)
			|| (parameterType == double.class && argumentType == Double.class);
	}

	private static boolean matchesExpectedType(Class<?> actualType, Class<?> expectedType) {
		if (expectedType == boolean.class) {
			return actualType == boolean.class || actualType == Boolean.class;
		}

		if (expectedType == double.class) {
			return actualType == double.class || actualType == Double.class;
		}

		return expectedType.isAssignableFrom(actualType);
	}

	private static Object invoke(Method method, Object instance, Object... args) {
		try {
			return method.invoke(instance, args);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to invoke " + method, exception);
		}
	}

	private static boolean classExists(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}
}

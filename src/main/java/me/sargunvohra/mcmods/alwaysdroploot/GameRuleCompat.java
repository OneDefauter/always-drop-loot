package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;

public final class GameRuleCompat {
	private static final String MOD_ID = "always-drop-loot";
	private static final String LEGACY_HELPER = "me.sargunvohra.mcmods.alwaysdroploot.LegacyGameRules";
	private static final boolean MODERN_GAME_RULE_API = classExists("net.minecraft.world.level.gamerules.GameRule");
	private static final Codec<Double> COMPAT_DOUBLE_CODEC = Codec.either(Codec.DOUBLE, Codec.STRING).comapFlatMap(value -> value.map(
		DataResult::success,
		stringValue -> {
			try {
				return DataResult.success(Double.parseDouble(stringValue));
			} catch (NumberFormatException exception) {
				return DataResult.error(() -> "Not a number: " + stringValue);
			}
		}
	), doubleValue -> Either.left(doubleValue));

	private GameRuleCompat() {
	}

	public static Object registerBooleanRule(String modernPath, String legacyName, boolean defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerBooleanRule", legacyName, defaultValue);
	}

	public static Object registerDoubleRule(String modernPath, String legacyName, double defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerDoubleRule", legacyName, defaultValue);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String modernPath, String legacyName, E defaultValue) {
		return MODERN_GAME_RULE_API
			? registerModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerEnumRule", legacyName, defaultValue);
	}

	public static boolean getBooleanRule(ServerLevel level, Object rule) {
		return (Boolean) getRuleValue(level, rule, boolean.class);
	}

	public static double getDoubleRule(ServerLevel level, Object rule) {
		return ((Number) getRuleValue(level, rule, double.class)).doubleValue();
	}

	public static <E extends Enum<E>> E getEnumRule(ServerLevel level, Object rule, Class<E> enumClass) {
		return enumClass.cast(getRuleValue(level, rule, enumClass));
	}

	private static Object registerModernRule(String path, Object defaultValue) {
		try {
			Class<?> rulesClass = Class.forName("net.minecraft.world.level.gamerules.GameRules");
			Class<?> categoryClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleCategory");
			Object dropsCategory = readStaticField(categoryClass, "DROPS");
			String id = MOD_ID + ":" + path;

			if (defaultValue instanceof Boolean) {
				Method registerBoolean = findStaticMethod(rulesClass, "registerBoolean", 3);
				return invoke(registerBoolean, null, id, dropsCategory, defaultValue);
			}

			Class<?> typeClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleType");
			Object integerTypeHint = readStaticField(typeClass, "INT");
			Method register = findStaticMethod(rulesClass, "register", 9);
			Class<?> visitorCallerType = register.getParameterTypes()[7];
			Object visitorCaller = Proxy.newProxyInstance(
				visitorCallerType.getClassLoader(),
				new Class<?>[] { visitorCallerType },
				(proxy, method, args) -> null
			);

			ArgumentType<?> argumentType;
			Codec<?> codec;
			ToIntFunction<Object> commandResult;

			if (defaultValue instanceof Double) {
				argumentType = DoubleArgumentType.doubleArg();
				codec = COMPAT_DOUBLE_CODEC;
				commandResult = value -> Double.compare((Double) value, 0.0D);
			} else if (defaultValue instanceof Enum<?> enumValue) {
				argumentType = new EnumArgumentType<>(enumValue.getDeclaringClass());
				codec = createEnumCodec(enumValue.getDeclaringClass());
				commandResult = value -> ((Enum<?>) value).ordinal();
			} else {
				throw new IllegalArgumentException("Unsupported gamerule default type " + defaultValue.getClass().getName());
			}

			return invoke(
				register,
				null,
				id,
				dropsCategory,
				integerTypeHint,
				argumentType,
				codec,
				defaultValue,
				FeatureFlagSet.of(),
				visitorCaller,
				commandResult
			);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to register modern gamerule " + path, exception);
		}
	}

	private static Object invokeLegacyRegistration(String methodName, String name, Object defaultValue) {
		try {
			Class<?> helperClass = Class.forName(LEGACY_HELPER);
			Method method = findCompatibleMethod(helperClass, methodName, name, defaultValue);
			return invoke(method, null, name, defaultValue);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to register legacy gamerule " + name, exception);
		}
	}

	private static Object getRuleValue(ServerLevel level, Object rule, Class<?> expectedType) {
		Object gameRules = getGameRules(level);
		Method accessor = findRuleAccessor(gameRules.getClass(), rule.getClass(), expectedType);
		Object rawValue = invoke(accessor, gameRules, rule);

		if (rawValue == null) {
			throw new IllegalStateException("Gamerule accessor returned null for " + rule);
		}

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
		return invoke(findGameRulesAccessor(level.getClass()), level);
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

		int score = parameterType == argumentType ? 40 : 20;
		String methodName = method.getName();

		if (methodName.equals("get") || methodName.equals("getRuleValue")) {
			score += 500;
		} else if (methodName.equals("getRule")) {
			score += 400;
		} else if (methodName.equals("getBoolean") && expectedType == boolean.class) {
			score += 300;
		} else if (methodName.equals("getInt") && expectedType == int.class) {
			score += 300;
		}

		if (matchesExpectedType(returnType, expectedType)) {
			score += 120;
		} else if (!returnType.isPrimitive()) {
			score += 60;
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
			&& !returnTypeName.equals("net.minecraft.world.level.GameRules")) {
			return -1;
		}

		return method.getName().equals("getGameRules") ? 100 : 50;
	}

	private static Object unwrapRuleValue(Object rawValue, Class<?> expectedType) {
		for (Method method : rawValue.getClass().getMethods()) {
			if (method.getParameterCount() != 0
				|| (!matchesExpectedType(method.getReturnType(), expectedType)
					&& (expectedType.isPrimitive() || !method.getReturnType().isAssignableFrom(expectedType)))) {
				continue;
			}

			Object candidate = invoke(method, rawValue);
			if (candidate != null && matchesExpectedType(candidate.getClass(), expectedType)) {
				return candidate;
			}
		}

		return null;
	}

	private static Method findStaticMethod(Class<?> type, String name, int parameterCount) {
		for (Method method : type.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())
				&& method.getName().equals(name)
				&& method.getParameterCount() == parameterCount) {
				return method;
			}
		}

		throw new IllegalStateException("Could not find " + name + " on " + type.getName());
	}

	private static Method findCompatibleMethod(Class<?> type, String name, Object... arguments) {
		for (Method method : type.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers())
				|| !method.getName().equals(name)
				|| method.getParameterCount() != arguments.length) {
				continue;
			}

			Class<?>[] parameterTypes = method.getParameterTypes();
			boolean compatible = true;
			for (int index = 0; index < arguments.length; index++) {
				if (!isCompatible(parameterTypes[index], arguments[index])) {
					compatible = false;
					break;
				}
			}

			if (compatible) {
				return method;
			}
		}

		throw new IllegalStateException("Could not find compatible " + name + " on " + type.getName());
	}

	private static Object readStaticField(Class<?> type, String name) throws ReflectiveOperationException {
		Field field = type.getField(name);
		return field.get(null);
	}

	private static boolean isCompatible(Class<?> parameterType, Object argument) {
		if (argument == null) {
			return !parameterType.isPrimitive();
		}

		if (parameterType.isAssignableFrom(argument.getClass())) {
			return true;
		}

		return (parameterType == boolean.class && argument instanceof Boolean)
			|| (parameterType == double.class && argument instanceof Double);
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

	private static <E extends Enum<E>> Codec<E> createEnumCodec(Class<E> enumClass) {
		return Codec.STRING.comapFlatMap(value -> {
			try {
				return DataResult.success(Enum.valueOf(enumClass, value));
			} catch (IllegalArgumentException exception) {
				return DataResult.error(() -> value + " is not a valid " + enumClass.getSimpleName());
			}
		}, Enum::name);
	}

	private static final class EnumArgumentType<E extends Enum<E>> implements ArgumentType<E> {
		private final Class<E> enumClass;
		private final DynamicCommandExceptionType invalidValue = new DynamicCommandExceptionType(
			value -> () -> value + " is not a valid " + enumClassName()
		);

		private EnumArgumentType(Class<E> enumClass) {
			this.enumClass = enumClass;
		}

		@Override
		public E parse(StringReader reader) throws CommandSyntaxException {
			int start = reader.getCursor();
			String value = reader.readUnquotedString();
			try {
				return Enum.valueOf(enumClass, value);
			} catch (IllegalArgumentException exception) {
				reader.setCursor(start);
				throw invalidValue.createWithContext(reader, value);
			}
		}

		@Override
		public Collection<String> getExamples() {
			E[] constants = enumClass.getEnumConstants();
			return constants.length == 0 ? List.of() : List.of(constants[0].name());
		}

		private String enumClassName() {
			return enumClass == null ? "enum value" : enumClass.getSimpleName();
		}
	}
}

package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;

public final class GameRuleCompat {
	private static final String MOD_ID = "always-drop-loot";
	private static final String LEGACY_HELPER = "me.sargunvohra.mcmods.alwaysdroploot.LegacyGameRules";
	private static final boolean MODERN_GAME_RULE_API = classExists("net.minecraft.world.level.gamerules.GameRule");
	private static final List<ModernRuleHandle> PENDING_MODERN_RULES = new ArrayList<>();
	private static boolean modernForgeRegistrationInitialized;
	private static final Codec<Boolean> COMPAT_BOOLEAN_CODEC = Codec.either(Codec.BOOL, Codec.STRING).comapFlatMap(value -> value.map(
		DataResult::success,
		stringValue -> {
			if (stringValue.equalsIgnoreCase("true")) {
				return DataResult.success(true);
			}
			if (stringValue.equalsIgnoreCase("false")) {
				return DataResult.success(false);
			}
			return DataResult.error(() -> "Not a boolean: " + stringValue);
		}
	), booleanValue -> Either.left(booleanValue));
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
			? queueModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerBooleanRule", legacyName, defaultValue);
	}

	public static Object registerDoubleRule(String modernPath, String legacyName, double defaultValue) {
		return MODERN_GAME_RULE_API
			? queueModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerDoubleRule", legacyName, defaultValue);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String modernPath, String legacyName, E defaultValue) {
		return MODERN_GAME_RULE_API
			? queueModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerEnumRule", legacyName, defaultValue);
	}

	public static synchronized void initializeForgeRegistration() {
		if (!MODERN_GAME_RULE_API || modernForgeRegistrationInitialized) {
			return;
		}

		try {
			Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
			Object gameRuleRegistryKey = readStaticField(registriesClass, "GAME_RULE");
			Class<?> deferredRegisterClass = Class.forName("net.minecraftforge.registries.DeferredRegister");
			Method create = findCompatibleMethod(deferredRegisterClass, "create", gameRuleRegistryKey, MOD_ID);
			Object deferredRegister = invoke(create, null, gameRuleRegistryKey, MOD_ID);

			for (ModernRuleHandle handle : PENDING_MODERN_RULES) {
				Supplier<Object> supplier = () -> {
					Object rule = createModernRule(handle.path, handle.defaultValue);
					handle.resolve(rule);
					return rule;
				};
				Method registerRule = findCompatibleInstanceMethod(deferredRegisterClass, "register", handle.path, supplier);
				invoke(registerRule, deferredRegister, handle.path, supplier);
			}

			Class<?> contextClass = Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
			Object context = invoke(findStaticMethod(contextClass, "get", 0), null);
			Method busAccessor = findNamedNoArgMethod(contextClass, "getModBusGroup", "getModEventBus");
			Object modBus = invoke(busAccessor, context);
			Method attach = findCompatibleInstanceMethod(deferredRegisterClass, "register", modBus);
			invoke(attach, deferredRegister, modBus);
			modernForgeRegistrationInitialized = true;
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to initialize modern Forge gamerule registration", exception);
		}
	}

	public static boolean getBooleanRule(ServerLevel level, Object rule) {
		return (Boolean) getRuleValue(level, rule, boolean.class);
	}

	public static double getDoubleRule(ServerLevel level, Object rule) {
		return ((Number) getRuleValue(level, rule, double.class)).doubleValue();
	}

	public static <E extends Enum<E>> E getEnumRule(ServerLevel level, Object rule, Class<E> enumClass) {
		if (rule instanceof ModernRuleHandle handle) {
			Object value = getRuleValue(level, rule, Object.class);
			if (value instanceof String stringValue) {
				try {
					return Enum.valueOf(enumClass, stringValue);
				} catch (IllegalArgumentException ignored) {
					return enumClass.cast(handle.defaultValue);
				}
			}
			return enumClass.cast(value);
		}

		return enumClass.cast(getRuleValue(level, rule, enumClass));
	}

	private static Object queueModernRule(String path, Object defaultValue) {
		ModernRuleHandle handle = new ModernRuleHandle(path, defaultValue);
		PENDING_MODERN_RULES.add(handle);
		return handle;
	}

	private static Object createModernRule(String path, Object defaultValue) {
		try {
			Class<?> ruleClass = Class.forName("net.minecraft.world.level.gamerules.GameRule");
			Class<?> categoryClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleCategory");
			Object dropsCategory = readStaticField(categoryClass, "DROPS");
			Class<?> typeClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleType");
			Object typeHint;
			Object ruleDefaultValue = defaultValue;
			ArgumentType<?> argumentType;
			Codec<?> codec;
			ToIntFunction<Object> commandResult;
			boolean booleanRule = defaultValue instanceof Boolean;

			if (defaultValue instanceof Boolean) {
				typeHint = readStaticField(typeClass, "BOOL");
				argumentType = BoolArgumentType.bool();
				codec = COMPAT_BOOLEAN_CODEC;
				commandResult = value -> Boolean.TRUE.equals(value) ? 1 : 0;
			} else if (defaultValue instanceof Double) {
				typeHint = readStaticField(typeClass, "INT");
				argumentType = DoubleArgumentType.doubleArg();
				codec = COMPAT_DOUBLE_CODEC;
				commandResult = value -> Double.compare((Double) value, 0.0D);
			} else if (defaultValue instanceof Enum<?> enumValue) {
				typeHint = readStaticField(typeClass, "INT");
				argumentType = StringArgumentType.word();
				codec = Codec.STRING;
				ruleDefaultValue = enumValue.name();
				commandResult = value -> {
					try {
						return Enum.valueOf(enumValue.getDeclaringClass(), (String) value).ordinal();
					} catch (IllegalArgumentException exception) {
						return enumValue.ordinal();
					}
				};
			} else {
				throw new IllegalArgumentException("Unsupported gamerule default type " + defaultValue.getClass().getName());
			}

			Constructor<?> constructor = findConstructor(ruleClass, 8);
			Class<?> visitorCallerType = constructor.getParameterTypes()[3];
			Object visitorCaller = Proxy.newProxyInstance(
				visitorCallerType.getClassLoader(),
				new Class<?>[] { visitorCallerType },
				(proxy, method, args) -> {
					if (booleanRule && method.getName().equals("call") && args != null && args.length == 2) {
						Method visitBoolean = findCompatibleInstanceMethod(args[0].getClass(), "visitBoolean", args[1]);
						invoke(visitBoolean, args[0], args[1]);
					}
					return null;
				}
			);

			return constructor.newInstance(
				dropsCategory,
				typeHint,
				argumentType,
				visitorCaller,
				codec,
				commandResult,
				ruleDefaultValue,
				FeatureFlagSet.of()
			);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to create modern gamerule " + path, exception);
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
		if (rule instanceof ModernRuleHandle handle) {
			rule = handle.get();
		}

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

	private static Method findCompatibleInstanceMethod(Class<?> type, String name, Object... arguments) {
		for (Method method : type.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())
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

		throw new IllegalStateException("Could not find compatible instance method " + name + " on " + type.getName());
	}

	private static Method findNamedNoArgMethod(Class<?> type, String... names) {
		for (String name : names) {
			for (Method method : type.getMethods()) {
				if (!Modifier.isStatic(method.getModifiers())
					&& method.getName().equals(name)
					&& method.getParameterCount() == 0) {
					return method;
				}
			}
		}

		throw new IllegalStateException("Could not find any requested no-arg method on " + type.getName());
	}

	private static Constructor<?> findConstructor(Class<?> type, int parameterCount) {
		for (Constructor<?> constructor : type.getConstructors()) {
			if (constructor.getParameterCount() == parameterCount) {
				return constructor;
			}
		}

		throw new IllegalStateException("Could not find " + parameterCount + "-argument constructor on " + type.getName());
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

	private static final class ModernRuleHandle {
		private final String path;
		private final Object defaultValue;
		private volatile Object rule;

		private ModernRuleHandle(String path, Object defaultValue) {
			this.path = path;
			this.defaultValue = defaultValue;
		}

		private void resolve(Object rule) {
			this.rule = rule;
		}

		private Object get() {
			Object resolvedRule = rule;
			if (resolvedRule == null) {
				throw new IllegalStateException("Modern gamerule " + path + " has not been registered yet");
			}
			return resolvedRule;
		}
	}

}

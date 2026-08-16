package me.sargunvohra.mcmods.alwaysdroploot;

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
	private static final List<DeferredRule> MODERN_RULES = new ArrayList<>();
	private static boolean modernRulesBound;
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
			? deferModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerBooleanRule", legacyName, defaultValue);
	}

	public static Object registerDoubleRule(String modernPath, String legacyName, double defaultValue) {
		return MODERN_GAME_RULE_API
			? deferModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerDoubleRule", legacyName, defaultValue);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String modernPath, String legacyName, E defaultValue) {
		return MODERN_GAME_RULE_API
			? deferModernRule(modernPath, defaultValue)
			: invokeLegacyRegistration("registerEnumRule", legacyName, defaultValue);
	}

	public static synchronized void bindModernRules(Object modEventBus) {
		if (!MODERN_GAME_RULE_API || modernRulesBound) {
			return;
		}

		try {
			Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
			Object gameRuleRegistryKey = readStaticField(registriesClass, "GAME_RULE");
			Class<?> deferredRegisterClass = Class.forName("net.neoforged.neoforge.registries.DeferredRegister");
			Method create = findCompatibleMethod(deferredRegisterClass, "create", gameRuleRegistryKey, MOD_ID);
			Object deferredRegister = invoke(create, null, gameRuleRegistryKey, MOD_ID);

			for (DeferredRule rule : MODERN_RULES) {
				Supplier<Object> supplier = () -> createUnregisteredModernRule(rule.defaultValue);
				Method register = findCompatibleInstanceMethod(deferredRegister.getClass(), "register", rule.path, supplier);
				rule.holder = invoke(register, deferredRegister, rule.path, supplier);
			}

			Method bind = findCompatibleInstanceMethod(deferredRegister.getClass(), "register", modEventBus);
			invoke(bind, deferredRegister, modEventBus);
			modernRulesBound = true;
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to bind modern gamerules", exception);
		}
	}

	public static Object createModernBooleanRule(boolean defaultValue) {
		return createUnregisteredModernRule(defaultValue);
	}

	public static Object createModernDoubleRule(double defaultValue) {
		return createUnregisteredModernRule(defaultValue);
	}

	public static <E extends Enum<E>> Object createModernEnumRule(E defaultValue) {
		return createUnregisteredModernRule(defaultValue);
	}

	public static boolean getBooleanRule(ServerLevel level, Object rule) {
		return (Boolean) getRuleValue(level, rule, boolean.class);
	}

	public static double getDoubleRule(ServerLevel level, Object rule) {
		return ((Number) getRuleValue(level, rule, double.class)).doubleValue();
	}

	public static <E extends Enum<E>> E getEnumRule(ServerLevel level, Object rule, Class<E> enumClass) {
		Object value = getRuleValue(level, rule, MODERN_GAME_RULE_API ? Object.class : enumClass);
		if (value instanceof String stringValue) {
			return Enum.valueOf(enumClass, stringValue);
		}
		return enumClass.cast(value);
	}

	private static Object deferModernRule(String path, Object defaultValue) {
		DeferredRule rule = new DeferredRule(path, defaultValue);
		MODERN_RULES.add(rule);
		return rule;
	}

	private static Object createUnregisteredModernRule(Object defaultValue) {
		try {
			Class<?> ruleClass = Class.forName("net.minecraft.world.level.gamerules.GameRule");
			Class<?> categoryClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleCategory");
			Class<?> typeClass = Class.forName("net.minecraft.world.level.gamerules.GameRuleType");
			Class<?> visitorCallerType = Class.forName("net.minecraft.world.level.gamerules.GameRules$VisitorCaller");

			Object category = readStaticField(categoryClass, "DROPS");
			Object type;
			ArgumentType<?> argumentType;
			Codec<?> codec;
			Object ruleDefaultValue = defaultValue;
			ToIntFunction<Object> commandResult;
			String visitorMethod;

			if (defaultValue instanceof Boolean) {
				type = readStaticField(typeClass, "BOOL");
				argumentType = BoolArgumentType.bool();
				codec = Codec.BOOL;
				commandResult = value -> (Boolean) value ? 1 : 0;
				visitorMethod = "visitBoolean";
			} else if (defaultValue instanceof Double) {
				type = readStaticField(typeClass, "INT");
				argumentType = DoubleArgumentType.doubleArg();
				codec = COMPAT_DOUBLE_CODEC;
				commandResult = value -> Double.compare((Double) value, 0.0D);
				visitorMethod = "visit";
			} else if (defaultValue instanceof Enum<?> enumValue) {
				type = readStaticField(typeClass, "INT");
				argumentType = StringArgumentType.word();
				codec = Codec.STRING;
				ruleDefaultValue = enumValue.name();
				commandResult = value -> enumOrdinal(enumValue, value);
				visitorMethod = "visit";
			} else {
				throw new IllegalArgumentException("Unsupported gamerule default type " + defaultValue.getClass().getName());
			}

			Object visitorCaller = Proxy.newProxyInstance(
				visitorCallerType.getClassLoader(),
				new Class<?>[] { visitorCallerType },
				(proxy, method, args) -> {
					if (method.getName().equals("call") && args != null && args.length == 2) {
						Class<?> visitorType = Class.forName("net.minecraft.world.level.gamerules.GameRuleTypeVisitor");
						Method visit = visitorType.getMethod(visitorMethod, ruleClass);
						return visit.invoke(args[0], args[1]);
					}
					return null;
				}
			);

			for (var constructor : ruleClass.getConstructors()) {
				if (constructor.getParameterCount() == 8) {
					return constructor.newInstance(
						category,
						type,
						argumentType,
						visitorCaller,
						codec,
						commandResult,
						ruleDefaultValue,
						FeatureFlagSet.of()
					);
				}
			}

			throw new IllegalStateException("Could not find the modern GameRule constructor");
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to create modern gamerule", exception);
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
		rule = unwrapRegisteredRule(rule);
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

	private static Object unwrapRegisteredRule(Object rule) {
		if (!MODERN_GAME_RULE_API) {
			return rule;
		}

		if (rule instanceof DeferredRule deferredRule) {
			rule = deferredRule.requireHolder();
		}

		try {
			Class<?> gameRuleClass = Class.forName("net.minecraft.world.level.gamerules.GameRule");
			if (gameRuleClass.isInstance(rule)) {
				return rule;
			}

			for (Method method : rule.getClass().getMethods()) {
				if (method.getParameterCount() == 0
					&& (method.getName().equals("get") || method.getName().equals("value"))) {
					Object value = invoke(method, rule);
					if (gameRuleClass.isInstance(value)) {
						return value;
					}
				}
			}
		} catch (ClassNotFoundException exception) {
			throw new RuntimeException("Failed to resolve modern gamerule type", exception);
		}

		throw new IllegalStateException("Could not resolve registered gamerule from " + rule.getClass().getName());
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

	private static int enumOrdinal(Enum<?> defaultValue, Object value) {
		try {
			return Enum.valueOf(defaultValue.getDeclaringClass(), (String) value).ordinal();
		} catch (IllegalArgumentException | ClassCastException exception) {
			return defaultValue.ordinal();
		}
	}

	private static final class DeferredRule {
		private final String path;
		private final Object defaultValue;
		private Object holder;

		private DeferredRule(String path, Object defaultValue) {
			this.path = path;
			this.defaultValue = defaultValue;
		}

		private Object requireHolder() {
			if (holder == null) {
				throw new IllegalStateException("Gamerule " + path + " was accessed before registry binding");
			}
			return holder;
		}
	}
}

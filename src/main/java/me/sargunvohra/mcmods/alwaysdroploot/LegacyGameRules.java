package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.GameRules;

public final class LegacyGameRules {
	private static final String MOD_ID = "always-drop-loot";

	private LegacyGameRules() {
	}

	public static Object registerBooleanRule(String name, boolean defaultValue) {
		Method create = findMethod(GameRules.BooleanValue.class, true, 1, "create", "m_46250_");
		Object type = invoke(create, null, defaultValue);
		return register(name, type);
	}

	public static Object registerDoubleRule(String name, double defaultValue) {
		GameRules.Type<DoubleRule> type = createType(
			DoubleArgumentType::doubleArg,
			ruleType -> new DoubleRule(ruleType, defaultValue),
			DoubleRule.class
		);
		return register(name, type);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String name, E defaultValue) {
		Class<E> enumClass = defaultValue.getDeclaringClass();
		GameRules.Type<EnumRule<E>> type = createType(
			StringArgumentType::word,
			ruleType -> new EnumRule<>(ruleType, defaultValue, enumClass),
			enumRuleClass()
		);
		return register(name, type);
	}

	private static Object register(String name, Object type) {
		Method register = findMethod(GameRules.class, true, 3, "register", "m_46189_");
		return invoke(register, null, MOD_ID + ":" + name, GameRules.Category.DROPS, type);
	}

	@SuppressWarnings("unchecked")
	private static <T extends GameRules.Value<T>> GameRules.Type<T> createType(
		Supplier<ArgumentType<?>> argument,
		Function<GameRules.Type<T>, T> valueFactory,
		Class<T> valueClass
	) {
		try {
			for (Constructor<?> constructor : GameRules.Type.class.getDeclaredConstructors()) {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length < 4
					|| !Supplier.class.isAssignableFrom(parameterTypes[0])
					|| !Function.class.isAssignableFrom(parameterTypes[1])
					|| !BiConsumer.class.isAssignableFrom(parameterTypes[2])) {
					continue;
				}

				Object visitorCaller = Proxy.newProxyInstance(
					parameterTypes[3].getClassLoader(),
					new Class<?>[] { parameterTypes[3] },
					(proxy, method, args) -> null
				);
				Object[] arguments = new Object[parameterTypes.length];
				arguments[0] = argument;
				arguments[1] = valueFactory;
				arguments[2] = (BiConsumer<MinecraftServer, T>) (server, value) -> {
				};
				arguments[3] = visitorCaller;

				for (int index = 4; index < parameterTypes.length; index++) {
					if (parameterTypes[index] == Class.class) {
						arguments[index] = valueClass;
					} else if (parameterTypes[index] == FeatureFlagSet.class) {
						Method of = findMethod(FeatureFlagSet.class, true, 0, "of", "m_246902_");
						arguments[index] = invoke(of, null);
					} else {
						throw new IllegalStateException("Unsupported GameRules.Type constructor parameter " + parameterTypes[index].getName());
					}
				}

				constructor.setAccessible(true);
				return (GameRules.Type<T>) constructor.newInstance(arguments);
			}
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to construct legacy gamerule type", exception);
		}

		throw new IllegalStateException("Could not find a compatible GameRules.Type constructor");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <E extends Enum<E>> Class<EnumRule<E>> enumRuleClass() {
		return (Class) EnumRule.class;
	}

	private static Method findMethod(Class<?> type, boolean staticMethod, int parameterCount, String... names) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Method method : current.getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers()) != staticMethod || method.getParameterCount() != parameterCount) {
					continue;
				}

				for (String name : names) {
					if (method.getName().equals(name)) {
						method.setAccessible(true);
						return method;
					}
				}
			}
		}

		throw new IllegalStateException("Could not find " + String.join("/", names) + " on " + type.getName());
	}

	private static Object invoke(Method method, Object instance, Object... arguments) {
		try {
			return method.invoke(instance, arguments);
		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Failed to invoke " + method, exception);
		}
	}

	private static void notifyChanged(GameRules.Value<?> rule, MinecraftServer server) {
		Method onChanged = findMethod(GameRules.Value.class, false, 1, "onChanged", "m_46368_");
		invoke(onChanged, rule, server);
	}

	public static final class DoubleRule extends GameRules.Value<DoubleRule> {
		private final GameRules.Type<DoubleRule> ruleType;
		private double value;

		private DoubleRule(GameRules.Type<DoubleRule> type, double defaultValue) {
			super(type);
			this.ruleType = type;
			this.value = defaultValue;
		}

		@Override
		protected void updateFromArgument(CommandContext<CommandSourceStack> context, String parameterName) {
			this.value = DoubleArgumentType.getDouble(context, parameterName);
		}

		@Override
		protected void deserialize(String serializedValue) {
			try {
				this.value = Double.parseDouble(serializedValue);
			} catch (NumberFormatException ignored) {
			}
		}

		@Override
		public String serialize() {
			return Double.toString(this.value);
		}

		@Override
		public int getCommandResult() {
			return Double.compare(this.value, 0.0D);
		}

		@Override
		protected DoubleRule getSelf() {
			return this;
		}

		@Override
		protected DoubleRule copy() {
			return new DoubleRule(this.ruleType, this.value);
		}

		@Override
		public void setFrom(DoubleRule other, MinecraftServer server) {
			this.value = other.value;
			notifyChanged(this, server);
		}

		public double get() {
			return this.value;
		}

		protected void m_5528_(CommandContext<CommandSourceStack> context, String parameterName) {
			this.updateFromArgument(context, parameterName);
		}

		protected void m_7377_(String serializedValue) {
			this.deserialize(serializedValue);
		}

		public String m_5831_() {
			return this.serialize();
		}

		public int m_6855_() {
			return this.getCommandResult();
		}

		@SuppressWarnings("rawtypes")
		protected GameRules.Value m_5589_() {
			return this;
		}

		@SuppressWarnings("rawtypes")
		protected GameRules.Value m_5590_() {
			return this.copy();
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public void m_5614_(GameRules.Value other, MinecraftServer server) {
			this.setFrom((DoubleRule) other, server);
		}
	}

	public static final class EnumRule<E extends Enum<E>> extends GameRules.Value<EnumRule<E>> {
		private final GameRules.Type<EnumRule<E>> ruleType;
		private final Class<E> enumClass;
		private E value;

		private EnumRule(GameRules.Type<EnumRule<E>> type, E defaultValue, Class<E> enumClass) {
			super(type);
			this.ruleType = type;
			this.enumClass = enumClass;
			this.value = defaultValue;
		}

		@Override
		protected void updateFromArgument(CommandContext<CommandSourceStack> context, String parameterName) {
			this.value = Enum.valueOf(this.enumClass, StringArgumentType.getString(context, parameterName));
		}

		@Override
		protected void deserialize(String serializedValue) {
			try {
				this.value = Enum.valueOf(this.enumClass, serializedValue);
			} catch (IllegalArgumentException ignored) {
			}
		}

		@Override
		public String serialize() {
			return this.value.name();
		}

		@Override
		public int getCommandResult() {
			return this.value.ordinal();
		}

		@Override
		protected EnumRule<E> getSelf() {
			return this;
		}

		@Override
		protected EnumRule<E> copy() {
			return new EnumRule<>(this.ruleType, this.value, this.enumClass);
		}

		@Override
		public void setFrom(EnumRule<E> other, MinecraftServer server) {
			this.value = other.value;
			notifyChanged(this, server);
		}

		public E get() {
			return this.value;
		}

		protected void m_5528_(CommandContext<CommandSourceStack> context, String parameterName) {
			this.updateFromArgument(context, parameterName);
		}

		protected void m_7377_(String serializedValue) {
			this.deserialize(serializedValue);
		}

		public String m_5831_() {
			return this.serialize();
		}

		public int m_6855_() {
			return this.getCommandResult();
		}

		@SuppressWarnings("rawtypes")
		protected GameRules.Value m_5589_() {
			return this;
		}

		@SuppressWarnings("rawtypes")
		protected GameRules.Value m_5590_() {
			return this.copy();
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public void m_5614_(GameRules.Value other, MinecraftServer server) {
			this.setFrom((EnumRule<E>) other, server);
		}
	}
}

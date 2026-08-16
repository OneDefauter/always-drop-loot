package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Constructor;
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
		return GameRules.register(
			MOD_ID + ":" + name,
			GameRules.Category.DROPS,
			GameRules.BooleanValue.create(defaultValue)
		);
	}

	public static Object registerDoubleRule(String name, double defaultValue) {
		GameRules.Type<DoubleRule> type = createType(
			DoubleArgumentType::doubleArg,
			ruleType -> new DoubleRule(ruleType, defaultValue),
			DoubleRule.class
		);
		return GameRules.register(MOD_ID + ":" + name, GameRules.Category.DROPS, type);
	}

	public static <E extends Enum<E>> Object registerEnumRule(String name, E defaultValue) {
		Class<E> enumClass = defaultValue.getDeclaringClass();
		GameRules.Type<EnumRule<E>> type = createType(
			StringArgumentType::word,
			ruleType -> new EnumRule<>(ruleType, defaultValue, enumClass),
			enumRuleClass()
		);
		return GameRules.register(MOD_ID + ":" + name, GameRules.Category.DROPS, type);
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
						arguments[index] = FeatureFlagSet.of();
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

	public static final class DoubleRule extends GameRules.Value<DoubleRule> {
		private double value;

		private DoubleRule(GameRules.Type<DoubleRule> type, double defaultValue) {
			super(type);
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
			return new DoubleRule(this.type, this.value);
		}

		@Override
		public void setFrom(DoubleRule other, MinecraftServer server) {
			this.value = other.value;
			this.onChanged(server);
		}

		public double get() {
			return this.value;
		}
	}

	public static final class EnumRule<E extends Enum<E>> extends GameRules.Value<EnumRule<E>> {
		private final Class<E> enumClass;
		private E value;

		private EnumRule(GameRules.Type<EnumRule<E>> type, E defaultValue, Class<E> enumClass) {
			super(type);
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
			return new EnumRule<>(this.type, this.value, this.enumClass);
		}

		@Override
		public void setFrom(EnumRule<E> other, MinecraftServer server) {
			this.value = other.value;
			this.onChanged(server);
		}

		public E get() {
			return this.value;
		}
	}
}

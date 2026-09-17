package me.sargunvohra.mcmods.alwaysdroploot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerLevel;

public final class ServerLevelResolver {
	private static final String[] WORLD_TYPE_NAMES = {
		"net.minecraft.world.level.Level",
		"net.minecraft.world.World",
		"net.minecraft.class_1937"
	};

	private static final String[] SERVER_LEVEL_TYPE_NAMES = {
		"net.minecraft.server.level.ServerLevel",
		"net.minecraft.server.world.ServerWorld",
		"net.minecraft.class_3218"
	};

	private static final String[] PREFERRED_METHOD_NAMES = {
		"level",
		"getLevel",
		"getWorld",
		"method_37908",
		"method_73183"
	};

	private static final String[] PREFERRED_FIELD_NAMES = {
		"level",
		"world",
		"field_6002"
	};

	private static final Map<Class<?>, Accessor> ACCESSORS = new ConcurrentHashMap<>();
	private static final Accessor NO_ACCESSOR = new Accessor(null, null);

	private ServerLevelResolver() {
	}

	public static ServerLevel getServerLevel(Object source) {
		if (source == null) {
			return null;
		}

		if (source instanceof ServerLevel serverLevel) {
			return serverLevel;
		}

		Accessor accessor = ACCESSORS.computeIfAbsent(source.getClass(), ServerLevelResolver::resolveAccessor);
		Object value = accessor.read(source);
		return value instanceof ServerLevel serverLevel ? serverLevel : null;
	}

	private static Accessor resolveAccessor(Class<?> type) {
		Method method = findAccessorMethod(type);
		if (method != null) {
			return new Accessor(method, null);
		}

		Field field = findAccessorField(type);
		if (field != null) {
			return new Accessor(null, field);
		}

		return NO_ACCESSOR;
	}

	private static Method findAccessorMethod(Class<?> type) {
		Method bestMethod = null;
		int bestScore = -1;

		for (Method method : type.getMethods()) {
			int score = scoreMethod(method);
			if (score > bestScore) {
				bestMethod = method;
				bestScore = score;
			}
		}

		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Method method : current.getDeclaredMethods()) {
				int score = scoreMethod(method);
				if (score > bestScore) {
					method.setAccessible(true);
					bestMethod = method;
					bestScore = score;
				}
			}
		}

		return bestMethod;
	}

	private static int scoreMethod(Method method) {
		if ((method.getModifiers() & Modifier.STATIC) != 0 || method.getParameterCount() != 0) {
			return -1;
		}

		int typeScore = scoreWorldType(method.getReturnType());
		if (typeScore < 0) {
			return -1;
		}

		return typeScore + scoreName(method.getName(), PREFERRED_METHOD_NAMES);
	}

	private static Field findAccessorField(Class<?> type) {
		Field bestField = null;
		int bestScore = -1;

		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if ((field.getModifiers() & Modifier.STATIC) != 0) {
					continue;
				}

				int typeScore = scoreWorldType(field.getType());
				if (typeScore < 0) {
					continue;
				}

				int score = typeScore + scoreName(field.getName(), PREFERRED_FIELD_NAMES);
				if (score > bestScore) {
					field.setAccessible(true);
					bestField = field;
					bestScore = score;
				}
			}
		}

		return bestField;
	}

	private static int scoreWorldType(Class<?> type) {
		if (type == null) {
			return -1;
		}

		String typeName = type.getName();
		if (matchesName(typeName, SERVER_LEVEL_TYPE_NAMES)) {
			return 280;
		}

		if (ServerLevel.class.isAssignableFrom(type)) {
			return 260;
		}

		if (matchesName(typeName, WORLD_TYPE_NAMES)) {
			return 200;
		}

		return -1;
	}

	private static int scoreName(String name, String[] candidates) {
		for (int index = 0; index < candidates.length; index++) {
			if (candidates[index].equals(name)) {
				return (candidates.length - index) * 10;
			}
		}

		return 0;
	}

	private static boolean matchesName(String actualName, String[] candidates) {
		for (String candidate : candidates) {
			if (candidate.equals(actualName)) {
				return true;
			}
		}

		return false;
	}

	private static final class Accessor {
		private final Method method;
		private final Field field;

		private Accessor(Method method, Field field) {
			this.method = method;
			this.field = field;
		}

		private Object read(Object source) {
			if (method != null) {
				try {
					return method.invoke(source);
				} catch (ReflectiveOperationException exception) {
					throw new RuntimeException("Failed to resolve ServerLevel via " + method, exception);
				}
			}

			if (field != null) {
				try {
					return field.get(source);
				} catch (ReflectiveOperationException exception) {
					throw new RuntimeException("Failed to resolve ServerLevel via " + field, exception);
				}
			}

			return null;
		}
	}
}

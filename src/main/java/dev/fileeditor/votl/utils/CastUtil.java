package dev.fileeditor.votl.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class CastUtil {

	@Nullable
	public static Long castLong(@Nullable Object o) {
		return o != null ? Long.valueOf(o.toString()) : null;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public static <T> T getOrDefault(@Nullable Object obj, @Nullable T defaultObj) {
		if (obj == null) return defaultObj;
		if (obj instanceof Long || defaultObj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	@SuppressWarnings("unchecked")
	@NotNull
	public static <T> T requireNonNull(@Nullable Object obj) {
		if (obj == null) throw new NullPointerException("Object is null");
		if (obj instanceof Long) {
			return (T) castLong(obj);
		}
		return (T) obj;
	}

	@Nullable
	public static <T> T resolveOrDefault(@Nullable Object obj, @NotNull Function<Object, T> resolver, @Nullable T defaultObj) {
		if (obj == null) return defaultObj;
		return resolver.apply(obj);
	}

}

package dev.fileeditor.votl.utils;

import ch.qos.logback.classic.Logger;
import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.reflection.Reflectional;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AutoloaderUtil {

	private final static Logger LOG = (Logger) LoggerFactory.getLogger(AutoloaderUtil.class);

	public static void load(@NotNull String path, @NotNull Consumer<Reflectional> callback) {
		load(path, callback, true);
	}

	public static void load(@NotNull String path, Consumer<Reflectional> callback, boolean parseAppInstance) {
		List<Class<? extends Reflectional>> types = new ArrayList<>();
		try (ScanResult scanResult = new ClassGraph()
			.enableClassInfo()
			.ignoreClassVisibility()
			.enableClasspath()
			.acceptPackages(path)
			.scan()) {
			for (ClassInfo info : scanResult.getAllClassesImplementing(Reflectional.class)) {
				try {
					// ClassGraph does not load classes
					types.add(Class.forName(info.getName(), false, AutoloaderUtil.class.getClassLoader()).asSubclass(Reflectional.class));
				} catch (ClassNotFoundException | LinkageError e) {
					LOG.error("Failed to load class {}", info.getName(), e);
				}
			}
		}

		for (Class<? extends Reflectional> reflectionClass : types) {
			// Skip inner/nested classes
			if (reflectionClass.isMemberClass() || reflectionClass.getEnclosingClass() != null) {
				continue;
			}

			// Skip abstract classes
			if (Modifier.isAbstract(reflectionClass.getModifiers())) {
				continue;
			}

			if (reflectionClass.getPackage().getName().contains("contracts")) {
				continue;
			}

			try {
				if (parseAppInstance) {
					//noinspection rawtypes
					Class[] arguments = new Class[1];
					arguments[0] = App.class;

					callback.accept(reflectionClass.getDeclaredConstructor(arguments)
						.newInstance(App.getInstance()));
				} else {
					callback.accept(reflectionClass.getDeclaredConstructor()
						.newInstance());
				}
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				LOG.error("Failed to create new instance of {}", reflectionClass.getName(), e);
			}
		}
	}

}

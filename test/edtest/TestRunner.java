package edtest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Runs every public no-arg method named test* of every class named *Test found under the directory given as the first argument. */
public final class TestRunner {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        int passed = 0;
        List<String> failed = new ArrayList<>();
        for (String className : findTestClasses(root)) {
            Class<?> type = Class.forName(className);
            for (Method m : type.getMethods()) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0) {
                    continue;
                }
                String id = className + "." + m.getName();
                try {
                    m.invoke(type.getDeclaredConstructor().newInstance());
                    passed++;
                } catch (InvocationTargetException e) {
                    failed.add(id + " -> " + e.getCause());
                }
            }
        }
        failed.forEach(f -> System.out.println("FAIL " + f));
        System.out.println(passed + " passed, " + failed.size() + " failed");
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    private static List<String> findTestClasses(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.map(p -> root.relativize(p).toString())
                    .filter(n -> n.endsWith("Test.class"))
                    .map(n -> n.substring(0, n.length() - ".class".length()).replace('\\', '.').replace('/', '.'))
                    .sorted()
                    .toList();
        }
    }
}

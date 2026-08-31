package it.uniroma2.isw2.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JavaClassFinder {

    private JavaClassFinder(){}

    public static List<String> findProductionClasses(
            Path repository)
            throws IOException {

        return findJavaClasses(repository).stream()
                .filter(JavaClassFinder::isProductionClass)
                .toList();
    }

    /** Restituisce tutti i file Java dello snapshot, inclusi i test. */
    public static List<String> findJavaClasses(Path repository) throws IOException {

        List<String> classes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repository)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {

                        String p = path.toString().replace("\\", "/");

                        if (!p.endsWith(".java")) {
                            return;
                        }

                        classes.add(normalizePath(p));
                    });
        }

        return classes;
    }

    private static boolean isProductionClass(String path) {
        return path.contains("/src/main/java/");
    }

    private static String normalizePath(
            String fullPath) {

        int index =
                fullPath.indexOf("/tajo/");

        if (index != -1) {
            return fullPath.substring(index + 1);
        }

        // fallback nel caso in cui il path non contenga "/tajo/"
        return fullPath;
    }
}

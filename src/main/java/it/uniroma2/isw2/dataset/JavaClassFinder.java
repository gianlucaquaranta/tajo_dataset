package it.uniroma2.isw2.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaClassFinder {

    private JavaClassFinder(){}

    public static List<String> findProductionClasses(
            Path repository)
            throws IOException {

        List<String> classes =
                new ArrayList<>();

        Files.walk(repository)
                .filter(Files::isRegularFile)
                .forEach(path -> {

                    String p =
                            path.toString()
                                    .replace("\\", "/");

                    if (!p.endsWith(".java")) {
                        return;
                    }

                    if (!p.contains("/src/main/java/")) {
                        return;
                    }

                    classes.add(
                            normalizePath(p)
                    );
                });

        return classes;
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
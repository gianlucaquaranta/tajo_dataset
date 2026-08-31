package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.model.ClassProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Filtro condiviso (M1 e M4) per escludere dal dataset le classi "rumore"
 * strutturale, cosi' da lasciare solo classi significative.
 * <p>
 * Esclude: modulo {@code tajo-thirdparty}, enum, interfacce, eccezioni (per
 * package {@code exception} o per nome Exception/Error) e annotation
 * ({@code @interface}). Nel flusso M1 il filtro preliminare esclude anche i
 * test, mantenendo soltanto file sotto {@code src/main/java}.
 * <p>
 * NOTA: le <b>classi astratte</b> NON sono escluse qui (possono essere classi
 * centrali con molti metodi implementati). Chi le vuole escludere - es. la M4
 * per il ranking dei candidati al refactoring - usa {@code profile.isAbstractClass()}
 * in aggiunta a questo filtro.
 */
public final class ClassFilter {

    private static final String THIRD_PARTY_MODULE = "tajo-thirdparty";
    private static final String EXCEPTION_PACKAGE = "/exception/";

    private ClassFilter() {
    }

    /**
     * Restituisce tutte le classi di produzione dello snapshot. Utile quando
     * ulteriori filtri dipendono da metriche calcolate successivamente (M4).
     */
    public static List<String> findProductionClasses(Path repositoryPath)
            throws IOException {
        return findJavaClasses(repositoryPath).stream()
                .filter(ClassFilter::isProductionClass)
                .toList();
    }

    /**
     * Seleziona i candidati M1 prima di CK: scandisce lo snapshot, esclude test
     * e non-production, poi applica tutte le regole indipendenti da CK.
     */
    public static List<String> findClassesBeforeCk(Path repositoryPath)
            throws IOException {
        return filterBeforeCk(findJavaClasses(repositoryPath), repositoryPath);
    }

    /**
     * @param clazz   path git-relative della classe (es. "tajo/tajo-core/.../Foo.java")
     * @param path    path assoluto del file sorgente
     * @param profile profilo CK della classe (puo' essere null se non risolta)
     * @return true se la classe va esclusa dal dataset
     */
    public static boolean isExcluded(String clazz, Path path, ClassProfile profile) {
        return isExcludedBeforeCk(clazz, path) || isExcludedAfterCk(profile);
    }

    /**
     * Filtro unico da invocare prima di CK: scarta prima i test e le altre
     * classi non di produzione, poi il rumore riconoscibile senza CK.
     */
    public static List<String> filterBeforeCk(
            List<String> javaClasses,
            Path repositoryPath) {

        Path repositoryParent = repositoryPath.getParent();
        List<String> included = new ArrayList<>();
        for (String clazz : javaClasses) {
            Path sourcePath = repositoryParent != null
                    ? repositoryParent.resolve(clazz).toAbsolutePath().normalize()
                    : repositoryPath.resolve(clazz).toAbsolutePath().normalize();
            if (!isExcludedBeforeCk(clazz, sourcePath)) {
                included.add(clazz);
            }
        }
        return included;
    }

    private static List<String> findJavaClasses(Path repositoryPath)
            throws IOException {
        List<String> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryPath)) {
            paths.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> path.endsWith(".java"))
                    .map(ClassFilter::normalizePath)
                    .forEach(classes::add);
        }
        return classes;
    }

    /**
     * Filtri applicabili prima di CK: non dipendono dalle metriche o dal tipo
     * sintattico restituito dal tool.
     */
    public static boolean isExcludedBeforeCk(String clazz, Path path) {
        if (!isProductionClass(clazz)) {
            return true;
        }
        if (isThirdParty(clazz)) {
            return true;
        }
        if (isInExceptionPackage(clazz) || isExceptionByName(clazz)) {
            return true;
        }
        if (isAnnotation(path, clazz)) {
            return true;
        }
        return false;
    }

    private static boolean isProductionClass(String clazz) {
        return clazz.replace('\\', '/').contains("/src/main/java/");
    }

    /** Mantiene il formato storico della colonna CLASS: {@code tajo/...}. */
    private static String normalizePath(String fullPath) {
        int index = fullPath.indexOf("/tajo/");
        return (index >= 0) ? fullPath.substring(index + 1) : fullPath;
    }

    /**
     * Filtri che richiedono il profilo prodotto da CK. Le classi astratte NON
     * vengono escluse nella milestone 1.
     */
    public static boolean isExcludedAfterCk(ClassProfile profile) {
        return profile != null && (profile.isEnum() || profile.isInterface());
    }

    public static boolean isThirdParty(String clazz) {
        return clazz.contains(THIRD_PARTY_MODULE);
    }

    private static boolean isInExceptionPackage(String clazz) {
        return clazz.toLowerCase().contains(EXCEPTION_PACKAGE);
    }

    private static boolean isExceptionByName(String clazz) {
        String name = baseName(clazz);
        return name.endsWith("Exception") || name.endsWith("Error");
    }

    /**
     * Rileva un file annotation ({@code @interface Nome}) leggendo il sorgente:
     * CK non emette risultati per le annotation, quindi non sono nei profili.
     * Lettura in ISO-8859-1 (non fallisce mai; il pattern cercato e' ASCII).
     */
    private static boolean isAnnotation(Path path, String clazz) {
        try {
            String content = new String(
                    Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
            Pattern pattern = Pattern.compile(
                    "@interface\\s+" + Pattern.quote(baseName(clazz)) + "\\b");
            return pattern.matcher(content).find();
        } catch (IOException e) {
            return false;
        }
    }

    /** Nome semplice del file, senza directory e senza estensione .java. */
    public static String baseName(String clazz) {
        int slash = clazz.lastIndexOf('/');
        String name = (slash >= 0) ? clazz.substring(slash + 1) : clazz;
        return name.endsWith(".java")
                ? name.substring(0, name.length() - ".java".length())
                : name;
    }
}

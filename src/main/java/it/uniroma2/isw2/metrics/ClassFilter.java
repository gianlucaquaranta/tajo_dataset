package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.model.ClassProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Filtro condiviso (M1 e M4) per escludere dal dataset le classi "rumore"
 * strutturale, cosi' da lasciare solo classi significative.
 * <p>
 * Esclude: modulo {@code tajo-thirdparty}, enum, interfacce, eccezioni (per
 * package {@code exception} o per nome Exception/Error) e annotation
 * ({@code @interface}). Le classi di test sono gia' escluse a monte da
 * {@code JavaClassFinder} (solo {@code src/main/java}).
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
     * @param clazz   path git-relative della classe (es. "tajo/tajo-core/.../Foo.java")
     * @param path    path assoluto del file sorgente
     * @param profile profilo CK della classe (puo' essere null se non risolta)
     * @return true se la classe va esclusa dal dataset
     */
    public static boolean isExcluded(String clazz, Path path, ClassProfile profile) {
        if (isThirdParty(clazz)) {
            return true;
        }
        if (isInExceptionPackage(clazz) || isExceptionByName(clazz)) {
            return true;
        }
        if (isAnnotation(path, clazz)) {
            return true;
        }
        // Enum e interfacce. Le classi astratte NON vengono escluse qui.
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

package it.uniroma2.isw2.metrics;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import it.uniroma2.isw2.model.CkMetrics;
import it.uniroma2.isw2.model.ClassProfile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Calcola le metriche di prodotto C&amp;K (WMC, DIT, RFC, Fan-In, Fan-Out) con il
 * tool <a href="https://github.com/mauricioaniche/ck">CK</a> di Maurício Aniche.
 * <p>
 * CK lavora sul <b>sorgente</b> (Eclipse JDT), quindi gira direttamente sullo
 * snapshot già in checkout, senza bisogno di compilare. Viene eseguito una sola
 * volta sull'intero repository della release (necessario a CK per risolvere i
 * tipi e calcolare correttamente dipendenze e gerarchie tra i moduli).
 * <p>
 * Un file .java produce piu' risultati CK (classe top-level, inner class, enum,
 * ecc.): per la riga del dataset si tiene la <b>classe top-level</b> (nome senza
 * {@code $}), preferendo quella il cui nome coincide col nome del file.
 */
public final class CkAnalyzer {

    private static final Logger LOGGER =
            Logger.getLogger(CkAnalyzer.class.getName());

    private CkAnalyzer() {
    }

    /**
     * Esegue CK sull'intero repository e restituisce, per ciascun file, il {@link ClassProfile}
     * completo (metriche + tipo/modifiers) per ciascun file, utile a chi deve
     * anche classificare le classi (enum, interfacce, astratte, ...).
     */
    public static Map<String, ClassProfile> analyzeProfiles(String repositoryPath) {

        Map<String, ClassProfile> byFile = new HashMap<>();
        // File per cui abbiamo gia' trovato la classe che combacia col nome del
        // file: da quel momento e' definitiva e non va sovrascritta.
        Set<String> matchedByFileName = new HashSet<>();

        new CK().calculate(repositoryPath, result -> collect(result, byFile, matchedByFileName));

        LOGGER.info(() -> "CK: metriche calcolate per " + byFile.size() + " classi (classi di test incluse)");
        return byFile;
    }

    private static void collect(
            CKClassResult result,
            Map<String, ClassProfile> byFile,
            Set<String> matchedByFileName) {

        // Solo classi top-level: le inner/anonymous hanno '$' nel nome.
        if (result.getClassName().contains("$")) {
            return;
        }

        String key = keyOf(result.getFile());
        boolean nameMatchesFile =
                simpleName(result.getClassName()).equals(baseName(result.getFile()));

        if (nameMatchesFile) {
            byFile.put(key, toProfile(result));
            matchedByFileName.add(key);
        } else if (!matchedByFileName.contains(key)) {
            // fallback: prima classe top-level, finche' non ne troviamo una
            // che combacia col nome del file.
            byFile.putIfAbsent(key, toProfile(result));
        }
    }

    private static ClassProfile toProfile(CKClassResult r) {
        CkMetrics metrics = new CkMetrics(
                r.getWmc(), r.getDit(), r.getRfc(), r.getFanin(), r.getFanout(),
                r.getNumberOfMethods());
        return new ClassProfile(metrics, r.getType(), r.getModifiers());
    }

    private static String keyOf(String file) {
        return Paths.get(file).toAbsolutePath().normalize().toString();
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return (dot >= 0) ? className.substring(dot + 1) : className;
    }

    private static String baseName(String file) {
        Path name = Paths.get(file).getFileName();
        String s = (name != null) ? name.toString() : file;
        return s.endsWith(".java") ? s.substring(0, s.length() - ".java".length()) : s;
    }
}

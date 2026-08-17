package it.uniroma2.isw2.metrics;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.rule.RuleSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calcola la complessità ciclomatica totale (CC) per classe usando PMD.
 * <p>
 * Esegue un batch {@link PmdAnalysis} con la sola regola
 * {@link CyclomaticComplexityRule}, che per ogni classe top-level calcola la
 * metrica {@code WEIGHED_METHOD_COUNT} (somma delle CYCLO dei metodi). I
 * risultati sono raccolti in un collettore statico: l'analisi e' mono-thread
 * ({@code setThreads(0)}) quindi non serve sincronizzazione durante la visita,
 * e il metodo pubblico e' {@code synchronized} per evitare run concorrenti.
 */
public final class CcAnalyzer {

    private static final Logger LOGGER =
            Logger.getLogger(CcAnalyzer.class.getName());

    /** Collettore statico: chiave-file (path assoluto normalizzato) -> CC. */
    private static final Map<String, Integer> COLLECTOR = new HashMap<>();
    /** File per cui la classe top-level combacia col nome del file (definitiva). */
    private static final Set<String> MATCHED = new HashSet<>();

    private CcAnalyzer() {
    }

    /**
     * Calcola la CC per ogni file fornito.
     *
     * @param files percorsi assoluti dei file .java
     * @return mappa chiave-file -> CC (0 per i file senza classe misurabile)
     */
    public static synchronized Map<String, Integer> countCyclomatic(List<Path> files) {

        COLLECTOR.clear();
        MATCHED.clear();

        Map<String, Integer> result = new HashMap<>();
        List<Path> existing = new ArrayList<>();
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                Path key = file.toAbsolutePath().normalize();
                result.put(key.toString(), 0);
                existing.add(key);
            }
        }
        if (existing.isEmpty()) {
            return result;
        }

        PMDConfiguration configuration = new PMDConfiguration();
        LanguageVersion javaVersion = LanguageRegistry.PMD
                .getLanguageById("java").getDefaultVersion();
        configuration.setDefaultLanguageVersion(javaVersion);
        configuration.setThreads(0);
        existing.forEach(configuration::addInputPath);

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            pmd.addRuleSet(RuleSet.forSingleRule(new CyclomaticComplexityRule()));
            pmd.performAnalysisAndCollectReport();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "CC analysis failed", e);
        }

        result.putAll(COLLECTOR);
        return result;
    }

    /**
     * Registra la CC di una classe top-level (chiamato dalla regola PMD).
     * Preferisce la classe il cui nome combacia col nome del file.
     */
    static void record(String absPath, String simpleName, int cc) {
        Path path = Paths.get(absPath).toAbsolutePath().normalize();
        String key = path.toString();
        String base = baseName(path);
        if (simpleName.equals(base)) {
            COLLECTOR.put(key, cc);
            MATCHED.add(key);
        } else if (!MATCHED.contains(key)) {
            COLLECTOR.putIfAbsent(key, cc);
        }
    }

    private static String baseName(Path path) {
        Path name = path.getFileName();
        String s = (name != null) ? name.toString() : path.toString();
        return s.endsWith(".java") ? s.substring(0, s.length() - ".java".length()) : s;
    }
}

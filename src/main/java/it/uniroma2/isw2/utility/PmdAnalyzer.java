package it.uniroma2.isw2.utility;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conta il numero di code smell rilevati da PMD, usato per popolare la
 * colonna NSMELLS del dataset.
 * <p>
 * L'analisi viene eseguita in batch su tutti i file di una release in una
 * sola invocazione PMD: il ruleset e il parser Java vengono inizializzati una
 * volta sola e PMD sfrutta il proprio parallelismo interno. Eseguire una
 * PmdAnalysis separata per ogni file e' invece estremamente lento (ricarica
 * ruleset e parser ad ogni file) e fa sembrare la generazione "bloccata".
 */
public class PmdAnalyzer {

    /**
     * Ruleset PMD usato per il conteggio degli smell.
     * quickstart.xml e' il set raccomandato ufficialmente da PMD e
     * rappresenta un insieme bilanciato di regole (best practices,
     * design, error prone, ecc.).
     */
    private static final String RULESET = "rulesets/java/quickstart.xml";

    private PmdAnalyzer() {
    }

    /**
     * Analizza in un'unica esecuzione tutti i file Java forniti e restituisce,
     * per ciascuno, il numero di smell (violazioni PMD).
     * <p>
     * La mappa e' indicizzata dalla chiave canonica del file
     * ({@link #key(Path)}); ogni file di input e' presente nel risultato,
     * anche con conteggio 0. In caso di errore di analisi tutti i file
     * ottengono conteggio 0, cosi' da non interrompere la generazione del
     * dataset.
     *
     * @param files percorsi assoluti dei file .java da analizzare
     * @return mappa chiave-file -> numero di violazioni
     */
    public static Map<String, Integer> countSmells(List<Path> files) {

        Map<String, Integer> counts = new HashMap<>();
        if (files == null || files.isEmpty()) {
            return counts;
        }

        // Pre-popola a 0 (solo i file realmente esistenti) cosi' anche le
        // classi senza violazioni compaiono nel risultato.
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                counts.put(key(file), 0);
            }
        }
        if (counts.isEmpty()) {
            return counts;
        }

        PMDConfiguration configuration = new PMDConfiguration();

        LanguageVersion javaVersion =
                LanguageRegistry.PMD
                        .getLanguageById("java")
                        .getDefaultVersion();
        configuration.setDefaultLanguageVersion(javaVersion);

        configuration.addRuleSet(RULESET);
        files.stream()
                .filter(Files::isRegularFile)
                .forEach(configuration::addInputPath);

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            Report report = pmd.performAnalysisAndCollectReport();
            for (RuleViolation violation : report.getViolations()) {
                String violationKey =
                        key(Paths.get(violation.getFileId().getAbsolutePath()));
                counts.computeIfPresent(
                        violationKey, (k, current) -> current + 1);
            }
        } catch (Exception e) {
            System.err.println(
                    "PMD batch analysis failed: " + e.getMessage());
        }

        return counts;
    }

    /**
     * Numero di smell per un singolo file. Comodo per usi puntuali; per
     * generare il dataset preferire {@link #countSmells(List)} in batch.
     *
     * @param filePath percorso assoluto del file .java
     * @return numero di violazioni PMD (0 se il file manca o l'analisi fallisce)
     */
    public static int countSmells(Path filePath) {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return 0;
        }
        return countSmells(List.of(filePath))
                .getOrDefault(key(filePath), 0);
    }

    /**
     * Chiave canonica di un file, coerente tra i path di input e i path
     * riportati da PMD nelle violazioni.
     */
    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}

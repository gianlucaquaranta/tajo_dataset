package it.uniroma2.isw2.utility;

import com.opencsv.CSVWriter;
import it.uniroma2.isw2.labeling.ProportionTotalLabeler;
import it.uniroma2.isw2.metrics.CcAnalyzer;
import it.uniroma2.isw2.metrics.CkAnalyzer;
import it.uniroma2.isw2.metrics.ClassFilter;
import it.uniroma2.isw2.metrics.ClocAnalyzer;
import it.uniroma2.isw2.metrics.GitRepositoryAnalyzer;
import it.uniroma2.isw2.metrics.PmdAnalyzer;
import it.uniroma2.isw2.model.CkMetrics;
import it.uniroma2.isw2.model.ClassProfile;
import it.uniroma2.isw2.model.ProcessMetrics;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class CsvUtils {

    private static final Logger LOGGER =
            Logger.getLogger(CsvUtils.class.getName());

    /** Prefisso che la colonna CLASS aggiunge rispetto al path git-relative. */
    private static final String CLASS_PREFIX = "tajo/";

    private CsvUtils(){}

    public static void writeRows(
            CSVWriter writer,
            List<String> classes,
            int releaseId,
            String releaseName,
            String repositoryPath,
            GitRepositoryAnalyzer gitAnalyzer,
            LocalDateTime releaseDate,
            ProportionTotalLabeler labeler) {
        LOGGER.info(() -> "Classi totali nella release: " + classes.size());

        // La colonna CLASS include gia' il prefisso "tajo/" e repositoryPath
        // termina con "tajo", quindi il file reale va risolto dalla directory
        // padre del repository (evita il path errato ".../tajo/tajo/...").
        Path repoParent = Paths.get(repositoryPath).getParent();

        // Associazione classe -> path assoluto del file, mantenendo l'ordine.
        Map<String, Path> allClasses = new LinkedHashMap<>();
        for (String clazz : classes) {
            Path fullPath = (repoParent != null)
                    ? repoParent.resolve(clazz)
                    : Paths.get(repositoryPath, clazz);
            allClasses.put(clazz, fullPath.toAbsolutePath().normalize());
        }

        // CK (tipo/modifiers + metriche di prodotto) una volta sull'intero repo:
        // serve sia per le colonne C&K sia per il filtraggio.
        Map<String, ClassProfile> profilesByFile =
                CkAnalyzer.analyzeProfiles(repositoryPath);

        // Filtra il rumore strutturale (test gia' esclusi a monte): tajo-thirdparty,
        // enum/interface/abstract, eccezioni e annotation.
        Map<String, Path> classToPath = new LinkedHashMap<>();
        for (Map.Entry<String, Path> e : allClasses.entrySet()) {
            if (!ClassFilter.isExcluded(
                    e.getKey(), e.getValue(), profilesByFile.get(e.getValue().toString()))) {
                classToPath.put(e.getKey(), e.getValue());
            }
        }
        LOGGER.info(() -> "Classi tenute: " + classToPath.size()
                + " (su " + allClasses.size() + " totali)");

        List<Path> paths = List.copyOf(classToPath.values());

        // Un'unica invocazione per l'intera release: PMD per gli smell, cloc
        // per le LOC, PMD (metrica) per la CC (CK gia' calcolato sopra).
        LOGGER.info(() ->
                "Running PMD + cloc + CC on " + classToPath.size() + " classes...");
        Map<String, Integer> smellsByFile = PmdAnalyzer.countSmells(paths);
        Map<String, Integer> locByFile = ClocAnalyzer.countLoc(paths);
        Map<String, Integer> ccByFile = CcAnalyzer.countCyclomatic(paths);

        for (Map.Entry<String, Path> entry : classToPath.entrySet()) {
            String clazz = entry.getKey();
            Path fullPath = entry.getValue();

            int loc = locByFile.getOrDefault(fullPath.toString(), 0);
            int nSmells = smellsByFile.getOrDefault(
                    fullPath.toString(), 0);

            // Metriche di processo dalla history condivisa (aggregate a livello
            // di classe, cumulative fino alla data della release).
            ProcessMetrics pm = gitAnalyzer.metricsFor(
                    toGitRelativePath(clazz), releaseDate);

            // Metriche di prodotto C&K da CK (per file).
            ClassProfile profile = profilesByFile.get(fullPath.toString());
            CkMetrics ck = (profile != null) ? profile.metrics() : CkMetrics.zero();

            writer.writeNext(
                    new String[] {
                            "TAJO",
                            String.valueOf(releaseId),
                            releaseName,
                            clazz,
                            String.valueOf(loc),
                            String.valueOf(nSmells),
                            String.valueOf(pm.locAdded()),
                            String.valueOf(pm.maxLocAdded()),
                            String.valueOf(pm.churn()),
                            formatDecimal(pm.avgChurn()),
                            String.valueOf(pm.nr()),
                            String.valueOf(pm.nFix()),
                            formatDecimal(pm.fixRate()),
                            String.valueOf(pm.nAuth()),
                            formatDecimal(pm.avgChangeSet()),
                            String.valueOf(pm.maxChangeSet()),
                            formatDecimal(pm.avgNd()),
                            String.valueOf(pm.ageLastChange()),
                            String.valueOf(pm.minExp()),
                            String.valueOf(ck.nMethods()),
                            String.valueOf(ck.dit()),
                            String.valueOf(ck.rfc()),
                            String.valueOf(ck.fanIn()),
                            String.valueOf(ck.fanOut()),
                            String.valueOf(ccByFile.getOrDefault(fullPath.toString(), 0)),
                            labeler.isBuggy(toGitRelativePath(clazz),
                                    releaseId, releaseDate)
                                    ? "yes" : "no"
                    }
            );
        }
    }

    /**
     * Converte il valore della colonna CLASS (con prefisso "tajo/") nel path
     * git-relative usato dalla history (senza prefisso).
     */
    private static String toGitRelativePath(String clazz) {
        return clazz.startsWith(CLASS_PREFIX)
                ? clazz.substring(CLASS_PREFIX.length())
                : clazz;
    }

    /** Formatta un decimale col punto (Locale.US) per non rompere il CSV. */
    private static String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public static CSVWriter createWriter(
            String outputPath)
            throws Exception {

        CSVWriter writer =
                new CSVWriter(
                        new FileWriter(outputPath)
                );

        writer.writeNext(
                new String[]{
                        "PROJECT",
                        "RELEASE_ID",
                        "RELEASE_NAME",
                        "CLASS",
                        "LOC",
                        "NSMELLS",
                        "LOC_ADDED",
                        "MAX_LOC_ADDED",
                        "CHURN",
                        "AVG_CHURN",
                        "NR",
                        "NFIX",
                        "FIX_RATE",
                        "NAUTH",
                        "AVG_CHANGE_SET",
                        "MAX_CHANGE_SET",
                        "AVG_ND",
                        "AGE_LAST_CHANGE",
                        "MIN_EXP",
                        "NUM_METHODS",
                        "DIT",
                        "RFC",
                        "FAN_IN",
                        "FAN_OUT",
                        "CC",
                        "BUGGY"
                });

        return writer;
    }
}

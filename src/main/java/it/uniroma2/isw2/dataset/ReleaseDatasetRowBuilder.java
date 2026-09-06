package it.uniroma2.isw2.dataset;

import it.uniroma2.isw2.labeling.ProportionTotalLabeler;
import it.uniroma2.isw2.metrics.CcAnalyzer;
import it.uniroma2.isw2.metrics.CkAnalyzer;
import it.uniroma2.isw2.metrics.ClassFilter;
import it.uniroma2.isw2.metrics.ClocAnalyzer;
import it.uniroma2.isw2.metrics.GitRepositoryAnalyzer;
import it.uniroma2.isw2.metrics.PmdAnalyzer;
import it.uniroma2.isw2.model.ClassProfile;
import it.uniroma2.isw2.model.CkMetrics;
import it.uniroma2.isw2.model.DatasetRow;
import it.uniroma2.isw2.model.ProcessMetrics;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Costruisce le righe del dataset per una release, calcolando metriche statiche,
 * metriche di processo e label. Non conosce il formato CSV ne' il writer.
 */
public final class ReleaseDatasetRowBuilder {

    private static final Logger LOGGER =
            Logger.getLogger(ReleaseDatasetRowBuilder.class.getName());
    private static final String PROJECT_KEY = "TAJO";
    private static final String CLASS_PREFIX = "tajo/";

    private ReleaseDatasetRowBuilder() {
    }

    public static List<DatasetRow> build(
            List<String> prefilteredClasses,
            int releaseId,
            String releaseName,
            String repositoryPath,
            GitRepositoryAnalyzer gitAnalyzer,
            LocalDateTime releaseDate,
            ProportionTotalLabeler labeler) {

        LOGGER.info(() -> "Classi candidate nella release: "
                + prefilteredClasses.size());
        Map<String, Path> allClasses = resolvePaths(prefilteredClasses, repositoryPath);

        // CK conserva il contesto dell'intero snapshot per gerarchie e dipendenze.
        Map<String, ClassProfile> profilesByFile =
                CkAnalyzer.analyzeProfiles(repositoryPath);
        Map<String, Path> classes = applyCkFilters(allClasses, profilesByFile);
        LOGGER.info(() -> "Classi tenute dopo il filtro CK: " + classes.size());

        List<Path> paths = List.copyOf(classes.values());
        LOGGER.info(() -> "Running PMD + cloc + CC on " + classes.size() + " classes...");
        Map<String, Integer> smellsByFile = PmdAnalyzer.countSmells(paths);
        Map<String, Integer> locByFile = ClocAnalyzer.countLoc(paths);
        Map<String, Integer> ccByFile = CcAnalyzer.countCyclomatic(paths);

        List<DatasetRow> rows = new ArrayList<>(classes.size());
        for (Map.Entry<String, Path> entry : classes.entrySet()) {
            String clazz = entry.getKey();
            Path file = entry.getValue();
            ProcessMetrics process = gitAnalyzer.metricsFor(
                    toGitRelativePath(clazz), releaseDate);
            ClassProfile profile = profilesByFile.get(file.toString());
            CkMetrics ck = (profile != null) ? profile.metrics() : CkMetrics.zero();

            rows.add(new DatasetRow(
                    PROJECT_KEY, releaseId, releaseName, clazz,
                    locByFile.getOrDefault(file.toString(), 0),
                    smellsByFile.getOrDefault(file.toString(), 0),
                    process.locAdded(), process.maxLocAdded(), process.churn(),
                    process.avgChurn(), process.nr(), process.nFix(), process.fixRate(),
                    process.nAuth(), process.avgChangeSet(), process.maxChangeSet(),
                    process.avgNd(), process.ageLastChange(), process.minExp(),
                    ck.nMethods(), ck.dit(), ck.rfc(), ck.fanIn(), ck.fanOut(),
                    ccByFile.getOrDefault(file.toString(), 0),
                    labeler.isBuggy(toGitRelativePath(clazz), releaseId, releaseDate)));
        }
        return rows;
    }

    private static Map<String, Path> resolvePaths(
            List<String> classes, String repositoryPath) {
        Path repositoryParent = Paths.get(repositoryPath).getParent();
        Map<String, Path> paths = new LinkedHashMap<>();
        for (String clazz : classes) {
            Path path = (repositoryParent != null)
                    ? repositoryParent.resolve(clazz)
                    : Paths.get(repositoryPath, clazz);
            paths.put(clazz, path.toAbsolutePath().normalize());
        }
        return paths;
    }

    private static Map<String, Path> applyCkFilters(
            Map<String, Path> classes,
            Map<String, ClassProfile> profilesByFile) {
        Map<String, Path> included = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : classes.entrySet()) {
            ClassProfile profile = profilesByFile.get(entry.getValue().toString());
            if (!ClassFilter.isExcludedAfterCk(profile)) {
                included.put(entry.getKey(), entry.getValue());
            }
        }
        return included;
    }

    private static String toGitRelativePath(String clazz) {
        return clazz.startsWith(CLASS_PREFIX)
                ? clazz.substring(CLASS_PREFIX.length())
                : clazz;
    }
}

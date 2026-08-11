package it.uniroma2.isw2.utility;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CsvUtils {

    private static final Logger LOGGER =
            Logger.getLogger(CsvUtils.class.getName());

    private CsvUtils(){}

    public static void writeRows(
            CSVWriter writer,
            List<String> classes,
            int releaseId,
            String releaseName,
            String repositoryPath) {

        // La colonna CLASS include gia' il prefisso "tajo/" e repositoryPath
        // termina con "tajo", quindi il file reale va risolto dalla directory
        // padre del repository (evita il path errato ".../tajo/tajo/...").
        Path repoParent = Paths.get(repositoryPath).getParent();

        // Associazione classe -> path assoluto del file, mantenendo l'ordine.
        Map<String, Path> classToPath = new LinkedHashMap<>();
        for (String clazz : classes) {
            Path fullPath = (repoParent != null)
                    ? repoParent.resolve(clazz)
                    : Paths.get(repositoryPath, clazz);
            classToPath.put(clazz, fullPath.toAbsolutePath().normalize());
        }

        List<Path> paths = List.copyOf(classToPath.values());

        // Un'unica invocazione per l'intera release (molto piu' veloce che
        // analizzare un file alla volta): PMD per gli smell, cloc per le LOC.
        LOGGER.info(() ->
                "Running PMD + cloc on " + classToPath.size() + " classes...");
        Map<String, Integer> smellsByFile = PmdAnalyzer.countSmells(paths);
        Map<String, Integer> locByFile = ClocAnalyzer.countLoc(paths);

        for (Map.Entry<String, Path> entry : classToPath.entrySet()) {
            String clazz = entry.getKey();
            Path fullPath = entry.getValue();

            int loc = locByFile.getOrDefault(fullPath.toString(), 0);
            int nSmells = smellsByFile.getOrDefault(
                    fullPath.toString(), 0);

            writer.writeNext(
                    new String[] {
                            "TAJO",
                            String.valueOf(releaseId),
                            releaseName,
                            clazz,
                            String.valueOf(loc),
                            String.valueOf(nSmells)
                    }
            );
        }
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
                        "NSMELLS"
                });

        return writer;
    }
}
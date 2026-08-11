package it.uniroma2.isw2.utility;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvUtils {

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

        // Un'unica analisi PMD per l'intera release (molto piu' veloce che
        // analizzare un file alla volta).
        System.out.println(
                "  Running PMD on " + classToPath.size() + " classes...");
        Map<String, Integer> smellsByFile =
                PmdAnalyzer.countSmells(
                        List.copyOf(classToPath.values()));

        for (Map.Entry<String, Path> entry : classToPath.entrySet()) {
            String clazz = entry.getKey();
            int nSmells = smellsByFile.getOrDefault(
                    entry.getValue().toString(), 0);

            writer.writeNext(
                    new String[] {
                            "TAJO",
                            String.valueOf(releaseId),
                            releaseName,
                            clazz,
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
                        "NSMELLS"
                });

        return writer;
    }
}
package it.uniroma2.isw2.utility;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calcola le LOC (righe di codice, ovvero non vuote e non di commento) usando
 * il tool esterno <a href="https://github.com/AlDanial/cloc">cloc</a>.
 * <p>
 * L'analisi e' eseguita in batch: tutti i file di una release vengono passati a
 * cloc in un'unica invocazione tramite {@code --list-file}, e il valore della
 * colonna {@code code} viene riattribuito a ciascun file. cloc deve essere
 * disponibile sul PATH (installabile con {@code winget install AlDanial.Cloc}).
 */
public final class ClocAnalyzer {

    /** Eseguibile cloc; deve essere raggiungibile dal PATH. */
    private static final String CLOC = "cloc";

    private static final Logger LOGGER =
            Logger.getLogger(ClocAnalyzer.class.getName());

    /**
     * Charset nativo dell'OS: cloc (Perl) legge il list-file e scrive il report
     * usando la codepage di sistema, non UTF-8. Su Windows e' tipicamente
     * windows-1252, su Linux/macOS UTF-8. Usarlo per list-file e report evita
     * che i path con caratteri non-ASCII (es. "Universita'") vadano persi.
     */
    private static final Charset NATIVE_CHARSET = resolveNativeCharset();

    private ClocAnalyzer() {
    }

    private static Charset resolveNativeCharset() {
        for (String prop : new String[]{
                "native.encoding", "sun.jnu.encoding", "file.encoding"}) {
            String name = System.getProperty(prop);
            if (name != null && !name.isBlank()) {
                try {
                    return Charset.forName(name);
                } catch (Exception ignored) {
                    // prova la successiva
                }
            }
        }
        return Charset.defaultCharset();
    }

    /**
     * Esegue cloc su tutti i file forniti e restituisce, per ciascuno, il
     * numero di righe di codice.
     *
     * @param files percorsi assoluti dei file .java da analizzare
     * @return mappa chiave-file (path assoluto normalizzato) -> LOC
     */
    public static Map<String, Integer> countLoc(List<Path> files) {

        Map<String, Integer> counts = new HashMap<>();
        if (files == null || files.isEmpty()) {
            return counts;
        }

        List<Path> existing = new ArrayList<>();
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                Path key = file.toAbsolutePath().normalize();
                counts.put(key.toString(), 0);
                existing.add(key);
            }
        }
        if (existing.isEmpty()) {
            return counts;
        }

        Path listFile = null;
        Path reportFile = null;
        try {
            listFile = Files.createTempFile("cloc-list-", ".txt");
            reportFile = Files.createTempFile("cloc-report-", ".csv");

            List<String> lines = new ArrayList<>(existing.size());
            for (Path p : existing) {
                lines.add(p.toString());
            }
            Files.write(listFile, lines, NATIVE_CHARSET);

            runCloc(listFile, reportFile);
            parseReport(reportFile, counts);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.SEVERE,
                    "cloc analysis failed (LOC impostate a 0 per questa release)",
                    e);
        } finally {
            deleteQuietly(listFile);
            deleteQuietly(reportFile);
        }

        return counts;
    }

    private static void runCloc(Path listFile, Path reportFile)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(
                CLOC,
                "--quiet",
                "--csv",
                "--by-file",
                "--list-file=" + listFile.toAbsolutePath(),
                "--report-file=" + reportFile.toAbsolutePath());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // Consuma l'output per evitare blocchi su buffer pieni.
        String consoleOutput = new String(
                process.getInputStream().readAllBytes(),
                NATIVE_CHARSET);
        int exit = process.waitFor();

        if (exit != 0) {
            throw new IOException(
                    "cloc ha restituito exit code " + exit + ": " + consoleOutput);
        }
    }

    private static void parseReport(Path reportFile, Map<String, Integer> counts)
            throws IOException {

        List<String> reportLines =
                Files.readAllLines(reportFile, NATIVE_CHARSET);

        for (String line : reportLines) {

            if (line.isBlank()
                    || line.startsWith("language,filename")
                    || line.startsWith("SUM,")) {
                continue;
            }

            // Righe dati: language,filename,blank,comment,code
            // filename puo' contenere spazi ma non virgole: i tre campi
            // numerici sono sempre gli ultimi tre.
            String[] parts = line.split(",");
            if (parts.length < 5) {
                continue;
            }

            Integer code = tryParse(parts[parts.length - 1]);
            if (code == null) {
                continue;
            }

            int filenameEnd = parts.length - 3;
            StringBuilder filename = new StringBuilder(parts[1]);
            for (int i = 2; i < filenameEnd; i++) {
                filename.append(',').append(parts[i]);
            }

            String key = Paths.get(filename.toString())
                    .toAbsolutePath()
                    .normalize()
                    .toString();
            counts.computeIfPresent(key, (k, current) -> code);
        }
    }

    private static Integer tryParse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best effort: file temporaneo, ignorabile
        }
    }
}

package it.uniroma2.isw2.jira;

import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fornisce l'insieme delle key dei bug fixati di un progetto, usato per
 * classificare i commit come "bug fixing" (metrica NFix): un commit e' un bug
 * fix se referenzia una di queste key.
 * <p>
 * Il recupero da JIRA e' delegato a {@link RetrieveTicketsID} (la classe con la
 * query fornita nei materiali del corso); questa classe aggiunge solo lo strato
 * di <b>cache</b> su file locale, cosi' la generazione del dataset non dipende
 * dalla rete ad ogni esecuzione ed e' riproducibile.
 */
public final class BugTicketLoader {

    private static final Logger LOGGER =
            Logger.getLogger(BugTicketLoader.class.getName());

    private BugTicketLoader() {
    }

    /**
     * Restituisce le key dei bug fixati del progetto. Se il file di cache
     * esiste lo legge, altrimenti interroga JIRA e lo salva.
     *
     * @param projectName chiave progetto JIRA (es. "TAJO")
     * @param cacheFile   file di cache (una key per riga); puo' essere null
     * @return insieme delle key (es. "TAJO-1234"), in maiuscolo
     */
    public static Set<String> loadFixedBugKeys(String projectName, Path cacheFile)
            throws IOException {

        if (cacheFile != null && Files.isRegularFile(cacheFile)) {
            Set<String> cached = new HashSet<>();
            for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    cached.add(line.trim().toUpperCase());
                }
            }
            LOGGER.info(() -> "Bug keys da cache (" + cacheFile + "): " + cached.size());
            return cached;
        }

        Set<String> keys = fetchFromJira(projectName);

        if (cacheFile != null) {
            List<String> sorted = new ArrayList<>(keys);
            Collections.sort(sorted);
            Files.write(cacheFile, sorted, StandardCharsets.UTF_8);
            LOGGER.info(() -> "Bug keys salvate in cache: " + cacheFile);
        }
        return keys;
    }

    private static Set<String> fetchFromJira(String projectName) throws IOException {

        Set<String> keys = new HashSet<>();
        try {
            for (String key : RetrieveTicketsID.retrieveTicketsID(projectName)) {
                keys.add(key.toUpperCase());
            }
        } catch (JSONException e) {
            throw new IOException(
                    "Errore nel parsing della risposta JIRA", e);
        }
        LOGGER.info(() -> "Bug keys da JIRA (via RetrieveTicketsID): " + keys.size());
        return keys;
    }
}

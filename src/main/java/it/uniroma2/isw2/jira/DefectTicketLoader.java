package it.uniroma2.isw2.jira;

import it.uniroma2.isw2.model.DefectTicket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Carica i ticket JIRA chiusi/risolti con risoluzione Fixed, completi delle
 * informazioni richieste dal labeling Proportion Total. Il risultato viene
 * mantenuto in una cache JSON per rendere la generazione riproducibile e non
 * dipendente dalla rete a ogni esecuzione.
 */
public final class DefectTicketLoader {

    private static final Logger LOGGER =
            Logger.getLogger(DefectTicketLoader.class.getName());

    private static final int PAGE_SIZE = 1_000;

    private DefectTicketLoader() {
    }

    public static List<DefectTicket> loadClosedFixedDefects(
            String projectKey,
            Path cacheFile) throws IOException {

        if (cacheFile != null && Files.isRegularFile(cacheFile)) {
            List<DefectTicket> cached = readCache(cacheFile);
            LOGGER.info(() -> "Defect ticket da cache (" + cacheFile + "): "
                    + cached.size());
            return cached;
        }

        List<DefectTicket> tickets = fetchFromJira(projectKey);
        if (cacheFile != null) {
            writeCache(cacheFile, projectKey, tickets);
            LOGGER.info(() -> "Defect ticket salvati in cache: " + cacheFile);
        }
        return tickets;
    }

    private static List<DefectTicket> fetchFromJira(String projectKey)
            throws IOException {

        List<DefectTicket> tickets = new ArrayList<>();
        int startAt = 0;
        int total;

        try {
            do {
                JSONObject response = RetrieveTicketsID.readJsonFromUrl(
                        queryUrl(projectKey, startAt));
                total = response.getInt("total");
                JSONArray issues = response.getJSONArray("issues");
                for (int i = 0; i < issues.length(); i++) {
                    tickets.add(toTicket(issues.getJSONObject(i)));
                }
                startAt += issues.length();
            } while (startAt < total);
        } catch (JSONException e) {
            throw new IOException("Errore nel parsing della risposta JIRA", e);
        }

        tickets.sort(Comparator.comparing(DefectTicket::key));
        LOGGER.info(() -> "Defect ticket da JIRA: " + tickets.size());
        return tickets;
    }

    private static String queryUrl(String projectKey, int startAt) {
        return "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                + projectKey
                + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22"
                + "&fields=key,created,versions&startAt=" + startAt
                + "&maxResults=" + PAGE_SIZE;
    }

    private static DefectTicket toTicket(JSONObject issue) {
        JSONObject fields = issue.getJSONObject("fields");
        List<String> affected = new ArrayList<>();
        JSONArray versions = fields.optJSONArray("versions");
        if (versions != null) {
            for (int i = 0; i < versions.length(); i++) {
                affected.add(versions.getJSONObject(i).getString("name"));
            }
        }
        return new DefectTicket(
                issue.getString("key").toUpperCase(),
                parseJiraDate(fields.getString("created")),
                affected);
    }

    private static LocalDateTime parseJiraDate(String value) {
        return OffsetDateTime.parse(
                value,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                .toLocalDateTime();
    }

    private static List<DefectTicket> readCache(Path cacheFile)
            throws IOException {

        JSONObject root = new JSONObject(
                Files.readString(cacheFile, StandardCharsets.UTF_8));
        JSONArray rows = root.getJSONArray("tickets");
        List<DefectTicket> tickets = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            List<String> affected = new ArrayList<>();
            JSONArray versions = row.getJSONArray("affectedVersions");
            for (int j = 0; j < versions.length(); j++) {
                affected.add(versions.getString(j));
            }
            tickets.add(new DefectTicket(
                    row.getString("key").toUpperCase(),
                    LocalDateTime.parse(row.getString("openedAt")),
                    affected));
        }
        return tickets;
    }

    private static void writeCache(
            Path cacheFile,
            String projectKey,
            List<DefectTicket> tickets) throws IOException {

        JSONArray rows = new JSONArray();
        for (DefectTicket ticket : tickets) {
            JSONObject row = new JSONObject();
            row.put("key", ticket.key());
            row.put("openedAt", ticket.openedAt().toString());
            row.put("affectedVersions", ticket.affectedVersions());
            rows.put(row);
        }
        JSONObject root = new JSONObject();
        root.put("project", projectKey);
        root.put("tickets", rows);
        Files.writeString(cacheFile, root.toString(2), StandardCharsets.UTF_8);
    }
}

package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Informazioni JIRA minime necessarie al labeling Proportion Total.
 *
 * @param key              chiave JIRA del ticket (es. TAJO-123)
 * @param openedAt         data di apertura del ticket
 * @param affectedVersions versioni affette dichiarate in JIRA (AV)
 */
public record DefectTicket(
        String key,
        LocalDateTime openedAt,
        List<String> affectedVersions) {

    public DefectTicket {
        affectedVersions = List.copyOf(affectedVersions);
    }
}

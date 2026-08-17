package it.uniroma2.isw2.model;

import it.uniroma2.isw2.metrics.GitRepositoryAnalyzer;

import java.time.LocalDateTime;

/**
 * Una revisione (commit) che tocca una specifica classe.
 * <p>
 * E' l'unita' elementare della struttura condivisa costruita una sola volta da
 * {@link GitRepositoryAnalyzer}: contiene tutto cio' che serve per aggregare le
 * metriche di processo a livello di (classe, release), senza dover ri-scandire
 * la history Git.
 *
 * @param commitId       hash del commit
 * @param date           data del commit
 * @param author         identita' dell'autore (email, per NAuth / EXP futuri)
 * @param locAdded       linee aggiunte alla classe in questa revisione
 * @param locDeleted     linee eliminate dalla classe in questa revisione
 * @param changeSetSize  numero di file toccati dal commit (change set size)
 * @param changeSetDirs  numero di directory distinte toccate dal commit (ND)
 * @param bugFix         true se il commit e' un bug fix (referenzia un bug JIRA fixato)
 */
public record Revision(
        String commitId,
        LocalDateTime date,
        String author,
        int locAdded,
        int locDeleted,
        int changeSetSize,
        int changeSetDirs,
        boolean bugFix) {
}

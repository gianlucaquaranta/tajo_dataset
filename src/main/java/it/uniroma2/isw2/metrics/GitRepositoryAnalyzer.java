package it.uniroma2.isw2.metrics;

import it.uniroma2.isw2.model.ProcessMetrics;
import it.uniroma2.isw2.model.Revision;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legge la history Git una sola volta e costruisce una struttura condivisa che
 * alimenta tutte le metriche di processo del dataset, a livello di (classe,
 * release).
 * <p>
 * Per ogni commit non-merge (fino alla data massima delle release considerate)
 * calcola il diff verso il parent con <b>rename detection</b> attiva e accumula,
 * per ciascuna classe di produzione, la lista delle sue {@link Revision}
 * (linee aggiunte/eliminate, autore, data, dimensione del change set).
 * <p>
 * L'identita' di una classe e' un <i>track</i> che sopravvive a rinomine e
 * spostamenti: quando un commit rinomina {@code A -> B}, il track di A viene
 * trasferito a B, cosi' la storia pre-rinomina resta attribuita alla stessa
 * classe. Per ogni release viene salvata la fotografia {@code path -> track}
 * valida a quella data, per poter agganciare la classe (identificata dal path
 * nello snapshot) alla sua intera storia.
 */
public final class GitRepositoryAnalyzer {

    private static final Logger LOGGER =
            Logger.getLogger(GitRepositoryAnalyzer.class.getName());

    /**
     * Limite di rename detection di JGit. Il default (400) e' troppo basso per
     * i commit che spostano interi package (es. tajo.* -> org.apache.tajo.*,
     * 561 rinomine in un solo commit): oltre il limite JGit salta la detection
     * e i rename appaiono come ADD+DELETE, spezzando la storia della classe.
     */
    private static final int RENAME_LIMIT = 5000;

    /** Pattern di una issue key JIRA nel messaggio di commit (es. TAJO-1234). */
    private static final Pattern ISSUE_KEY =
            Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");

    /** Storia di ogni classe, indicizzata dall'id di track. */
    private final Map<Integer, List<Revision>> revisionsByTrack;

    /** Per ogni data-release: fotografia path (git-relative) -> track. */
    private final Map<LocalDateTime, Map<String, Integer>> pathToTrackByRelease;

    /** Per ogni data-release: numero di commit nel progetto per autore (esperienza). */
    private final Map<LocalDateTime, Map<String, Integer>> authorExpByRelease;

    /** Classi coinvolte nei fix, indicizzate dalla key JIRA del ticket. */
    private final Map<String, Set<Integer>> fixTracksByTicket;

    /** Data del primo fix commit noto per ciascun ticket JIRA. */
    private final Map<String, LocalDateTime> firstFixDateByTicket;

    private GitRepositoryAnalyzer(
            Map<Integer, List<Revision>> revisionsByTrack,
            Map<LocalDateTime, Map<String, Integer>> pathToTrackByRelease,
            Map<LocalDateTime, Map<String, Integer>> authorExpByRelease,
            Map<String, Set<Integer>> fixTracksByTicket,
            Map<String, LocalDateTime> firstFixDateByTicket) {
        this.revisionsByTrack = revisionsByTrack;
        this.pathToTrackByRelease = pathToTrackByRelease;
        this.authorExpByRelease = authorExpByRelease;
        this.fixTracksByTicket = fixTracksByTicket;
        this.firstFixDateByTicket = firstFixDateByTicket;
    }

    /**
     * Verifica se un path git-relative e' una classe di produzione
     * (stessa regola di JavaClassFinder: .java sotto src/main/java).
     */
    private static boolean isProductionClass(String path) {
        return path != null
                && path.endsWith(".java")
                && path.contains("/src/main/java/");
    }

    /**
     * Costruisce l'analizzatore scandendo la history una sola volta.
     *
     * @param git          repository aperto
     * @param mainBranch   nome del branch principale (es. "master")
     * @param releaseDates date delle release considerate; per ognuna viene
     *                     salvata la fotografia path->track a quella data
     * @param bugKeys      key dei bug fixati (per marcare i commit di bug fixing)
     * @return analizzatore pronto per {@link #metricsFor(String, LocalDateTime)}
     */
    public static GitRepositoryAnalyzer build(
            Git git,
            String mainBranch,
            List<LocalDateTime> releaseDates,
            Set<String> bugKeys)
            throws IOException {

        Repository repo = git.getRepository();
        ObjectId branch = repo.resolve(mainBranch);
        if (branch == null) {
            throw new IOException("Branch non trovato: " + mainBranch);
        }

        List<LocalDateTime> sortedDates = new ArrayList<>(releaseDates);
        sortedDates.sort(Comparator.naturalOrder());
        LocalDateTime maxDate = sortedDates.get(sortedDates.size() - 1);

        Map<Integer, List<Revision>> revisionsByTrack = new HashMap<>();
        Map<String, Integer> pathToTrack = new HashMap<>();
        Map<LocalDateTime, Map<String, Integer>> snapshots = new HashMap<>();
        // Numero di commit (non-merge) per autore nel progetto, con fotografia
        // ad ogni data-release: base per Min EXP.
        Map<String, Integer> authorCommitCount = new HashMap<>();
        Map<LocalDateTime, Map<String, Integer>> authorExpSnapshots = new HashMap<>();
        Map<String, Set<Integer>> fixTracksByTicket = new HashMap<>();
        Map<String, LocalDateTime> firstFixDateByTicket = new HashMap<>();
        int[] nextTrackId = {0};

        try (RevWalk walk = new RevWalk(repo);
             ObjectReader reader = repo.newObjectReader();
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diff.setRepository(repo);
            diff.setDetectRenames(true);
            diff.getRenameDetector().setRenameLimit(RENAME_LIMIT);

            List<RevCommit> commits = collectCommits(walk, branch, maxDate);
            // Dal piu' vecchio al piu' recente: i track evolvono in avanti.
            commits.sort(Comparator.comparingInt(RevCommit::getCommitTime));

            int dateIdx = 0;
            int processed = 0;
            for (RevCommit commit : commits) {

                LocalDateTime commitDate = toLocalDateTime(commit.getCommitTime());

                // Prima di elaborare un commit oltre una data-release, congela
                // le fotografie (path->track e commit-per-autore) per quella release.
                while (dateIdx < sortedDates.size()
                        && commitDate.isAfter(sortedDates.get(dateIdx))) {
                    snapshots.put(sortedDates.get(dateIdx),
                            new HashMap<>(pathToTrack));
                    authorExpSnapshots.put(sortedDates.get(dateIdx),
                            new HashMap<>(authorCommitCount));
                    dateIdx++;
                }

                // Esperienza: conta il commit (solo non-merge) per il suo autore.
                if (commit.getParentCount() <= 1) {
                    authorCommitCount.merge(authorOf(commit), 1, Integer::sum);
                }

                processCommit(commit, walk, diff, reader,
                        pathToTrack, revisionsByTrack, nextTrackId, bugKeys,
                        fixTracksByTicket, firstFixDateByTicket);

                if (++processed % 250 == 0) {
                    final int done = processed;
                    LOGGER.info(() -> "  history: " + done
                            + "/" + commits.size() + " commit analizzati");
                }
            }

            // Release con data >= dell'ultimo commit: fotografia finale.
            while (dateIdx < sortedDates.size()) {
                snapshots.put(sortedDates.get(dateIdx),
                        new HashMap<>(pathToTrack));
                authorExpSnapshots.put(sortedDates.get(dateIdx),
                        new HashMap<>(authorCommitCount));
                dateIdx++;
            }
        }

        LOGGER.info(() -> "History analizzata: "
                + revisionsByTrack.size() + " classi tracciate");
        return new GitRepositoryAnalyzer(
                revisionsByTrack,
                snapshots,
                authorExpSnapshots,
                fixTracksByTicket,
                firstFixDateByTicket);
    }

    private static List<RevCommit> collectCommits(
            RevWalk walk, ObjectId branch, LocalDateTime maxDate)
            throws IOException {

        List<RevCommit> commits = new ArrayList<>();
        walk.markStart(walk.parseCommit(branch));
        for (RevCommit commit : walk) {
            if (!toLocalDateTime(commit.getCommitTime()).isAfter(maxDate)) {
                commits.add(commit);
            }
        }
        return commits;
    }

    private static void processCommit(
            RevCommit commit,
            RevWalk walk,
            DiffFormatter diff,
            ObjectReader reader,
            Map<String, Integer> pathToTrack,
            Map<Integer, List<Revision>> revisionsByTrack,
            int[] nextTrackId,
            Set<String> bugKeys,
            Map<String, Set<Integer>> fixTracksByTicket,
            Map<String, LocalDateTime> firstFixDateByTicket) throws IOException {

        // Solo commit lineari: i merge (2+ parent) raddoppierebbero il churn.
        if (commit.getParentCount() > 1) {
            return;
        }

        AbstractTreeIterator oldTree;
        if (commit.getParentCount() == 1) {
            RevCommit parent = walk.parseCommit(commit.getParent(0));
            oldTree = treeIterator(reader, parent.getTree());
        } else {
            oldTree = new EmptyTreeIterator();   // commit radice
        }
        AbstractTreeIterator newTree = treeIterator(reader, commit.getTree());

        List<DiffEntry> entries = diff.scan(oldTree, newTree);
        int changeSetSize = entries.size();
        int changeSetDirs = countDirectories(entries);

        String author = authorOf(commit);
        LocalDateTime date = toLocalDateTime(commit.getCommitTime());
        Set<String> fixedTickets = matchingIssueKeys(commit, bugKeys);
        boolean bugFix = !fixedTickets.isEmpty();
        for (String ticket : fixedTickets) {
            firstFixDateByTicket.merge(ticket, date,
                    (oldDate, newDate) -> oldDate.isBefore(newDate) ? oldDate : newDate);
        }

        for (DiffEntry entry : entries) {
            String newPath = entry.getNewPath();
            String oldPath = entry.getOldPath();
            boolean newIsProd = isProductionClass(newPath);
            boolean oldIsProd = isProductionClass(oldPath);

            if (!newIsProd && !oldIsProd) {
                continue;   // file non di produzione: ignorato (conta solo nel changeSet)
            }

            Integer track = resolveTrack(
                    entry, oldPath, newPath, oldIsProd,
                    pathToTrack, nextTrackId);
            if (track == null) {
                continue;
            }

            for (String ticket : fixedTickets) {
                fixTracksByTicket
                        .computeIfAbsent(ticket, ignored -> new HashSet<>())
                        .add(track);
            }

            int[] ad = addedDeleted(diff, entry);
            revisionsByTrack
                    .computeIfAbsent(track, k -> new ArrayList<>())
                    .add(new Revision(commit.getName(), date, author,
                            ad[0], ad[1], changeSetSize, changeSetDirs, bugFix));
        }
    }

    /**
     * Numero di directory distinte modificate dal commit (metrica ND del change
     * set): per ogni file cambiato si considera la sua directory contenitrice.
     */
    private static int countDirectories(List<DiffEntry> entries) {
        Set<String> dirs = new HashSet<>();
        for (DiffEntry entry : entries) {
            String path = (entry.getChangeType() == DiffEntry.ChangeType.DELETE)
                    ? entry.getOldPath()
                    : entry.getNewPath();
            int slash = path.lastIndexOf('/');
            dirs.add(slash >= 0 ? path.substring(0, slash) : "");
        }
        return dirs.size();
    }

    /**
     * Un commit e' un bug fix se il suo messaggio referenzia la key di un bug
     * JIRA fixato. In Tajo quasi ogni commit cita una issue key, quindi il
     * discriminante e' l'appartenenza all'insieme dei bug (non il semplice
     * riferimento a una key).
     */
    private static Set<String> matchingIssueKeys(
            RevCommit commit,
            Set<String> bugKeys) {

        Set<String> matches = new HashSet<>();
        if (bugKeys.isEmpty()) {
            return matches;
        }
        Matcher matcher = ISSUE_KEY.matcher(
                commit.getFullMessage().toUpperCase());
        while (matcher.find()) {
            if (bugKeys.contains(matcher.group())) {
                matches.add(matcher.group());
            }
        }
        return matches;
    }

    /**
     * Determina il track della classe coinvolta nell'entry, aggiornando la
     * mappa path->track in base al tipo di modifica (add/modify/rename/delete).
     */
    private static Integer resolveTrack(
            DiffEntry entry,
            String oldPath,
            String newPath,
            boolean oldIsProd,
            Map<String, Integer> pathToTrack,
            int[] nextTrackId) {

        switch (entry.getChangeType()) {
            case ADD, COPY -> {
                int track = nextTrackId[0]++;
                pathToTrack.put(newPath, track);
                return track;
            }
            case DELETE -> {
                Integer track = pathToTrack.remove(oldPath);
                return (track != null) ? track : nextTrackId[0]++;
            }
            case RENAME -> {
                // trasferisce l'identita' storica dal vecchio al nuovo path
                Integer track = oldIsProd ? pathToTrack.remove(oldPath) : null;
                if (track == null) {
                    track = nextTrackId[0]++;
                }
                if (isProductionClass(newPath)) {
                    pathToTrack.put(newPath, track);
                }
                return track;
            }
            default -> {   // MODIFY
                return pathToTrack.computeIfAbsent(
                        newPath, k -> nextTrackId[0]++);
            }
        }
    }

    private static int[] addedDeleted(DiffFormatter diff, DiffEntry entry) {
        int added = 0;
        int deleted = 0;
        try {
            for (Edit edit : diff.toFileHeader(entry).toEditList()) {
                added += edit.getEndB() - edit.getBeginB();
                deleted += edit.getEndA() - edit.getBeginA();
            }
        } catch (IOException | RuntimeException e) {
            // file binario o diff non calcolabile: 0/0
            LOGGER.log(Level.FINE,
                    "diff non calcolabile per " + entry.getNewPath(), e);
        }
        return new int[]{added, deleted};
    }

    private static AbstractTreeIterator treeIterator(
            ObjectReader reader, RevTree tree) throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, tree);
        return parser;
    }

    private static String authorOf(RevCommit commit) {
        var ident = commit.getAuthorIdent();
        if (ident == null) {
            return "unknown";
        }
        String email = ident.getEmailAddress();
        return (email != null && !email.isBlank()) ? email : ident.getName();
    }

    private static LocalDateTime toLocalDateTime(int epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Restituisce l'identita' stabile della classe a una release, seguendo
     * automaticamente rinomine e spostamenti di package/modulo.
     */
    public Integer trackFor(String gitRelativePath, LocalDateTime releaseDate) {
        return pathToTrackByRelease
                .getOrDefault(releaseDate, Map.of())
                .get(gitRelativePath);
    }

    /** Track delle classi toccate dai commit di fix di un ticket JIRA. */
    public Set<Integer> fixTracksFor(String ticketKey) {
        return fixTracksByTicket.getOrDefault(ticketKey.toUpperCase(), Set.of());
    }

    /** Data del primo commit di fix che referenzia il ticket. */
    public java.util.Optional<LocalDateTime> firstFixDateFor(String ticketKey) {
        return java.util.Optional.ofNullable(
                firstFixDateByTicket.get(ticketKey.toUpperCase()));
    }

    /**
     * Metriche di processo per una classe (path git-relative, senza il prefisso
     * "tajo/") a una determinata release, aggregate cumulativamente su tutta la
     * sua storia fino a {@code releaseDate}.
     */
    public ProcessMetrics metricsFor(String gitRelativePath, LocalDateTime releaseDate) {

        Map<String, Integer> pathToTrack = pathToTrackByRelease.get(releaseDate);
        if (pathToTrack == null) {
            return ProcessMetrics.zero();
        }
        Integer track = pathToTrack.get(gitRelativePath);
        if (track == null) {
            return ProcessMetrics.zero();
        }

        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int nr = 0;
        int nFix = 0;
        int changeSetTotal = 0;
        int maxChangeSet = 0;
        int dirsTotal = 0;
        LocalDateTime lastChange = null;
        Set<String> authors = new HashSet<>();
        for (Revision r : revisionsByTrack.getOrDefault(track, List.of())) {
            if (r.date().isAfter(releaseDate)) {
                continue;
            }
            locAdded += r.locAdded();
            maxLocAdded = Math.max(maxLocAdded, r.locAdded());
            churn += r.locAdded() + r.locDeleted();
            nr++;
            if (r.bugFix()) {
                nFix++;
            }
            authors.add(r.author());
            changeSetTotal += r.changeSetSize();
            maxChangeSet = Math.max(maxChangeSet, r.changeSetSize());
            dirsTotal += r.changeSetDirs();
            if (lastChange == null || r.date().isAfter(lastChange)) {
                lastChange = r.date();
            }
        }

        double avgChurn = (nr > 0) ? (double) churn / nr : 0.0;
        double fixRate = (nr > 0) ? (double) nFix / nr : 0.0;
        double avgChangeSet = (nr > 0) ? (double) changeSetTotal / nr : 0.0;
        double avgNd = (nr > 0) ? (double) dirsTotal / nr : 0.0;

        // Giorni tra l'ultima modifica della classe e la data della release.
        int ageSinceLastChange = (lastChange != null)
                ? (int) ChronoUnit.DAYS.between(lastChange, releaseDate) : 0;

        // Min EXP: minimo, tra gli autori della classe, del numero di commit
        // che ciascuno ha fatto nel progetto fino a questa release.
        Map<String, Integer> expByAuthor =
                authorExpByRelease.getOrDefault(releaseDate, Map.of());
        int minExp = 0;
        if (!authors.isEmpty()) {
            minExp = Integer.MAX_VALUE;
            for (String author : authors) {
                minExp = Math.min(minExp, expByAuthor.getOrDefault(author, 0));
            }
        }

        return new ProcessMetrics(
                locAdded, maxLocAdded, churn, avgChurn,
                nr, nFix, fixRate, authors.size(),
                avgChangeSet, maxChangeSet, avgNd,
                ageSinceLastChange, minExp);
    }
}

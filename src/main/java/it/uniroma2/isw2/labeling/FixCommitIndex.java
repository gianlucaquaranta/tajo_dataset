package it.uniroma2.isw2.labeling;

import it.uniroma2.isw2.metrics.ClassTrackResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indice dedicato al labeling: scandisce l'intera history Git, senza il limite
 * dell'ultima release del dataset. Registra i commit di fix e le classi di
 * produzione coinvolte, preservando la loro identita' attraverso rinomine.
 */
public final class FixCommitIndex {

    private static final int RENAME_LIMIT = 5_000;
    private static final Pattern ISSUE_KEY =
            Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");

    private final Map<LocalDateTime, Map<String, Integer>> tracksByReleaseDate;
    private final Map<String, Set<Integer>> tracksByTicket;
    private final Map<String, LocalDateTime> firstFixDateByTicket;

    private FixCommitIndex(
            Map<LocalDateTime, Map<String, Integer>> tracksByReleaseDate,
            Map<String, Set<Integer>> tracksByTicket,
            Map<String, LocalDateTime> firstFixDateByTicket) {
        this.tracksByReleaseDate = tracksByReleaseDate;
        this.tracksByTicket = tracksByTicket;
        this.firstFixDateByTicket = firstFixDateByTicket;
    }

    public static FixCommitIndex build(
            Git git,
            String mainBranch,
            List<LocalDateTime> releaseDates,
            Set<String> bugKeys) throws IOException {

        Repository repository = git.getRepository();
        ObjectId branch = repository.resolve(mainBranch);
        if (branch == null) {
            throw new IOException("Branch non trovato: " + mainBranch);
        }

        List<LocalDateTime> sortedReleaseDates = new ArrayList<>(releaseDates);
        sortedReleaseDates.sort(Comparator.naturalOrder());
        Map<LocalDateTime, Map<String, Integer>> snapshots = new HashMap<>();
        Map<String, Set<Integer>> tracksByTicket = new HashMap<>();
        Map<String, LocalDateTime> firstFixDates = new HashMap<>();
        ClassTrackResolver tracks = new ClassTrackResolver();

        try (RevWalk walk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader();
             DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diff.setRepository(repository);
            diff.setDetectRenames(true);
            diff.getRenameDetector().setRenameLimit(RENAME_LIMIT);

            List<RevCommit> commits = collectAllCommits(walk, branch);
            commits.sort(Comparator.comparingInt(RevCommit::getCommitTime));

            int releaseIndex = 0;
            for (RevCommit commit : commits) {
                LocalDateTime commitDate = toLocalDateTime(commit.getCommitTime());
                while (releaseIndex < sortedReleaseDates.size()
                        && commitDate.isAfter(sortedReleaseDates.get(releaseIndex))) {
                    snapshots.put(sortedReleaseDates.get(releaseIndex), tracks.snapshot());
                    releaseIndex++;
                }
                processCommit(commit, walk, diff, reader, tracks, bugKeys,
                        tracksByTicket, firstFixDates);
            }

            while (releaseIndex < sortedReleaseDates.size()) {
                snapshots.put(sortedReleaseDates.get(releaseIndex), tracks.snapshot());
                releaseIndex++;
            }
        }

        return new FixCommitIndex(snapshots, tracksByTicket, firstFixDates);
    }

    public Integer trackFor(String gitRelativePath, LocalDateTime releaseDate) {
        return tracksByReleaseDate.getOrDefault(releaseDate, Map.of())
                .get(gitRelativePath);
    }

    public Set<Integer> tracksForTicket(String ticketKey) {
        return tracksByTicket.getOrDefault(ticketKey.toUpperCase(), Set.of());
    }

    public Optional<LocalDateTime> firstFixDateFor(String ticketKey) {
        return Optional.ofNullable(firstFixDateByTicket.get(ticketKey.toUpperCase()));
    }

    private static List<RevCommit> collectAllCommits(RevWalk walk, ObjectId branch)
            throws IOException {
        List<RevCommit> commits = new ArrayList<>();
        walk.markStart(walk.parseCommit(branch));
        for (RevCommit commit : walk) {
            commits.add(commit);
        }
        return commits;
    }

    private static void processCommit(
            RevCommit commit,
            RevWalk walk,
            DiffFormatter diff,
            ObjectReader reader,
            ClassTrackResolver tracks,
            Set<String> bugKeys,
            Map<String, Set<Integer>> tracksByTicket,
            Map<String, LocalDateTime> firstFixDates) throws IOException {

        Set<String> fixedTickets = matchingIssueKeys(commit, bugKeys);
        if (fixedTickets.isEmpty()) {
            advanceTracks(commit, walk, diff, reader, tracks, Set.of(),
                    tracksByTicket, firstFixDates);
            return;
        }

        advanceTracks(commit, walk, diff, reader, tracks, fixedTickets,
                tracksByTicket, firstFixDates);
    }

    private static void advanceTracks(
            RevCommit commit,
            RevWalk walk,
            DiffFormatter diff,
            ObjectReader reader,
            ClassTrackResolver tracks,
            Set<String> fixedTickets,
            Map<String, Set<Integer>> tracksByTicket,
            Map<String, LocalDateTime> firstFixDates) throws IOException {

        AbstractTreeIterator oldTree = commit.getParentCount() == 1
                ? treeIterator(reader, walk.parseCommit(commit.getParent(0)).getTree())
                : new EmptyTreeIterator();
        List<DiffEntry> entries = diff.scan(oldTree, treeIterator(reader, commit.getTree()));
        LocalDateTime date = toLocalDateTime(commit.getCommitTime());

        for (String ticket : fixedTickets) {
            firstFixDates.merge(ticket, date,
                    (oldDate, newDate) -> oldDate.isBefore(newDate) ? oldDate : newDate);
        }

        for (DiffEntry entry : entries) {
            String oldPath = entry.getOldPath();
            String newPath = entry.getNewPath();
            boolean oldIsProduction = ClassTrackResolver.isProductionClass(oldPath);
            boolean newIsProduction = ClassTrackResolver.isProductionClass(newPath);
            if (!oldIsProduction && !newIsProduction) {
                continue;
            }

            Integer track = tracks.resolve(entry, oldPath, newPath, oldIsProduction);
            for (String ticket : fixedTickets) {
                tracksByTicket.computeIfAbsent(ticket, ignored -> new HashSet<>()).add(track);
            }
        }
    }

    private static Set<String> matchingIssueKeys(RevCommit commit, Set<String> bugKeys) {
        Set<String> matches = new HashSet<>();
        Matcher matcher = ISSUE_KEY.matcher(commit.getFullMessage().toUpperCase());
        while (matcher.find()) {
            if (bugKeys.contains(matcher.group())) {
                matches.add(matcher.group());
            }
        }
        return matches;
    }

    private static AbstractTreeIterator treeIterator(ObjectReader reader, RevTree tree)
            throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, tree);
        return parser;
    }

    private static LocalDateTime toLocalDateTime(int epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}

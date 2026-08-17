package it.uniroma2.isw2.labeling;

import it.uniroma2.isw2.metrics.GitRepositoryAnalyzer;
import it.uniroma2.isw2.model.DefectTicket;
import it.uniroma2.isw2.model.Release;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Ground-truth labeler basato sulla tecnica Proportion Total.
 * <p>
 * La proporzione P viene stimata usando tutti i ticket con AV consistente
 * dell'intera storia disponibile. Per i ticket senza AV, IV viene stimata con
 * {@code IV = OV - P * (FV - OV)}. Una classe e' buggy nelle release
 * {@code [IV, FV)}, cioe' FV e' esclusa.
 */
public final class ProportionTotalLabeler {

    private static final Logger LOGGER =
            Logger.getLogger(ProportionTotalLabeler.class.getName());

    private final Map<Integer, Set<Integer>> buggyTracksByReleaseId;
    private final GitRepositoryAnalyzer history;
    private final double proportionTotal;

    private ProportionTotalLabeler(
            Map<Integer, Set<Integer>> buggyTracksByReleaseId,
            GitRepositoryAnalyzer history,
            double proportionTotal) {
        this.buggyTracksByReleaseId = buggyTracksByReleaseId;
        this.history = history;
        this.proportionTotal = proportionTotal;
    }

    public static ProportionTotalLabeler build(
            List<Release> allReleases,
            List<DefectTicket> tickets,
            GitRepositoryAnalyzer history) {

        List<Release> releases = new ArrayList<>(allReleases);
        releases.sort(Comparator.comparing(Release::getReleaseDate));

        Map<String, Integer> indexByName = new HashMap<>();
        for (int i = 0; i < releases.size(); i++) {
            indexByName.put(normalize(releases.get(i).getReleaseName()), i);
        }

        List<TicketWindow> windows = new ArrayList<>();
        List<Double> proportions = new ArrayList<>();
        for (DefectTicket ticket : tickets) {
            Optional<TicketWindow> window = baseWindow(ticket, releases, history);
            if (window.isEmpty()) {
                continue;
            }
            TicketWindow base = window.get();
            Integer iv = oldestAffectedVersion(ticket, indexByName);
            if (iv != null && iv <= base.openingVersion()) {
                windows.add(base.withInjectedVersion(iv));
                proportions.add((double) (base.openingVersion() - iv)
                        / (base.fixedVersion() - base.openingVersion()));
            } else {
                windows.add(base);
            }
        }

        double total = proportions.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Map<Integer, Set<Integer>> buggyTracks = new HashMap<>();
        int labeledTickets = 0;
        int estimatedTickets = 0;
        for (TicketWindow window : windows) {
            int iv = window.injectedVersion() != null
                    ? window.injectedVersion()
                    : estimateInjectedVersion(window, total);
            if (window.injectedVersion() == null) {
                estimatedTickets++;
            }
            Set<Integer> tracks = history.fixTracksFor(window.key());
            if (tracks.isEmpty()) {
                continue;
            }
            labeledTickets++;
            for (int i = iv; i < window.fixedVersion(); i++) {
                buggyTracks.computeIfAbsent(releases.get(i).getReleaseId(),
                        ignored -> new HashSet<>()).addAll(tracks);
            }
        }

        final int knownCount = proportions.size();
        final int labeledCount = labeledTickets;
        final int estimatedCount = estimatedTickets;
        LOGGER.info(() -> "Proportion Total P=" + total
                + " (" + knownCount + " ticket con AV); ticket etichettanti="
                + labeledCount + ", IV stimata=" + estimatedCount);
        return new ProportionTotalLabeler(buggyTracks, history, total);
    }

    public boolean isBuggy(String gitRelativePath, Release release) {
        return isBuggy(gitRelativePath,
                release.getReleaseId(), release.getReleaseDate());
    }

    public boolean isBuggy(
            String gitRelativePath,
            int releaseId,
            LocalDateTime releaseDate) {

        Integer track = history.trackFor(gitRelativePath, releaseDate);
        return track != null
                && buggyTracksByReleaseId
                .getOrDefault(releaseId, Set.of())
                .contains(track);
    }

    public double proportionTotal() {
        return proportionTotal;
    }

    private static Optional<TicketWindow> baseWindow(
            DefectTicket ticket,
            List<Release> releases,
            GitRepositoryAnalyzer history) {

        int ov = firstReleaseAtOrAfter(releases, ticket.openedAt());
        Optional<LocalDateTime> fixDate = history.firstFixDateFor(ticket.key());
        if (ov < 0 || fixDate.isEmpty()) {
            return Optional.empty();
        }
        int fv = firstReleaseAtOrAfter(releases, fixDate.get());
        if (fv <= ov || fv < 0) {
            return Optional.empty();
        }
        return Optional.of(new TicketWindow(ticket.key(), ov, fv, null));
    }

    private static int firstReleaseAtOrAfter(
            List<Release> releases,
            LocalDateTime date) {
        for (int i = 0; i < releases.size(); i++) {
            if (!releases.get(i).getReleaseDate().isBefore(date)) {
                return i;
            }
        }
        return -1;
    }

    private static Integer oldestAffectedVersion(
            DefectTicket ticket,
            Map<String, Integer> indexByName) {

        return ticket.affectedVersions().stream()
                .map(ProportionTotalLabeler::normalize)
                .map(indexByName::get)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static int estimateInjectedVersion(TicketWindow window, double p) {
        int estimate = (int) Math.round(window.openingVersion()
                - p * (window.fixedVersion() - window.openingVersion()));
        return Math.max(0, Math.min(window.openingVersion(), estimate));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase();
    }

    private record TicketWindow(
            String key,
            int openingVersion,
            int fixedVersion,
            Integer injectedVersion) {

        TicketWindow withInjectedVersion(int iv) {
            return new TicketWindow(key, openingVersion, fixedVersion, iv);
        }
    }
}

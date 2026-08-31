package it.uniroma2.isw2.metrics;

import org.eclipse.jgit.diff.DiffEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * Mantiene un'identita' stabile per le classi di produzione mentre una history
 * Git avanza nel tempo. L'identita' sopravvive a rinomine e spostamenti.
 */
public final class ClassTrackResolver {

    private final Map<String, Integer> pathToTrack = new HashMap<>();
    private int nextTrackId;

    public Integer resolve(DiffEntry entry, String oldPath, String newPath,
                           boolean oldIsProductionClass) {
        return switch (entry.getChangeType()) {
            case ADD, COPY -> add(newPath);
            case DELETE -> remove(oldPath);
            case RENAME -> rename(oldPath, newPath, oldIsProductionClass);
            default -> pathToTrack.computeIfAbsent(newPath, ignored -> nextTrackId++);
        };
    }

    public Map<String, Integer> snapshot() {
        return new HashMap<>(pathToTrack);
    }

    public static boolean isProductionClass(String path) {
        return path != null
                && path.endsWith(".java")
                && path.contains("/src/main/java/");
    }

    private int add(String newPath) {
        int track = nextTrackId++;
        pathToTrack.put(newPath, track);
        return track;
    }

    private int remove(String oldPath) {
        Integer track = pathToTrack.remove(oldPath);
        return (track != null) ? track : nextTrackId++;
    }

    private int rename(String oldPath, String newPath,
                       boolean oldIsProductionClass) {
        Integer track = oldIsProductionClass ? pathToTrack.remove(oldPath) : null;
        if (track == null) {
            track = nextTrackId++;
        }
        if (isProductionClass(newPath)) {
            pathToTrack.put(newPath, track);
        }
        return track;
    }
}

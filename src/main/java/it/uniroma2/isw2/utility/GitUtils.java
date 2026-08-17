package it.uniroma2.isw2.utility;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class GitUtils {

    private static final String MAIN_BRANCH = "master";

    private GitUtils(){}

    public static RevCommit getLastCommitBeforeDate(
            Git git,
            LocalDateTime releaseDate)
            throws Exception {

        RevCommit best = null;

        // IMPORTANTE: scandire sempre la history a partire dal branch
        // principale, non da HEAD. Dopo il checkout di una release HEAD e'
        // detached su un commit passato, e git.log() (che parte da HEAD)
        // vedrebbe solo i suoi antenati: tutte le release successive
        // collasserebbero sullo stesso snapshot della prima release.
        ObjectId branch = git.getRepository().resolve(MAIN_BRANCH);
        Iterable<RevCommit> history = (branch != null)
                ? git.log().add(branch).call()
                : git.log().call();

        for (RevCommit commit : history) {

            LocalDateTime commitDate =
                    Instant.ofEpochSecond(
                                    commit.getCommitTime())
                            .atZone(
                                    ZoneId.systemDefault())
                            .toLocalDateTime();

            if(commitDate.isAfter(releaseDate))
                continue;

            if(best == null) {
                best = commit;
                continue;
            }

            LocalDateTime currentBestDate =
                    Instant.ofEpochSecond(
                                    best.getCommitTime())
                            .atZone(
                                    ZoneId.systemDefault())
                            .toLocalDateTime();

            if(commitDate.isAfter(currentBestDate)) {
                best = commit;
            }
        }

        return best;
    }

    public static void checkout(
            Git git,
            String commitHash)
            throws Exception {

        git.checkout()
                .setName(commitHash)
                .call();
    }

    public static void checkoutMaster(
            Git git)
            throws Exception {

        git.checkout()
                .setName(MAIN_BRANCH)
                .call();
    }
}
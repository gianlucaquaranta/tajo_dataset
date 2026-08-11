package it.uniroma2.isw2.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class GitUtils {

    private GitUtils(){}

    public static RevCommit getLastCommitBeforeDate(
            Git git,
            LocalDateTime releaseDate)
            throws Exception {

        RevCommit best = null;

        for (RevCommit commit : git.log().call()) {

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
                .setName("master")
                .call();
    }
}
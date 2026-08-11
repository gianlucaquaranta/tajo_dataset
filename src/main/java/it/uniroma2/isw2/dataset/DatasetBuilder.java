package it.uniroma2.isw2.dataset;

import com.opencsv.CSVWriter;
import it.uniroma2.isw2.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import it.uniroma2.isw2.utility.CsvUtils;
import it.uniroma2.isw2.git.GitUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

public class DatasetBuilder {

    private static final Logger LOGGER =
            Logger.getLogger(DatasetBuilder.class.getName());

    private static final String REPOSITORY_PATH =
            "C:\\Users\\gianl\\Desktop\\Università\\Magistrale\\ISW2\\ISW2_Project\\tajo";

    private static final String RELEASE_CSV =
            "TAJOVersionInfo.csv";

    public static void main(String[] args)
            throws Exception {

        List<Release> releases =
                ReleaseLoader.load(RELEASE_CSV);

        releases =
                ReleaseLoader.keepFirst34Percent(
                        releases
                );

        Git git =
                Git.open(
                        new File(REPOSITORY_PATH)
                );

        CSVWriter writer =
                CsvUtils.createWriter(
                        "dataset_tajo.csv"
                );

        for (Release release : releases) {

            LOGGER.info(() ->
                    "Processing release " + release.getReleaseId());

            RevCommit commit =
                    GitUtils
                            .getLastCommitBeforeDate(
                                    git,
                                    release
                                            .getReleaseDate()
                            );

            if (commit == null) {
                continue;
            }

            GitUtils.checkout(
                    git,
                    commit.getName()
            );

            List<String> classes =
                    JavaClassFinder
                            .findProductionClasses(
                                    Paths.get(
                                            REPOSITORY_PATH
                                    )
                            );

            CsvUtils.writeRows(
                    writer,
                    classes,
                    release.getReleaseId(),
                    release.getReleaseName(),
                    REPOSITORY_PATH
            );
        }

        writer.close();

        GitUtils.checkoutMaster(git);

        LOGGER.info("DONE");
    }
}
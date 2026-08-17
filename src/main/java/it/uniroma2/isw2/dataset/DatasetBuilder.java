package it.uniroma2.isw2.dataset;

import com.opencsv.CSVWriter;
import it.uniroma2.isw2.jira.DefectTicketLoader;
import it.uniroma2.isw2.labeling.ProportionTotalLabeler;
import it.uniroma2.isw2.metrics.GitRepositoryAnalyzer;
import it.uniroma2.isw2.model.DefectTicket;
import it.uniroma2.isw2.model.Release;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import it.uniroma2.isw2.utility.CsvUtils;
import it.uniroma2.isw2.utility.GitUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public class DatasetBuilder {

    private static final Logger LOGGER =
            Logger.getLogger(DatasetBuilder.class.getName());

    private static final String REPOSITORY_PATH =
            "C:\\Users\\gianl\\Desktop\\Università\\Magistrale\\ISW2\\ISW2_Project\\tajo";

    private static final String RELEASE_CSV =
            "TAJOVersionInfo.csv";

    private static final String MAIN_BRANCH = "master";

    private static final String PROJECT_KEY = "TAJO";

    private static final String DEFECT_TICKETS_CACHE = "tajo_defect_tickets.json";

    private static final String DATASET_OUTPUT = "dataset_tajo.csv";

    public static void main(String[] args)
            throws Exception {

        List<Release> allReleases =
                ReleaseLoader.load(RELEASE_CSV);

        List<Release> releases =
                ReleaseLoader.keepFirst34Percent(
                        allReleases
                );

        Git git = Git.open(new File(REPOSITORY_PATH));
        var outputPath = Paths.get(DATASET_OUTPUT);
        var temporaryOutput = outputPath.resolveSibling(
                outputPath.getFileName() + ".tmp");

        try {
            // Ticket defect chiusi/fixed completi di data di apertura e AV. La cache
            // JSON e' usata sia per NFix sia per il labeling Proportion Total.
            List<DefectTicket> defectTickets =
                    DefectTicketLoader.loadClosedFixedDefects(
                            PROJECT_KEY, Paths.get(DEFECT_TICKETS_CACHE));
            Set<String> bugKeys = defectTickets.stream()
                    .map(DefectTicket::key)
                    .collect(Collectors.toSet());

        // Scansione unica della history: struttura condivisa per tutte le
        // metriche di processo (LOC Added, Churn, NR, NFix, ...). Costruita una
        // sola volta, prima dei checkout, perchè legge dall'object DB.
        List<LocalDateTime> releaseDates =
                allReleases.stream()
                        .map(Release::getReleaseDate)
                        .toList();
        LOGGER.info("Analisi della history Git (una volta sola)...");
        GitRepositoryAnalyzer gitAnalyzer =
                GitRepositoryAnalyzer.build(
                        git, MAIN_BRANCH, releaseDates, bugKeys);

        // Ground truth: P e IV usano tutti i ticket e tutte le release, mentre
        // le righe che verranno scritte restano solo quelle della M1.
        ProportionTotalLabeler labeler = ProportionTotalLabeler.build(
                allReleases, defectTickets, gitAnalyzer);

            try (CSVWriter writer = CsvUtils.createWriter(temporaryOutput.toString())) {

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
                    REPOSITORY_PATH,
                    gitAnalyzer,
                    release.getReleaseDate(),
                    labeler
            );
                }
            }

            moveDataset(temporaryOutput, outputPath);
        } finally {
            Files.deleteIfExists(temporaryOutput);
            GitUtils.checkoutMaster(git);
            git.close();
        }

        LOGGER.info("DONE");
    }

    private static void moveDataset(
            java.nio.file.Path temporaryOutput,
            java.nio.file.Path outputPath) throws IOException {

        try {
            Files.move(temporaryOutput, outputPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporaryOutput, outputPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
